package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.patrol.PatrolRoute;
import com.example.patrol.PatrolRouteManager;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * AI goal allowing minions to patrol designated waypoint routes.
 * <p>
 * Behavior & Features:
 * <ul>
 *   <li><b>Waypoint Traversal:</b> Marches sequentially from waypoint to waypoint.</li>
 *   <li><b>Arrival Tolerance:</b> Considers waypoint reached when within 1.5 blocks.</li>
 *   <li><b>Vigilant Linger Scanning:</b> Lingers for 10s–15s (200–300 ticks) at each checkpoint,
 *       periodically turning head to scan for threats like a patrol officer.</li>
 *   <li><b>Infinite Looping:</b> Loops continuously (1 -> 2 -> 3 -> ... -> 1).</li>
 *   <li><b>Smart Combat Resumption:</b> Yields to combat goals when hostiles are detected;
 *       once combat ends, smoothly resumes patrol at the nearest checkpoint.</li>
 *   <li><b>Arcane Levitation Traversal:</b> Automatically activates arcane flight to scale
 *       walls, ramparts, and cliffs along the route without getting stuck.</li>
 * </ul>
 */
public class MinionPatrolGoal extends Goal {

	private final MinionEntity minion;
	private static final double ARRIVAL_TOLERANCE_SQ = 2.25D; // 1.5 blocks squared (1.5 * 1.5)
	private static final double MARCH_SPEED = 1.15D;
	private static final double SPRINT_SPEED = 1.35D;

	private int lingerTicks = 0;
	private int lookAroundCooldown = 0;
	private boolean wasInCombat = false;
	private boolean wasInterrupted = false;
	private int repathCooldown = 0;
	private int patrolDirection = 1;

	public MinionPatrolGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	private PatrolRoute resolveActiveRoute() {
		int routeId = this.minion.getPatrolRouteId();
		if (routeId < 0) {
			return null;
		}
		if (this.minion.getOwnerUuid() != null) {
			return PatrolRouteManager.getInstance().getRoute(this.minion.getOwnerUuid(), routeId);
		}
		return null;
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasVehicle()) {
			return false;
		}

		// Minions holding a designated anchor post yield to WaypointHoldGoal / SentinelGuardGoal
		if (this.minion.getGuardAnchorPos() != null || this.minion.isHoldingPosition()) {
			return false;
		}

		// Minions with an assigned squad leader follow their leader instead of driving their own patrol
		if (this.minion.hasLeader()) {
			return false;
		}

		// Yield during active construction work or healing
		if (this.minion.isActivelyBuilding() || this.minion.isActivelyHealing()) {
			this.wasInterrupted = true;
			return false;
		}

