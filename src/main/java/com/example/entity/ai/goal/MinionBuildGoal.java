package com.example.entity.ai.goal;

import com.example.construction.ConstructionManager;
import com.example.construction.ConstructionSession;
import com.example.construction.ConstructionTask;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Autonomous construction AI goal for {@link MinionEntity}.
 * Minions dynamically locate their master's active {@link ConstructionSession},
 * claim topological block placement tasks, navigate to the target coordinates,
 * and execute placement.
 *
 * Supports two resource paradigms:
 * 1. Creative Mode: instant, infinite placement at zero resource cost.
 * 2. Survival Mode: validates minion's 9-slot inventory first; if missing,
 *    scavenges nearby containers (chests, barrels, shulker boxes) within 12 blocks;
 *    if absent, emits smoke, dispenser fail sound, releases the task, and notifies the player.
 */
public class MinionBuildGoal extends Goal {

	private final MinionEntity minion;
	private ConstructionSession currentSession = null;
	private ConstructionTask currentTask = null;

	private int ticksNavigating = 0;
	private int workTicks = 0;
	private long failureCooldownUntilTick = 0L;

	// Scaffolding navigation, climbing, and descent state
	private BlockPos activeScaffoldColumn = null;
	private int targetScaffoldTopY = -1;
	private int targetScaffoldBottomY = -1;
	private boolean isAscendingScaffolding = false;
	private boolean isDescendingScaffolding = false;
	private int climbTicks = 0;
	private int descentTicks = 0;
	private int stallTicks = 0;
	private double lastClimbY = 0.0D;
	private final Set<BlockPos> blacklistedScaffoldColumns = new HashSet<>();
	private ConstructionTask pendingNextTask = null;

	// Equipment preservation across construction cycles
	private ItemStack savedHeldWeapon = ItemStack.EMPTY;

