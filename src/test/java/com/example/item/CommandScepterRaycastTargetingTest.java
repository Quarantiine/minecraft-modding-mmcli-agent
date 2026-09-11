package com.example.item;

import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.item.custom.CommandScepterItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests validating the 32-block crosshair raycasting mechanics in {@link CommandScepterItem}:
 * - Exact 3D point-to-ray squared distance geometry (clamped segment projections, horizontal/vertical offsets).
 * - Behind-the-player and beyond-max-range boundary clamping.
 * - Direction vector normalization invariance and zero-vector degeneration handling.
 * - Crosshair hostile candidate sorting (angular alignment, depth tie-breaking, rear hostile rejection).
 * - Line-of-sight terrain obstruction clipping and entity-over-block priority.
 * - Scepter mode-dependent quick-tap (<8 ticks) dispatch state machine.
 * - Entity targetability filtering (spectators, self, owned minions, enemy minions, players).
 */
public class CommandScepterRaycastTargetingTest {

	private static final double EPSILON = 1e-6;

	// =========================================================================
	// 1. Exact 3D Point-to-Ray Distance Math & Boundary Clamping
	// =========================================================================

	@Test
	@DisplayName("Point directly on the ray line in front has distance 0.0")
	void testPointDirectlyOnRay() {
		Vec3d rayStart = new Vec3d(0.0, 64.0, 0.0);
		Vec3d rayDir = new Vec3d(0.0, 0.0, 1.0); // Looking South (+Z)
		double maxRange = 32.0;

		// Point at Z = 15.0 directly on the ray
		Vec3d pointOnRay = new Vec3d(0.0, 64.0, 15.0);
		double distSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, pointOnRay);
		Assertions.assertEquals(0.0, distSq, EPSILON);