		// Yield during combat to attack goals; sound breach alarm if transitioning to combat
		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive()) {
			if (!this.wasInCombat) {
				triggerBreachAlarm(target);
			}
			this.wasInCombat = true;
			return false;
		}

		PatrolRoute route = resolveActiveRoute();
		if (route == null || route.waypoints().isEmpty()) {
			if (this.minion.getPatrolRouteId() >= 0) {
				this.minion.setPatrolRouteId(-1);
			}
			return false;
		}

		return true;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasVehicle()) {
			return false;
		}

		if (this.minion.getGuardAnchorPos() != null || this.minion.isHoldingPosition()) {
			return false;
		}

		if (this.minion.hasLeader() || this.minion.isActivelyBuilding() || this.minion.isActivelyHealing()) {
			this.wasInterrupted = true;
			return false;
		}

		// Yield to combat
		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive()) {
			if (!this.wasInCombat) {
				triggerBreachAlarm(target);
			}
			this.wasInCombat = true;
			return false;
		}

		PatrolRoute route = resolveActiveRoute();
		if (route == null || route.waypoints().isEmpty()) {
			if (this.minion.getPatrolRouteId() >= 0) {
				this.minion.setPatrolRouteId(-1);
			}
			return false;
		}
		return true;
	}

	private void triggerBreachAlarm(LivingEntity target) {
		if (this.minion.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(
				ParticleTypes.ANGRY_VILLAGER,
				this.minion.getX(),
				this.minion.getY() + 1.2D,
				this.minion.getZ(),
				8,
				0.3D,
				0.3D,
				0.3D,
				0.05D
			);

			// Alert nearby idle or holding owned minions within 16m
			if (this.minion.getOwnerUuid() != null) {
				List<MinionEntity> nearbyAllies = serverWorld.getEntitiesByClass(
					MinionEntity.class,
					this.minion.getBoundingBox().expand(16.0D),
					ally -> ally.isAlive() && ally != this.minion && java.util.Objects.equals(ally.getOwnerUuid(), this.minion.getOwnerUuid()) && !ally.isActivelyBuilding()
				);

				for (MinionEntity ally : nearbyAllies) {
					if (ally.getTarget() == null || !ally.getTarget().isAlive()) {
						ally.setTarget(target);
						ally.setSitting(false);
						ally.getNavigation().startMovingTo(target, 1.35D);
					}
				}
			}
		}
	}

	@Override
	public void start() {
		PatrolRoute route = resolveActiveRoute();
		if (route == null || route.waypoints().isEmpty()) {
			return;
		}

		// Smart Combat/Interruption Resume: If returning from combat, building, or healing, snap to the nearest waypoint
		if (this.wasInCombat || this.wasInterrupted) {
			int closestIdx = findClosestWaypointIndex(route.waypoints());
			this.minion.setCurrentWaypointIndex(closestIdx);
			this.wasInCombat = false;
			this.wasInterrupted = false;
			this.lingerTicks = 0;
		} else {
			int clamped = MathHelper.clamp(this.minion.getCurrentWaypointIndex(), 0, route.waypoints().size() - 1);
			this.minion.setCurrentWaypointIndex(clamped);
		}

		if (this.minion.isArcaneLevitating() && this.minion.isOnGround()) {
			this.minion.setArcaneLevitating(false);
			this.minion.setNoGravity(false);
		}
		navigateToCurrentWaypoint(route);
	}

	@Override
	public void stop() {
		this.minion.getNavigation().stop();
		this.minion.clearActiveTraversalDestination();
		if (this.minion.isArcaneLevitating()) {
			this.minion.setArcaneLevitating(false);
			this.minion.setNoGravity(false);
		}
		this.lingerTicks = 0;
	}

	@Override
	public void tick() {
		PatrolRoute route = resolveActiveRoute();
		if (route == null || route.waypoints().isEmpty()) {
			return;
		}

		List<BlockPos> waypoints = route.waypoints();
		int storedIndex = this.minion.getCurrentWaypointIndex();
		int currentIndex = MathHelper.clamp(storedIndex, 0, waypoints.size() - 1);
		if (storedIndex != currentIndex) {
			this.minion.setCurrentWaypointIndex(currentIndex);
		}
		BlockPos currentTargetPos = waypoints.get(currentIndex);
		Vec3d targetVec = currentTargetPos.toBottomCenterPos();

		double distSq = this.minion.squaredDistanceTo(targetVec.x, targetVec.y, targetVec.z);

		// 1. Check if waypoint reached within 1.5 blocks
		if (distSq <= ARRIVAL_TOLERANCE_SQ) {
			// Minions smoothly traverse intermediate pathway tiles placed by players without stopping.
			// They only perform the vigilant sentry wait (10s–15s) at the end of a pathway:
			// - In LOOP mode: at the final waypoint (currentIndex == waypoints.size() - 1) before looping back to 0.
			// - In PING_PONG mode: at the terminal waypoints before reversing direction:
			//   * index waypoints.size() - 1 when patrolling forward (patrolDirection == 1)
			//   * index 0 when patrolling backward (patrolDirection == -1)
			// If waypoints.size() <= 1, the single waypoint is inherently terminal.
			boolean isTerminal;
			if (waypoints.size() <= 1) {
				isTerminal = true;
			} else if (route.patrolMode() == PatrolRoute.PatrolMode.PING_PONG) {
				isTerminal = (this.patrolDirection == 1 && currentIndex >= waypoints.size() - 1)
					|| (this.patrolDirection == -1 && currentIndex <= 0);
			} else {
				isTerminal = (currentIndex >= waypoints.size() - 1);
			}

			if (!isTerminal) {
				// Intermediate waypoint reached: smoothly advance to the next waypoint without lingering
				int nextIndex;
				if (route.patrolMode() == PatrolRoute.PatrolMode.PING_PONG) {
					nextIndex = MathHelper.clamp(currentIndex + this.patrolDirection, 0, waypoints.size() - 1);
				} else {
					nextIndex = (currentIndex + 1) % waypoints.size();
				}
				this.minion.setCurrentWaypointIndex(nextIndex);
				this.repathCooldown = 0;
				navigateToCurrentWaypoint(route);
				return;
			}

			// Terminal end reached: perform sentry linger scan
			if (this.lingerTicks <= 0) {
				// Initialize vigilant linger scan: 10s to 15s (200 to 300 ticks)
				this.lingerTicks = this.minion.getRandom().nextBetween(200, 300);
				this.minion.getNavigation().stop();

				// Ambient checkpoint arrival effect
				if (this.minion.getWorld() instanceof ServerWorld serverWorld) {
					serverWorld.spawnParticles(
						ParticleTypes.ENCHANT,
						this.minion.getX(),
						this.minion.getY() + 1.0D,
						this.minion.getZ(),
						5,
						0.3D,
						0.3D,
						0.3D,
						0.05D
					);
				}
			}

			// Perform vigilant sentry scan during linger
			this.lingerTicks--;
			this.lookAroundCooldown--;

			if (this.lookAroundCooldown <= 0) {
				this.lookAroundCooldown = this.minion.getRandom().nextBetween(30, 50);
				float randomYaw = this.minion.getYaw() + this.minion.getRandom().nextFloat() * 120.0F - 60.0F;
				float randomPitch = this.minion.getRandom().nextFloat() * 20.0F - 10.0F;
				this.minion.getLookControl().lookAt(
					this.minion.getX() - MathHelper.sin(randomYaw * ((float) Math.PI / 180.0F)) * 5.0D,
					this.minion.getEyeY() - MathHelper.sin(randomPitch * ((float) Math.PI / 180.0F)) * 2.0D,
					this.minion.getZ() + MathHelper.cos(randomYaw * ((float) Math.PI / 180.0F)) * 5.0D
				);
			}

			// When linger completes, advance to next waypoint based on patrol mode
			if (this.lingerTicks <= 0) {
				int nextIndex;
				if (route.patrolMode() == PatrolRoute.PatrolMode.PING_PONG) {
					if (this.patrolDirection == 1 && currentIndex >= waypoints.size() - 1) {
						this.patrolDirection = -1;
					} else if (this.patrolDirection == -1 && currentIndex <= 0) {
						this.patrolDirection = 1;
					}
					nextIndex = MathHelper.clamp(currentIndex + this.patrolDirection, 0, waypoints.size() - 1);
				} else {
					nextIndex = (currentIndex + 1) % waypoints.size();
				}
				this.minion.setCurrentWaypointIndex(nextIndex);
				this.repathCooldown = 0;
				navigateToCurrentWaypoint(route);
			}
			return;
		}

		// 2. Navigating towards target waypoint
		this.lingerTicks = 0;
		this.repathCooldown--;
		if (this.repathCooldown <= 0) {
			this.repathCooldown = 15;

			this.minion.setActiveTraversalDestination(targetVec);
			this.minion.getLookControl().lookAt(targetVec.x, targetVec.y + 1.0D, targetVec.z);

			double speed = distSq > 64.0D ? SPRINT_SPEED : MARCH_SPEED;
			this.minion.getNavigation().startMovingTo(targetVec.x, targetVec.y, targetVec.z, speed);
		}
	}

	private void navigateToCurrentWaypoint(PatrolRoute route) {
		List<BlockPos> waypoints = route.waypoints();
		if (waypoints.isEmpty()) return;

		int idx = MathHelper.clamp(this.minion.getCurrentWaypointIndex(), 0, waypoints.size() - 1);
		BlockPos targetPos = waypoints.get(idx);
		Vec3d targetVec = targetPos.toBottomCenterPos();

		this.minion.setActiveTraversalDestination(targetVec);
		this.minion.getNavigation().startMovingTo(targetVec.x, targetVec.y, targetVec.z, MARCH_SPEED);
	}

	private int findClosestWaypointIndex(List<BlockPos> waypoints) {
		int closestIdx = 0;
		double closestDistSq = Double.MAX_VALUE;
		for (int i = 0; i < waypoints.size(); i++) {
			double distSq = this.minion.squaredDistanceTo(waypoints.get(i).toBottomCenterPos());
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closestIdx = i;
			}
		}
		return closestIdx;
	}
}