	public MinionBuildGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		// Minion must be alive, tamed, not sitting, and have an owner
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.getOwnerUuid() == null) {
			return false;
		}

		// Architectural gating: BUILDER participates in construction & deconstruction; MINER participates in deconstruction
		MinionRole role = this.minion.getRole();
		if (role != MinionRole.BUILDER && role != MinionRole.MINER) {
			return false;
		}

		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}

		// Check failure / retry cooldown
		if (serverWorld.getTime() < this.failureCooldownUntilTick) {
			return false;
		}

		// Find nearest active construction/dismantle session belonging to minion's master within 48 blocks
		Optional<ConstructionSession> sessionOpt = ConstructionManager.getInstance().findNearestSessionForMinion(
			serverWorld,
			this.minion.getBlockPos(),
			this.minion.getOwnerUuid(),
			48.0D,
			role
		);

		if (sessionOpt.isEmpty()) {
			return false;
		}

		ConstructionSession session = sessionOpt.get();
		if (role == MinionRole.MINER && !session.isDismantle()) {
			return false;
		}

		ConstructionTask task = session.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
		if (task == null) {
			return false;
		}

		this.currentSession = session;
		this.currentTask = task;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}

		// Continue goal while descending scaffolding to reach safe ground
		if (this.isDescendingScaffolding) {
			return true;
		}

		if (this.currentSession == null || !this.currentSession.isActive()) {
			return false;
		}

		if (this.currentTask == null || !this.currentTask.isClaimed() || !Objects.equals(this.currentTask.getClaimedBy(), this.minion.getUuid())) {
			return false;
		}

		return !this.currentTask.isCompleted();
	}

	@Override
	public void start() {
		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.isAscendingScaffolding = false;
		this.isDescendingScaffolding = false;
		this.minion.setClimbingScaffolding(false);
		this.climbTicks = 0;
		this.stallTicks = 0;
		this.lastClimbY = this.minion.getY();
		this.descentTicks = 0;
		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.targetScaffoldBottomY = -1;
		this.pendingNextTask = null;

		if (this.currentTask != null) {
			// Preserve held weapon before equipping preview block or dismantle tool
			saveHeldWeapon();

			// Visually equip the item in main hand while working
			if (this.currentSession != null && this.currentSession.isDismantle()) {
				ItemStack dismantleTool = resolveDismantleTool();
				this.minion.equipStack(EquipmentSlot.MAINHAND, dismantleTool);
			} else {
				ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
				this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);
			}

			if (this.minion.getWorld() instanceof ServerWorld serverWorld) {
				setupNavigationForTask(serverWorld);
			}
		}
	}

	@Override
	public void stop() {
		if (this.currentTask != null && !this.currentTask.isCompleted() && this.currentSession != null) {
			this.currentSession.releaseTask(this.currentTask);
		}

		if (this.activeScaffoldColumn != null && this.currentSession != null) {
			this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
		}
		if (this.currentSession != null) {
			this.currentSession.releaseScaffoldColumnsForMinion(this.minion.getUuid());
		}

		this.currentTask = null;
		this.pendingNextTask = null;
		this.currentSession = null;
		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.targetScaffoldBottomY = -1;
		this.isAscendingScaffolding = false;
		this.isDescendingScaffolding = false;
		this.minion.setClimbingScaffolding(false);
		this.climbTicks = 0;
		this.stallTicks = 0;
		this.descentTicks = 0;
		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.blacklistedScaffoldColumns.clear();
		this.minion.getNavigation().stop();
		this.minion.fallDistance = 0.0F;

		// Restore held weapon when stopping
		restoreHeldWeapon();
	}

	@Override
	public void tick() {
		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}

		// Controlled descent handler when transitioning down scaffolding columns
		if (this.isDescendingScaffolding) {
			this.descentTicks++;
			this.minion.getNavigation().stop();
			this.minion.fallDistance = 0.0F;

			double scCenterX = (this.activeScaffoldColumn != null ? this.activeScaffoldColumn.getX() : this.minion.getBlockX()) + 0.5D;
			double scCenterZ = (this.activeScaffoldColumn != null ? this.activeScaffoldColumn.getZ() : this.minion.getBlockZ()) + 0.5D;
			double alignX = scCenterX - this.minion.getX();
			double alignZ = scCenterZ - this.minion.getZ();

			// Removing scaffolding on descent in DISMANTLE mode or demobilization
			if (shouldTeardownOnDescent()) {
				int minionY = this.minion.getBlockY();
				for (int y = this.targetScaffoldTopY; y > minionY + 1; y--) {
					BlockPos scaffoldPos = new BlockPos(this.activeScaffoldColumn.getX(), y, this.activeScaffoldColumn.getZ());
					if (this.currentSession.isTemporaryScaffolding(scaffoldPos) && serverWorld.getBlockState(scaffoldPos).isOf(Blocks.SCAFFOLDING)) {
						removeScaffoldBlockWithFeedback(serverWorld, scaffoldPos);
					}
				}
			}

			boolean onSolidGround = this.minion.isOnGround() && !serverWorld.getBlockState(this.minion.getBlockPos().down()).isOf(Blocks.SCAFFOLDING);
			if (this.minion.getY() <= this.targetScaffoldBottomY + 0.15D || onSolidGround || this.descentTicks > 120) {
				// Reached safe ground
				// Clean up remaining scaffold column blocks that were descended
				if (shouldTeardownOnDescent()) {
					int groundY = Math.max(serverWorld.getBottomY() + 1, (int) Math.floor(this.targetScaffoldBottomY));
					for (int y = groundY; y <= this.targetScaffoldTopY; y++) {
						BlockPos scaffoldPos = new BlockPos(this.activeScaffoldColumn.getX(), y, this.activeScaffoldColumn.getZ());
						if (this.currentSession.isTemporaryScaffolding(scaffoldPos) && serverWorld.getBlockState(scaffoldPos).isOf(Blocks.SCAFFOLDING)) {
							removeScaffoldBlockWithFeedback(serverWorld, scaffoldPos);
						}
					}
				}

				if (this.activeScaffoldColumn != null && this.currentSession != null) {
					this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
				}

				this.isDescendingScaffolding = false;
				this.minion.setClimbingScaffolding(false);
				this.descentTicks = 0;
				this.minion.setVelocity(0.0D, 0.0D, 0.0D);
				this.minion.velocityModified = true;
				this.minion.fallDistance = 0.0F;
				this.activeScaffoldColumn = null;
				this.targetScaffoldTopY = -1;
				this.targetScaffoldBottomY = -1;

				if (this.pendingNextTask != null) {
					this.currentTask = this.pendingNextTask;
					this.pendingNextTask = null;
					if (this.currentSession != null && this.currentSession.isDismantle()) {
						this.minion.equipStack(EquipmentSlot.MAINHAND, resolveDismantleTool());
					} else {
						ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
						this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);
					}
					setupNavigationForTask(serverWorld);
				} else if (this.currentTask != null) {
					setupNavigationForTask(serverWorld);
				} else {
					restoreHeldWeapon();
				}
				return;
			}

			// Controlled downward descent through scaffolding column
			this.minion.setVelocity(alignX * 0.25D, -0.22D, alignZ * 0.25D);
			this.minion.velocityModified = true;
			this.minion.fallDistance = 0.0F;

			if (this.descentTicks % 8 == 0) {
				serverWorld.playSound(
					null,
					this.minion.getX(),
					this.minion.getY(),
					this.minion.getZ(),
					SoundEvents.BLOCK_SCAFFOLDING_STEP,
					SoundCategory.BLOCKS,
					0.7F,
					0.9F
				);
				serverWorld.spawnParticles(
					ParticleTypes.CLOUD,
					this.minion.getX(),
					this.minion.getY(),
					this.minion.getZ(),
					2,
					0.1,
					0.05,
					0.1,
					0.01
				);
			}
			return;
		}

		// Kinematic platform ascent handler (runs before inRange checks to prevent reach interruptions)
		if (this.isAscendingScaffolding) {
			this.climbTicks++;
			this.minion.getNavigation().stop();
			this.minion.fallDistance = 0.0F;

			if (this.activeScaffoldColumn == null || this.targetScaffoldTopY == -1) {
				abortClimbAndDescend(serverWorld);
				return;
			}

			double scCenterX = this.activeScaffoldColumn.getX() + 0.5D;
			double scCenterZ = this.activeScaffoldColumn.getZ() + 0.5D;
			double alignX = scCenterX - this.minion.getX();
			double alignZ = scCenterZ - this.minion.getZ();

			// Ceiling collision sensor: 2 blocks above feet (head clearance)
			BlockPos headBlockPos = BlockPos.ofFloored(this.minion.getX(), this.minion.getY() + 2.0D, this.minion.getZ());
			BlockState headState = serverWorld.getBlockState(headBlockPos);
			boolean ceilingBlocked = !headState.isAir() && !headState.isOf(Blocks.SCAFFOLDING) && !headState.canPathfindThrough(NavigationType.LAND);

			// Stall sensor: detect if vertical displacement is stalled for >10 ticks
			if (this.minion.getY() <= this.lastClimbY + 0.05D) {
				this.stallTicks++;
			} else {
				this.stallTicks = 0;
				this.lastClimbY = this.minion.getY();
			}

			if (ceilingBlocked || this.stallTicks > 10 || this.climbTicks > 140) {
				abortClimbAndDescend(serverWorld);
				return;
			}

			// Ascend vertically until reaching platform surface at targetScaffoldTopY + 1.0D
			if (this.minion.getY() < (double) this.targetScaffoldTopY + 0.95D) {
				// Vertical climb impulse (+0.25D) with horizontal centering lock
				this.minion.setVelocity(alignX * 0.3D, 0.25D, alignZ * 0.3D);
				this.minion.velocityModified = true;
				this.minion.fallDistance = 0.0F;
				this.minion.setJumping(true);

				// Auditory and visual climbing feedback
				if (this.climbTicks % 8 == 0) {
					serverWorld.playSound(
						null,
						this.minion.getX(),
						this.minion.getY(),
						this.minion.getZ(),
						SoundEvents.BLOCK_SCAFFOLDING_STEP,
						SoundCategory.BLOCKS,
						0.7F,
						1.2F
					);
					serverWorld.spawnParticles(
						ParticleTypes.CLOUD,
						this.minion.getX(),
						this.minion.getY(),
						this.minion.getZ(),
						2,
						0.1,
						0.05,
						0.1,
						0.01
					);
				}
				return;
			} else {
				// True kinematic platform landing! Cleanly snap minion to surface atop the scaffolding block
				this.minion.setPosition(scCenterX, (double) this.targetScaffoldTopY + 1.0D, scCenterZ);
				this.minion.setVelocity(0.0D, 0.0D, 0.0D);
				this.minion.velocityModified = true;
				this.minion.fallDistance = 0.0F;
				this.minion.setJumping(false);
				this.minion.setClimbingScaffolding(false);
				this.isAscendingScaffolding = false;
				this.climbTicks = 0;
				this.stallTicks = 0;

				serverWorld.playSound(
					null,
					scCenterX,
					(double) this.targetScaffoldTopY + 1.0D,
					scCenterZ,
					SoundEvents.BLOCK_SCAFFOLDING_STEP,
					SoundCategory.BLOCKS,
					0.8F,
					1.0F
				);
				return;
			}
		}

		if (this.currentTask == null || this.currentSession == null) {
			return;
		}

		BlockPos targetPos = this.currentTask.getWorldPos();

		// Keep facing the construction block
		this.minion.getLookControl().lookAt(
			targetPos.getX() + 0.5D,
			targetPos.getY() + 0.5D,
			targetPos.getZ() + 0.5D,
			30.0F,
			30.0F
		);

		double targetCenterX = targetPos.getX() + 0.5D;
		double targetCenterZ = targetPos.getZ() + 0.5D;
		double dx = targetCenterX - this.minion.getX();
		double dz = targetCenterZ - this.minion.getZ();
		double horizontalDistSq = dx * dx + dz * dz;
		double verticalDiff = Math.abs(this.minion.getY() - (double) targetPos.getY());

		// Realistic builder reach: horizontal reach <= 4.0 blocks (16.0 sq) and vertical diff <= 2.5 blocks
		boolean inRange = horizontalDistSq <= 16.0D && verticalDiff <= 2.5D;

		if (!inRange) {
			int diffY = targetPos.getY() - this.minion.getBlockY();

			// If target block is elevated (> 2 blocks above minion), deploy and utilize scaffolding
			if (diffY > 2 || (this.targetScaffoldTopY != -1 && this.minion.getY() < (double) this.targetScaffoldTopY + 0.95D)) {
				if (this.activeScaffoldColumn == null) {
					BlockPos scaffoldBase = deployScaffoldingIfNeeded(serverWorld, targetPos);
					if (scaffoldBase != null) {
						this.activeScaffoldColumn = scaffoldBase;
						this.targetScaffoldTopY = targetPos.getY() - 1;
					}
				}

				if (this.activeScaffoldColumn != null) {
					double scCenterX = this.activeScaffoldColumn.getX() + 0.5D;
					double scCenterZ = this.activeScaffoldColumn.getZ() + 0.5D;
					double scDx = scCenterX - this.minion.getX();
					double scDz = scCenterZ - this.minion.getZ();
					double distToColumnSq = scDx * scDx + scDz * scDz;

					// If minion is not yet at the column base (horizontal dist > 1.2 blocks), navigate to column base along ground
					if (distToColumnSq > 1.44D && !this.isAscendingScaffolding) {
						this.ticksNavigating++;
						if (this.ticksNavigating % 15 == 0 || this.minion.getNavigation().isIdle()) {
							this.minion.getNavigation().startMovingTo(
								scCenterX,
								this.activeScaffoldColumn.getY(),
								scCenterZ,
								1.15D
							);
						}

						if (this.ticksNavigating > 160) {
							handleNavigationTimeout(serverWorld);
							return;
						}
						return;
					}

					// Minion arrived at the column base: initiate ascent
					this.isAscendingScaffolding = true;
					this.minion.setClimbingScaffolding(true);
					this.minion.getNavigation().stop();
					this.climbTicks = 0;
					this.stallTicks = 0;
					this.lastClimbY = this.minion.getY();
					return;
				}
			}

			// If reach is not met and minion is not climbing scaffolding, navigate towards safe stand position
			this.ticksNavigating++;
			if (this.ticksNavigating % 15 == 0 || this.minion.getNavigation().isIdle()) {
				BlockPos standPos = findSafeStandPositionNear(serverWorld, targetPos);
				double navX = standPos != null ? standPos.getX() + 0.5D : targetCenterX;
				double navY = standPos != null ? standPos.getY() : targetPos.getY();
				double navZ = standPos != null ? standPos.getZ() + 0.5D : targetCenterZ;

				this.minion.getNavigation().startMovingTo(
					navX,
					navY,
					navZ,
					1.15D
				);
			}

			if (this.ticksNavigating > 160) {
				handleNavigationTimeout(serverWorld);
				return;
			}
			return;
		}

		// Minion is within realistic reach: halt movement, stabilize on platform, and perform construction work
		this.minion.getNavigation().stop();
		this.minion.setJumping(false);
		this.minion.fallDistance = 0.0F;

		BlockPos feetPos = this.minion.getBlockPos();
		BlockState feetState = serverWorld.getBlockState(feetPos);
		BlockState belowFeetState = serverWorld.getBlockState(feetPos.down());
		if (feetState.isOf(Blocks.SCAFFOLDING) || belowFeetState.isOf(Blocks.SCAFFOLDING)) {
			this.minion.setVelocity(0.0D, 0.0D, 0.0D);
			this.minion.velocityModified = true;
		}

		if (this.currentSession.isDismantle()) {
			executeDismantleWork(serverWorld, targetPos);
			return;
		}

		this.workTicks++;

		// Small animation delay (4 ticks) so placement feels deliberate and tangible
		if (this.workTicks < 4) {
			return;
		}

		// Handle resource resolution
		Item requiredItem = this.currentTask.getBlueprintBlock().getRequiredItem();
		BlockState targetState = this.currentTask.getBlueprintBlock().state();
		boolean alreadyPlaced = serverWorld.getBlockState(targetPos).equals(targetState);
		boolean hasResources = false;

		if (alreadyPlaced || this.currentSession.isCreative() || requiredItem == Items.SCAFFOLDING) {
			// Already in place, creative mode, or zero-cost scaffolding: free placement
			hasResources = true;
		} else {
			// Survival mode: 1. Check minion's 9-slot inventory
			SimpleInventory minionInv = this.minion.getInventory();
			int minionSlot = findItemSlot(minionInv, requiredItem);
			if (minionSlot != -1) {
				minionInv.removeStack(minionSlot, 1);
				hasResources = true;
			} else {
				// 2. Scavenge nearby containers (chests, barrels, shulkers) within 12 blocks
				hasResources = scavengeNearbyContainers(serverWorld, requiredItem);
			}
		}

		// If resources could not be found in inventory or nearby containers
		if (!hasResources) {
			handleResourceDeficiency(serverWorld, requiredItem);
			return;
		}

		// Execute block placement in world if not already matching
		if (!alreadyPlaced) {
			if (targetState.getBlock() instanceof DoorBlock) {
				if (targetState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_ALL);
					BlockState upperState = targetState.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
					serverWorld.setBlockState(targetPos.up(), upperState, Block.NOTIFY_ALL);
				} else {
					BlockPos lowerPos = targetPos.down();
					if (!serverWorld.getBlockState(lowerPos).isOf(targetState.getBlock())) {
						BlockState lowerState = targetState.with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
						serverWorld.setBlockState(lowerPos, lowerState, Block.NOTIFY_ALL);
					}
					serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_ALL);
				}
			} else {
				serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_ALL);
			}
		}

		// Visual and auditory feedback
		this.minion.swingHand(Hand.MAIN_HAND);
		serverWorld.spawnParticles(
			new BlockStateParticleEffect(ParticleTypes.BLOCK, targetState),
			targetPos.getX() + 0.5D,
			targetPos.getY() + 0.5D,
			targetPos.getZ() + 0.5D,
			14,
			0.3,
			0.3,
			0.3,
			0.15
		);

		serverWorld.playSound(
			null,
			targetPos,
			targetState.getSoundGroup().getPlaceSound(),
			SoundCategory.BLOCKS,
			1.0F,
			0.9F + (this.minion.getRandom().nextFloat() * 0.2F)
		);

		// Villager affirmative vocalization upon placing block
		serverWorld.playSound(
			null,
			this.minion.getX(),
			this.minion.getY(),
			this.minion.getZ(),
			SoundEvents.ENTITY_VILLAGER_YES,
			SoundCategory.NEUTRAL,
			0.8F,
			1.0F + (this.minion.getRandom().nextFloat() * 0.2F)
		);

		// Complete the task in the session
		this.currentSession.completeTask(this.currentTask, serverWorld);

		// Reset work and navigation counters
		this.workTicks = 0;
		this.ticksNavigating = 0;
		this.climbTicks = 0;
		this.stallTicks = 0;

		// Immediately try to claim the next topological task for seamless continuous building
		ConstructionTask nextTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
		if (nextTask != null) {
			if (isStandingOnPlatform() && isTaskReachableFromPlatform(nextTask)) {
				// Elevated task chaining: stay elevated on the platform!
				this.currentTask = nextTask;
				ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
				this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);
				this.minion.setVelocity(0.0D, 0.0D, 0.0D);
				this.minion.velocityModified = true;
				this.minion.getLookControl().lookAt(
					nextTask.getWorldPos().getX() + 0.5D,
					nextTask.getWorldPos().getY() + 0.5D,
					nextTask.getWorldPos().getZ() + 0.5D,
					30.0F,
					30.0F
				);
				return;
			}

			boolean elevated = this.activeScaffoldColumn != null || isStandingOnScaffolding(serverWorld);
			if (elevated) {
				this.pendingNextTask = nextTask;
				initiateDescent(serverWorld, false);
			} else {
				this.currentTask = nextTask;
				ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
				this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);
				setupNavigationForTask(serverWorld);
			}
		} else {
			// No more tasks: if minion is elevated on scaffolding, descend and teardown top-to-bottom
			if (this.activeScaffoldColumn != null || isStandingOnScaffolding(serverWorld)) {
				this.pendingNextTask = null;
				initiateDescent(serverWorld, true);
			} else {
				if (this.activeScaffoldColumn != null && this.currentSession != null) {
					this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
				}
				this.currentTask = null;
				this.activeScaffoldColumn = null;
				this.targetScaffoldTopY = -1;
				this.isAscendingScaffolding = false;
				this.minion.setClimbingScaffolding(false);
				// Construction complete for this minion - restore held weapon
				restoreHeldWeapon();
			}
		}
	}

	private int findItemSlot(Inventory inventory, Item item) {
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Scavenges nearby container block entities within 12 blocks of the minion or session anchor.
	 */
	private boolean scavengeNearbyContainers(ServerWorld world, Item requiredItem) {
		BlockPos center = this.minion.getBlockPos();
		int radius = 12;

		BlockPos minPos = center.add(-radius, -4, -radius);
		BlockPos maxPos = center.add(radius, 4, radius);

		for (BlockPos pos : BlockPos.iterate(minPos, maxPos)) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof Inventory containerInv) {
				int slot = findItemSlot(containerInv, requiredItem);
				if (slot != -1) {
					containerInv.removeStack(slot, 1);
					be.markDirty();

					// Arcane scavenging particle trail and chest creak sound
					world.spawnParticles(
						ParticleTypes.CLOUD,
						pos.getX() + 0.5,
						pos.getY() + 0.8,
						pos.getZ() + 0.5,
						4,
						0.1,
						0.1,
						0.1,
						0.02
					);
					world.playSound(
						null,
						pos,
						SoundEvents.BLOCK_CHEST_OPEN,
						SoundCategory.BLOCKS,
						0.4F,
						1.3F
					);
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Handles missing resources: emits smoke particles, dispenser fail sound,
	 * releases the task back to the pending pool, and sends an action-bar alert to the master.
	 */
	private void handleResourceDeficiency(ServerWorld world, Item requiredItem) {
		world.spawnParticles(
			ParticleTypes.SMOKE,
			this.minion.getX(),
			this.minion.getY() + 1.8,
			this.minion.getZ(),
			6,
			0.15,
			0.15,
			0.15,
			0.05
		);

		world.playSound(
			null,
			this.minion.getX(),
			this.minion.getY(),
			this.minion.getZ(),
			SoundEvents.ENTITY_VILLAGER_NO,
			SoundCategory.NEUTRAL,
			1.0F,
			1.0F
		);

		world.playSound(
			null,
			this.minion.getX(),
			this.minion.getY(),
			this.minion.getZ(),
			SoundEvents.BLOCK_DISPENSER_FAIL,
			SoundCategory.NEUTRAL,
			0.8F,
			1.2F
		);

		// Release the task so other minions or later retries can pick it up
		if (this.currentTask != null) {
			this.currentSession.releaseTask(this.currentTask);
			this.currentTask = null;
		}

		if (this.activeScaffoldColumn != null && this.currentSession != null) {
			this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
		}

		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.isAscendingScaffolding = false;
		this.minion.setClimbingScaffolding(false);
		this.climbTicks = 0;
		this.stallTicks = 0;
		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.minion.getNavigation().stop();
		this.minion.fallDistance = 0.0F;

		// 3-second cooldown before attempting to claim work again
		this.failureCooldownUntilTick = world.getTime() + 60L;

		// Restore held weapon while paused/waiting
		restoreHeldWeapon();

		// Notify owner via action bar
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(this.currentSession.getOwnerUuid());
		if (owner != null) {
			owner.sendMessage(
				Text.literal("§cMinion needs §e" + requiredItem.getName().getString() + " §cto continue building!§r"),
				true
			);
		}
	}
	/**
	 * Configures navigation and scaffolding targets for the current task.
	 * If the task is elevated above the minion's reach, deploys or reuses scaffolding and navigates to its base.
	 * Otherwise, navigates towards a safe adjacent standing position next to the target block.
	 */
	private void setupNavigationForTask(ServerWorld world) {
		if (this.currentTask == null) {
			return;
		}

		BlockPos targetPos = this.currentTask.getWorldPos();

		// If minion is already standing on an active platform and the task is reachable, stay elevated!
		if (isStandingOnPlatform() && isTaskReachableFromPlatform(this.currentTask)) {
			this.minion.getNavigation().stop();
			this.minion.setVelocity(0.0D, 0.0D, 0.0D);
			this.minion.velocityModified = true;
			return;
		}

		int diffY = targetPos.getY() - this.minion.getBlockY();

		if (diffY > 2 || targetPos.getY() > this.minion.getBlockY() + 1) {
			// Task is elevated: deploy or reuse scaffolding column and navigate to its base
			BlockPos scaffoldBase = deployScaffoldingIfNeeded(world, targetPos);
			if (scaffoldBase != null) {
				this.activeScaffoldColumn = scaffoldBase;
				this.targetScaffoldTopY = targetPos.getY() - 1;
				this.isAscendingScaffolding = false;
				this.minion.setClimbingScaffolding(false);
				this.climbTicks = 0;
				this.stallTicks = 0;

				// Navigate to base of scaffolding column along ground
				this.minion.getNavigation().startMovingTo(
					scaffoldBase.getX() + 0.5D,
					scaffoldBase.getY(),
					scaffoldBase.getZ() + 0.5D,
					1.15D
				);
				return;
			}
		}

		// Ground-level or reach-accessible task: release existing scaffold column if reserved
		if (this.activeScaffoldColumn != null && this.currentSession != null) {
			this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
		}
		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.isAscendingScaffolding = false;
		this.minion.setClimbingScaffolding(false);
		this.climbTicks = 0;
		this.stallTicks = 0;

		BlockPos standPos = findSafeStandPositionNear(world, targetPos);
		double navX = standPos != null ? standPos.getX() + 0.5D : targetPos.getX() + 0.5D;
		double navY = standPos != null ? standPos.getY() : targetPos.getY();
		double navZ = standPos != null ? standPos.getZ() + 0.5D : targetPos.getZ() + 0.5D;

		this.minion.getNavigation().startMovingTo(navX, navY, navZ, 1.15D);
	}

	private void handleNavigationTimeout(ServerWorld serverWorld) {
		if (this.currentTask != null && this.currentSession != null) {
			this.currentSession.releaseTask(this.currentTask);
		}
		if (this.activeScaffoldColumn != null && this.currentSession != null) {
			this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
		}
		this.currentTask = null;
		this.pendingNextTask = null;
		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.targetScaffoldBottomY = -1;
		this.isAscendingScaffolding = false;
		this.isDescendingScaffolding = false;
		this.minion.setClimbingScaffolding(false);
		this.climbTicks = 0;
		this.stallTicks = 0;
		this.descentTicks = 0;
		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.minion.getNavigation().stop();
		this.minion.fallDistance = 0.0F;
		this.failureCooldownUntilTick = serverWorld.getTime() + 40L;
		restoreHeldWeapon();
	}

	/**
	 * Preserves the minion's currently held weapon before equipping temporary construction preview items.
	 */
	private void saveHeldWeapon() {
		ItemStack currentHeld = this.minion.getEquippedStack(EquipmentSlot.MAINHAND);
		if (this.savedHeldWeapon.isEmpty() && !currentHeld.isEmpty()) {
			this.savedHeldWeapon = currentHeld.copy();
		}
	}

	/**
	 * Restores the minion's preserved held weapon upon task/goal completion or cancellation.
	 */
	private void restoreHeldWeapon() {
		if (!this.savedHeldWeapon.isEmpty()) {
			this.minion.equipStack(EquipmentSlot.MAINHAND, this.savedHeldWeapon.copy());
			this.savedHeldWeapon = ItemStack.EMPTY;
		} else {
			this.minion.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	/**
	 * Deploys zero-cost temporary scaffolding for elevated construction tasks.
	 * Checks first for reusable columns from the current session; otherwise searches for a
	 * valid adjacent column not obstructed by uncompleted blueprint tasks.
	 *
	 * @param world     The server world.
	 * @param targetPos Target block position to be constructed.
	 * @return The base {@link BlockPos} of the scaffolding column, or null if deployment failed.
	 */
	private BlockPos deployScaffoldingIfNeeded(ServerWorld world, BlockPos targetPos) {
		if (this.currentSession == null) {
			return null;
		}

		// 1. Check if an existing scaffolding column from this session is already nearby and reusable
		BlockPos reusableColumn = findReusableScaffoldColumn(world, targetPos);
		if (reusableColumn != null) {
			extendScaffoldColumnTo(world, reusableColumn, targetPos.getY() - 1);
			return reusableColumn;
		}

		// 2. Find a suitable new column adjacent to the target position
		BlockPos scaffoldColumnBase = findScaffoldColumn(world, targetPos);
		if (scaffoldColumnBase == null) {
			return null;
		}

		int startY = scaffoldColumnBase.getY();
		int topY = targetPos.getY() - 1;

		if (topY < startY) {
			return scaffoldColumnBase;
		}

		boolean placedAny = false;
		for (int y = startY; y <= topY; y++) {
			BlockPos p = new BlockPos(scaffoldColumnBase.getX(), y, scaffoldColumnBase.getZ());
			BlockState current = world.getBlockState(p);
			if (current.isAir() || current.isReplaceable()) {
				BlockState scaffoldState = Blocks.SCAFFOLDING.getDefaultState()
					.with(ScaffoldingBlock.DISTANCE, 0)
					.with(ScaffoldingBlock.BOTTOM, false);
				world.setBlockState(p, scaffoldState, Block.NOTIFY_ALL);
				this.currentSession.addTemporaryScaffolding(p);
				placedAny = true;
			}
		}

		if (placedAny) {
			world.playSound(
				null,
				scaffoldColumnBase.getX() + 0.5D,
				startY + 0.5D,
				scaffoldColumnBase.getZ() + 0.5D,
				SoundEvents.BLOCK_SCAFFOLDING_PLACE,
				SoundCategory.BLOCKS,
				0.8F,
				1.1F
			);
		}

		return scaffoldColumnBase;
	}

	/**
	 * Checks if an existing scaffolding column erected by this session is within 1 to 4 blocks
	 * horizontal reach, has valid column reservation, and can be reused to access the target block.
	 */
	private BlockPos findReusableScaffoldColumn(ServerWorld world, BlockPos targetPos) {
		if (this.currentSession == null) {
			return null;
		}
		int targetY = targetPos.getY();

		for (BlockPos scaffoldPos : this.currentSession.getTemporaryScaffolding()) {
			double dx = scaffoldPos.getX() - targetPos.getX();
			double dz = scaffoldPos.getZ() - targetPos.getZ();
			double distSq = dx * dx + dz * dz;

			if (distSq >= 1.0D && distSq <= 16.0D) {
				int x = scaffoldPos.getX();
				int z = scaffoldPos.getZ();

				// Column reservation check
				BlockPos probePos = new BlockPos(x, targetY, z);
				if (!this.currentSession.isScaffoldColumnAvailable(probePos, this.minion.getUuid())) {
					continue;
				}

				// Blacklist check
				if (isColumnBlacklisted(x, z)) {
					continue;
				}

				int baseY = scaffoldPos.getY();
				while (baseY > world.getBottomY() && world.getBlockState(new BlockPos(x, baseY - 1, z)).isOf(Blocks.SCAFFOLDING)) {
					baseY--;
				}
				BlockPos base = new BlockPos(x, baseY, z);
				if (!isPosInDoorwayCorridor(world, x, z, baseY, targetY + 1)
					&& isHeadroomClear(world, x, z, targetY - 1)
					&& !isBlueprintColumnBlocked(x, z, baseY, targetY + 2)) {
					this.currentSession.claimScaffoldColumn(base, this.minion.getUuid());
					return base;
				}
			}
		}
		return null;
	}

	/**
	 * Extends an existing scaffolding column upward to reach the desired target top Y level.
	 */
	private void extendScaffoldColumnTo(ServerWorld world, BlockPos columnBase, int targetTopY) {
		int x = columnBase.getX();
		int z = columnBase.getZ();
		int startY = columnBase.getY();
		boolean placed = false;

		for (int y = startY; y <= targetTopY; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState current = world.getBlockState(p);
			if (current.isAir() || current.isReplaceable()) {
				BlockState scaffoldState = Blocks.SCAFFOLDING.getDefaultState()
					.with(ScaffoldingBlock.DISTANCE, 0)
					.with(ScaffoldingBlock.BOTTOM, false);
				world.setBlockState(p, scaffoldState, Block.NOTIFY_ALL);
				this.currentSession.addTemporaryScaffolding(p);
				placed = true;
			}
		}

		if (placed) {
			world.playSound(
				null,
				x + 0.5D,
				targetTopY + 0.5D,
				z + 0.5D,
				SoundEvents.BLOCK_SCAFFOLDING_PLACE,
				SoundCategory.BLOCKS,
				0.8F,
				1.1F
			);
		}
	}

	/**
	 * Finds an optimal candidate coordinate adjacent to targetPos for erecting a scaffolding column.
	 * First projects candidate positions 1 block outside the session's worldBoundingBox perimeter
	 * (minX - 1, maxX + 1, minZ - 1, maxZ + 1), sorting by combined distance to target and minion position
	 * to eliminate directional bias. Verifies unobstructed clearance in world and blueprint tasks up
	 * through targetY + 2, and validates column reservation availability.
	 *
	 * If no perimeter column is reachable or valid, falls back to sorted candidate positions around targetPos.
	 */
	private BlockPos findScaffoldColumn(ServerWorld world, BlockPos targetPos) {
		int targetY = targetPos.getY();

		// 1. Exterior perimeter column search using session worldBoundingBox
		if (this.currentSession != null && this.currentSession.getWorldBoundingBox() != null) {
			BlockBox box = this.currentSession.getWorldBoundingBox();
			int minX = box.getMinX();
			int maxX = box.getMaxX();
			int minZ = box.getMinZ();
			int maxZ = box.getMaxZ();

			List<int[]> perimeterCandidates = new ArrayList<>();

			// North perimeter (z = minZ - 1)
			for (int x = minX - 1; x <= maxX + 1; x++) {
				perimeterCandidates.add(new int[] { x, minZ - 1 });
			}
			// South perimeter (z = maxZ + 1)
			for (int x = minX - 1; x <= maxX + 1; x++) {
				perimeterCandidates.add(new int[] { x, maxZ + 1 });
			}
			// West perimeter (x = minX - 1)
			for (int z = minZ; z <= maxZ; z++) {
				perimeterCandidates.add(new int[] { minX - 1, z });
			}
			// East perimeter (x = maxX + 1)
			for (int z = minZ; z <= maxZ; z++) {
				perimeterCandidates.add(new int[] { maxX + 1, z });
			}

			// Filter perimeter candidates to those within builder reach (distSq <= 16.0) of targetPos
			List<int[]> reachablePerimeter = new ArrayList<>();
			for (int[] cand : perimeterCandidates) {
				int cx = cand[0];
				int cz = cand[1];
				double dx = (double) cx - targetPos.getX();
				double dz = (double) cz - targetPos.getZ();
				double distSq = dx * dx + dz * dz;
				if (distSq >= 1.0D && distSq <= 16.0D) {
					reachablePerimeter.add(cand);
				}
			}

			// Sort candidates by combined distance: (distToTarget + 0.5 * distToMinion)
			// This eliminates North bias and naturally distributes minions across faces based on their approach
			reachablePerimeter.sort((a, b) -> {
				double distTargetA = Math.sqrt(Math.pow(a[0] - targetPos.getX(), 2) + Math.pow(a[1] - targetPos.getZ(), 2));
				double distTargetB = Math.sqrt(Math.pow(b[0] - targetPos.getX(), 2) + Math.pow(b[1] - targetPos.getZ(), 2));

				double distMinionA = Math.sqrt(Math.pow((a[0] + 0.5D) - this.minion.getX(), 2) + Math.pow((a[1] + 0.5D) - this.minion.getZ(), 2));
				double distMinionB = Math.sqrt(Math.pow((b[0] + 0.5D) - this.minion.getX(), 2) + Math.pow((b[1] + 0.5D) - this.minion.getZ(), 2));

				double scoreA = distTargetA + 0.5D * distMinionA;
				double scoreB = distTargetB + 0.5D * distMinionB;
				return Double.compare(scoreA, scoreB);
			});

			for (int[] cand : reachablePerimeter) {
				int cx = cand[0];
				int cz = cand[1];

				// Column reservation check
				BlockPos probePos = new BlockPos(cx, targetY, cz);
				if (!this.currentSession.isScaffoldColumnAvailable(probePos, this.minion.getUuid())) {
					continue;
				}

				// Blacklist check
				if (isColumnBlacklisted(cx, cz)) {
					continue;
				}

				BlockPos base = getValidScaffoldBase(world, cx, cz, targetY);
				if (base != null && !isBlueprintColumnBlocked(cx, cz, base.getY(), targetY + 2)) {
					this.currentSession.claimScaffoldColumn(base, this.minion.getUuid());
					return base;
				}
			}
		}

		// 2. Fallback candidate search around targetPos if exterior perimeter yields no viable candidate
		List<int[]> fallbackCandidates = new ArrayList<>();
		// Cardinal distance 1
		fallbackCandidates.add(new int[] { targetPos.getX(), targetPos.getZ() - 1 });
		fallbackCandidates.add(new int[] { targetPos.getX(), targetPos.getZ() + 1 });
		fallbackCandidates.add(new int[] { targetPos.getX() + 1, targetPos.getZ() });
		fallbackCandidates.add(new int[] { targetPos.getX() - 1, targetPos.getZ() });
		// Diagonal distance 1
		fallbackCandidates.add(new int[] { targetPos.getX() - 1, targetPos.getZ() - 1 });
		fallbackCandidates.add(new int[] { targetPos.getX() + 1, targetPos.getZ() - 1 });
		fallbackCandidates.add(new int[] { targetPos.getX() - 1, targetPos.getZ() + 1 });
		fallbackCandidates.add(new int[] { targetPos.getX() + 1, targetPos.getZ() + 1 });
		// Cardinal distance 2
		fallbackCandidates.add(new int[] { targetPos.getX(), targetPos.getZ() - 2 });
		fallbackCandidates.add(new int[] { targetPos.getX(), targetPos.getZ() + 2 });
		fallbackCandidates.add(new int[] { targetPos.getX() + 2, targetPos.getZ() });
		fallbackCandidates.add(new int[] { targetPos.getX() - 2, targetPos.getZ() });

		// Sort fallback candidates by distance to minion to avoid fixed direction bias
		fallbackCandidates.sort((a, b) -> {
			double distMinionA = Math.pow((a[0] + 0.5D) - this.minion.getX(), 2) + Math.pow((a[1] + 0.5D) - this.minion.getZ(), 2);
			double distMinionB = Math.pow((b[0] + 0.5D) - this.minion.getX(), 2) + Math.pow((b[1] + 0.5D) - this.minion.getZ(), 2);
			return Double.compare(distMinionA, distMinionB);
		});

		for (int[] cand : fallbackCandidates) {
			int cx = cand[0];
			int cz = cand[1];

			BlockPos probePos = new BlockPos(cx, targetY, cz);
			if (this.currentSession != null && !this.currentSession.isScaffoldColumnAvailable(probePos, this.minion.getUuid())) {
				continue;
			}

			if (isColumnBlacklisted(cx, cz)) {
				continue;
			}

			BlockPos base = getValidScaffoldBase(world, cx, cz, targetY);
			if (base != null && !isBlueprintColumnBlocked(cx, cz, base.getY(), targetY + 2)) {
				if (this.currentSession != null) {
					this.currentSession.claimScaffoldColumn(base, this.minion.getUuid());
				}
				return base;
			}
		}

		return null;
	}

	/**
	 * Checks if any uncompleted blueprint task is scheduled to occupy coordinates
	 * (x, z) at any height between fromY and toY.
	 */
	private boolean isBlueprintColumnBlocked(int x, int z, int fromY, int toY) {
		if (this.currentSession == null) {
			return false;
		}
		for (ConstructionTask task : this.currentSession.getTasks()) {
			if (!task.isCompleted()) {
				BlockPos pos = task.getWorldPos();
				if (pos.getX() == x && pos.getZ() == z && pos.getY() >= fromY && pos.getY() <= toY) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Validates and locates solid ground support for a scaffolding column, ensuring all blocks
	 * from ground level up to topY are passable or air.
	 */
	private BlockPos getValidScaffoldBase(ServerWorld world, int x, int z, int targetY) {
		int anchorY = this.currentSession != null ? this.currentSession.getAnchorPos().getY() : this.minion.getBlockY();
		int minY = Math.max(world.getBottomY() + 1, anchorY - 6);
		int topY = targetY - 1;

		if (topY < minY) {
			topY = minY;
		}

		// Scan down from topY to locate solid ground support
		int groundY = -1;
		for (int y = topY; y >= minY; y--) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState state = world.getBlockState(p);
			if (!state.isAir() && !state.isReplaceable() && !state.isOf(Blocks.SCAFFOLDING)) {
				groundY = y + 1;
				break;
			}
		}

		if (groundY == -1) {
			groundY = Math.max(this.minion.getBlockY(), anchorY);
		}

		// Validate that the entire column from groundY to topY is clear
		for (int y = groundY; y <= topY; y++) {
			BlockPos checkPos = new BlockPos(x, y, z);
			BlockState st = world.getBlockState(checkPos);
			if (!st.isAir() && !st.isReplaceable() && !st.isOf(Blocks.SCAFFOLDING)) {
				return null;
			}
		}

		// Validate that candidate column is not in doorway corridor and has headroom clearance
		if (isPosInDoorwayCorridor(world, x, z, groundY, topY + 2)) {
			return null;
		}

		if (!isHeadroomClear(world, x, z, topY)) {
			return null;
		}

		return new BlockPos(x, groundY, z);
	}

	/**
	 * Finds the ground Y coordinate directly beneath the given position.
	 */
	private int getGroundYBelow(ServerWorld world, BlockPos pos) {
		int startY = pos.getY();
		int bottomY = Math.max(world.getBottomY() + 1, startY - 30);
		for (int y = startY; y >= bottomY; y--) {
			BlockPos p = new BlockPos(pos.getX(), y, pos.getZ());
			BlockState st = world.getBlockState(p);
			if (!st.isAir() && !st.isOf(Blocks.SCAFFOLDING) && !st.isReplaceable()) {
				return y + 1;
			}
		}
		return startY;
	}

	/**
	 * Checks if the minion is currently standing on or inside a scaffolding block.
	 */
	private boolean isStandingOnScaffolding(ServerWorld world) {
		BlockPos feet = this.minion.getBlockPos();
		return world.getBlockState(feet).isOf(Blocks.SCAFFOLDING) || world.getBlockState(feet.down()).isOf(Blocks.SCAFFOLDING);
	}

	/**
	 * Finds a safe, walkable adjacent block position near targetPos for the minion to stand
	 * while placing blocks.
	 */
	private BlockPos findSafeStandPositionNear(ServerWorld world, BlockPos targetPos) {
		Direction[] directions = new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

		// Check 1-block horizontal neighbors at same Y or 1 block below/above
		for (int dy : new int[] { 0, -1, 1 }) {
			for (Direction dir : directions) {
				BlockPos candidate = targetPos.offset(dir).add(0, dy, 0);
				if (isWalkableStandPosition(world, candidate)) {
					return candidate;
				}
			}
		}

		// Check 2-block horizontal neighbors
		for (int dy : new int[] { 0, -1, 1 }) {
			for (Direction dir : directions) {
				BlockPos candidate = targetPos.offset(dir, 2).add(0, dy, 0);
				if (isWalkableStandPosition(world, candidate)) {
					return candidate;
				}
			}
		}

		return null;
	}

	/**
	 * Validates whether candidate block position pos has solid footing and clear headroom for standing.
	 */
	private boolean isWalkableStandPosition(ServerWorld world, BlockPos pos) {
		BlockPos below = pos.down();
		BlockState belowState = world.getBlockState(below);

		// Floor must be solid or scaffolding
		if (!belowState.isSolidBlock(world, below) && !belowState.isOf(Blocks.SCAFFOLDING)) {
			return false;
		}

		// Space for feet and head
		BlockState feet = world.getBlockState(pos);
		BlockState head = world.getBlockState(pos.up());

		boolean feetPassable = feet.isAir() || feet.isOf(Blocks.SCAFFOLDING) || feet.canPathfindThrough(NavigationType.LAND);
		boolean headPassable = head.isAir() || head.isOf(Blocks.SCAFFOLDING) || head.canPathfindThrough(NavigationType.LAND);

		return feetPassable && headPassable && !feet.isOf(Blocks.LAVA) && !feet.isOf(Blocks.FIRE);
	}

	/**
	 * Determines whether a position (x, z) falls within a doorway or entrance corridor within
	 * the specified vertical range.
	 *
	 * Doorway corridors include the door block itself and immediate horizontal neighbors (cardinal)
	 * within 1 block distance. Checks both active session blueprint tasks (for planned door blocks)
	 * and existing world door blocks.
	 *
	 * @param world The server world.
	 * @param x     The X coordinate to test.
	 * @param z     The Z coordinate to test.
	 * @param minY  The minimum Y coordinate of the vertical range.
	 * @param maxY  The maximum Y coordinate of the vertical range.
	 * @return true if (x, z) is inside or adjacent to a doorway corridor; false otherwise.
	 */
	public boolean isPosInDoorwayCorridor(ServerWorld world, int x, int z, int minY, int maxY) {
		// 1. Check blueprint tasks in the active session for door blocks
		if (this.currentSession != null) {
			for (ConstructionTask task : this.currentSession.getTasks()) {
				BlockPos taskPos = task.getWorldPos();
				if (taskPos.getY() >= minY - 1 && taskPos.getY() <= maxY + 1) {
					BlockState blueprintState = task.getBlueprintBlock().state();
					if (isDoorBlock(blueprintState)) {
						if (isWithinDoorwayCorridor(x, z, taskPos.getX(), taskPos.getZ())) {
							return true;
						}
					}
				}
			}
		}

		// 2. Check world blocks in the vicinity for placed door blocks
		int checkMinY = Math.max(world.getBottomY(), minY - 1);
		int checkMaxY = Math.min(world.getTopY(), maxY + 1);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = checkMinY; y <= checkMaxY; y++) {
					BlockPos p = new BlockPos(x + dx, y, z + dz);
					BlockState st = world.getBlockState(p);
					if (isDoorBlock(st)) {
						if (isWithinDoorwayCorridor(x, z, p.getX(), p.getZ())) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	/**
	 * Helper to determine if a block state represents any door type.
	 */
	public static boolean isDoorBlock(BlockState state) {
		return state.getBlock() instanceof DoorBlock;
	}

	/**
	 * Helper to test if (x, z) is at or cardinally adjacent to the doorway anchor (doorX, doorZ).
	 */
	private static boolean isWithinDoorwayCorridor(int x, int z, int doorX, int doorZ) {
		int diffX = Math.abs(x - doorX);
		int diffZ = Math.abs(z - doorZ);
		// Same block or directly adjacent cardinal corridor block (distance <= 1)
		return (diffX + diffZ) <= 1;
	}

	/**
	 * Checks vertical headroom clearance above a scaffolding column top platform (topY).
	 * Ensures that topY + 1 and topY + 2 are free of solid blocks (both in the world and
	 * in pending blueprint tasks) so that a standing minion does not suffocate or become trapped.
	 *
	 * @param world The server world.
	 * @param x     The column X coordinate.
	 * @param z     The column Z coordinate.
	 * @param topY  The Y level of the top scaffolding block.
	 * @return true if 2 blocks of vertical headroom above topY are completely unobstructed.
	 */
	public boolean isHeadroomClear(ServerWorld world, int x, int z, int topY) {
		// Verify world blocks at topY + 1 and topY + 2
		for (int yOffset = 1; yOffset <= 2; yOffset++) {
			int y = topY + yOffset;
			if (y > world.getTopY()) {
				return false;
			}
			BlockPos p = new BlockPos(x, y, z);
			BlockState st = world.getBlockState(p);
			if (!st.isAir() && !st.isReplaceable() && !st.isOf(Blocks.SCAFFOLDING)) {
				return false;
			}
		}

		// Verify uncompleted blueprint tasks scheduled to place solid blocks at topY + 1 and topY + 2
		if (this.currentSession != null) {
			for (ConstructionTask task : this.currentSession.getTasks()) {
				if (!task.isCompleted()) {
					BlockPos pos = task.getWorldPos();
					if (pos.getX() == x && pos.getZ() == z && (pos.getY() == topY + 1 || pos.getY() == topY + 2)) {
						BlockState plannedState = task.getBlueprintBlock().state();
						if (!plannedState.isAir() && !plannedState.isReplaceable() && !plannedState.isOf(Blocks.SCAFFOLDING)) {
							return false;
						}
					}
				}
			}
		}

		return true;
	}

	/**
	 * Executes deconstruction work on a target block: plays break sounds and particles,
	 * drops harvested items in survival, handles multi-block doors, and completes the task.
	 */
	private void executeDismantleWork(ServerWorld serverWorld, BlockPos targetPos) {
		this.minion.getNavigation().stop();
		this.minion.setJumping(false);
		this.minion.fallDistance = 0.0F;

		BlockPos feetPos = this.minion.getBlockPos();
		BlockState feetState = serverWorld.getBlockState(feetPos);
		BlockState belowFeetState = serverWorld.getBlockState(feetPos.down());
		if (feetState.isOf(Blocks.SCAFFOLDING) || belowFeetState.isOf(Blocks.SCAFFOLDING)) {
			this.minion.setVelocity(0.0D, 0.0D, 0.0D);
			this.minion.velocityModified = true;
		}

		this.workTicks++;

		// 4 ticks deliberate work delay
		if (this.workTicks < 4) {
			return;
		}

		BlockState currentState = serverWorld.getBlockState(targetPos);
		boolean alreadyAir = currentState.isAir();

		if (!alreadyAir) {
			// Handle double doors cleanly
			if (currentState.getBlock() instanceof DoorBlock) {
				if (currentState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					BlockPos upper = targetPos.up();
					if (serverWorld.getBlockState(upper).isOf(currentState.getBlock())) {
						serverWorld.breakBlock(upper, !this.currentSession.isCreative(), this.minion);
					}
				} else {
					BlockPos lower = targetPos.down();
					if (serverWorld.getBlockState(lower).isOf(currentState.getBlock())) {
						serverWorld.breakBlock(lower, !this.currentSession.isCreative(), this.minion);
					}
				}
			}

			// Break block: SFX, breaking particles, and item drops in survival
			boolean dropResources = !this.currentSession.isCreative();
			serverWorld.breakBlock(targetPos, dropResources, this.minion);

			// Extra block break particles and sound
			serverWorld.spawnParticles(
				new BlockStateParticleEffect(ParticleTypes.BLOCK, currentState),
				targetPos.getX() + 0.5D,
				targetPos.getY() + 0.5D,
				targetPos.getZ() + 0.5D,
				16,
				0.3,
				0.3,
				0.3,
				0.15
			);

			serverWorld.playSound(
				null,
				targetPos,
				currentState.getSoundGroup().getBreakSound(),
				SoundCategory.BLOCKS,
				1.0F,
				0.9F + (this.minion.getRandom().nextFloat() * 0.2F)
			);
		}

		// Minion hand swing and vocalization
		this.minion.swingHand(Hand.MAIN_HAND);
		serverWorld.playSound(
			null,
			this.minion.getX(),
			this.minion.getY(),
			this.minion.getZ(),
			SoundEvents.ENTITY_VILLAGER_YES,
			SoundCategory.NEUTRAL,
			0.8F,
			1.0F + (this.minion.getRandom().nextFloat() * 0.2F)
		);

		// Complete the task in session
		this.currentSession.completeTask(this.currentTask, serverWorld);

		// Reset work and navigation counters
		this.workTicks = 0;
		this.ticksNavigating = 0;
		this.climbTicks = 0;
		this.stallTicks = 0;

		// Immediately try to claim the next top-down task for seamless deconstruction
		ConstructionTask nextTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
		if (nextTask != null) {
			if (isStandingOnPlatform() && isTaskReachableFromPlatform(nextTask)) {
				// Elevated task chaining: stay elevated on the platform!
				this.currentTask = nextTask;
				this.minion.equipStack(EquipmentSlot.MAINHAND, resolveDismantleTool());
				this.minion.setVelocity(0.0D, 0.0D, 0.0D);
				this.minion.velocityModified = true;
				this.minion.getLookControl().lookAt(
					nextTask.getWorldPos().getX() + 0.5D,
					nextTask.getWorldPos().getY() + 0.5D,
					nextTask.getWorldPos().getZ() + 0.5D,
					30.0F,
					30.0F
				);
				return;
			}

			boolean elevated = this.activeScaffoldColumn != null || isStandingOnScaffolding(serverWorld);
			if (elevated) {
				this.pendingNextTask = nextTask;
				initiateDescent(serverWorld, true);
			} else {
				this.currentTask = nextTask;
				this.minion.equipStack(EquipmentSlot.MAINHAND, resolveDismantleTool());
				setupNavigationForTask(serverWorld);
			}
		} else {
			// No more tasks: if minion is elevated on scaffolding, descend and tear down scaffolding on descent
			if (this.activeScaffoldColumn != null || isStandingOnScaffolding(serverWorld)) {
				this.pendingNextTask = null;
				initiateDescent(serverWorld, true);
			} else {
				if (this.activeScaffoldColumn != null && this.currentSession != null) {
					this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
				}
				this.currentTask = null;
				this.activeScaffoldColumn = null;
				this.targetScaffoldTopY = -1;
				this.isAscendingScaffolding = false;
				this.minion.setClimbingScaffolding(false);
				restoreHeldWeapon();
			}
		}
	}

	private void removeScaffoldBlockWithFeedback(ServerWorld world, BlockPos pos) {
		if (this.currentSession != null) {
			this.currentSession.removeTemporaryScaffolding(pos);
		}
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		world.spawnParticles(
			new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SCAFFOLDING.getDefaultState()),
			pos.getX() + 0.5D,
			pos.getY() + 0.5D,
			pos.getZ() + 0.5D,
			8,
			0.2,
			0.2,
			0.2,
			0.05
		);
		world.playSound(
			null,
			pos,
			SoundEvents.BLOCK_SCAFFOLDING_BREAK,
			SoundCategory.BLOCKS,
			0.8F,
			1.0F
		);
	}

	private ItemStack resolveDismantleTool() {
		if (!this.savedHeldWeapon.isEmpty() && isPickaxe(this.savedHeldWeapon)) {
			return this.savedHeldWeapon.copy();
		}
		SimpleInventory inv = this.minion.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack st = inv.getStack(i);
			if (!st.isEmpty() && isPickaxe(st)) {
				return st.copy();
			}
		}
		return new ItemStack(Items.IRON_PICKAXE);
	}

	private static boolean isPickaxe(ItemStack stack) {
		Item item = stack.getItem();
		return item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE ||
			   item == Items.IRON_PICKAXE || item == Items.GOLDEN_PICKAXE ||
			   item == Items.STONE_PICKAXE || item == Items.WOODEN_PICKAXE;
	}

	/**
	 * Aborts vertical scaffolding climb due to overhead ceiling obstruction or stall,
	 * blacklists the column, and initiates safe descent back to ground.
	 */
	private void abortClimbAndDescend(ServerWorld world) {
		if (this.activeScaffoldColumn != null) {
			this.blacklistedScaffoldColumns.add(new BlockPos(this.activeScaffoldColumn.getX(), 0, this.activeScaffoldColumn.getZ()));
			if (this.currentSession != null) {
				this.currentSession.releaseScaffoldColumn(this.activeScaffoldColumn, this.minion.getUuid());
			}
		}
		this.isAscendingScaffolding = false;
		this.minion.setClimbingScaffolding(false);
		this.climbTicks = 0;
		this.stallTicks = 0;

		initiateDescent(world, false);

		world.playSound(
			null,
			this.minion.getX(),
			this.minion.getY(),
			this.minion.getZ(),
			SoundEvents.ENTITY_VILLAGER_NO,
			SoundCategory.NEUTRAL,
			0.8F,
			1.1F
		);
		world.spawnParticles(
			ParticleTypes.SMOKE,
			this.minion.getX(),
			this.minion.getY() + 1.5D,
			this.minion.getZ(),
			5,
			0.2,
			0.2,
			0.2,
			0.02
		);
	}

	/**
	 * Initiates a controlled downward descent through a scaffolding column towards safe ground.
	 *
	 * @param world        The server world.
	 * @param demobilizing True if this descent is part of teardown / session completion.
	 */
	private void initiateDescent(ServerWorld world, boolean demobilizing) {
		this.isDescendingScaffolding = true;
		this.isAscendingScaffolding = false;
		this.minion.setClimbingScaffolding(false);
		this.descentTicks = 0;
		this.targetScaffoldBottomY = getGroundYBelow(world, this.minion.getBlockPos());
		this.minion.getNavigation().stop();
		this.minion.setVelocity(0.0D, -0.22D, 0.0D);
		this.minion.velocityModified = true;
		this.minion.fallDistance = 0.0F;
	}

	/**
	 * Determines whether scaffolding blocks should be dismantled during descent.
	 * Returns true if in DISMANTLE mode, or if demobilizing (no pending tasks remain or session completed)
	 * in BUILD mode.
	 */
	public boolean shouldTeardownOnDescent() {
		if (this.currentSession == null || this.activeScaffoldColumn == null) {
			return false;
		}
		if (this.currentSession.isDismantle()) {
			return true;
		}
		return this.pendingNextTask == null || !this.currentSession.isActive();
	}

	/**
	 * Checks whether a construction task can be reached and executed directly from the minion's
	 * current active scaffolding platform without descending or pathfinding along the ground.
	 *
	 * @param task The task to evaluate.
	 * @return true if the task is within horizontal distance <= 4.0 blocks (distSq <= 16.0)
	 *         and vertical difference <= 2.5 blocks of the platform standing surface.
	 */
	public boolean isTaskReachableFromPlatform(ConstructionTask task) {
		if (task == null || this.activeScaffoldColumn == null || this.targetScaffoldTopY == -1) {
			return false;
		}

		double platformCenterX = this.activeScaffoldColumn.getX() + 0.5D;
		double platformCenterZ = this.activeScaffoldColumn.getZ() + 0.5D;
		double platformStandingY = (double) this.targetScaffoldTopY + 1.0D;

		BlockPos targetPos = task.getWorldPos();
		double dx = (targetPos.getX() + 0.5D) - platformCenterX;
		double dz = (targetPos.getZ() + 0.5D) - platformCenterZ;
		double horizontalDistSq = dx * dx + dz * dz;
		double verticalDiff = Math.abs(platformStandingY - (double) targetPos.getY());

		return horizontalDistSq <= 16.0D && verticalDiff <= 2.5D;
	}

	/**
	 * Checks whether the minion is currently standing on top of its active scaffolding platform.
	 */
	public boolean isStandingOnPlatform() {
		if (this.activeScaffoldColumn == null || this.targetScaffoldTopY == -1) {
			return false;
		}
		if (this.isAscendingScaffolding || this.isDescendingScaffolding) {
			return false;
		}
		double scCenterX = this.activeScaffoldColumn.getX() + 0.5D;
		double scCenterZ = this.activeScaffoldColumn.getZ() + 0.5D;
		double dx = this.minion.getX() - scCenterX;
		double dz = this.minion.getZ() - scCenterZ;
		double distSq = dx * dx + dz * dz;
		double expectedY = (double) this.targetScaffoldTopY + 1.0D;
		return distSq <= 1.0D && Math.abs(this.minion.getY() - expectedY) <= 0.6D;
	}

	public boolean isAscendingScaffolding() {
		return this.isAscendingScaffolding;
	}

	public boolean isDescendingScaffolding() {
		return this.isDescendingScaffolding;
	}

	public BlockPos getActiveScaffoldColumn() {
		return this.activeScaffoldColumn;
	}

	public int getTargetScaffoldTopY() {
		return this.targetScaffoldTopY;
	}

	public int getTargetScaffoldBottomY() {
		return this.targetScaffoldBottomY;
	}

	public Set<BlockPos> getBlacklistedScaffoldColumns() {
		return Collections.unmodifiableSet(this.blacklistedScaffoldColumns);
	}

	public boolean isColumnBlacklisted(int x, int z) {
		for (BlockPos p : this.blacklistedScaffoldColumns) {
			if (p.getX() == x && p.getZ() == z) {
				return true;
			}
		}
		return false;
	}
}