package com.example.entity.ai.goal;

import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Squad-based dynamic formation following AI goal for {@link MinionEntity}.
 * <p>
 * Replaces vanilla collision-prone following with parametric distributed formation offsets
 * relative to the owner player's yaw:
 * <ul>
 *   <li><b>Frontline Ranks (Warriors):</b> Straight battle lines ahead of master to intercept threats (melee swordsmen & ranged archers).</li>
 *   <li><b>Midline Escort (Sentinels):</b> Protective escort lines shielding the master's immediate perimeter.</li>
 *   <li><b>Rearguard Support (Builders & Miners):</b> Non-combatant support column tucked safely behind the master.</li>
 * </ul>
 * <p>
 * Features:
 * <ul>
 *   <li><b>Deterministic Ranking:</b> Assigns unique, flicker-free station ranks per role.</li>
 *   <li><b>Collision Clearance:</b> Stations maintain 2.5 - 3.0 blocks spacing between comrades.</li>
 *   <li><b>Dynamic Pacing:</b> 1.15D steady march; 1.35D sprint when lagging behind (>8 blocks).</li>
 *   <li><b>Arrival Tolerance:</b> Halts within 2.0 blocks of assigned station to prevent jitter.</li>
 *   <li><b>Emergency Teleport:</b> Instantly recalls minion if distance exceeds 64 blocks.</li>
 * </ul>
 */
public class MinionFormationFollowGoal extends Goal {

	private final MinionEntity minion;
	private int cachedRank = 0;
	private int rankUpdateCooldown = 0;
	private int navigationTimer = 0;
	private int stuckTicks = 0;

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
	public static final double COMBAT_LEASH_OVERRIDE_SQ = 256.0D; // 16 blocks
	public static final double ASSAULT_LEASH_OVERRIDE_SQ = 2304.0D; // 48 blocks

	public static final double YAW_ANCHOR_DISPLACEMENT_THRESHOLD_SQ = 0.04D; // 0.2 blocks squared (0.2 * 0.2)
	private static final Map<UUID, FormationAnchor> FORMATION_ANCHORS = new ConcurrentHashMap<>();

	private static final int RANK_UPDATE_INTERVAL = 15;
	private static final int NAVIGATION_REPATH_INTERVAL = 10;

	public MinionFormationFollowGoal(MinionEntity minion) {
		this.minion = minion;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasVehicle() || !this.minion.isSelected()) {
			return false;
		}

		LivingEntity owner = this.minion.getOwner();
		if (owner == null || !owner.isAlive() || owner.isSpectator()) {
			return false;
		}

		// Minions holding a designated waypoint anchor post yield to WaypointHoldGoal / SentinelGuardGoal
		if (this.minion.getGuardAnchorPos() != null) {
			return false;
		}

		// Minions assigned to patrol routes yield to MinionPatrolGoal
		if (this.minion.getPatrolRouteId() >= 0) {
			return false;
		}

		// Minions escorting a Squad Leader yield to MinionFollowLeaderGoal
		if (this.minion.hasLeader()) {
			return false;
		}

		// Minions actively building or engaged in construction work never follow formation
		if (this.minion.isActivelyBuilding() || com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(this.minion)) {
			return false;
		}

		// During active mass assault, minions relentlessly pursue targets across the battlefield;
		// do not disengage or return to formation until all targets are slain or minion is recalled/estranged (>48 blocks)
		if (this.minion.hasAssaultTargets() && this.minion.squaredDistanceTo(owner) < ASSAULT_LEASH_OVERRIDE_SQ) {
			return false;
		}