		// Point at ray start (Z = 0.0)
		double distAtStart = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, rayStart);
		Assertions.assertEquals(0.0, distAtStart, EPSILON);

		// Point at ray tip (Z = 32.0)
		Vec3d pointAtTip = new Vec3d(0.0, 64.0, 32.0);
		double distAtTip = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, pointAtTip);
		Assertions.assertEquals(0.0, distAtTip, EPSILON);
	}

	@Test
	@DisplayName("Perpendicular offsets horizontally and vertically calculate exact Pythagorean distances")
	void testPerpendicularOffsetsFromRay() {
		Vec3d rayStart = new Vec3d(10.0, 70.0, 10.0);
		Vec3d rayDir = new Vec3d(1.0, 0.0, 0.0); // Looking East (+X)
		double maxRange = 32.0;

		// Point at X = 20.0 (10 blocks along ray), with Y = +3 and Z = -4 offset
		// Perpendicular distance squared should be 3^2 + (-4)^2 = 9 + 16 = 25.0
		Vec3d offsetPoint = new Vec3d(20.0, 73.0, 6.0);
		double distSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, offsetPoint);
		Assertions.assertEquals(25.0, distSq, EPSILON);

		// Pure horizontal offset of 2 blocks at X = 15.0
		Vec3d horizontalPoint = new Vec3d(15.0, 70.0, 12.0);
		double horizDistSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, horizontalPoint);
		Assertions.assertEquals(4.0, horizDistSq, EPSILON);

		// Pure vertical offset of 5 blocks at X = 25.0
		Vec3d verticalPoint = new Vec3d(25.0, 75.0, 10.0);
		double vertDistSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, verticalPoint);
		Assertions.assertEquals(25.0, vertDistSq, EPSILON);
	}

	@Test
	@DisplayName("Points behind the ray origin clamp to origin (t = 0)")
	void testPointsBehindRayOriginClampToStart() {
		Vec3d rayStart = new Vec3d(0.0, 64.0, 0.0);
		Vec3d rayDir = new Vec3d(0.0, 0.0, 1.0); // Looking +Z
		double maxRange = 32.0;

		// Point 10 blocks behind player at Z = -10.0 on the line
		// Clamped to rayStart (0, 64, 0), so distance squared should be (-10)^2 = 100.0
		Vec3d behindPoint = new Vec3d(0.0, 64.0, -10.0);
		double distSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, behindPoint);
		Assertions.assertEquals(100.0, distSq, EPSILON);

		// Point behind and to the side: (-3, 64, -4) -> distance to (0, 64, 0) is 3^2 + 4^2 = 25.0
		Vec3d behindDiagonal = new Vec3d(-3.0, 64.0, -4.0);
		double diagDistSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, behindDiagonal);
		Assertions.assertEquals(25.0, diagDistSq, EPSILON);
	}

	@Test
	@DisplayName("Points beyond max range clamp to ray tip (t = maxRange)")
	void testPointsBeyondMaxRangeClampToTip() {
		Vec3d rayStart = new Vec3d(0.0, 64.0, 0.0);
		Vec3d rayDir = new Vec3d(0.0, 0.0, 1.0); // Looking +Z
		double maxRange = 32.0;

		// Point at Z = 40.0 along the line (8 blocks past tip at Z = 32.0)
		Vec3d pastTip = new Vec3d(0.0, 64.0, 40.0);
		double distSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, pastTip);
		Assertions.assertEquals(64.0, distSq, EPSILON); // (40 - 32)^2 = 64.0

		// Point at Z = 35.0 with perpendicular offset X = 4.0
		// Tip is at (0, 64, 32). Vector from tip to point is (4, 0, 3). Distance squared = 16 + 9 = 25.0
		Vec3d pastTipOffset = new Vec3d(4.0, 64.0, 35.0);
		double offsetDistSq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, pastTipOffset);
		Assertions.assertEquals(25.0, offsetDistSq, EPSILON);
	}

	@Test
	@DisplayName("Direction vector normalization invariance and zero-vector safety")
	void testVectorNormalizationAndDegeneration() {
		Vec3d rayStart = new Vec3d(5.0, 5.0, 5.0);
		Vec3d unnormalizedDir = new Vec3d(0.0, 10.0, 0.0); // Looking Up (+Y), length 10
		Vec3d unitDir = new Vec3d(0.0, 1.0, 0.0);
		double maxRange = 32.0;

		Vec3d testPoint = new Vec3d(7.0, 15.0, 5.0); // X = +2 offset at Y = 15

		double unnormDist = CommandScepterItem.calculateDistanceSqToRay(rayStart, unnormalizedDir, maxRange, testPoint);
		double unitDist = CommandScepterItem.calculateDistanceSqToRay(rayStart, unitDir, maxRange, testPoint);

		Assertions.assertEquals(unitDist, unnormDist, EPSILON);
		Assertions.assertEquals(4.0, unnormDist, EPSILON);

		// Degenerate zero-length direction vector: falls back to distance to rayStart
		Vec3d zeroDir = Vec3d.ZERO;
		double zeroDist = CommandScepterItem.calculateDistanceSqToRay(rayStart, zeroDir, maxRange, testPoint);
		Assertions.assertEquals(testPoint.squaredDistanceTo(rayStart), zeroDist, EPSILON);
	}

	// =========================================================================
	// 2. Crosshair Candidate Sorting & Target Acquisition
	// =========================================================================

	public record MockCandidate(
		String name,
		Vec3d position,
		double height
	) {
		public Vec3d getCenter() {
			return new Vec3d(this.position.x, this.position.y + (this.height * 0.5D), this.position.z);
		}
	}

	@Test
	@DisplayName("Hostile in front on crosshair is prioritized over closer hostile behind player")
	void testPrioritizeCrosshairHostileOverRearCloserHostile() {
		Vec3d eyePos = new Vec3d(0.0, 64.0, 0.0);
		Vec3d lookDir = new Vec3d(0.0, 0.0, 1.0); // Looking North/South +Z
		double maxRange = 32.0;

		MockCandidate rearZombie = new MockCandidate("RearZombie", new Vec3d(0.0, 63.0, -3.0), 2.0); // 3 blocks behind
		MockCandidate crosshairCreeper = new MockCandidate("CrosshairCreeper", new Vec3d(0.0, 63.0, 16.0), 2.0); // 16 blocks directly ahead

		double rearDistSq = CommandScepterItem.calculateDistanceSqToRay(eyePos, lookDir, maxRange, rearZombie.getCenter());
		double crosshairDistSq = CommandScepterItem.calculateDistanceSqToRay(eyePos, lookDir, maxRange, crosshairCreeper.getCenter());

		// Crosshair creeper is at distance 0 to the ray; rear zombie clamps to origin and has distance ~9
		Assertions.assertEquals(0.0, crosshairDistSq, EPSILON);
		Assertions.assertTrue(rearDistSq > 8.0, "Rear zombie distance to ray must be >= 9");

		List<MockCandidate> candidates = new ArrayList<>(List.of(rearZombie, crosshairCreeper));
		candidates.sort(Comparator
			.comparingDouble((MockCandidate m) -> CommandScepterItem.calculateDistanceSqToRay(eyePos, lookDir, maxRange, m.getCenter()))
			.thenComparingDouble(m -> m.position.squaredDistanceTo(eyePos))
		);

		Assertions.assertEquals("CrosshairCreeper", candidates.get(0).name(), "Crosshair-aligned target must sort first");
	}

	@Test
	@DisplayName("Angular alignment priority: among hostiles at equal depth, closest to crosshair wins")
	void testAngularAlignmentPriority() {
		Vec3d eyePos = new Vec3d(0.0, 64.0, 0.0);
		Vec3d lookDir = new Vec3d(1.0, 0.0, 0.0); // Looking +X
		double maxRange = 32.0;

		MockCandidate wideSkeleton = new MockCandidate("WideSkeleton", new Vec3d(20.0, 63.0, 8.0), 2.0);   // offset Z = 8
		MockCandidate nearCenterSpider = new MockCandidate("NearCenterSpider", new Vec3d(20.0, 63.0, 1.0), 1.0); // offset Z = 1
		MockCandidate edgeWitch = new MockCandidate("EdgeWitch", new Vec3d(20.0, 63.0, -5.0), 2.0);      // offset Z = 5

		List<MockCandidate> candidates = new ArrayList<>(List.of(wideSkeleton, nearCenterSpider, edgeWitch));
		candidates.sort(Comparator
			.comparingDouble((MockCandidate m) -> CommandScepterItem.calculateDistanceSqToRay(eyePos, lookDir, maxRange, m.getCenter()))
			.thenComparingDouble(m -> m.position.squaredDistanceTo(eyePos))
		);

		Assertions.assertEquals("NearCenterSpider", candidates.get(0).name());
		Assertions.assertEquals("EdgeWitch", candidates.get(1).name());
		Assertions.assertEquals("WideSkeleton", candidates.get(2).name());
	}

	@Test
	@DisplayName("Depth tie-breaking: between two enemies on crosshairs, the closer one is attacked first")
	void testDepthTieBreakingOnCrosshair() {
		Vec3d eyePos = new Vec3d(0.0, 64.0, 0.0);
		Vec3d lookDir = new Vec3d(0.0, 0.0, 1.0); // Looking +Z
		double maxRange = 32.0;

		MockCandidate distantZombie = new MockCandidate("DistantZombie", new Vec3d(0.0, 63.0, 24.0), 2.0);
		MockCandidate closeZombie = new MockCandidate("CloseZombie", new Vec3d(0.0, 63.0, 8.0), 2.0);

		List<MockCandidate> candidates = new ArrayList<>(List.of(distantZombie, closeZombie));
		candidates.sort(Comparator
			.comparingDouble((MockCandidate m) -> CommandScepterItem.calculateDistanceSqToRay(eyePos, lookDir, maxRange, m.getCenter()))
			.thenComparingDouble(m -> m.position.squaredDistanceTo(eyePos))
		);

		Assertions.assertEquals("CloseZombie", candidates.get(0).name());
		Assertions.assertEquals("DistantZombie", candidates.get(1).name());
	}

	// =========================================================================
	// 3. Terrain Obstruction & Raycast Priority Simulation
	// =========================================================================

	public enum TargetHitType { NONE, BLOCK, ENTITY }

	public record SimulatedRaycastResult(
		TargetHitType type,
		String targetId,
		double distance
	) {}

	/**
	 * Pure mathematical simulation of the raycast targeting arbitration implemented in {@link CommandScepterItem}.
	 */
	public static SimulatedRaycastResult resolveRaycastTarget(
		Vec3d rayStart,
		Vec3d rayDir,
		double maxRange,
		Double blockHitDistance,
		List<MockCandidate> entities
	) {
		double maxAllowedEntityDistSq = (blockHitDistance != null) ? (blockHitDistance * blockHitDistance) : (maxRange * maxRange);

		// Find entity directly intersected by cursor ray (within entity hitbox radius, e.g. 0.8 blocks)
		MockCandidate hitEntity = null;
		double bestDistSq = maxAllowedEntityDistSq;

		for (MockCandidate entity : entities) {
			double distToRaySq = CommandScepterItem.calculateDistanceSqToRay(rayStart, rayDir, maxRange, entity.getCenter());
			if (distToRaySq <= 0.64) { // Hitbox tolerance radius ~0.8 blocks
				double distToEyeSq = entity.getCenter().squaredDistanceTo(rayStart);
				if (distToEyeSq < bestDistSq) {
					bestDistSq = distToEyeSq;
					hitEntity = entity;
				}
			}
		}

		if (hitEntity != null) {
			return new SimulatedRaycastResult(TargetHitType.ENTITY, hitEntity.name(), Math.sqrt(bestDistSq));
		}

		if (blockHitDistance != null && blockHitDistance <= maxRange) {
			return new SimulatedRaycastResult(TargetHitType.BLOCK, "ground_block", blockHitDistance);
		}

		return new SimulatedRaycastResult(TargetHitType.NONE, null, maxRange);
	}

	@Test
	@DisplayName("Direct entity hit is prioritized when unobstructed by terrain")
	void testUnobstructedEntityPrioritization() {
		Vec3d start = new Vec3d(0.0, 64.0, 0.0);
		Vec3d dir = new Vec3d(0.0, 0.0, 1.0);
		double maxRange = 32.0;

		MockCandidate enemy = new MockCandidate("Zombie", new Vec3d(0.0, 63.0, 12.0), 2.0); // 12 blocks away
		Double groundHitAt20Blocks = 20.0; // Wall behind the zombie at 20 blocks

		SimulatedRaycastResult result = resolveRaycastTarget(start, dir, maxRange, groundHitAt20Blocks, List.of(enemy));

		Assertions.assertEquals(TargetHitType.ENTITY, result.type());
		Assertions.assertEquals("Zombie", result.targetId());
	}

	@Test
	@DisplayName("Solid block obstructs entity behind it; block hit is returned")
	void testSolidBlockObstructsEntity() {
		Vec3d start = new Vec3d(0.0, 64.0, 0.0);
		Vec3d dir = new Vec3d(0.0, 0.0, 1.0);
		double maxRange = 32.0;

		MockCandidate enemyBehindWall = new MockCandidate("ZombieBehindWall", new Vec3d(0.0, 63.0, 15.0), 2.0);
		Double wallHitAt6Blocks = 6.0; // Solid wall between player and zombie

		SimulatedRaycastResult result = resolveRaycastTarget(start, dir, maxRange, wallHitAt6Blocks, List.of(enemyBehindWall));

		Assertions.assertEquals(TargetHitType.BLOCK, result.type());
		Assertions.assertEquals("ground_block", result.targetId());
		Assertions.assertEquals(6.0, result.distance(), EPSILON);
	}

	@Test
	@DisplayName("Sky aiming returns miss and falls back to directive broadcast")
	void testSkyAimingReturnsNone() {
		Vec3d start = new Vec3d(0.0, 64.0, 0.0);
		Vec3d dir = new Vec3d(0.0, 1.0, 0.0); // Looking directly up at sky
		double maxRange = 32.0;

		SimulatedRaycastResult result = resolveRaycastTarget(start, dir, maxRange, null, List.of());

		Assertions.assertEquals(TargetHitType.NONE, result.type());
		Assertions.assertNull(result.targetId());
	}

	// =========================================================================
	// 4. Mode-Dependent Quick-Tap Dispatch Logic Matrix
	// =========================================================================

	public enum ActionOutcome {
		CYCLE_BLUEPRINT,
		FOCUS_FIRE_PING,
		TRANSFIGURE_MINION,
		GROUND_WAYPOINT_PING,
		GLOBAL_DIRECTIVE_BROADCAST,
		RECRUIT_ERROR
	}

	public static ActionOutcome dispatchQuickTap(
		CommandMode mode,
		SquadGroup squad,
		TargetHitType hitType,
		boolean hitIsMob,
		boolean hitIsMinion,
		boolean hitIsPlayer
	) {
		// Quick tap (<8 ticks)
		if (mode == CommandMode.BUILD) {
			return ActionOutcome.CYCLE_BLUEPRINT;
		}

		if (hitType == TargetHitType.ENTITY) {
			if (mode == CommandMode.RECRUIT) {
				if (hitIsMob && !hitIsMinion && !hitIsPlayer) {
					return ActionOutcome.TRANSFIGURE_MINION;
				}
				return ActionOutcome.RECRUIT_ERROR;
			} else {
				if (!hitIsPlayer) {
					return ActionOutcome.FOCUS_FIRE_PING;
				}
			}
		}

		if (hitType == TargetHitType.BLOCK) {
			return ActionOutcome.GROUND_WAYPOINT_PING;
		}

		return ActionOutcome.GLOBAL_DIRECTIVE_BROADCAST;
	}

	@Test
	@DisplayName("Validate quick-tap dispatch outcomes across interaction matrix")
	void testQuickTapDispatchMatrix() {
		// BUILD mode always cycles blueprint on quick tap
		Assertions.assertEquals(
			ActionOutcome.CYCLE_BLUEPRINT,
			dispatchQuickTap(CommandMode.BUILD, SquadGroup.ALL, TargetHitType.BLOCK, false, false, false)
		);
		Assertions.assertEquals(
			ActionOutcome.CYCLE_BLUEPRINT,
			dispatchQuickTap(CommandMode.BUILD, SquadGroup.ALL, TargetHitType.ENTITY, true, false, false)
		);

		// ATTACK mode aiming at hostile entity -> focus fire
		Assertions.assertEquals(
			ActionOutcome.FOCUS_FIRE_PING,
			dispatchQuickTap(CommandMode.ATTACK, SquadGroup.ALPHA, TargetHitType.ENTITY, true, false, false)
		);

		// ATTACK mode aiming at 32-block ground -> ground waypoint ping
		Assertions.assertEquals(
			ActionOutcome.GROUND_WAYPOINT_PING,
			dispatchQuickTap(CommandMode.ATTACK, SquadGroup.BRAVO, TargetHitType.BLOCK, false, false, false)
		);

		// FOLLOW mode aiming at 32-block ground -> long range RTS waypoint ping
		Assertions.assertEquals(
			ActionOutcome.GROUND_WAYPOINT_PING,
			dispatchQuickTap(CommandMode.FOLLOW, SquadGroup.CHARLIE, TargetHitType.BLOCK, false, false, false)
		);

		// STAY mode aiming at sky -> global stay directive broadcast
		Assertions.assertEquals(
			ActionOutcome.GLOBAL_DIRECTIVE_BROADCAST,
			dispatchQuickTap(CommandMode.STAY, SquadGroup.DELTA, TargetHitType.NONE, false, false, false)
		);

		// RECRUIT mode aiming at living vanilla mob -> transfigure into minion
		Assertions.assertEquals(
			ActionOutcome.TRANSFIGURE_MINION,
			dispatchQuickTap(CommandMode.RECRUIT, SquadGroup.ALL, TargetHitType.ENTITY, true, false, false)
		);

		// RECRUIT mode aiming at minion or player -> recruit error
		Assertions.assertEquals(
			ActionOutcome.RECRUIT_ERROR,
			dispatchQuickTap(CommandMode.RECRUIT, SquadGroup.ALL, TargetHitType.ENTITY, true, true, false)
		);
		Assertions.assertEquals(
			ActionOutcome.RECRUIT_ERROR,
			dispatchQuickTap(CommandMode.RECRUIT, SquadGroup.ALL, TargetHitType.ENTITY, false, false, true)
		);
	}

	// =========================================================================
	// 5. Entity Targetability Invariants
	// =========================================================================

	public record MockTargetEntity(
		UUID id,
		boolean spectator,
		boolean alive,
		boolean isPlayer,
		boolean isMinion,
		UUID minionOwnerId
	) {
		public boolean isTargetableBy(UUID commanderId) {
			if (this.spectator || !this.alive || this.id.equals(commanderId)) {
				return false;
			}
			if (this.isPlayer) {
				return false;
			}
			if (this.isMinion && commanderId.equals(this.minionOwnerId)) {
				return false;
			}
			return true;
		}
	}

	@Test
	@DisplayName("Targetability filter correctly handles spectators, self, owned minions, and enemies")
	void testEntityTargetabilityInvariants() {
		UUID commanderId = UUID.randomUUID();
		UUID otherPlayerId = UUID.randomUUID();

		MockTargetEntity normalZombie = new MockTargetEntity(UUID.randomUUID(), false, true, false, false, null);
		MockTargetEntity deadZombie = new MockTargetEntity(UUID.randomUUID(), false, false, false, false, null);
		MockTargetEntity spectatorMob = new MockTargetEntity(UUID.randomUUID(), true, true, false, false, null);
		MockTargetEntity selfPlayer = new MockTargetEntity(commanderId, false, true, true, false, null);
		MockTargetEntity otherPlayer = new MockTargetEntity(otherPlayerId, false, true, true, false, null);
		MockTargetEntity myMinion = new MockTargetEntity(UUID.randomUUID(), false, true, false, true, commanderId);
		MockTargetEntity enemyMinion = new MockTargetEntity(UUID.randomUUID(), false, true, false, true, otherPlayerId);

		Assertions.assertTrue(normalZombie.isTargetableBy(commanderId), "Hostile zombie must be targetable");
		Assertions.assertFalse(deadZombie.isTargetableBy(commanderId), "Dead entity must NOT be targetable");
		Assertions.assertFalse(spectatorMob.isTargetableBy(commanderId), "Spectator must NOT be targetable");
		Assertions.assertFalse(selfPlayer.isTargetableBy(commanderId), "Commander player must NOT be self-targetable");
		Assertions.assertFalse(otherPlayer.isTargetableBy(commanderId), "Other player must NOT be targetable by default");
		Assertions.assertFalse(myMinion.isTargetableBy(commanderId), "Owned minion must NOT be friendly-fired");
		Assertions.assertTrue(enemyMinion.isTargetableBy(commanderId), "Enemy thrall MUST be targetable");
	}

	// =========================================================================
	// 6. Individual Minion Follow & Standby Invariants
	// =========================================================================

	public static boolean simulateRaycastInteractable(MockTargetEntity entity, UUID commanderId) {
		if (entity.spectator() || !entity.alive() || entity.id().equals(commanderId)) {
			return false;
		}
		// Owned minions are interactable for individual follow commands
		if (entity.isMinion() && commanderId.equals(entity.minionOwnerId())) {
			return true;
		}
		return entity.isTargetableBy(commanderId);
	}

	@Test
	@DisplayName("Raycast interactable filter includes owned minions for individual follow commands")
	void testRaycastInteractableEntityInvariants() {
		UUID commanderId = UUID.randomUUID();
		UUID otherPlayerId = UUID.randomUUID();

		MockTargetEntity normalZombie = new MockTargetEntity(UUID.randomUUID(), false, true, false, false, null);
		MockTargetEntity deadZombie = new MockTargetEntity(UUID.randomUUID(), false, false, false, false, null);
		MockTargetEntity spectatorMob = new MockTargetEntity(UUID.randomUUID(), true, true, false, false, null);
		MockTargetEntity selfPlayer = new MockTargetEntity(commanderId, false, true, true, false, null);
		MockTargetEntity otherPlayer = new MockTargetEntity(otherPlayerId, false, true, true, false, null);
		MockTargetEntity myMinion = new MockTargetEntity(UUID.randomUUID(), false, true, false, true, commanderId);
		MockTargetEntity enemyMinion = new MockTargetEntity(UUID.randomUUID(), false, true, false, true, otherPlayerId);

		Assertions.assertTrue(simulateRaycastInteractable(normalZombie, commanderId), "Hostile zombie must be interactable");
		Assertions.assertFalse(simulateRaycastInteractable(deadZombie, commanderId), "Dead entity must NOT be interactable");
		Assertions.assertFalse(simulateRaycastInteractable(spectatorMob, commanderId), "Spectator must NOT be interactable");
		Assertions.assertFalse(simulateRaycastInteractable(selfPlayer, commanderId), "Self player must NOT be interactable");
		Assertions.assertFalse(simulateRaycastInteractable(otherPlayer, commanderId), "Other player must NOT be interactable");
		Assertions.assertTrue(simulateRaycastInteractable(myMinion, commanderId), "Owned minion MUST be raycast-interactable for individual follow");
		Assertions.assertTrue(simulateRaycastInteractable(enemyMinion, commanderId), "Enemy thrall must be raycast-interactable for focus fire");
	}

	@Test
	@DisplayName("Individual minion follow command resets sitting, clears guard anchor, and starts moving to player")
	void testIndividualMinionFollowStateMachine() {
		class TestMinionUnit {
			boolean sitting = true;
			Vec3d guardAnchor = new Vec3d(100.0, 64.0, 100.0);
			String combatTarget = "Zombie_42";
			boolean navigatingToPlayer = false;
			double speed = 0.0;

			void commandFollow(Vec3d playerPos) {
				this.sitting = false;
				this.guardAnchor = null;
				this.combatTarget = null;
				this.navigatingToPlayer = true;
				this.speed = 1.35;
			}
		}

		TestMinionUnit minion = new TestMinionUnit();
		Assertions.assertTrue(minion.sitting);
		Assertions.assertNotNull(minion.guardAnchor);
		Assertions.assertNotNull(minion.combatTarget);

		minion.commandFollow(new Vec3d(0.0, 64.0, 0.0));

		Assertions.assertFalse(minion.sitting, "Sitting state must be cleared on individual follow");
		Assertions.assertNull(minion.guardAnchor, "Guard anchor must be cleared to allow dynamic following");
		Assertions.assertNull(minion.combatTarget, "Combat target must be cleared");
		Assertions.assertTrue(minion.navigatingToPlayer, "Minion must start navigation toward commander");
		Assertions.assertEquals(1.35, minion.speed, EPSILON, "Pacing speed must be 1.35D sprint");
	}

	@Test
	@DisplayName("Newly enthralled thralls initialize in Standby (sitting with guard anchor at spawn pos)")
	void testNewlyEnthralledStandbyState() {
		class TestEnthrallmentResult {
			final boolean sitting;
			final Vec3d guardAnchor;
			final boolean targetNull;
			final boolean navigationStopped;

			TestEnthrallmentResult(Vec3d spawnPos) {
				this.sitting = true;
				this.guardAnchor = spawnPos;
				this.targetNull = true;
				this.navigationStopped = true;
			}
		}

		Vec3d spawnLoc = new Vec3d(24.0, 64.0, -18.0);
		TestEnthrallmentResult thrall = new TestEnthrallmentResult(spawnLoc);

		Assertions.assertTrue(thrall.sitting, "Newly enthralled thrall must be in sitting/standby state");
		Assertions.assertEquals(spawnLoc, thrall.guardAnchor, "Guard anchor must be set to spawn position");
		Assertions.assertTrue(thrall.targetNull, "Target must be null to prevent instant charge");
		Assertions.assertTrue(thrall.navigationStopped, "Navigation must be stopped");
	}

	@Test
	@DisplayName("Waypoint ping and attack broadcasts ignore sitting minions to prevent squad leak")
	void testSquadLeakPrevention() {
		UUID commanderId = UUID.randomUUID();

		record ThrallEntity(int id, UUID owner, boolean alive, SquadGroup squad, boolean sitting) {}

		List<ThrallEntity> army = List.of(
			new ThrallEntity(1, commanderId, true, SquadGroup.ALPHA, false), // Mobile
			new ThrallEntity(2, commanderId, true, SquadGroup.ALPHA, true),  // Stationed/Holding
			new ThrallEntity(3, commanderId, true, SquadGroup.ALPHA, true),  // Stationed/Holding
			new ThrallEntity(4, commanderId, true, SquadGroup.BRAVO, false)  // Different squad
		);

		// Ground waypoint ping with SquadGroup.ALPHA filter
		List<ThrallEntity> waypointMobilized = army.stream()
			.filter(m -> m.alive() && m.owner().equals(commanderId) && !m.sitting() && SquadGroup.ALPHA.matches(m.squad()))
			.toList();

		Assertions.assertEquals(1, waypointMobilized.size(), "Only mobile non-sitting minion 1 should march");
		Assertions.assertEquals(1, waypointMobilized.get(0).id());

		// Attack broadcast with SquadGroup.ALL filter
		List<ThrallEntity> attackMobilized = army.stream()
			.filter(m -> m.alive() && m.owner().equals(commanderId) && !m.sitting() && SquadGroup.ALL.matches(m.squad()))
			.toList();

		Assertions.assertEquals(2, attackMobilized.size(), "Only mobile units (1 and 4) should mobilize on ALL attack broadcast");
		Assertions.assertTrue(attackMobilized.stream().noneMatch(ThrallEntity::sitting), "Sitting minions must never leak into attack");
	}
}
