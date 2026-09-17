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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

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

	private ConstructionTask pendingNextTask = null;
	private Vec3d hoverStationVec = null;

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

		// Architectural gating: BUILDER participates in both construction & deconstruction
		MinionRole role = this.minion.getRole();
		if (role != MinionRole.BUILDER) {
			return false;
		}

		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}

		// Check failure / retry cooldown
		if (serverWorld.getTime() < this.failureCooldownUntilTick) {
			return false;
		}

		// Find nearest active construction/dismantle session belonging to minion's master within 128 blocks
		Optional<ConstructionSession> sessionOpt = ConstructionManager.getInstance().findNearestSessionForMinion(
			serverWorld,
			this.minion.getBlockPos(),
			this.minion.getOwnerUuid(),
			128.0D,
			role
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

		// Continue goal while floating down from levitation to safely land on solid ground
		if (this.minion.isArcaneLevitating() && !this.minion.isOnGround()) {
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
		this.pendingNextTask = null;
		this.hoverStationVec = null;

		if (this.currentTask != null) {
			this.minion.setActivelyBuilding(true);
			this.minion.setArcaneLevitating(true);
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

		this.currentTask = null;
		this.pendingNextTask = null;
		this.currentSession = null;
		this.hoverStationVec = null;
		this.minion.setActivelyBuilding(false);
		this.minion.setArcaneLevitating(false);
		this.minion.setNoGravity(false);
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

		if (this.currentTask == null || this.currentSession == null) {
			this.minion.setActivelyBuilding(false);
			restoreHeldWeapon();
			// If idle or tasks finished and still levitating, check if an active destination exists.
			// If active destination exists (e.g. commander on ground or waypoint), allow universal traversal to guide them;
			// otherwise float gently down to ground.
			if (this.minion.isArcaneLevitating()) {
				Vec3d activeDest = this.minion.resolveActiveTargetDestination();
				if (activeDest == null) {
					BlockPos feet = this.minion.getBlockPos();
					BlockPos below = feet.down();
					BlockState belowState = serverWorld.getBlockState(below);
					if (!this.minion.isOnGround() && !belowState.isSolidBlock(serverWorld, below) && feet.getY() > serverWorld.getBottomY()) {
						this.minion.setVelocity(0.0D, -0.22D, 0.0D);
						this.minion.velocityModified = true;
					} else {
						this.minion.setArcaneLevitating(false);
						this.minion.setVelocity(0.0D, 0.0D, 0.0D);
						this.minion.velocityModified = true;
					}
				}
			}
			return;
		}

		BlockPos targetPos = this.currentTask.getWorldPos();

		// Safeguard for DISMANTLE mode: if the target block in the world is already air or indestructible,
		// do not pathfind to it or swing at it! Complete it immediately and advance to next block.
		if (this.currentSession.isDismantle()) {
			BlockState targetState = serverWorld.getBlockState(targetPos);
			if (targetState.isAir() || isIndestructibleBlock(targetState, serverWorld, targetPos)) {
				this.currentSession.completeTask(this.currentTask, serverWorld);
				this.workTicks = 0;
				this.ticksNavigating = 0;
				this.currentTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
				if (this.currentTask != null) {
					this.minion.equipStack(EquipmentSlot.MAINHAND, resolveDismantleTool());
					setupNavigationForTask(serverWorld);
				} else {
					this.currentTask = null;
					this.hoverStationVec = null;
					this.minion.setActivelyBuilding(false);
					restoreHeldWeapon();
				}
				return;
			}
		}

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

		if (!this.minion.isArcaneLevitating()) {
			this.minion.setArcaneLevitating(true);
			this.hoverStationVec = findOptimalHoverStation(serverWorld, targetPos);
		}

		if (this.minion.isArcaneLevitating()) {
			if (this.hoverStationVec == null) {
				this.hoverStationVec = findOptimalHoverStation(serverWorld, targetPos);
			}

			Vec3d delta = this.hoverStationVec.subtract(this.minion.getPos());
			double distToHover = delta.length();
			boolean inLevitationReach = horizontalDistSq <= 16.0D && verticalDiff <= 3.0D;

			this.minion.getNavigation().stop();
			this.minion.fallDistance = 0.0F;

			if (!inLevitationReach || distToHover > 0.45D) {
				this.ticksNavigating++;
				Vec3d vel = delta.normalize().multiply(0.35D);
				if (this.minion.horizontalCollision || (targetPos.getY() > this.minion.getBlockY() && delta.y > 0.1D)) {
					vel = new Vec3d(vel.x * 0.4D, Math.max(vel.y, 0.40D), vel.z * 0.4D);
				}
				this.minion.setVelocity(vel);
				this.minion.velocityModified = true;

				if (this.ticksNavigating > 400) {
					handleNavigationTimeout(serverWorld);
					return;
				}
				return;
			}

			// In reach at hover station: hold position in mid-air
			this.minion.setVelocity(0.0D, 0.0D, 0.0D);
			this.minion.velocityModified = true;
			this.ticksNavigating = 0;
		} else {
			// Ground navigation reach check: horizontal <= 4.0 blocks (16.0 sq) and vertical diff <= 2.5 blocks
			boolean inRange = horizontalDistSq <= 16.0D && verticalDiff <= 2.5D;
			if (!inRange) {
				this.ticksNavigating++;
				if (this.ticksNavigating % 15 == 0 || this.minion.getNavigation().isIdle()) {
					BlockPos standPos = findSafeStandPositionNear(serverWorld, targetPos);
					if (standPos != null) {
						boolean started = this.minion.getNavigation().startMovingTo(
							standPos.getX() + 0.5D,
							standPos.getY(),
							standPos.getZ() + 0.5D,
							1.15D
						);
						if (!started || this.minion.getNavigation().getCurrentPath() == null || !this.minion.getNavigation().getCurrentPath().reachesTarget()) {
							this.minion.setArcaneLevitating(true);
							this.hoverStationVec = findOptimalHoverStation(serverWorld, targetPos);
							return;
						}
					} else {
						// Fall back to levitation if ground path blocked
						this.minion.setArcaneLevitating(true);
						this.hoverStationVec = findOptimalHoverStation(serverWorld, targetPos);
						return;
					}
				}

				if (this.minion.horizontalCollision && this.ticksNavigating > 15) {
					this.minion.setArcaneLevitating(true);
					this.hoverStationVec = findOptimalHoverStation(serverWorld, targetPos);
					return;
				}

				if (this.ticksNavigating > 400) {
					handleNavigationTimeout(serverWorld);
					return;
				}
				return;
			}

			// In range on ground: halt movement
			this.minion.getNavigation().stop();
			this.minion.setJumping(false);
			this.minion.fallDistance = 0.0F;
		}

		// Minion is within realistic reach: halt movement and perform construction work
		this.minion.getNavigation().stop();
		this.minion.setJumping(false);
		this.minion.fallDistance = 0.0F;

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

			// 3. Peer-to-peer allied minion block sharing
			if (!hasResources) {
				hasResources = com.example.entity.ai.logistics.MinionLogisticsHelper.requestItemFromAllies(this.minion, serverWorld, requiredItem);
				if (hasResources) {
					int slot = findItemSlot(minionInv, requiredItem);
					if (slot != -1) {
						minionInv.removeStack(slot, 1);
					}
				}
			}

			// 4. Autonomous Material Harvesting & Agro-Forestry
			if (!hasResources) {
				hasResources = com.example.entity.ai.logistics.MinionHarvestingHelper.tryAutonomousHarvest(this.minion, serverWorld, requiredItem, this.currentSession);
				if (hasResources) {
					int slot = findItemSlot(minionInv, requiredItem);
					if (slot != -1) {
						minionInv.removeStack(slot, 1);
					}
				}
			}
		}

		// If resources could not be found in inventory or nearby containers
		if (!hasResources) {
			handleResourceDeficiency(serverWorld, requiredItem);
			return;
		}

		// Execute block placement in world if not already matching
		if (!alreadyPlaced) {
			// Zero-drop pre-clearing in creative mode
			if (this.currentSession.isCreative()) {
				BlockState existingObstacle = serverWorld.getBlockState(targetPos);
				if (!existingObstacle.isAir()) {
					serverWorld.breakBlock(targetPos, false, this.minion);
				}
			}

			if (targetState.getBlock() instanceof DoorBlock) {
				if (targetState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					if (this.currentSession.isCreative()) {
						BlockState upperObstacle = serverWorld.getBlockState(targetPos.up());
						if (!upperObstacle.isAir()) {
							serverWorld.breakBlock(targetPos.up(), false, this.minion);
						}
					}
					serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_ALL);
					BlockState upperState = targetState.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
					serverWorld.setBlockState(targetPos.up(), upperState, Block.NOTIFY_ALL);
				} else {
					BlockPos lowerPos = targetPos.down();
					if (!serverWorld.getBlockState(lowerPos).isOf(targetState.getBlock())) {
						if (this.currentSession.isCreative()) {
							BlockState lowerObstacle = serverWorld.getBlockState(lowerPos);
							if (!lowerObstacle.isAir()) {
								serverWorld.breakBlock(lowerPos, false, this.minion);
							}
						}
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

		// Check for excess non-blueprint materials and deposit into supply depots
		com.example.entity.ai.logistics.MinionHarvestingHelper.checkAndDepositExcessMaterials(this.minion, serverWorld, this.currentSession);

		// Reset work and navigation counters
		this.workTicks = 0;
		this.ticksNavigating = 0;
		this.hoverStationVec = null;

		// Immediately try to claim the next topological task for seamless continuous building
		ConstructionTask nextTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
		if (nextTask != null) {
			this.currentTask = nextTask;
			ItemStack previewStack = this.currentTask.getBlueprintBlock().getRequiredStack();
			this.minion.equipStack(EquipmentSlot.MAINHAND, previewStack);

			BlockPos nextPos = nextTask.getWorldPos();
			if (nextPos.getY() > this.minion.getBlockY() + 1 || this.minion.isArcaneLevitating()) {
				this.minion.setArcaneLevitating(true);
				this.hoverStationVec = findOptimalHoverStation(serverWorld, nextPos);
			} else {
				setupNavigationForTask(serverWorld);
			}
		} else {
			this.currentTask = null;
			this.hoverStationVec = null;
			this.minion.setActivelyBuilding(false);
			restoreHeldWeapon();
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
	 * Finds an optimal 3D air position for arcane levitation adjacent to the target construction block.
	 * Evaluates 8 horizontal cardinal and diagonal offsets at a comfortable distance (1.4 - 1.8 blocks)
	 * and selects the closest non-solid, open position to the minion with adequate headroom.
	 *
	 * @param world     The server world context.
	 * @param targetPos The target block position to be constructed or dismantled.
	 * @return A {@link Vec3d} representing the target hovering coordinates in 3D space.
	 */
	public Vec3d findOptimalHoverStation(ServerWorld world, BlockPos targetPos) {
		double targetX = targetPos.getX() + 0.5D;
		double targetY = targetPos.getY() - 0.2D;
		double targetZ = targetPos.getZ() + 0.5D;

		int[][] offsets = {
			{ 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 },
			{ 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
		};

		Vec3d bestCandidate = null;
		double bestScore = Double.MAX_VALUE;

		for (int[] offset : offsets) {
			double dist = (offset[0] != 0 && offset[1] != 0) ? 1.4D : 1.8D;
			double candX = targetX + (offset[0] * dist);
			double candZ = targetZ + (offset[1] * dist);
			BlockPos candPos = BlockPos.ofFloored(candX, targetY, candZ);
			BlockPos candHeadPos = candPos.up();

			BlockState feetState = world.getBlockState(candPos);
			BlockState headState = world.getBlockState(candHeadPos);

			boolean feetOpen = feetState.isAir() || !feetState.isSolidBlock(world, candPos);
			boolean headOpen = headState.isAir() || !headState.isSolidBlock(world, candHeadPos);

			if (feetOpen && headOpen) {
				Vec3d candVec = new Vec3d(candX, targetY, candZ);
				double distToMinion = candVec.squaredDistanceTo(this.minion.getPos());

				HitResult hit = world.raycast(new RaycastContext(
					this.minion.getEyePos(),
					candVec.add(0, 0.5D, 0),
					RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE,
					this.minion
				));
				boolean obstructed = (hit.getType() != HitResult.Type.MISS);
				double score = distToMinion + (obstructed ? 1000.0D : 0.0D);

				if (score < bestScore) {
					bestScore = score;
					bestCandidate = candVec;
				}
			}
		}

		if (bestCandidate != null && bestScore < 1000.0D) {
			return bestCandidate;
		}

		// Fallback: check 1.2 to 2.2 blocks elevated above the block in open air
		for (double dy : new double[] { 1.2D, 2.2D }) {
			double elevatedY = targetPos.getY() + dy;
			for (int[] offset : offsets) {
				double dist = (offset[0] != 0 && offset[1] != 0) ? 1.4D : 1.8D;
				double candX = targetX + (offset[0] * dist);
				double candZ = targetZ + (offset[1] * dist);
				BlockPos candPos = BlockPos.ofFloored(candX, elevatedY, candZ);
				if (world.getBlockState(candPos).isAir()) {
					return new Vec3d(candX, elevatedY, candZ);
				}
			}
		}

		if (bestCandidate != null) {
			return bestCandidate;
		}

		return new Vec3d(targetX + 1.8D, targetY, targetZ);
	}

	/**
	 * Configures navigation targets for the current task.
	 * If the task is elevated above the minion's reach, engages Arcane Levitation.
	 * Otherwise, navigates towards a safe adjacent standing position next to the target block.
	 */
	private void setupNavigationForTask(ServerWorld world) {
		if (this.currentTask == null) {
			return;
		}

		BlockPos targetPos = this.currentTask.getWorldPos();
		int diffY = targetPos.getY() - this.minion.getBlockY();

		// If task is elevated or minion is already levitating, engage Arcane Builder Levitation!
		if (diffY > 1 || this.minion.isArcaneLevitating()) {
			this.minion.setArcaneLevitating(true);
			this.hoverStationVec = findOptimalHoverStation(world, targetPos);
			this.minion.getNavigation().stop();
			return;
		}

		// Ground-level or reach-accessible task: try ground navigation first
		BlockPos standPos = findSafeStandPositionNear(world, targetPos);
		if (standPos != null) {
			boolean started = this.minion.getNavigation().startMovingTo(
				standPos.getX() + 0.5D,
				standPos.getY(),
				standPos.getZ() + 0.5D,
				1.15D
			);
			if (started && this.minion.getNavigation().getCurrentPath() != null && this.minion.getNavigation().getCurrentPath().reachesTarget()) {
				this.hoverStationVec = null;
				this.minion.setArcaneLevitating(false);
				return;
			}
		}

		// Fallback to Arcane Levitation if ground path blocked or unreachable
		this.minion.setArcaneLevitating(true);
		this.hoverStationVec = findOptimalHoverStation(world, targetPos);
		this.minion.getNavigation().stop();
	}

	private void handleNavigationTimeout(ServerWorld serverWorld) {
		if (this.currentTask != null && this.currentSession != null) {
			this.currentSession.releaseTask(this.currentTask);
		}
		this.currentTask = null;
		this.pendingNextTask = null;
		this.hoverStationVec = null;
		this.minion.setArcaneLevitating(false);
		this.minion.setNoGravity(false);
		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.minion.getNavigation().stop();
		this.minion.fallDistance = 0.0F;
		this.failureCooldownUntilTick = serverWorld.getTime() + 15L;
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
		if (!belowState.isSolidBlock(world, below) && !isScaffoldBlock(belowState)) {
			return false;
		}

		// Space for feet and head
		BlockState feet = world.getBlockState(pos);
		BlockState head = world.getBlockState(pos.up());

		boolean feetPassable = feet.isAir() || isScaffoldBlock(feet) || feet.canPathfindThrough(NavigationType.LAND);
		boolean headPassable = head.isAir() || isScaffoldBlock(head) || head.canPathfindThrough(NavigationType.LAND);

		return feetPassable && headPassable && !feet.isOf(Blocks.LAVA) && !feet.isOf(Blocks.FIRE);
	}



	/**
	 * Checks whether the given block state represents a valid scaffolding block.
	 *
	 * @param state The BlockState to inspect.
	 * @return True if state is Blocks.SCAFFOLDING.
	 */
	public static boolean isScaffoldBlock(BlockState state) {
		if (state == null) {
			return false;
		}
		return state.isOf(Blocks.SCAFFOLDING);
	}

	/**
	 * Strictly checks whether a block is indestructible or bedrock.
	 * Enforces both explicit Blocks.BEDROCK check and hardness < 0.0F check.
	 *
	 * @param state BlockState to check.
	 * @param world World context.
	 * @param pos   Position context.
	 * @return True if the block is indestructible, unbreakable, or bedrock.
	 */
	public static boolean isIndestructibleBlock(BlockState state, World world, BlockPos pos) {
		if (state == null) {
			return false;
		}
		if (state.isOf(Blocks.BEDROCK)) {
			return true;
		}
		try {
			if (world != null && pos != null) {
				if (state.getHardness(world, pos) < 0.0F) {
					return true;
				}
			}
			if (state.getBlock().getHardness() < 0.0F) {
				return true;
			}
		} catch (Exception ignored) {
			return state.isOf(Blocks.BEDROCK);
		}
		return ConstructionSession.isIndestructible(state, world, pos);
	}



	/**
	 * Executes deconstruction work on a target block: plays break sounds and particles,
	 * drops harvested items in survival, handles multi-block doors, and completes the task.
	 */
	private void executeDismantleWork(ServerWorld serverWorld, BlockPos targetPos) {
		this.minion.getNavigation().stop();
		this.minion.setJumping(false);
		this.minion.fallDistance = 0.0F;

		this.workTicks++;

		// 4 ticks deliberate work delay
		if (this.workTicks < 4) {
			return;
		}

		BlockState currentState = serverWorld.getBlockState(targetPos);
		boolean alreadyAir = currentState.isAir();

		if (alreadyAir) {
			this.currentSession.completeTask(this.currentTask, serverWorld);
			this.workTicks = 0;
			this.ticksNavigating = 0;
			this.hoverStationVec = null;

			ConstructionTask nextTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
			if (nextTask != null) {
				this.currentTask = nextTask;
				this.minion.equipStack(EquipmentSlot.MAINHAND, resolveDismantleTool());

				BlockPos nextPos = nextTask.getWorldPos();
				if (nextPos.getY() > this.minion.getBlockY() + 1 || this.minion.isArcaneLevitating()) {
					this.minion.setArcaneLevitating(true);
					this.hoverStationVec = findOptimalHoverStation(serverWorld, nextPos);
				} else {
					setupNavigationForTask(serverWorld);
				}
			} else {
				this.currentTask = null;
				this.hoverStationVec = null;
				if (!this.minion.isArcaneLevitating()) {
					restoreHeldWeapon();
				}
			}
			return;
		}

		// Strict safeguard: Indestructible blocks (hardness < 0.0F or Blocks.BEDROCK) must NEVER be broken!
		if (isIndestructibleBlock(currentState, serverWorld, targetPos)) {
			this.currentSession.completeTask(this.currentTask, serverWorld);
			this.workTicks = 0;
			return;
		}

		// Handle double doors cleanly
		if (currentState.getBlock() instanceof DoorBlock) {
			if (currentState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				BlockPos upper = targetPos.up();
				BlockState upperState = serverWorld.getBlockState(upper);
				if (upperState.isOf(currentState.getBlock()) && !isIndestructibleBlock(upperState, serverWorld, upper)) {
					serverWorld.breakBlock(upper, !this.currentSession.isCreative(), this.minion);
				}
			} else {
				BlockPos lower = targetPos.down();
				BlockState lowerState = serverWorld.getBlockState(lower);
				if (lowerState.isOf(currentState.getBlock()) && !isIndestructibleBlock(lowerState, serverWorld, lower)) {
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
		this.hoverStationVec = null;

		// Immediately try to claim the next top-down task for seamless deconstruction
		ConstructionTask nextTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
		if (nextTask != null) {
			this.currentTask = nextTask;
			this.minion.equipStack(EquipmentSlot.MAINHAND, resolveDismantleTool());

			BlockPos nextPos = nextTask.getWorldPos();
			if (nextPos.getY() > this.minion.getBlockY() + 1 || this.minion.isArcaneLevitating()) {
				this.minion.setArcaneLevitating(true);
				this.hoverStationVec = findOptimalHoverStation(serverWorld, nextPos);
			} else {
				setupNavigationForTask(serverWorld);
			}
		} else {
			this.currentTask = null;
			this.hoverStationVec = null;
			if (!this.minion.isArcaneLevitating()) {
				restoreHeldWeapon();
			}
		}
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

}