		// When engaged in combat, yield to attack goals unless owner is too far away
		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive() && this.minion.squaredDistanceTo(owner) < COMBAT_LEASH_OVERRIDE_SQ) {
			return false;
		}

		this.cachedRank = this.resolveRank(owner);
		Vec3d station = calculateFormationStation(owner, this.minion.getEffectiveRole(), this.cachedRank);
		double walkableY = resolveWalkableY(this.minion.getWorld(), station.x, owner.getY(), station.z);
		double distToStationSq = this.minion.squaredDistanceTo(station.x, walkableY, station.z);
		double distToOwnerSq = this.minion.squaredDistanceTo(owner);

		return distToStationSq > START_FOLLOW_DISTANCE_SQ || distToOwnerSq > SPRINT_DISTANCE_THRESHOLD_SQ;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.minion.isAlive() || !this.minion.isTamed() || this.minion.isSitting() || this.minion.hasVehicle() || !this.minion.isSelected()) {
			return false;
		}

		LivingEntity owner = this.minion.getOwner();
		if (owner == null || !owner.isAlive() || owner.isSpectator()) {
			return false;
		}

		if (this.minion.getGuardAnchorPos() != null) {
			return false;
		}

		if (this.minion.getPatrolRouteId() >= 0 || this.minion.hasLeader()) {
			return false;
		}

		// Minions actively building or engaged in construction work never follow formation
		if (this.minion.isActivelyBuilding() || com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(this.minion)) {
			return false;
		}

		// During active mass assault, minions relentlessly pursue targets across the battlefield;
		// do not disengage or return to formation until all targets are slain or minion is recalled/estranged (>48 blocks)
		if (this.minion.hasAssaultTargets() && this.minion.squaredDistanceTo(owner) < ASSAULT_LEASH_OVERRIDE_SQ) {
			return false;
		}

		LivingEntity target = this.minion.getTarget();
		if (target != null && target.isAlive() && this.minion.squaredDistanceTo(owner) < COMBAT_LEASH_OVERRIDE_SQ) {
			return false;
		}

		Vec3d station = calculateFormationStation(owner, this.minion.getEffectiveRole(), this.cachedRank);
		double walkableY = resolveWalkableY(this.minion.getWorld(), station.x, owner.getY(), station.z);
		double distToStationSq = this.minion.squaredDistanceTo(station.x, walkableY, station.z);
		double distToOwnerSq = this.minion.squaredDistanceTo(owner);

		return distToStationSq > STOPPING_DISTANCE_SQ || distToOwnerSq > SPRINT_DISTANCE_THRESHOLD_SQ;
	}

	@Override
	public void start() {
		this.navigationTimer = 0;
		this.rankUpdateCooldown = 0;
		this.stuckTicks = 0;
		LivingEntity owner = this.minion.getOwner();
		if (owner != null) {
			Vec3d station = calculateFormationStation(owner, this.minion.getEffectiveRole(), this.cachedRank);
			double walkableY = resolveWalkableY(this.minion.getWorld(), station.x, owner.getY(), station.z);
			Vec3d targetStation = new Vec3d(station.x, walkableY, station.z);
			this.minion.setActiveTraversalDestination(targetStation);
		}
	}

	@Override
	public void stop() {
		this.minion.getNavigation().stop();
		this.minion.setVelocity(0.0D, this.minion.getVelocity().y, 0.0D);
		this.minion.velocityModified = true;
		this.minion.clearActiveTraversalDestination();
		this.stuckTicks = 0;
	}

	@Override
	public void tick() {
		LivingEntity owner = this.minion.getOwner();
		if (owner == null) {
			return;
		}

		this.minion.getLookControl().lookAt(owner, 10.0F, (float) this.minion.getMaxLookPitchChange());

		if (--this.rankUpdateCooldown <= 0) {
			this.cachedRank = this.resolveRank(owner);
			this.rankUpdateCooldown = RANK_UPDATE_INTERVAL;
		}

		double distToOwnerSq = this.minion.squaredDistanceTo(owner);

		// Follower Catch-Up & Emergency Teleport:
		// 1. Estranged distance check (> 24 blocks / 576.0 sq)
		// 2. Stuck / pit trap check (> 10 blocks away / 100.0 sq AND navigation idle or stuck against wall for 40+ ticks)
		boolean separated = distToOwnerSq > 100.0D;
		boolean navigationStuck = this.minion.getNavigation().isIdle() || (this.minion.horizontalCollision && distToOwnerSq > 100.0D);
		if (separated && navigationStuck) {
			this.stuckTicks++;
		} else {
			this.stuckTicks = Math.max(0, this.stuckTicks - 1);
		}

		boolean shouldCatchUpTeleport = distToOwnerSq > 576.0D || (this.stuckTicks >= 40 && distToOwnerSq > 100.0D);
		if (shouldCatchUpTeleport) {
			if (!this.minion.isActivelyBuilding() && !com.example.construction.ConstructionManager.getInstance().isMinionEngagedInConstruction(this.minion)) {
				if (owner instanceof ServerPlayerEntity serverPlayer) {
					this.stuckTicks = 0;
					this.minion.teleportToPlayer(serverPlayer);
					return;
				}
			}
		}

		Vec3d station = calculateFormationStation(owner, this.minion.getEffectiveRole(), this.cachedRank);
		double walkableY = resolveWalkableY(this.minion.getWorld(), station.x, owner.getY(), station.z);
		Vec3d targetStation = new Vec3d(station.x, walkableY, station.z);
		this.minion.setActiveTraversalDestination(targetStation);

		double distToStationSq = this.minion.squaredDistanceTo(station.x, walkableY, station.z);

		// Arrival station check: stop within 2 blocks
		if (distToStationSq <= STOPPING_DISTANCE_SQ) {
			this.minion.getNavigation().stop();
			this.minion.setVelocity(0.0D, this.minion.getVelocity().y, 0.0D);
			this.minion.velocityModified = true;
			return;
		}

		// Dynamic pacing: sprint at 1.35D when lagging behind (>8 blocks), else march at 1.15D
		double speed = (distToOwnerSq > SPRINT_DISTANCE_THRESHOLD_SQ || distToStationSq > SPRINT_DISTANCE_THRESHOLD_SQ)
			? SPRINT_SPEED
			: MARCH_SPEED;

		if (--this.navigationTimer <= 0) {
			this.navigationTimer = NAVIGATION_REPATH_INTERVAL;
			this.minion.getNavigation().startMovingTo(station.x, walkableY, station.z, speed);
		}
	}

	/**
	 * Represents an anchored kinematic reference frame for an owner's formation stations.
	 * <p>
	 * Prevents formation stations from spinning in a disorienting circle when the commander
	 * rotates their camera while standing stationary or within sub-threshold movement (<= 0.04 blocks^2).
	 */
	public static final class FormationAnchor {
		private double lastX;
		private double lastZ;
		private float anchoredYaw;

		public FormationAnchor(double x, double z, float yaw) {
			this.lastX = x;
			this.lastZ = z;
			this.anchoredYaw = yaw;
		}

		public synchronized double getLastX() {
			return this.lastX;
		}

		public synchronized double getLastZ() {
			return this.lastZ;
		}

		public synchronized float getAnchoredYaw() {
			return this.anchoredYaw;
		}

		public synchronized float update(double currentX, double currentZ, float currentYaw) {
			double dx = currentX - this.lastX;
			double dz = currentZ - this.lastZ;
			double distSq = dx * dx + dz * dz;

			if ((distSq - YAW_ANCHOR_DISPLACEMENT_THRESHOLD_SQ) > 1.0E-5D) {
				this.lastX = currentX;
				this.lastZ = currentZ;
				this.anchoredYaw = currentYaw;
			}
			return this.anchoredYaw;
		}

		public synchronized void snap(double currentX, double currentZ, float currentYaw) {
			this.lastX = currentX;
			this.lastZ = currentZ;
			this.anchoredYaw = currentYaw;
		}
	}

	/**
	 * Retrieves or updates the anchored formation yaw for a given commander UUID and position.
	 * <p>
	 * Freezes formation yaw when owner displacement is <= 0.04 blocks^2, updating only
	 * upon deliberate displacement (> 0.04 blocks^2).
	 */
	public static float getFormationYaw(UUID ownerUuid, double currentX, double currentZ, float currentYaw) {
		if (ownerUuid == null) {
			return currentYaw;
		}
		FormationAnchor anchor = FORMATION_ANCHORS.get(ownerUuid);
		if (anchor == null) {
			anchor = new FormationAnchor(currentX, currentZ, currentYaw);
			FORMATION_ANCHORS.put(ownerUuid, anchor);
			return currentYaw;
		}
		return anchor.update(currentX, currentZ, currentYaw);
	}

	/**
	 * Retrieves or updates the anchored formation yaw for the given living commander.
	 */
	public static float getFormationYaw(LivingEntity owner) {
		if (owner == null) {
			return 0.0F;
		}
		return getFormationYaw(owner.getUuid(), owner.getX(), owner.getZ(), owner.getYaw());
	}

	/**
	 * Immediately snaps the formation anchor for the owner to their current position and yaw,
	 * aligning the formation stations to the owner's immediate heading.
	 */
	public static void refreshFormationAnchor(UUID ownerUuid, double currentX, double currentZ, float currentYaw) {
		if (ownerUuid == null) {
			return;
		}
		FormationAnchor anchor = FORMATION_ANCHORS.get(ownerUuid);
		if (anchor != null) {
			anchor.snap(currentX, currentZ, currentYaw);
		} else {
			FORMATION_ANCHORS.put(ownerUuid, new FormationAnchor(currentX, currentZ, currentYaw));
		}
	}

	/**
	 * Immediately snaps the formation anchor for the living owner to their current position and yaw.
	 */
	public static void refreshFormationAnchor(LivingEntity owner) {
		if (owner == null) {
			return;
		}
		refreshFormationAnchor(owner.getUuid(), owner.getX(), owner.getZ(), owner.getYaw());
	}

	/**
	 * Returns the current formation anchor for the given owner UUID, or null if unanchored.
	 */
	public static FormationAnchor getFormationAnchor(UUID ownerUuid) {
		return ownerUuid != null ? FORMATION_ANCHORS.get(ownerUuid) : null;
	}

	/**
	 * Returns the current formation anchor for the given living owner, or null if unanchored.
	 */
	public static FormationAnchor getFormationAnchor(LivingEntity owner) {
		return owner != null ? getFormationAnchor(owner.getUuid()) : null;
	}

	/**
	 * Clears all cached formation anchors across all commanders.
	 */
	public static void clearFormationAnchors() {
		FORMATION_ANCHORS.clear();
	}

	/**
	 * Clears the formation anchor for the specified commander UUID.
	 */
	public static void clearFormationAnchor(UUID ownerUuid) {
		if (ownerUuid != null) {
			FORMATION_ANCHORS.remove(ownerUuid);
		}
	}

	/**
	 * Clears the formation anchor for the specified living commander.
	 */
	public static void clearFormationAnchor(LivingEntity owner) {
		if (owner != null) {
			clearFormationAnchor(owner.getUuid());
		}
	}

	/**
	 * Checks whether the candidate minion satisfies all eligibility criteria to occupy a formation station.
	 * Excludes unselected units, units holding position (sitting or guarding), and units with guard anchors.
	 */
	public static boolean isEligibleForFormation(MinionEntity minion, LivingEntity owner) {
		return minion != null
			&& minion.isAlive()
			&& minion.isTamed()
			&& minion.isOwner(owner)
			&& minion.isSelected()
			&& !minion.isHoldingPosition()
			&& minion.getGuardAnchorPos() == null
			&& minion.getPatrolRouteId() < 0;
	}

	/**
	 * Pure boolean predicate version for verifying formation rank eligibility invariants without game entity dependencies.
	 */
	public static boolean isEligibleForFormationRank(boolean isAlive, boolean isTamed, boolean isOwner, boolean isSelected, boolean isHoldingPosition, boolean hasGuardAnchor) {
		return isAlive && isTamed && isOwner && isSelected && !isHoldingPosition && !hasGuardAnchor;
	}

	/**
	 * Generic deterministic rank resolver for comrades sharing the same archetype role.
	 */
	public static <T> int resolveRank(List<T> comrades, T candidate, Function<T, MinionRole> roleExtractor, ToIntFunction<T> idExtractor) {
		if (candidate == null || comrades == null) {
			return 0;
		}
		MinionRole targetRole = roleExtractor.apply(candidate);
		List<T> sameRoleComrades = comrades.stream()
			.filter(m -> roleExtractor.apply(m) == targetRole)
			.sorted(Comparator.comparingInt(idExtractor))
			.toList();

		int index = sameRoleComrades.indexOf(candidate);
		return Math.max(0, index);
	}

	/**
	 * Resolves a deterministic 0-based station rank among active minion thralls belonging to the same owner
	 * sharing the same archetype role. Excludes unselected minions and standing sentinels from polluting ranks.
	 *
	 * @param owner The commander entity.
	 * @return A stable integer rank (0, 1, 2, ...).
	 */
	public int resolveRank(LivingEntity owner) {
		if (this.minion.getWorld() == null || owner == null) {
			return 0;
		}

		List<MinionEntity> comrades = this.minion.getWorld().getEntitiesByClass(
			MinionEntity.class,
			owner.getBoundingBox().expand(48.0D),
			m -> isEligibleForFormation(m, owner)
		);

		return resolveRank(comrades, this.minion, MinionEntity::getEffectiveRole, Entity::getId);
	}

	/**
	 * Calculates local (forward, flank) offset relative to the commander's facing direction.
	 * Formations are structured in straight, parallel military battle ranks (lines of 4 units):
	 * <ul>
	 *   <li><b>Frontline Ranks (Warriors):</b> Straight battle lines ahead of commander (+4.0 forward, -2.0 per subsequent rank).</li>
	 *   <li><b>Midline Escort Ranks (Sentinels):</b> Flanking battle lines guarding commander (+1.8 forward, -2.0 per subsequent rank).</li>
	 *   <li><b>Rearguard Support Ranks (Builders & Miners):</b> Support column lines behind commander (-2.5 forward, -2.0 per subsequent rank).</li>
	 * </ul>
	 *
	 * @param role The archetype role of the minion.
	 * @param rank The deterministic 0-based rank within that role.
	 * @return A 3D vector where x = forwardOffset (blocks) and z = flankOffset (blocks).
	 */
	public static Vec3d calculateFormationOffset(MinionRole role, int rank) {
		int lineIndex = rank / 4;
		int posInLine = rank % 4;
		int side = (posInLine % 2 == 0) ? -1 : 1;
		double colSpacing = (posInLine < 2) ? 1.35D : 3.60D;
		double flankOffset = side * colSpacing;

		double baseForward;
		switch (role) {
			case WARRIOR -> baseForward = 4.0D;
			case SENTINEL -> baseForward = 1.8D;
			case BUILDER -> baseForward = -2.0D;
			default -> baseForward = -2.0D;
		}

		double forwardOffset = baseForward - (lineIndex * 2.0D);
		return new Vec3d(forwardOffset, 0.0D, flankOffset);
	}

	/**
	 * Computes world coordinates for the given formation station based on commander position, yaw, role, and rank.
	 */
	public static Vec3d calculateFormationStation(double ownerX, double ownerY, double ownerZ, float ownerYaw, MinionRole role, int rank) {
		Vec3d localOffset = calculateFormationOffset(role, rank);
		double forwardOffset = localOffset.x;
		double flankOffset = localOffset.z;

		double yawRad = Math.toRadians(ownerYaw);
		// In Minecraft coordinate space:
		// Forward unit vector: (-sin(yaw), cos(yaw))
		// Right (flank) unit vector: (cos(yaw), sin(yaw))
		double offsetX = forwardOffset * (-Math.sin(yawRad)) + flankOffset * Math.cos(yawRad);
		double offsetZ = forwardOffset * Math.cos(yawRad) + flankOffset * Math.sin(yawRad);

		return new Vec3d(ownerX + offsetX, ownerY, ownerZ + offsetZ);
	}

	/**
	 * Computes world coordinates for the given formation station relative to a commander UUID and position,
	 * utilizing anchored formation yaw hysteresis.
	 */
	public static Vec3d calculateFormationStation(UUID ownerUuid, double ownerX, double ownerY, double ownerZ, float ownerYaw, MinionRole role, int rank) {
		float formationYaw = getFormationYaw(ownerUuid, ownerX, ownerZ, ownerYaw);
		return calculateFormationStation(ownerX, ownerY, ownerZ, formationYaw, role, rank);
	}

	/**
	 * Computes world coordinates for the given formation station relative to a living owner,
	 * utilizing anchored formation yaw hysteresis to prevent station whirling when the owner is stationary.
	 */
	public static Vec3d calculateFormationStation(LivingEntity owner, MinionRole role, int rank) {
		if (owner == null) {
			return Vec3d.ZERO;
		}
		return calculateFormationStation(owner.getUuid(), owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), role, rank);
	}

	/**
	 * Scans the local vertical column for the nearest walkable surface to prevent pathing into mid-air or solid walls.
	 */
	public static double resolveWalkableY(World world, double x, double baseY, double z) {
		if (world == null) {
			return baseY;
		}
		int blockX = MathHelper.floor(x);
		int blockZ = MathHelper.floor(z);
		int startY = MathHelper.floor(baseY);
		BlockPos.Mutable mutable = new BlockPos.Mutable(blockX, startY + 4, blockZ);

		for (int dy = 4; dy >= -8; dy--) {
			mutable.setY(startY + dy);
			BlockState state = world.getBlockState(mutable);
			BlockState stateBelow = world.getBlockState(mutable.down());
			if (stateBelow.isSolidBlock(world, mutable.down()) && (state.isAir() || state.canPathfindThrough(NavigationType.LAND))) {
				return mutable.getY();
			}
		}
		return baseY;
	}

	public int getCachedRank() {
		return this.cachedRank;
	}
}
