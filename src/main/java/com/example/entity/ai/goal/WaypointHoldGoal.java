package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * AI goal allowing any minion thrall (regardless of archetype role) to march to
 * and maintain position at a designated tactical ground waypoint ping.
 * <p>
 * If the minion is assigned a {@link MinionEntity#getGuardAnchorPos()}, this goal moves the
 * thrall towards the waypoint station and holds position within a 2-block arrival tolerance.
 * During combat, hostiles within 12 blocks may be engaged, but if lured beyond 12 blocks
 * from the waypoint anchor, aggro is broken and the minion returns to its post at 1.35D sprint speed.
 */
public class WaypointHoldGoal extends Goal {

	private final MinionEntity minion;
	private static final double ARRIVAL_TOLERANCE_SQ = 4.0D; // 2.0 blocks
	private static final double LEASH_DISTANCE = 128.0D;
	private static final double LEASH_DISTANCE_SQ = LEASH_DISTANCE * LEASH_DISTANCE;
	private static final double SPRINT_SPEED = 1.35D;

	public WaypointHoldGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasLeader()) {
			return false;
		}
		// SentinelGuardGoal already handles Sentinels
		if (this.minion.getRole() == MinionRole.SENTINEL) {
			return false;
		}

		BlockPos anchor = this.minion.getGuardAnchorPos();
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
			return targetDistSq > LEASH_DISTANCE_SQ || minionDistSq > LEASH_DISTANCE_SQ;
		}

		double distSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
		return distSq > ARRIVAL_TOLERANCE_SQ;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasLeader()) {
			return false;
		}
		if (this.minion.getRole() == MinionRole.SENTINEL) {
			return false;
		}

		BlockPos anchor = this.minion.getGuardAnchorPos();
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
			return false;
		}

		double distSq = this.minion.squaredDistanceTo(anchorCenterX, anchorCenterY, anchorCenterZ);
		return distSq > ARRIVAL_TOLERANCE_SQ;
	}

	@Override
	public void start() {
		BlockPos anchor = this.minion.getGuardAnchorPos();
		if (anchor != null) {
			if (this.minion.getTarget() != null) {
				this.minion.setTarget(null);
			}
			Vec3d anchorVec = new Vec3d(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
			this.minion.setActiveTraversalDestination(anchorVec);
			double dy = anchor.getY() - this.minion.getY();
			if (dy > 1.25D || dy < -1.5D) {
				this.minion.setArcaneLevitating(true);
			}
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
		BlockPos anchor = this.minion.getGuardAnchorPos();
		if (anchor == null) {
			return;
		}

		double anchorCenterX = anchor.getX() + 0.5D;
		double anchorCenterY = anchor.getY();
		double anchorCenterZ = anchor.getZ() + 0.5D;
		Vec3d anchorVec = new Vec3d(anchorCenterX, anchorCenterY, anchorCenterZ);
		this.minion.setActiveTraversalDestination(anchorVec);

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
			double dy = anchorCenterY - this.minion.getY();
			if (dy > 1.25D || dy < -1.5D) {
				this.minion.setArcaneLevitating(true);
			}
			if (!this.minion.isArcaneLevitating() && this.minion.getNavigation().isIdle()) {
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
