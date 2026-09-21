package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * AI goal allowing minions to follow and escort a designated Squad Leader minion.
 * <p>
 * Features:
 * <ul>
 *   <li><b>Dynamic Army Formation Ranks:</b> Supports any escort squad size ("no matter the size"),
 *       assigning each unit a deterministic, non-overlapping battle station around the leader.
 *       Sentinels flank the sides, Warriors lead on the frontline, and Builders protect the rear.</li>
 *   <li><b>Walkable Surface Resolution:</b> Resolves ground elevation via {@link MinionFormationFollowGoal#resolveWalkableY},
 *       preventing pathing into solid blocks or mid-air.</li>
 *   <li><b>Arcane Levitation & Vaulting:</b> Automatically engages 3D flight to scale vertical obstacles.</li>
 *   <li><b>Heading Hysteresis:</b> Anchors formation yaw relative to the leader to prevent station spinning.</li>
 *   <li><b>Pacing & Emergency Teleport:</b> Marches at 1.15D, sprints at 1.35D when lagging (>8 blocks),
 *       and teleports instantly if estranged beyond 64 blocks.</li>
 *   <li><b>Combat Leash Enforcement:</b> Yields to combat near the leader, but immediately breaks aggro
 *       and returns if drawn more than 16 blocks away.</li>
 * </ul>
 */
public class MinionFollowLeaderGoal extends Goal {

	private final MinionEntity minion;
	private MinionEntity leader;
	private int cachedRank = 0;
	private int rankUpdateCooldown = 0;
	private int repathCooldown = 0;

	public static final double MARCH_SPEED = 1.15D;
	public static final double SPRINT_SPEED = 1.35D;
	public static final double STOPPING_DISTANCE = 2.0D;
	public static final double STOPPING_DISTANCE_SQ = STOPPING_DISTANCE * STOPPING_DISTANCE; // 4.0D
	public static final double START_FOLLOW_DISTANCE = 2.5D;
	public static final double START_FOLLOW_DISTANCE_SQ = START_FOLLOW_DISTANCE * START_FOLLOW_DISTANCE; // 6.25D
	public static final double SPRINT_DISTANCE_THRESHOLD = 8.0D;
	public static final double SPRINT_DISTANCE_THRESHOLD_SQ = SPRINT_DISTANCE_THRESHOLD * SPRINT_DISTANCE_THRESHOLD; // 64.0D
	public static final double TELEPORT_DISTANCE_THRESHOLD = 64.0D;
	public static final double TELEPORT_DISTANCE_THRESHOLD_SQ = TELEPORT_DISTANCE_THRESHOLD * TELEPORT_DISTANCE_THRESHOLD; // 4096.0D
	public static final double COMBAT_LEASH_OVERRIDE_SQ = 256.0D; // 16.0 blocks

	private static final int RANK_UPDATE_INTERVAL = 15;
	private static final int NAVIGATION_REPATH_INTERVAL = 10;

	public MinionFollowLeaderGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	public MinionEntity resolveLeader() {
		UUID leaderUuid = this.minion.getLeaderMinionUuid();
		if (leaderUuid == null) {
			return null;
		}
		if (this.leader != null && this.leader.isAlive() && this.leader.getUuid().equals(leaderUuid)) {
			return this.leader;
		}
		if (this.minion.getWorld() instanceof ServerWorld serverWorld) {
			Entity entity = serverWorld.getEntity(leaderUuid);
			if (entity instanceof MinionEntity minionLeader) {
				if (minionLeader.isAlive()) {
					this.leader = minionLeader;
					return this.leader;
				} else {
					// Leader is dead or removed; dissolve escort bond cleanly
					this.minion.clearLeader();
					this.leader = null;
					return null;
				}
			}
		}
		return null;
	}

	/**
	 * Resolves the deterministic 0-based station rank for this escort among all fellow
	 * minions escorting the same leader and sharing the same tactical role.
	 */
	public int resolveRank(MinionEntity leaderEntity) {
		if (this.minion.getWorld() == null || leaderEntity == null) {
			return 0;
		}

		List<MinionEntity> comrades = this.minion.getWorld().getEntitiesByClass(
			MinionEntity.class,
			leaderEntity.getBoundingBox().expand(64.0D),
			m -> m.isAlive() && m.isTamed() && !m.isSitting() && Objects.equals(m.getLeaderMinionUuid(), leaderEntity.getUuid())
		);

		return MinionFormationFollowGoal.resolveRank(comrades, this.minion, MinionEntity::getRole, Entity::getId);
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasVehicle()) {
			return false;
		}

		if (!this.minion.hasLeader()) {
			return false;
		}

		MinionEntity resolvedLeader = resolveLeader();
		if (resolvedLeader == null || !resolvedLeader.isAlive()) {
			return false;
		}

		// Yield during active building
		if (this.minion.isActivelyBuilding()) {
			return false;
		}

		// Combat leash check: if engaged in combat, yield only if within 16 blocks of leader
		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive()) {
			if (this.minion.squaredDistanceTo(resolvedLeader) > COMBAT_LEASH_OVERRIDE_SQ || target.squaredDistanceTo(resolvedLeader) > COMBAT_LEASH_OVERRIDE_SQ) {
				// Target lured escort too far from leader; break aggro and sprint back
				this.minion.setTarget(null);
			} else {
				return false;
			}
		}

		this.cachedRank = this.resolveRank(resolvedLeader);
		Vec3d station = MinionFormationFollowGoal.calculateFormationStation(
			resolvedLeader.getUuid(),
			resolvedLeader.getX(),
			resolvedLeader.getY(),
			resolvedLeader.getZ(),
			resolvedLeader.getYaw(),
			this.minion.getEffectiveRole(),
			this.cachedRank
		);
		double walkableY = MinionFormationFollowGoal.resolveWalkableY(this.minion.getWorld(), station.x, resolvedLeader.getY(), station.z);
		double distToStationSq = this.minion.squaredDistanceTo(station.x, walkableY, station.z);
		double distToLeaderSq = this.minion.squaredDistanceTo(resolvedLeader);

		return distToStationSq > START_FOLLOW_DISTANCE_SQ || distToLeaderSq > SPRINT_DISTANCE_THRESHOLD_SQ;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasVehicle() || !this.minion.hasLeader()) {
			return false;
		}

		MinionEntity resolvedLeader = resolveLeader();
		if (resolvedLeader == null || !resolvedLeader.isAlive()) {
			return false;
		}

		if (this.minion.isActivelyBuilding()) {
			return false;
		}

		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive()) {
			if (this.minion.squaredDistanceTo(resolvedLeader) > COMBAT_LEASH_OVERRIDE_SQ || target.squaredDistanceTo(resolvedLeader) > COMBAT_LEASH_OVERRIDE_SQ) {
				this.minion.setTarget(null);
			} else {
				return false;
			}
		}

		Vec3d station = MinionFormationFollowGoal.calculateFormationStation(
			resolvedLeader.getUuid(),
			resolvedLeader.getX(),
			resolvedLeader.getY(),
			resolvedLeader.getZ(),
			resolvedLeader.getYaw(),
			this.minion.getEffectiveRole(),
			this.cachedRank
		);
		double walkableY = MinionFormationFollowGoal.resolveWalkableY(this.minion.getWorld(), station.x, resolvedLeader.getY(), station.z);
		double distToStationSq = this.minion.squaredDistanceTo(station.x, walkableY, station.z);
		double distToLeaderSq = this.minion.squaredDistanceTo(resolvedLeader);

		return distToStationSq > STOPPING_DISTANCE_SQ || distToLeaderSq > SPRINT_DISTANCE_THRESHOLD_SQ;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
		this.rankUpdateCooldown = 0;
		MinionEntity resolvedLeader = resolveLeader();
		if (resolvedLeader != null) {
			this.cachedRank = this.resolveRank(resolvedLeader);
			Vec3d station = MinionFormationFollowGoal.calculateFormationStation(
				resolvedLeader.getUuid(),
				resolvedLeader.getX(),
				resolvedLeader.getY(),
				resolvedLeader.getZ(),
				resolvedLeader.getYaw(),
				this.minion.getEffectiveRole(),
				this.cachedRank
			);
			double walkableY = MinionFormationFollowGoal.resolveWalkableY(this.minion.getWorld(), station.x, resolvedLeader.getY(), station.z);
			Vec3d targetStation = new Vec3d(station.x, walkableY, station.z);
			this.minion.setActiveTraversalDestination(targetStation);
			double dy = walkableY - this.minion.getY();
			if (dy > 1.25D || dy < -1.5D) {
				this.minion.setArcaneLevitating(true);
			}
			this.minion.getNavigation().startMovingTo(station.x, walkableY, station.z, MARCH_SPEED);
		}
	}

	@Override
	public void stop() {
		this.minion.clearActiveTraversalDestination();
		this.minion.getNavigation().stop();
	}

	@Override
	public void tick() {
		MinionEntity resolvedLeader = resolveLeader();
		if (resolvedLeader == null) {
			return;
		}

		this.minion.getLookControl().lookAt(resolvedLeader, 10.0F, (float) this.minion.getMaxLookPitchChange());

		if (--this.rankUpdateCooldown <= 0) {
			this.cachedRank = this.resolveRank(resolvedLeader);
			this.rankUpdateCooldown = RANK_UPDATE_INTERVAL;
		}

		double distToLeaderSq = this.minion.squaredDistanceTo(resolvedLeader);

		// Emergency teleport when estranged beyond 64 blocks
		if (distToLeaderSq > TELEPORT_DISTANCE_THRESHOLD_SQ) {
			this.minion.refreshPositionAndAngles(resolvedLeader.getX(), resolvedLeader.getY(), resolvedLeader.getZ(), resolvedLeader.getYaw(), 0.0F);
			this.minion.getNavigation().stop();
			if (this.minion.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.PORTAL, this.minion.getX(), this.minion.getY() + 0.5D, this.minion.getZ(), 16, 0.3D, 0.5D, 0.3D, 0.1D);
				sw.playSound(null, this.minion.getX(), this.minion.getY(), this.minion.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 1.0F, 1.2F);
			}
			return;
		}

		Vec3d station = MinionFormationFollowGoal.calculateFormationStation(
			resolvedLeader.getUuid(),
			resolvedLeader.getX(),
			resolvedLeader.getY(),
			resolvedLeader.getZ(),
			resolvedLeader.getYaw(),
			this.minion.getEffectiveRole(),
			this.cachedRank
		);
		double walkableY = MinionFormationFollowGoal.resolveWalkableY(this.minion.getWorld(), station.x, resolvedLeader.getY(), station.z);
		Vec3d targetStation = new Vec3d(station.x, walkableY, station.z);
		this.minion.setActiveTraversalDestination(targetStation);

		double distToStationSq = this.minion.squaredDistanceTo(station.x, walkableY, station.z);

		// Arrival station check: stop within tolerance
		if (distToStationSq <= STOPPING_DISTANCE_SQ) {
			this.minion.getNavigation().stop();
			this.minion.setVelocity(0.0D, this.minion.getVelocity().y, 0.0D);
			this.minion.velocityModified = true;
			return;
		}

		double dy = walkableY - this.minion.getY();
		if (dy > 1.25D || dy < -1.5D) {
			this.minion.setArcaneLevitating(true);
		}

		// Dynamic pacing: sprint at 1.35D when lagging behind (>8 blocks), else march at 1.15D
		double speed = (distToLeaderSq > SPRINT_DISTANCE_THRESHOLD_SQ || distToStationSq > SPRINT_DISTANCE_THRESHOLD_SQ)
			? SPRINT_SPEED
			: MARCH_SPEED;

		if (!this.minion.isArcaneLevitating()) {
			if (--this.repathCooldown <= 0) {
				this.repathCooldown = NAVIGATION_REPATH_INTERVAL;
				this.minion.getNavigation().startMovingTo(station.x, walkableY, station.z, speed);
			}
		}
	}

	public int getCachedRank() {
		return this.cachedRank;
	}
}
