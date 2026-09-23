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
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
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
import net.minecraft.util.math.Box;
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
	private int stallCollisionTicks = 0;
	private boolean groundNavigationForced = false;
	private int exitTraverseTicks = 0;

	// Equipment preservation across construction cycles
	private ItemStack savedHeldWeapon = ItemStack.EMPTY;

	public MinionBuildGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		// Minion must be alive, tamed, and have an owner
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.getOwnerUuid() == null) {
			return false;
		}

		// Architectural gating: BUILDER participates in both construction & deconstruction
		if (!this.minion.matchesRole(MinionRole.BUILDER)) {
			return false;
		}

		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}

		// Check failure / retry cooldown
		if (serverWorld.getTime() < this.failureCooldownUntilTick) {
			return false;
		}

		// Stationed minions can autonomously mobilize if an active blueprint is nearby (<= 112 blocks)
		double searchRadius = this.minion.isSitting() ? 112.0D : 128.0D;

		// Find nearest active construction/dismantle session belonging to minion's master
		Optional<ConstructionSession> sessionOpt = ConstructionManager.getInstance().findNearestSessionForMinion(
			serverWorld,
			this.minion.getBlockPos(),
			this.minion.getOwnerUuid(),
			searchRadius,
			this.minion.getEffectiveRole()
		);

		if (sessionOpt.isEmpty()) {
			return false;
		}

		ConstructionSession session = sessionOpt.get();
		ConstructionTask task = session.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
		if (task == null) {
			return false;
		}

		// Mobilize from stationed/sitting posture to construct
		if (this.minion.isSitting()) {
			this.minion.setSitting(false);
		}
		if (this.minion.getGuardAnchorPos() != null) {
			this.minion.setGuardAnchorPos(null);
		}

		this.currentSession = session;
		this.currentTask = task;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed()) {
			return false;
		}

		// Continue goal while floating down from levitation to safely land on solid ground
		if (this.minion.isArcaneLevitating() && !this.minion.isOnGround()) {
			return true;
		}

		// If minion is sitting, check if an active session in vicinity (<= 128 blocks) can wake and chain them
		if (this.minion.isSitting()) {
			if (this.minion.getWorld() instanceof ServerWorld serverWorld && this.minion.getOwnerUuid() != null) {
				Optional<ConstructionSession> nearby = ConstructionManager.getInstance().findNearestSessionForMinion(
					serverWorld,
					this.minion.getBlockPos(),
					this.minion.getOwnerUuid(),
					128.0D,
					this.minion.getEffectiveRole()
				);
				if (nearby.isPresent() && nearby.get().isActive()) {
					this.minion.setSitting(false);
					this.minion.setGuardAnchorPos(null);
				} else {
					return false;
				}
			} else {
				return false;
			}
		}

		if (this.currentSession == null || !this.currentSession.isActive()) {
			if (this.minion.getWorld() instanceof ServerWorld serverWorld && this.minion.getOwnerUuid() != null) {
				Optional<ConstructionSession> chained = ConstructionManager.getInstance().findNearestSessionForMinion(
					serverWorld,
					this.minion.getBlockPos(),
					this.minion.getOwnerUuid(),
					128.0D,
					this.minion.getEffectiveRole()
				);
				if (chained.isPresent() && chained.get().isActive()) {
					return true;
				}
			}
			return false;
		}

		// Persistent build goal: stays active throughout the construction session until 100% finished
		return true;
	}

	@Override
	public void start() {
		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.stallCollisionTicks = 0;
		this.groundNavigationForced = false;
		this.pendingNextTask = null;
		this.hoverStationVec = null;

		if (this.currentTask != null) {
			this.minion.setActivelyBuilding(true);
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

		BlockBox sessionBox = this.currentSession != null ? this.currentSession.getWorldBoundingBox() : null;
		BlockPos sessionAnchor = this.currentSession != null ? this.currentSession.getAnchorPos() : null;

		this.currentTask = null;
		this.pendingNextTask = null;
		this.currentSession = null;
		this.hoverStationVec = null;
		this.stallCollisionTicks = 0;
		this.groundNavigationForced = false;
		this.exitTraverseTicks = 0;
		this.minion.setActivelyBuilding(false);
		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.minion.getNavigation().stop();
		this.minion.fallDistance = 0.0F;

		// Restore held weapon when stopping
		restoreHeldWeapon();

		// If minion is inside the structure when stopping, initiate structure egress before dropping flight/noClip
		if (sessionBox != null && sessionAnchor != null && this.minion.isInsideStructure(sessionBox)) {
			this.minion.startEgressFromStructure(sessionBox, sessionAnchor, null);
		} else if (!this.minion.isExitingBuilding()) {
			this.minion.setArcaneLevitating(false);
			this.minion.setNoGravity(false);
		}
	}

	@Override
	public void tick() {
		if (!(this.minion.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}

		if (this.currentTask == null || this.currentSession == null || !this.currentSession.isActive()) {
			if (tryClaimTaskOrChainNextSession(serverWorld)) {
				return;
			}

			// If current session is non-null and not active, trigger egress if inside structure
			if (this.currentSession != null && !this.minion.isExitingBuilding()) {
				BlockBox sessionBox = this.currentSession.getWorldBoundingBox();
				BlockPos sessionAnchor = this.currentSession.getAnchorPos();
				if (sessionBox != null && sessionAnchor != null && this.minion.isInsideStructure(sessionBox)) {
					this.minion.startEgressFromStructure(sessionBox, sessionAnchor, null);
				}
			}

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

		// Sweep stray items in Creative mode, or vacuum loose items in Survival mode
		if (this.currentSession.isCreative()) {
			Box sweepBox = this.minion.getBoundingBox().expand(4.0D);
			List<ItemEntity> strayItems = serverWorld.getEntitiesByClass(ItemEntity.class, sweepBox, e -> true);
			for (ItemEntity stray : strayItems) {
				stray.discard();
			}
		} else {
			Box vacuumBox = this.minion.getBoundingBox().expand(3.0D);
			List<ItemEntity> nearbyItems = serverWorld.getEntitiesByClass(ItemEntity.class, vacuumBox, e -> e.isAlive() && !e.cannotPickup());
			for (ItemEntity item : nearbyItems) {
				ItemStack stack = item.getStack();
				if (!stack.isEmpty()) {
					ItemStack rem = this.minion.getInventory().addStack(stack);
					if (rem.isEmpty()) {
						item.discard();
					} else {
						item.setStack(rem);
					}
				}
			}
		}

		// Safeguard for DISMANTLE mode: if the target block in the world is already air or indestructible,
		// do not pathfind to it or swing at it! Complete it immediately and advance to next block.
		if (this.currentSession.isDismantle()) {
			BlockState targetState = serverWorld.getBlockState(targetPos);
			if (targetState.isAir() || isIndestructibleBlock(targetState, serverWorld, targetPos)) {
				this.currentSession.completeTask(this.currentTask, serverWorld);
				this.workTicks = 0;
				this.ticksNavigating = 0;
				this.stallCollisionTicks = 0;
				this.groundNavigationForced = false;
				this.hoverStationVec = null;
				if (!tryClaimTaskOrChainNextSession(serverWorld)) {
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

		// Check for ceiling clearance above the minion
		boolean hasCeilingObstruction = hasCeilingAboveMinion(serverWorld, 3);

		// If minion is not levitating and ground navigation was not forced, evaluate if levitation is appropriate
		if (!this.minion.isArcaneLevitating() && !this.groundNavigationForced) {
			Vec3d optimalStation = findOptimalHoverStation(serverWorld, targetPos);
			if (this.minion.isPhasingBlocks() || hasLineOfSightToStation(serverWorld, optimalStation)) {
				if (this.minion.isPhasingBlocks() || !hasCeilingObstruction || !hasCeilingAboveMinion(serverWorld, 2) || optimalStation.getY() <= this.minion.getY() + 0.5D) {
					this.minion.setArcaneLevitating(true);
					this.hoverStationVec = optimalStation;
				} else {
					this.groundNavigationForced = true;
				}
			} else if (hasCeilingObstruction && !this.minion.isPhasingBlocks()) {
				this.groundNavigationForced = true;
			}
		}

		if (this.minion.isArcaneLevitating()) {
			if (this.hoverStationVec == null) {
				this.hoverStationVec = findOptimalHoverStation(serverWorld, targetPos);
			}

			// Validate line of sight from eye position to hover station
			if (!this.minion.isPhasingBlocks() && !hasLineOfSightToStation(serverWorld, this.hoverStationVec)) {
				// If target is inside while minion is outside/elevated, glide to entrance doorstep first
				BlockPos exitPos = findStructureExitWaypoint(serverWorld);
				if (exitPos != null && this.minion.getY() > exitPos.getY() + 1.5D) {
					this.hoverStationVec = Vec3d.ofBottomCenter(exitPos);
				} else {
					// Line of sight obstructed: drop levitation and fallback to ground A* door navigation
					this.minion.setArcaneLevitating(false);
					this.hoverStationVec = null;
					this.groundNavigationForced = true;
					this.stallCollisionTicks = 0;
				}
			}
		}

		if (this.minion.isArcaneLevitating()) {
			Vec3d delta = this.hoverStationVec != null ? this.hoverStationVec.subtract(this.minion.getPos()) : Vec3d.ZERO;
			double distToHover = delta.length();
			boolean inLevitationReach = horizontalDistSq <= 16.0D && verticalDiff <= 3.0D;

			this.minion.getNavigation().stop();
			this.minion.fallDistance = 0.0F;

			// If within reach of the target block or close to the hover station, halt and start building
			if (inLevitationReach || distToHover <= 0.35D) {
				this.minion.setVelocity(0.0D, 0.0D, 0.0D);
				this.minion.velocityModified = true;
				this.ticksNavigating = 0;
				this.stallCollisionTicks = 0;
			} else {
				this.ticksNavigating++;

				// Track collision and stall duration during levitation: only stall if actively colliding and stuck
				boolean isColliding = this.minion.horizontalCollision || (this.minion.verticalCollision && delta.y > 0.0D);
				boolean isActuallyStuck = isColliding && (this.minion.getVelocity().horizontalLengthSquared() < 0.005D || Math.abs(this.minion.getVelocity().y) < 0.01D);
				if (isActuallyStuck || (hasCeilingObstruction && this.minion.verticalCollision && delta.y > 0.0D)) {
					this.stallCollisionTicks++;
				} else {
					this.stallCollisionTicks = Math.max(0, this.stallCollisionTicks - 1);
				}

				// If stalled or colliding for 12+ ticks, fallback to ground navigation or phase-shift if persistent
				if (this.stallCollisionTicks >= 12 || this.ticksNavigating > 40) {
					BlockPos standPos = findSafeStandPositionNear(serverWorld, targetPos);
					if (this.ticksNavigating > 40) {
						Vec3d phaseDest = standPos != null ? Vec3d.ofBottomCenter(standPos) : (this.hoverStationVec != null ? this.hoverStationVec : Vec3d.ofBottomCenter(targetPos));
						serverWorld.spawnParticles(
							ParticleTypes.PORTAL,
							this.minion.getX(), this.minion.getY() + 0.5D, this.minion.getZ(),
							25, 0.4D, 0.6D, 0.4D, 0.2D
						);
						serverWorld.playSound(null, this.minion.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
						this.minion.requestTeleport(phaseDest.x, phaseDest.y, phaseDest.z);
						this.ticksNavigating = 0;
						this.stallCollisionTicks = 0;
						this.groundNavigationForced = false;
						this.minion.getNavigation().stop();
						return;
					}

					if (!this.minion.isPhasingBlocks()) {
						this.minion.setArcaneLevitating(false);
						this.hoverStationVec = null;
						this.groundNavigationForced = true;
						this.stallCollisionTicks = 0;
						if (standPos != null) {
							this.minion.getNavigation().startMovingTo(
								standPos.getX() + 0.5D,
								standPos.getY(),
								standPos.getZ() + 0.5D,
								1.15D
							);
						}
						return;
					}
				}

				// Distance-dampened velocity prevents overshooting and oscillation
				double speed = Math.min(0.35D, Math.max(0.08D, distToHover * 0.5D));
				Vec3d vel = delta.normalize().multiply(speed);
				if (this.minion.horizontalCollision || (targetPos.getY() > this.minion.getBlockY() && delta.y > 0.1D)) {
					vel = new Vec3d(vel.x * 0.4D, Math.max(vel.y, 0.40D), vel.z * 0.4D);
				}

				// Vertical velocity ceiling clamping: clamp vel.y <= 0 when solid blocks are within 1.9D overhead (unless phasing blocks)
				if (!this.minion.isPhasingBlocks() && hasCeilingAboveMinion(serverWorld, 2) && vel.y > 0.0D) {
					vel = new Vec3d(vel.x, 0.0D, vel.z);
				}

				this.minion.setVelocity(vel);
				this.minion.velocityModified = true;
				return;
			}
		} else {
			// Ground navigation reach check: horizontal <= 4.0 blocks (16.0 sq) and vertical diff <= 2.5 blocks
			boolean inRange = horizontalDistSq <= 16.0D && verticalDiff <= 2.5D;
			if (!inRange) {
				this.ticksNavigating++;
				autoOpenNearbyDoors(serverWorld);

				if (this.ticksNavigating % 10 == 0 || this.minion.getNavigation().isIdle()) {
					BlockPos standPos = findSafeStandPositionNear(serverWorld, targetPos);
					boolean pathValid = false;
					if (standPos != null) {
						boolean started = this.minion.getNavigation().startMovingTo(
							standPos.getX() + 0.5D,
							standPos.getY(),
							standPos.getZ() + 0.5D,
							1.15D
						);
						pathValid = started && this.minion.getNavigation().getCurrentPath() != null && this.minion.getNavigation().getCurrentPath().reachesTarget();
					}

					if (!pathValid) {
						Vec3d candidateStation = findOptimalHoverStation(serverWorld, targetPos);
						if (this.minion.isPhasingBlocks() || hasLineOfSightToStation(serverWorld, candidateStation)) {
							this.minion.setArcaneLevitating(true);
							this.hoverStationVec = candidateStation;
							this.groundNavigationForced = false;
							this.exitTraverseTicks = 0;
							return;
						}

						// Minion cannot pathfind on foot to target and has no line of sight to station (enclosed indoors/under ceiling).
						// Route toward the nearest exterior doorway or open-sky perimeter exit:
						BlockPos exitPos = findStructureExitWaypoint(serverWorld);
						if (exitPos != null && !this.minion.isPhasingBlocks()) {
							this.exitTraverseTicks++;
							this.minion.getNavigation().startMovingTo(
								exitPos.getX() + 0.5D,
								exitPos.getY(),
								exitPos.getZ() + 0.5D,
								1.25D
							);
							autoOpenNearbyDoors(serverWorld);

							double distToExitSq = this.minion.squaredDistanceTo(exitPos.getX() + 0.5D, exitPos.getY(), exitPos.getZ() + 0.5D);
							boolean reachedOutside = distToExitSq <= 3.0D || (!hasCeilingAboveMinion(serverWorld, 2) && hasLineOfSightToStation(serverWorld, candidateStation));

							if (reachedOutside) {
								this.minion.getNavigation().stop();
								this.groundNavigationForced = false;
								this.exitTraverseTicks = 0;
								this.minion.setArcaneLevitating(true);
								this.hoverStationVec = candidateStation;
								return;
							}

							// Arcane Phase Egress: If trapped in an enclosed room or blocked for 35+ ticks
							if (this.exitTraverseTicks > 35) {
								serverWorld.spawnParticles(
									ParticleTypes.PORTAL,
									this.minion.getX(), this.minion.getY() + 0.5D, this.minion.getZ(),
									25, 0.4D, 0.6D, 0.4D, 0.2D
								);
								serverWorld.playSound(null, this.minion.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
								this.minion.requestTeleport(exitPos.getX() + 0.5D, exitPos.getY(), exitPos.getZ() + 0.5D);
								this.groundNavigationForced = false;
								this.exitTraverseTicks = 0;
								this.minion.setArcaneLevitating(true);
								this.hoverStationVec = candidateStation;
								return;
							}
						}
					} else {
						this.exitTraverseTicks = 0;
					}
				}

				if (this.minion.horizontalCollision && this.ticksNavigating > 15) {
					Vec3d candidateStation = findOptimalHoverStation(serverWorld, targetPos);
					if (this.minion.isPhasingBlocks() || hasLineOfSightToStation(serverWorld, candidateStation)) {
						this.minion.setArcaneLevitating(true);
						this.hoverStationVec = candidateStation;
						this.groundNavigationForced = false;
						return;
					}
				}

				if (this.ticksNavigating > 40) {
					// Arcane Phase-Shift: If physically obstructed, immediately teleport directly to work stand position or hover station
					BlockPos standPos = findSafeStandPositionNear(serverWorld, targetPos);
					Vec3d phaseDest = standPos != null ? Vec3d.ofBottomCenter(standPos) : (this.hoverStationVec != null ? this.hoverStationVec : Vec3d.ofBottomCenter(targetPos));
					serverWorld.spawnParticles(
						ParticleTypes.PORTAL,
						this.minion.getX(), this.minion.getY() + 0.5D, this.minion.getZ(),
						25, 0.4D, 0.6D, 0.4D, 0.2D
					);
					serverWorld.playSound(null, this.minion.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
					this.minion.requestTeleport(phaseDest.x, phaseDest.y, phaseDest.z);
					this.ticksNavigating = 0;
					this.stallCollisionTicks = 0;
					this.groundNavigationForced = false;
					this.minion.getNavigation().stop();
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

			// 4. Autonomous Material Harvesting, Agro-Forestry & Mob Procurement
			if (!hasResources) {
				boolean harvestOrContract = com.example.entity.ai.logistics.MinionHarvestingHelper.tryAutonomousHarvest(this.minion, serverWorld, requiredItem, this.currentSession);
				if (harvestOrContract) {
					int slot = findItemSlot(minionInv, requiredItem);
					if (slot != -1) {
						minionInv.removeStack(slot, 1);
						hasResources = true;
					} else {
						// Procurement hunt contract commissioned or in progress: wait patiently for delivery
						this.ticksNavigating = 0;
						return;
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
			// Self-intersection nudging: ensure minion is not entombed inside targetPos before placement
			nudgeMinionAwayFromTargetBlock(serverWorld, targetPos);

			// Zero-drop pre-clearing in creative mode
			if (this.currentSession.isCreative()) {
				cleanPreExistingObstacles(serverWorld, targetPos);
			}

			if (targetState.getBlock() instanceof DoorBlock) {
				if (targetState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					// Also nudge away if intersecting upper door block
					nudgeMinionAwayFromTargetBlock(serverWorld, targetPos.up());

					if (this.currentSession.isCreative()) {
						cleanPreExistingObstacles(serverWorld, targetPos.up());
					}
					serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_ALL);
					BlockState upperState = targetState.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
					serverWorld.setBlockState(targetPos.up(), upperState, Block.NOTIFY_ALL);
				} else {
					BlockPos lowerPos = targetPos.down();
					nudgeMinionAwayFromTargetBlock(serverWorld, lowerPos);

					if (!serverWorld.getBlockState(lowerPos).isOf(targetState.getBlock())) {
						if (this.currentSession.isCreative()) {
							cleanPreExistingObstacles(serverWorld, lowerPos);
						}
						BlockState lowerState = targetState.with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
						serverWorld.setBlockState(lowerPos, lowerState, Block.NOTIFY_ALL);
					}
					serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_ALL);
				}
			} else if (targetState.getBlock() instanceof BedBlock) {
				Direction facing = targetState.get(BedBlock.FACING);
				net.minecraft.block.enums.BedPart part = targetState.get(BedBlock.PART);
				BlockPos otherPos = (part == net.minecraft.block.enums.BedPart.FOOT) ? targetPos.offset(facing) : targetPos.offset(facing.getOpposite());
				nudgeMinionAwayFromTargetBlock(serverWorld, otherPos);
				if (this.currentSession.isCreative()) {
					cleanPreExistingObstacles(serverWorld, otherPos);
				}
				net.minecraft.block.enums.BedPart otherPart = (part == net.minecraft.block.enums.BedPart.FOOT) ? net.minecraft.block.enums.BedPart.HEAD : net.minecraft.block.enums.BedPart.FOOT;
				BlockState otherState = targetState.with(BedBlock.PART, otherPart);
				serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_LISTENERS);
				serverWorld.setBlockState(otherPos, otherState, Block.NOTIFY_ALL);
			} else if (targetState.getBlock() instanceof TallPlantBlock) {
				DoubleBlockHalf half = targetState.get(TallPlantBlock.HALF);
				BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? targetPos.up() : targetPos.down();
				nudgeMinionAwayFromTargetBlock(serverWorld, otherPos);
				if (this.currentSession.isCreative()) {
					cleanPreExistingObstacles(serverWorld, otherPos);
				}
				DoubleBlockHalf otherHalf = (half == DoubleBlockHalf.LOWER) ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;
				BlockState otherState = targetState.with(TallPlantBlock.HALF, otherHalf);
				serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_LISTENERS);
				serverWorld.setBlockState(otherPos, otherState, Block.NOTIFY_ALL);
			} else {
				serverWorld.setBlockState(targetPos, targetState, Block.NOTIFY_ALL);
			}

			if (this.currentSession.isCreative()) {
				Box cleanBox = new Box(targetPos).expand(4.0D);
				List<ItemEntity> strayItems = serverWorld.getEntitiesByClass(ItemEntity.class, cleanBox, e -> true);
				for (ItemEntity stray : strayItems) {
					stray.discard();
				}
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
		this.stallCollisionTicks = 0;
		this.groundNavigationForced = false;
		this.hoverStationVec = null;

		// Immediately try to claim the next topological task or chain to the next active session
		if (!tryClaimTaskOrChainNextSession(serverWorld)) {
			this.currentTask = null;
			this.hoverStationVec = null;
			if (this.currentSession == null || !this.currentSession.isActive()) {
				this.minion.setActivelyBuilding(false);
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

		this.ticksNavigating = 0;
		this.workTicks = 0;
		this.minion.setArcaneLevitating(false);
		this.hoverStationVec = null;
		this.minion.getNavigation().stop();
		this.minion.fallDistance = 0.0F;

		// Short 10-tick retry interval to allow claiming other available materials or allied deliveries
		this.failureCooldownUntilTick = world.getTime() + 10L;

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
	 * Checks if solid ceiling blocks exist directly above the minion within checkBlocks height.
	 *
	 * @param world       The server world.
	 * @param checkBlocks Number of blocks above minion head/feet to inspect.
	 * @return true if a solid ceiling block exists overhead.
	 */
	public boolean hasCeilingAboveMinion(ServerWorld world, int checkBlocks) {
		int startY = this.minion.getBlockY() + 2;
		for (int cy = startY; cy <= startY + checkBlocks; cy++) {
			BlockPos ceilPos = new BlockPos(this.minion.getBlockX(), cy, this.minion.getBlockZ());
			BlockState state = world.getBlockState(ceilPos);
			if (state.isSolidBlock(world, ceilPos)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Validates line-of-sight from the minion's eye position to the candidate hover station vector.
	 *
	 * @param world      The server world.
	 * @param stationVec The candidate hover station vector.
	 * @return true if unobstructed line-of-sight exists.
	 */
	public boolean hasLineOfSightToStation(ServerWorld world, Vec3d stationVec) {
		if (stationVec == null) {
			return false;
		}
		HitResult hit = world.raycast(new RaycastContext(
			this.minion.getEyePos(),
			stationVec.add(0, 0.2D, 0),
			RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE,
			this.minion
		));
		return hit.getType() == HitResult.Type.MISS;
	}

	/**
	 * Pre-clears existing obstacles in Creative mode, handling multi-part blocks (double plants, beds, doors)
	 * and supporting blocks cleanly so no item entities pop off onto the ground.
	 */
	public void cleanPreExistingObstacles(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir()) {
			return;
		}

		if (state.getBlock() instanceof TallPlantBlock) {
			DoubleBlockHalf half = state.get(TallPlantBlock.HALF);
			BlockPos otherHalf = (half == DoubleBlockHalf.LOWER) ? pos.up() : pos.down();
			world.setBlockState(otherHalf, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		} else if (state.getBlock() instanceof BedBlock) {
			Direction facing = state.get(BedBlock.FACING);
			net.minecraft.block.enums.BedPart part = state.get(BedBlock.PART);
			BlockPos otherPart = (part == net.minecraft.block.enums.BedPart.FOOT) ? pos.offset(facing) : pos.offset(facing.getOpposite());
			world.setBlockState(otherPart, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		} else if (state.getBlock() instanceof DoorBlock) {
			DoubleBlockHalf half = state.get(DoorBlock.HALF);
			BlockPos otherHalf = (half == DoubleBlockHalf.LOWER) ? pos.up() : pos.down();
			world.setBlockState(otherHalf, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		} else {
			world.breakBlock(pos, false, this.minion);
		}

		// Also check if block directly above is a supported plant/torch/carpet that would break and drop
		BlockPos abovePos = pos.up();
		BlockState aboveState = world.getBlockState(abovePos);
		if (!aboveState.isAir() && (aboveState.getBlock() instanceof PlantBlock || aboveState.getBlock() instanceof TorchBlock || aboveState.getBlock() instanceof CarpetBlock)) {
			world.setBlockState(abovePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}

		Box cleanBox = new Box(pos).expand(4.0D);
		List<ItemEntity> strayItems = world.getEntitiesByClass(ItemEntity.class, cleanBox, e -> true);
		for (ItemEntity stray : strayItems) {
			stray.discard();
		}
	}

	/**
	 * Nudges the minion away from targetPos if the minion's bounding box intersects the block coordinate,
	 * preventing entity entombment inside newly placed blocks.
	 *
	 * @param world     The server world.
	 * @param targetPos The block coordinate to be placed.
	 */
	public void nudgeMinionAwayFromTargetBlock(ServerWorld world, BlockPos targetPos) {
		net.minecraft.util.math.Box targetBox = new net.minecraft.util.math.Box(targetPos);
		net.minecraft.util.math.Box minionBox = this.minion.getBoundingBox();

		if (minionBox.intersects(targetBox)) {
			// Find adjacent open direction to step or nudge into, prioritizing UP so minion steps safely on top of placed block
			Direction[] dirs = new Direction[] { Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
			Vec3d escapeVec = null;

			for (Direction dir : dirs) {
				BlockPos adjacent = targetPos.offset(dir);
				BlockState adjState = world.getBlockState(adjacent);
				BlockState adjHead = world.getBlockState(adjacent.up());
				if ((adjState.isAir() || !adjState.isSolidBlock(world, adjacent)) &&
					(adjHead.isAir() || !adjHead.isSolidBlock(world, adjacent.up()))) {
					escapeVec = Vec3d.ofBottomCenter(adjacent);
					break;
				}
			}

			if (escapeVec != null) {
				this.minion.requestTeleport(escapeVec.x, escapeVec.y, escapeVec.z);
			} else {
				// Fallback lateral push sufficiently far outside target block (>= 1.2 blocks from center)
				double pushX = this.minion.getX() - (targetPos.getX() + 0.5D);
				double pushZ = this.minion.getZ() - (targetPos.getZ() + 0.5D);
				double len = Math.sqrt(pushX * pushX + pushZ * pushZ);
				if (len < 0.01D) {
					pushX = 1.2D;
					pushZ = 0.0D;
				} else {
					pushX = (pushX / len) * 1.2D;
					pushZ = (pushZ / len) * 1.2D;
				}
				double safeY = Math.max(this.minion.getY(), targetPos.getY() + 1.0D);
				this.minion.requestTeleport(targetPos.getX() + 0.5D + pushX, safeY, targetPos.getZ() + 0.5D + pushZ);
			}
			this.minion.setVelocity(0.0D, 0.0D, 0.0D);
			this.minion.velocityModified = true;
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
		double targetY = targetPos.getY() + 0.05D;
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
				double safeCandY = targetY;
				BlockPos belowPos = candPos.down();
				if (world.getBlockState(belowPos).isSolidBlock(world, belowPos)) {
					safeCandY = Math.max(targetY, (double) candPos.getY() + 0.05D);
				}
				Vec3d candVec = new Vec3d(candX, safeCandY, candZ);
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
	 * If the task is elevated above the minion's reach, checks ceiling clearance and line of sight.
	 * If clear, engages Arcane Levitation. Otherwise, falls back to ground-based A* door navigation.
	 */
	private void setupNavigationForTask(ServerWorld world) {
		if (this.currentTask == null) {
			return;
		}

		BlockPos targetPos = this.currentTask.getWorldPos();
		int diffY = targetPos.getY() - this.minion.getBlockY();
		boolean hasCeiling = hasCeilingAboveMinion(world, 3);

		// If task is elevated, minion is already levitating, or minion is phasing blocks: engage Arcane Levitation
		if ((diffY > 1 || this.minion.isArcaneLevitating() || this.minion.isPhasingBlocks()) && !this.groundNavigationForced) {
			Vec3d candidateStation = findOptimalHoverStation(world, targetPos);
			if (this.minion.isPhasingBlocks() || hasLineOfSightToStation(world, candidateStation)) {
				this.minion.setArcaneLevitating(true);
				this.hoverStationVec = candidateStation;
				this.minion.getNavigation().stop();
				return;
			}
		}

		// Ground-level, ceiling-obstructed, or reach-accessible task: use ground navigation with door pathfinding
		if (!this.minion.isPhasingBlocks()) {
			this.minion.setArcaneLevitating(false);
			this.hoverStationVec = null;
			this.groundNavigationForced = true;
		}

		BlockPos standPos = findSafeStandPositionNear(world, targetPos);
		boolean pathValid = false;
		if (standPos != null) {
			boolean started = this.minion.getNavigation().startMovingTo(
				standPos.getX() + 0.5D,
				standPos.getY(),
				standPos.getZ() + 0.5D,
				1.15D
			);
			pathValid = started && this.minion.getNavigation().getCurrentPath() != null && this.minion.getNavigation().getCurrentPath().reachesTarget();
		}

		if (pathValid) {
			autoOpenNearbyDoors(world);
			return;
		}

		// Fallback: if ground path cannot reach standPos, attempt levitation if candidate station has line of sight (or phasing)
		Vec3d candidateStation = findOptimalHoverStation(world, targetPos);
		if (this.minion.isPhasingBlocks() || hasLineOfSightToStation(world, candidateStation)) {
			this.minion.setArcaneLevitating(true);
			this.hoverStationVec = candidateStation;
			this.groundNavigationForced = false;
			this.minion.getNavigation().stop();
			return;
		}

		// Minion is indoors or obstructed: route toward exterior exit (only if NOT phasing blocks)
		if (!this.minion.isPhasingBlocks()) {
			BlockPos exitPos = findStructureExitWaypoint(world);
			if (exitPos != null) {
				this.minion.getNavigation().startMovingTo(
					exitPos.getX() + 0.5D,
					exitPos.getY(),
					exitPos.getZ() + 0.5D,
					1.25D
				);
				autoOpenNearbyDoors(world);
				return;
			}
		}

		// Otherwise continue with ground navigation
		if (standPos != null) {
			this.minion.getNavigation().startMovingTo(
				standPos.getX() + 0.5D,
				standPos.getY(),
				standPos.getZ() + 0.5D,
				1.15D
			);
		}
	}

	private void handleNavigationTimeout(ServerWorld serverWorld) {
		if (this.currentTask != null && this.currentSession != null) {
			this.currentSession.releaseTask(this.currentTask);
		}
		this.currentTask = null;
		this.pendingNextTask = null;
		this.hoverStationVec = null;
		this.stallCollisionTicks = 0;
		this.groundNavigationForced = false;
		this.exitTraverseTicks = 0;
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
	 * Automatically opens any closed door in the minion's immediate navigation path (within 1.5 blocks)
	 * so builders can seamlessly walk in and out of structures without pathing stalls.
	 */
	public void autoOpenNearbyDoors(ServerWorld world) {
		BlockPos minionPos = this.minion.getBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos checkPos = minionPos.add(dx, dy, dz);
					BlockState state = world.getBlockState(checkPos);
					if (state.getBlock() instanceof DoorBlock && !state.get(DoorBlock.OPEN)) {
						((DoorBlock) state.getBlock()).setOpen(this.minion, world, state, checkPos, true);
						world.playSound(null, checkPos, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 1.0F);
					}
				}
			}
		}
	}

	/**
	 * Finds an exterior ground exit waypoint outside the active structure when a minion is trapped
	 * inside a room or under a ceiling and must reach an exterior/roof task.
	 *
	 * Prioritizes:
	 * 1. Exterior doorsteps of doors defined in the blueprint or world.
	 * 2. Walkable openings in the perimeter walls at floor level.
	 * 3. Open-sky perimeter blocks 1-2 blocks outside the structure bounding box.
	 */
	public BlockPos findStructureExitWaypoint(ServerWorld world) {
		if (this.currentSession != null) {
			BlockBox box = this.currentSession.getWorldBoundingBox();
			BlockPos anchor = this.currentSession.getAnchorPos();
			int floorY = anchor.getY();

			// 1. Scan for doors in the active blueprint session
			BlockPos bestDoorway = null;
			double minDoorDistSq = Double.MAX_VALUE;

			for (ConstructionTask task : this.currentSession.getTasks()) {
				BlockState state = task.getBlueprintBlock().state();
				if (state.getBlock() instanceof DoorBlock) {
					BlockPos doorPos = task.getWorldPos();
					// Inspect horizontal neighbors of the door
					for (Direction dir : Direction.Type.HORIZONTAL) {
						BlockPos candidate = doorPos.offset(dir);
						BlockPos stand = findGroundStandNear(world, candidate, 2);
						if (stand != null && isExteriorPosition(world, stand, box)) {
							double dSq = this.minion.squaredDistanceTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
							if (dSq < minDoorDistSq) {
								minDoorDistSq = dSq;
								bestDoorway = stand;
							}
						}
					}
				}
			}

			if (bestDoorway != null) {
				return bestDoorway;
			}

			// 2. Scan perimeter for open doorways or wall openings
			for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
				for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
					boolean isPerimeter = (x == box.getMinX() || x == box.getMaxX() || z == box.getMinZ() || z == box.getMaxZ());
					if (!isPerimeter) continue;

					BlockPos p = new BlockPos(x, floorY, z);
					BlockState sFeet = world.getBlockState(p);
					BlockState sHead = world.getBlockState(p.up());
					if ((sFeet.isAir() || sFeet.canPathfindThrough(NavigationType.LAND)) &&
						(sHead.isAir() || sHead.canPathfindThrough(NavigationType.LAND))) {
						for (Direction dir : Direction.Type.HORIZONTAL) {
							BlockPos outside = p.offset(dir);
							if (!box.contains(outside)) {
								BlockPos stand = findGroundStandNear(world, outside, 2);
								if (stand != null && isExteriorPosition(world, stand, box)) {
									return stand;
								}
							}
						}
					}
				}
			}

			// 3. Fallback: Check perimeter edges outside the bounding box
			Direction facing = this.minion.getHorizontalFacing();
			for (int offset = 1; offset <= 3; offset++) {
				BlockPos candidate = switch (facing) {
					case NORTH -> new BlockPos(this.minion.getBlockX(), floorY, box.getMinZ() - offset);
					case SOUTH -> new BlockPos(this.minion.getBlockX(), floorY, box.getMaxZ() + offset);
					case WEST -> new BlockPos(box.getMinX() - offset, floorY, this.minion.getBlockZ());
					case EAST -> new BlockPos(box.getMaxX() + offset, floorY, this.minion.getBlockZ());
					default -> new BlockPos(box.getMinX() - offset, floorY, box.getMinZ() - offset);
				};
				BlockPos stand = findGroundStandNear(world, candidate, 3);
				if (stand != null && isExteriorPosition(world, stand, box)) {
					return stand;
				}
			}
		}

		// Universal fallback: find nearest walkable block with open sky around the minion
		BlockPos minionPos = this.minion.getBlockPos();
		for (int radius = 2; radius <= 8; radius += 2) {
			for (Direction dir : Direction.Type.HORIZONTAL) {
				BlockPos cand = minionPos.offset(dir, radius);
				BlockPos stand = findGroundStandNear(world, cand, 2);
				if (stand != null && !hasCeilingAbove(world, stand, 4)) {
					return stand;
				}
			}
		}

		return null;
	}

	private BlockPos findGroundStandNear(ServerWorld world, BlockPos pos, int maxVerticalSearch) {
		for (int dy = 0; dy >= -maxVerticalSearch; dy--) {
			BlockPos cand = pos.add(0, dy, 0);
			if (isWalkableStandPosition(world, cand)) {
				return cand;
			}
		}
		for (int dy = 1; dy <= maxVerticalSearch; dy++) {
			BlockPos cand = pos.add(0, dy, 0);
			if (isWalkableStandPosition(world, cand)) {
				return cand;
			}
		}
		return null;
	}

	private boolean isExteriorPosition(ServerWorld world, BlockPos pos, BlockBox box) {
		return !box.contains(pos) && !hasCeilingAbove(world, pos, 4);
	}

	public static boolean hasCeilingAbove(ServerWorld world, BlockPos pos, int checkBlocks) {
		int startY = pos.getY() + 2;
		for (int cy = startY; cy <= startY + checkBlocks; cy++) {
			BlockPos ceilPos = new BlockPos(pos.getX(), cy, pos.getZ());
			BlockState state = world.getBlockState(ceilPos);
			if (state.isSolidBlock(world, ceilPos)) {
				return true;
			}
		}
		return false;
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
			this.stallCollisionTicks = 0;
			this.groundNavigationForced = false;
			this.hoverStationVec = null;

			if (!tryClaimTaskOrChainNextSession(serverWorld)) {
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

		// Handle multi-part blocks cleanly (doors, beds, tall plants)
		if (currentState.getBlock() instanceof DoorBlock) {
			if (currentState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				BlockPos upper = targetPos.up();
				BlockState upperState = serverWorld.getBlockState(upper);
				if (upperState.isOf(currentState.getBlock()) && !isIndestructibleBlock(upperState, serverWorld, upper)) {
					serverWorld.breakBlock(upper, false, this.minion);
				}
			} else {
				BlockPos lower = targetPos.down();
				BlockState lowerState = serverWorld.getBlockState(lower);
				if (lowerState.isOf(currentState.getBlock()) && !isIndestructibleBlock(lowerState, serverWorld, lower)) {
					serverWorld.breakBlock(lower, false, this.minion);
				}
			}
		} else if (currentState.getBlock() instanceof BedBlock) {
			Direction facing = currentState.get(BedBlock.FACING);
			net.minecraft.block.enums.BedPart part = currentState.get(BedBlock.PART);
			BlockPos otherPart = (part == net.minecraft.block.enums.BedPart.FOOT) ? targetPos.offset(facing) : targetPos.offset(facing.getOpposite());
			BlockState otherState = serverWorld.getBlockState(otherPart);
			if (otherState.isOf(currentState.getBlock()) && !isIndestructibleBlock(otherState, serverWorld, otherPart)) {
				serverWorld.breakBlock(otherPart, false, this.minion);
			}
		} else if (currentState.getBlock() instanceof TallPlantBlock) {
			DoubleBlockHalf half = currentState.get(TallPlantBlock.HALF);
			BlockPos otherHalf = (half == DoubleBlockHalf.LOWER) ? targetPos.up() : targetPos.down();
			BlockState otherState = serverWorld.getBlockState(otherHalf);
			if (otherState.isOf(currentState.getBlock()) && !isIndestructibleBlock(otherState, serverWorld, otherHalf)) {
				serverWorld.breakBlock(otherHalf, false, this.minion);
			}
		}

		if (currentState.isLiquid() || currentState.getBlock() instanceof net.minecraft.block.FluidBlock) {
			serverWorld.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		} else if (this.currentSession.isCreative()) {
			serverWorld.breakBlock(targetPos, false, this.minion);
			Box cleanBox = new Box(targetPos).expand(4.0D);
			List<ItemEntity> strayItems = serverWorld.getEntitiesByClass(ItemEntity.class, cleanBox, e -> true);
			for (ItemEntity stray : strayItems) {
				stray.discard();
			}
		} else {
			// Survival mode: Collect drops directly into minion inventory rather than scattering on the ground
			List<ItemStack> drops = Block.getDroppedStacks(
				currentState,
				serverWorld,
				targetPos,
				serverWorld.getBlockEntity(targetPos),
				this.minion,
				this.minion.getMainHandStack()
			);
			serverWorld.breakBlock(targetPos, false, this.minion);

			for (ItemStack drop : drops) {
				ItemStack remainder = this.minion.getInventory().addStack(drop);
				if (!remainder.isEmpty()) {
					// Offload excess into nearby or newly crafted autonomous chest
					com.example.entity.ai.logistics.MinionHarvestingHelper.checkAndDepositExcessMaterials(this.minion, serverWorld, this.currentSession, true);
					remainder = this.minion.getInventory().addStack(remainder);
					if (!remainder.isEmpty()) {
						remainder = com.example.entity.ai.logistics.MinionHarvestingHelper.depositStackIntoNearbyContainer(remainder, serverWorld, targetPos, this.currentSession, this.minion.getOwnerUuid());
						if (!remainder.isEmpty()) {
							this.minion.dropStack(remainder);
						}
					}
				}
			}
		}

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
		this.stallCollisionTicks = 0;
		this.groundNavigationForced = false;
		this.hoverStationVec = null;

		// Immediately try to claim the next top-down task or chain to the next active session
		if (!tryClaimTaskOrChainNextSession(serverWorld)) {
			this.currentTask = null;
			this.hoverStationVec = null;
			if (!this.minion.isArcaneLevitating()) {
				restoreHeldWeapon();
			}
		}
	}

	/**
	 * Configures kinematics, levitation, or ground navigation towards the target task position.
	 */
	private void setupTaskKinematics(ServerWorld serverWorld, BlockPos nextPos) {
		boolean nextCeiling = hasCeilingAboveMinion(serverWorld, 3);
		if (nextPos.getY() > this.minion.getBlockY() + 1 || this.minion.isArcaneLevitating()) {
			Vec3d nextStation = findOptimalHoverStation(serverWorld, nextPos);
			if (hasLineOfSightToStation(serverWorld, nextStation)) {
				this.minion.setArcaneLevitating(true);
				this.hoverStationVec = nextStation;
			} else {
				this.groundNavigationForced = true;
				setupNavigationForTask(serverWorld);
			}
		} else {
			setupNavigationForTask(serverWorld);
		}
	}

	/**
	 * Attempts to claim the next task from the current session or autonomously detects and chains
	 * to the nearest active blueprint session in the minion's vicinity (<= 128 blocks).
	 *
	 * @param serverWorld The server world instance.
	 * @return True if a task was claimed from the current or a newly chained session.
	 */
	private boolean tryClaimTaskOrChainNextSession(ServerWorld serverWorld) {
		// 1. Try to claim from current session if active
		if (this.currentSession != null && this.currentSession.isActive()) {
			ConstructionTask nextTask = this.currentSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
			if (nextTask != null) {
				this.currentTask = nextTask;
				this.minion.setActivelyBuilding(true);
				ItemStack toolOrBlock = this.currentSession.isDismantle()
					? resolveDismantleTool()
					: this.currentTask.getBlueprintBlock().getRequiredStack();
				this.minion.equipStack(EquipmentSlot.MAINHAND, toolOrBlock);
				setupTaskKinematics(serverWorld, nextTask.getWorldPos());
				return true;
			}
		}

		// 2. If current session is complete or has no tasks, search for nearest active session within 128 blocks
		if (this.minion.getOwnerUuid() != null) {
			Optional<ConstructionSession> chainedOpt = ConstructionManager.getInstance().findNearestSessionForMinion(
				serverWorld,
				this.minion.getBlockPos(),
				this.minion.getOwnerUuid(),
				128.0D,
				this.minion.getEffectiveRole()
			);

			if (chainedOpt.isPresent() && chainedOpt.get().isActive()) {
				ConstructionSession chainedSession = chainedOpt.get();
				ConstructionTask chainedTask = chainedSession.claimNextTask(this.minion.getUuid(), serverWorld.getTime(), serverWorld);
				this.currentSession = chainedSession;
				if (chainedTask != null) {
					this.currentTask = chainedTask;
					this.minion.setActivelyBuilding(true);
					this.minion.setSitting(false);
					this.minion.setGuardAnchorPos(null);
					ItemStack toolOrBlock = chainedSession.isDismantle()
						? resolveDismantleTool()
						: chainedTask.getBlueprintBlock().getRequiredStack();
					this.minion.equipStack(EquipmentSlot.MAINHAND, toolOrBlock);
					setupTaskKinematics(serverWorld, chainedTask.getWorldPos());
					return true;
				}
				// Session is active but waiting on dependency / ally: keep active builder posture
				this.currentTask = null;
				this.minion.setActivelyBuilding(true);
				this.minion.setSitting(false);
				this.minion.setGuardAnchorPos(null);
				return true;
			}
		}

		return false;
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