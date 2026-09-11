package com.example.entity.ai.goal;

import com.example.construction.ConstructionManager;
import com.example.construction.ConstructionSession;
import com.example.construction.ConstructionTask;
import com.example.entity.custom.MinionEntity;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
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

		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}

		// Check failure / retry cooldown
		if (serverWorld.getTime() < this.failureCooldownUntilTick) {
			return false;
		}

		// Find nearest active construction session belonging to minion's master within 48 blocks
		Optional<ConstructionSession> sessionOpt = ConstructionManager.getInstance().findNearestSessionForMinion(
			serverWorld,
			this.minion.getBlockPos(),
			this.minion.getOwnerUuid(),
			48.0D
		);

		if (sessionOpt.isEmpty()) {
			return false;
		}

		ConstructionSession session = sessionOpt.get();
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
		this.climbTicks = 0;
		this.descentTicks = 0;
		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.targetScaffoldBottomY = -1;
		this.pendingNextTask = null;

		if (this.currentTask != null) {
			// Preserve held weapon before equipping preview block
			saveHeldWeapon();

			// Visually equip the item in main hand while working
			ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
			this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);

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

		this.currentTask = null;
		this.pendingNextTask = null;
		this.currentSession = null;
		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.targetScaffoldBottomY = -1;
		this.isAscendingScaffolding = false;
		this.isDescendingScaffolding = false;
		this.climbTicks = 0;
		this.descentTicks = 0;
		this.ticksNavigating = 0;
		this.workTicks = 0;
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

			boolean onSolidGround = this.minion.isOnGround() && !serverWorld.getBlockState(this.minion.getBlockPos().down()).isOf(Blocks.SCAFFOLDING);
			if (this.minion.getY() <= this.targetScaffoldBottomY + 0.15D || onSolidGround || this.descentTicks > 120) {
				// Reached safe ground
				this.isDescendingScaffolding = false;
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
					ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
					this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);
					setupNavigationForTask(serverWorld);
				} else {
					this.currentTask = null;
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
			if (diffY > 2 || (this.targetScaffoldTopY != -1 && this.minion.getY() < this.targetScaffoldTopY)) {
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

					// Minion is at the base of the column or already ascending: climb scaffolding
					this.isAscendingScaffolding = true;
					this.minion.getNavigation().stop();

					if (this.minion.getY() < (double) this.targetScaffoldTopY) {
						this.climbTicks++;
						double alignX = scCenterX - this.minion.getX();
						double alignZ = scCenterZ - this.minion.getZ();

						// Ascend scaffolding vertically while remaining centered horizontally
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

						if (this.climbTicks > 120) {
							handleNavigationTimeout(serverWorld);
							return;
						}
						return;
					} else {
						// Arrived at top platform of scaffolding column
						this.minion.setPosition(scCenterX, (double) this.targetScaffoldTopY, scCenterZ);
						this.minion.setVelocity(0.0D, 0.0D, 0.0D);
						this.minion.velocityModified = true;
						this.minion.fallDistance = 0.0F;
						this.minion.setJumping(false);
					}
				}
			}

			// If reach is not met and minion is not climbing scaffolding, navigate towards safe stand position
			if (!this.isAscendingScaffolding) {
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

		// Immediately try to claim the next topological task for seamless continuous building
		ConstructionTask nextTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
		if (nextTask != null) {
			int currentY = this.minion.getBlockY();
			int nextY = nextTask.getWorldPos().getY();

			// If next task is significantly lower and minion is elevated on scaffolding, descend first
			if (currentY - nextY > 2 && (this.activeScaffoldColumn != null || isStandingOnScaffolding(serverWorld))) {
				this.pendingNextTask = nextTask;
				this.isDescendingScaffolding = true;
				this.descentTicks = 0;
				this.targetScaffoldBottomY = getGroundYBelow(serverWorld, this.minion.getBlockPos());
				this.minion.getNavigation().stop();
			} else {
				this.currentTask = nextTask;
				ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
				this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);
				setupNavigationForTask(serverWorld);
			}
		} else {
			// No more tasks: if minion is elevated on scaffolding, descend to ground safely before finishing
			if (this.activeScaffoldColumn != null || isStandingOnScaffolding(serverWorld)) {
				this.pendingNextTask = null;
				this.isDescendingScaffolding = true;
				this.descentTicks = 0;
				this.targetScaffoldBottomY = getGroundYBelow(serverWorld, this.minion.getBlockPos());
				this.minion.getNavigation().stop();
			} else {
				this.currentTask = null;
				this.activeScaffoldColumn = null;
				this.targetScaffoldTopY = -1;
				this.isAscendingScaffolding = false;
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

		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.isAscendingScaffolding = false;
		this.climbTicks = 0;
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
		int diffY = targetPos.getY() - this.minion.getBlockY();

		if (diffY > 2) {
			// Task is elevated: deploy or reuse scaffolding column and navigate to its base
			BlockPos scaffoldBase = deployScaffoldingIfNeeded(world, targetPos);
			if (scaffoldBase != null) {
				this.activeScaffoldColumn = scaffoldBase;
				this.targetScaffoldTopY = targetPos.getY() - 1;
				this.isAscendingScaffolding = false;
				this.climbTicks = 0;

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

		// Ground-level or reach-accessible task: navigate towards a safe adjacent standing position
		BlockPos standPos = findSafeStandPositionNear(world, targetPos);
		double navX = standPos != null ? standPos.getX() + 0.5D : targetPos.getX() + 0.5D;
		double navY = standPos != null ? standPos.getY() : targetPos.getY();
		double navZ = standPos != null ? standPos.getZ() + 0.5D : targetPos.getZ() + 0.5D;

		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.isAscendingScaffolding = false;
		this.climbTicks = 0;
		this.minion.getNavigation().startMovingTo(navX, navY, navZ, 1.15D);
	}

	private void handleNavigationTimeout(ServerWorld serverWorld) {
		if (this.currentTask != null && this.currentSession != null) {
			this.currentSession.releaseTask(this.currentTask);
		}
		this.currentTask = null;
		this.pendingNextTask = null;
		this.activeScaffoldColumn = null;
		this.targetScaffoldTopY = -1;
		this.targetScaffoldBottomY = -1;
		this.isAscendingScaffolding = false;
		this.isDescendingScaffolding = false;
		this.climbTicks = 0;
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
	 * Checks if an existing scaffolding column erected by this session is within 1 to 3 blocks
	 * horizontal reach and can be reused to access the target block.
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

			if (distSq >= 1.0D && distSq <= 12.0D) {
				int x = scaffoldPos.getX();
				int z = scaffoldPos.getZ();
				int baseY = scaffoldPos.getY();
				while (baseY > world.getBottomY() && world.getBlockState(new BlockPos(x, baseY - 1, z)).isOf(Blocks.SCAFFOLDING)) {
					baseY--;
				}
				BlockPos base = new BlockPos(x, baseY, z);
				if (!isBlueprintColumnBlocked(x, z, baseY, targetY - 1)) {
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
	 * Checks cardinal, diagonal, and offset positions, ensuring no uncompleted blueprint tasks occupy
	 * the column span.
	 */
	private BlockPos findScaffoldColumn(ServerWorld world, BlockPos targetPos) {
		Direction[] directions = new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
		int targetY = targetPos.getY();

		// First pass: cardinal directions not occupying unplaced blueprint blocks
		for (Direction dir : directions) {
			BlockPos candidate = targetPos.offset(dir);
			BlockPos base = getValidScaffoldBase(world, candidate.getX(), candidate.getZ(), targetY);
			if (base != null && !isBlueprintColumnBlocked(candidate.getX(), candidate.getZ(), base.getY(), targetY - 1)) {
				return base;
			}
		}

		// Second pass: diagonal directions not occupying unplaced blueprint blocks
		int[] dx = { -1, 1, -1, 1 };
		int[] dz = { -1, -1, 1, 1 };
		for (int i = 0; i < 4; i++) {
			BlockPos candidate = targetPos.add(dx[i], 0, dz[i]);
			BlockPos base = getValidScaffoldBase(world, candidate.getX(), candidate.getZ(), targetY);
			if (base != null && !isBlueprintColumnBlocked(candidate.getX(), candidate.getZ(), base.getY(), targetY - 1)) {
				return base;
			}
		}

		// Third pass: distance 2 cardinal directions
		for (Direction dir : directions) {
			BlockPos candidate = targetPos.offset(dir, 2);
			BlockPos base = getValidScaffoldBase(world, candidate.getX(), candidate.getZ(), targetY);
			if (base != null && !isBlueprintColumnBlocked(candidate.getX(), candidate.getZ(), base.getY(), targetY - 1)) {
				return base;
			}
		}

		// Fallback passes: any adjacent column with clear vertical air
		for (Direction dir : directions) {
			BlockPos candidate = targetPos.offset(dir);
			BlockPos base = getValidScaffoldBase(world, candidate.getX(), candidate.getZ(), targetY);
			if (base != null) {
				return base;
			}
		}

		for (int i = 0; i < 4; i++) {
			BlockPos candidate = targetPos.add(dx[i], 0, dz[i]);
			BlockPos base = getValidScaffoldBase(world, candidate.getX(), candidate.getZ(), targetY);
			if (base != null) {
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
}
