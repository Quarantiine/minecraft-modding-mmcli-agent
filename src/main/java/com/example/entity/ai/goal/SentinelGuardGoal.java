package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Tactical perimeter defense AI goal for {@link MinionEntity} thralls assigned the {@link MinionRole#SENTINEL} role.
 * <p>
 * Sentinels are anchored to a designated guard position (or the master's position if unset).
 * They maintain vigilance within an 8-block perimeter. If an engaged hostile target retreats or lures
 * the minion further than 12 blocks from the anchor position, the sentinel immediately breaks aggro
 * (clearing target) and sprints back to its anchor post at 1.35D speed.
 */
public class SentinelGuardGoal extends Goal {

	private final MinionEntity minion;
	private static final double PERIMETER_RADIUS = 8.0D;
	private static final double PERIMETER_RADIUS_SQ = PERIMETER_RADIUS * PERIMETER_RADIUS;
	private static final double LEASH_DISTANCE = 128.0D;
	private static final double LEASH_DISTANCE_SQ = LEASH_DISTANCE * LEASH_DISTANCE;
	private static final double ARRIVAL_TOLERANCE_SQ = 4.0D; // 2 blocks radius
	private static final double SPRINT_SPEED = 1.35D;

	public SentinelGuardGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	/**
	 * Resolves the active anchor position for the sentinel minion.
	 * Prioritizes {@link MinionEntity#getGuardAnchorPos()}, falling back to the living owner's position.
	 *
	 * @return The anchor BlockPos, or null if neither is available.
	 */
	public BlockPos getAnchorPos() {
		BlockPos anchor = this.minion.getGuardAnchorPos();
		if (anchor != null) {
			return anchor;
		}
		LivingEntity owner = this.minion.getOwner();
		if (owner != null) {
			return owner.getBlockPos();
		}
		return null;
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (!this.minion.matchesRole(MinionRole.SENTINEL)) {
			return false;
		}

		// Minions escorting a squad leader yield to MinionFollowLeaderGoal
		if (this.minion.hasLeader()) {
			return false;
		}

		// Minions on patrol routes yield to MinionPatrolGoal
		if (this.minion.getPatrolRouteId() >= 0) {
			return false;
		}

		BlockPos anchor = this.getAnchorPos();
		if (anchor == null) {
			return false;
		}

		double anchorCenterX = anchor.getX() + 0.5D;
		double anchorCenterY = anchor.getY();
		double anchorCenterZ = anchor.getZ() + 0.5D;

		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive()) {
			// Leash enforcement: if hostile target or minion is beyond 12 blocks from anchor, break aggro and sprint home
			double targetDistSq = target.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
			double minionDistSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
			return targetDistSq > LEASH_DISTANCE_SQ || minionDistSq > LEASH_DISTANCE_SQ;
		}

		// Idle return to post: if no target, ensure minion holds anchor within 2 blocks
		double distToAnchorSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
		return distToAnchorSq > ARRIVAL_TOLERANCE_SQ;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting()) {
			return false;
		}
		if (!this.minion.matchesRole(MinionRole.SENTINEL)) {
			return false;
		}

		if (this.minion.hasLeader() || this.minion.getPatrolRouteId() >= 0) {
			return false;
		}

		BlockPos anchor = this.getAnchorPos();
		if (anchor == null) {
			return false;
		}

		double anchorCenterX = anchor.getX() + 0.5D;
		double anchorCenterY = anchor.getY();
		double anchorCenterZ = anchor.getZ() + 0.5D;

		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive()) {
			double targetDistSq = target.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
			double minionDistSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
			if (targetDistSq > LEASH_DISTANCE_SQ || minionDistSq > LEASH_DISTANCE_SQ) {
				this.minion.setTarget(null);
				return true;
			}
			// Target returned inside perimeter; yield control to combat goals
			return false;
		}

		// Continue moving until within arrival tolerance
		double distToAnchorSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
		return distToAnchorSq > ARRIVAL_TOLERANCE_SQ;
	}

	@Override
	public void start() {
		BlockPos anchor = this.getAnchorPos();
		if (anchor != null) {
			if (this.minion.getTarget() != null) {
				this.minion.setTarget(null);
			}
			Vec3d anchorVec = new Vec3d(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
			this.minion.setActiveTraversalDestination(anchorVec);
			this.minion.getNavigation().startMovingTo(
				anchorVec.x,
				anchorVec.y,
				anchorVec.z,
				SPRINT_SPEED
			);
		}
	}

	@Override
	public void tick() {
		BlockPos anchor = this.getAnchorPos();
		if (anchor == null) {
			return;
		}

		double anchorCenterX = anchor.getX() + 0.5D;
		double anchorCenterY = anchor.getY();
		double anchorCenterZ = anchor.getZ() + 0.5D;
		Vec3d anchorVec = new Vec3d(anchorCenterX, anchorCenterY, anchorCenterZ);
		this.minion.setActiveTraversalDestination(anchorVec);

		// Continually clear target if beyond leash
		LivingEntity target = this.minion.getTarget();
		if (target != null) {
			double targetDistSq = target.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
			double minionDistSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
			if (targetDistSq > LEASH_DISTANCE_SQ || minionDistSq > LEASH_DISTANCE_SQ) {
				this.minion.setTarget(null);
			}
		}

		double distSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
		if (distSq > ARRIVAL_TOLERANCE_SQ) {
			if (this.minion.getNavigation().isIdle()) {
				this.minion.getNavigation().startMovingTo(anchorCenterX, anchorCenterY, anchorCenterZ, SPRINT_SPEED);
			}
		} else {
			this.minion.getNavigation().stop();
		}
		this.minion.getLookControl().lookAt(anchorCenterX, anchorCenterY + 1.0D, anchorCenterZ);
	}

	@Override
	public void stop() {
		this.minion.clearActiveTraversalDestination();
		this.minion.getNavigation().stop();
	}
}
