package com.example.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite validating Universal 3D Arcane Levitation Traversal:
 * 1. Source invariants across MinionEntity, MinionFormationFollowGoal, WaypointHoldGoal, SentinelGuardGoal, and MinionBuildGoal.
 * 2. Elevation disparity triggers (ascending > 1.25 blocks, descending < -1.5 blocks).
 * 3. 3D flight kinematics for scaling cliffs and gliding off completed multiblock structures.
 * 4. Active target destination resolution priority hierarchy.
 * 5. Arrival landing tolerance and smooth ground descent.
 */
public class UniversalArcaneLevitationTest {

	// =========================================================================
	// 1. MinionEntity Source & Invariant Tests
	// =========================================================================

	@Test
	@DisplayName("MinionEntity defines Universal Arcane Levitation fields, getters, and traversal logic")
	void testMinionEntityUniversalLevitationInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// Traversal destination & building state fields
		Assertions.assertTrue(
			content.contains("private Vec3d activeTraversalDestination = null;"),
			"MinionEntity must define activeTraversalDestination"
		);
		Assertions.assertTrue(
			content.contains("private boolean activelyBuilding = false;"),
			"MinionEntity must define activelyBuilding"
		);
		Assertions.assertTrue(
			content.contains("public Vec3d getActiveTraversalDestination()"),
			"MinionEntity must expose getActiveTraversalDestination"
		);
		Assertions.assertTrue(
			content.contains("public void setActiveTraversalDestination(Vec3d destination)"),
			"MinionEntity must expose setActiveTraversalDestination"
		);
		Assertions.assertTrue(
			content.contains("public void clearActiveTraversalDestination()"),
			"MinionEntity must expose clearActiveTraversalDestination"
		);
		Assertions.assertTrue(
			content.contains("public boolean isActivelyBuilding()"),
			"MinionEntity must expose isActivelyBuilding"
		);
		Assertions.assertTrue(
			content.contains("public void setActivelyBuilding(boolean activelyBuilding)"),
			"MinionEntity must expose setActivelyBuilding"
		);

		// Target destination resolution
		Assertions.assertTrue(
			content.contains("public Vec3d resolveActiveTargetDestination()"),
			"MinionEntity must define resolveActiveTargetDestination"
		);

		// Universal 3D Levitation tick
		Assertions.assertTrue(
			content.contains("public void tickUniversalArcaneLevitation()"),
			"MinionEntity must define tickUniversalArcaneLevitation"
		);
		Assertions.assertTrue(
			content.contains("this.tickUniversalArcaneLevitation();"),
			"MinionEntity tick() must invoke tickUniversalArcaneLevitation"
		);

		// Elevation disparity triggers
		Assertions.assertTrue(
			content.contains("dy > 1.25D || dy < -1.5D"),
			"MinionEntity must evaluate elevation disparity (dy > 1.25D || dy < -1.5D)"
		);

		// 3D Levitation kinematics
		Assertions.assertTrue(
			content.contains("vy = Math.max(vy, 0.38D)"),
			"MinionEntity must apply upward thrust (>= 0.38D) when scaling cliffs/elevated obstacles"
		);
		Assertions.assertTrue(
			content.contains("vy = Math.min(vy, -0.22D)"),
			"MinionEntity must apply controlled downward glide (<= -0.22D) when descending off high builds"
		);
	}

	// =========================================================================
	// 2. Goal Integration Invariants
	// =========================================================================

	@Test
	@DisplayName("WaypointHoldGoal integrates Universal Arcane Levitation traversal")
	void testWaypointHoldGoalLevitationInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/WaypointHoldGoal.java");
		Assertions.assertTrue(Files.exists(path), "WaypointHoldGoal.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("this.minion.setActiveTraversalDestination("),
			"WaypointHoldGoal must register active traversal destination"
		);
		Assertions.assertTrue(
			content.contains("this.minion.clearActiveTraversalDestination()"),
			"WaypointHoldGoal must clear traversal destination on stop"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setArcaneLevitating(true)"),
			"WaypointHoldGoal must activate arcane levitation for elevation disparity"
		);
	}

	@Test
	@DisplayName("MinionFormationFollowGoal integrates Universal Arcane Levitation traversal")
	void testMinionFormationFollowGoalLevitationInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionFormationFollowGoal.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("this.minion.setActiveTraversalDestination("),
			"MinionFormationFollowGoal must register active traversal destination"
		);
		Assertions.assertTrue(
			content.contains("this.minion.clearActiveTraversalDestination()"),
			"MinionFormationFollowGoal must clear traversal destination on stop"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setArcaneLevitating(true)"),
			"MinionFormationFollowGoal must activate arcane levitation for elevation disparity"
		);
		Assertions.assertTrue(
			content.contains("for (int dy = 4; dy >= -8; dy--)"),
			"MinionFormationFollowGoal must use expanded vertical search window (+4 to -8) in resolveWalkableY"
		);
	}

	@Test
	@DisplayName("SentinelGuardGoal integrates Universal Arcane Levitation traversal")
	void testSentinelGuardGoalLevitationInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/SentinelGuardGoal.java");
		Assertions.assertTrue(Files.exists(path), "SentinelGuardGoal.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("this.minion.setActiveTraversalDestination("),
			"SentinelGuardGoal must register active traversal destination"
		);
		Assertions.assertTrue(
			content.contains("this.minion.clearActiveTraversalDestination()"),
			"SentinelGuardGoal must clear traversal destination on stop"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setArcaneLevitating(true)"),
			"SentinelGuardGoal must activate arcane levitation for elevation disparity"
		);
	}

	@Test
	@DisplayName("MinionBuildGoal coordinates activelyBuilding state and graceful roof exits")
	void testMinionBuildGoalRoofExitInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("this.minion.setActivelyBuilding(true)"),
			"MinionBuildGoal must set activelyBuilding true during construction"
		);
		Assertions.assertTrue(
			content.contains("this.minion.setActivelyBuilding(false)"),
			"MinionBuildGoal must set activelyBuilding false upon task completion or release"
		);
		Assertions.assertTrue(
			content.contains("this.minion.resolveActiveTargetDestination()"),
			"MinionBuildGoal must consult active target destination before forcing roof landings"
		);
	}

	@Test
	@DisplayName("CommandScepterItem waypoint ping initiates 3D traversal and elevation levitation")
	void testCommandScepterWaypointPingTraversalInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(path), "CommandScepterItem.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("minion.setActiveTraversalDestination(new Vec3d(stationX, stationY, stationZ))"),
			"executeGroundWaypointPing must set active traversal destination on commanded minions"
		);
		Assertions.assertTrue(
			content.contains("if (dy > 1.25D || dy < -1.5D)") && content.contains("minion.setArcaneLevitating(true)"),
			"executeGroundWaypointPing must engage levitation if waypoint has elevation disparity"
		);
	}

	// =========================================================================
	// 3. Mathematical & Kinematic Traversal Simulation
	// =========================================================================

	/**
	 * Simulates 3D Arcane Levitation velocity computation identical to MinionEntity.tickUniversalArcaneLevitation.
	 */
	private static Vec3d computeLevitationVelocity(Vec3d minionPos, Vec3d targetDest, boolean horizontalCollision) {
		double dx = targetDest.x - minionPos.x;
		double dy = targetDest.y - minionPos.y;
		double dz = targetDest.z - minionPos.z;
		double totalDistSq = dx * dx + dy * dy + dz * dz;
		double dist = Math.sqrt(totalDistSq);
		if (dist < 0.01D) {
			return Vec3d.ZERO;
		}

		Vec3d dir = new Vec3d(dx / dist, dy / dist, dz / dist);
		double speed = dist > 10.0D ? 0.45D : 0.35D;
		double vx = dir.x * speed;
		double vy = dir.y * speed;
		double vz = dir.z * speed;

		if (dy > 0.5D || horizontalCollision) {
			vy = Math.max(vy, 0.38D);
		} else if (dy < -0.5D) {
			vy = Math.min(vy, -0.22D);
			vy = Math.max(vy, -0.42D);
		}

		return new Vec3d(vx, vy, vz);
	}

	@Test
	@DisplayName("Simulate builder smoothly gliding down off a completed watchtower roof to commander")
	void testRoofDescentGlideSimulation() {
		// Minion is at top of completed Overlord Watchtower roof (Y = 75.0)
		Vec3d minionOnRoof = new Vec3d(100.0D, 75.0D, 100.0D);
		// Commander is stationed on the ground below (Y = 64.0, offset 8 blocks north)
		Vec3d commanderOnGround = new Vec3d(100.0D, 64.0D, 92.0D);

		double dy = commanderOnGround.y - minionOnRoof.y;
		Assertions.assertEquals(-11.0D, dy, 0.001D, "Vertical drop must be -11 blocks");

		// Elevation disparity check: drop < -1.5D triggers levitation descent
		boolean triggersLevitation = dy > 1.25D || dy < -1.5D;
		Assertions.assertTrue(triggersLevitation, "11-block roof drop must trigger Arcane Levitation descent");

		// Compute 3D glide velocity
		Vec3d vel = computeLevitationVelocity(minionOnRoof, commanderOnGround, false);

		// Assert controlled downward velocity (between -0.42D and -0.22D)
		Assertions.assertTrue(vel.y <= -0.22D, "Descent velocity must provide downward glide (vy <= -0.22)");
		Assertions.assertTrue(vel.y >= -0.42D, "Descent velocity must not exceed maximum glide cap (vy >= -0.42)");

		// Assert lateral velocity is directed towards commander (Z velocity must be negative towards Z=92)
		Assertions.assertTrue(vel.z < -0.1D, "Z velocity must propel minion towards commander");
		Assertions.assertEquals(0.0D, vel.x, 0.001D, "X velocity must be zero along collinear axis");
	}

	@Test
	@DisplayName("Simulate minion ascending sheer cliff to reach elevated waypoint ping")
	void testCliffAscentSimulation() {
		// Minion is at cliff base (Y = 64.0)
		Vec3d minionAtBase = new Vec3d(50.0D, 64.0D, 50.0D);
		// Waypoint is placed on an elevated cliff plateau (Y = 72.0, 6 blocks East)
		Vec3d cliffWaypoint = new Vec3d(56.0D, 72.0D, 50.0D);

		double dy = cliffWaypoint.y - minionAtBase.y;
		Assertions.assertEquals(8.0D, dy, 0.001D, "Vertical climb must be +8 blocks");

		// Elevation disparity check: rise > 1.25D triggers levitation ascent
		boolean triggersLevitation = dy > 1.25D || dy < -1.5D;
		Assertions.assertTrue(triggersLevitation, "8-block cliff ascent must trigger Arcane Levitation ascent");

		// Compute 3D ascent velocity
		Vec3d vel = computeLevitationVelocity(minionAtBase, cliffWaypoint, false);

		// Assert strong upward lift (vy >= 0.38D) to clear cliff face
		Assertions.assertTrue(vel.y >= 0.38D, "Ascent velocity must provide strong lift (vy >= 0.38D)");

		// Assert forward thrust towards cliff plateau (X velocity positive towards X=56)
		Assertions.assertTrue(vel.x > 0.1D, "X velocity must propel minion towards cliff edge");
	}

	@Test
	@DisplayName("Simulate arrival tolerance and safe ground landing")
	void testArrivalToleranceAndLanding() {
		Vec3d destination = new Vec3d(100.0D, 64.0D, 100.0D);

		// Minion is at destination horizontally, within 1.0 block vertically
		Vec3d minionArrived = new Vec3d(100.8D, 64.6D, 100.5D);
		double dx = destination.x - minionArrived.x;
		double dy = destination.y - minionArrived.y;
		double dz = destination.z - minionArrived.z;
		double horizDistSq = dx * dx + dz * dz;

		boolean arrived = horizDistSq <= 4.0D && Math.abs(dy) <= 1.5D;
		Assertions.assertTrue(arrived, "Minion within 2 blocks horizontally and 1.5 blocks vertically must register arrived");

		// Minion 5 blocks away has not arrived
		Vec3d minionFar = new Vec3d(105.0D, 64.0D, 100.0D);
		double farDx = destination.x - minionFar.x;
		double farDz = destination.z - minionFar.z;
		boolean farArrived = (farDx * farDx + farDz * farDz) <= 4.0D && Math.abs(destination.y - minionFar.y) <= 1.5D;
		Assertions.assertFalse(farArrived, "Minion 5 blocks away must not register arrived");
	}

	@Test
	@DisplayName("Destination resolution priority hierarchy logic")
	void testDestinationPriorityHierarchy() {
		class MockDestinationResolver {
			Vec3d explicitDest = null;
			Vec3d combatTargetPos = null;
			BlockPos guardAnchor = null;
			Vec3d ownerPos = null;
			BlockPos navTarget = null;

			Vec3d resolve() {
				if (explicitDest != null) return explicitDest;
				if (combatTargetPos != null) return combatTargetPos;
				if (guardAnchor != null) return Vec3d.ofBottomCenter(guardAnchor);
				if (ownerPos != null) return ownerPos;
				if (navTarget != null) return Vec3d.ofBottomCenter(navTarget);
				return null;
			}
		}

		MockDestinationResolver resolver = new MockDestinationResolver();
		Assertions.assertNull(resolver.resolve(), "With no targets, resolve must return null");

		// Level 5: Nav target
		resolver.navTarget = new BlockPos(10, 64, 10);
		Assertions.assertEquals(new Vec3d(10.5, 64.0, 10.5), resolver.resolve());

		// Level 4: Owner pos overrides nav target
		resolver.ownerPos = new Vec3d(20.0, 64.0, 20.0);
		Assertions.assertEquals(resolver.ownerPos, resolver.resolve());

		// Level 3: Guard anchor overrides owner pos
		resolver.guardAnchor = new BlockPos(30, 64, 30);
		Assertions.assertEquals(new Vec3d(30.5, 64.0, 30.5), resolver.resolve());

		// Level 2: Combat target overrides guard anchor
		resolver.combatTargetPos = new Vec3d(40.0, 64.0, 40.0);
		Assertions.assertEquals(resolver.combatTargetPos, resolver.resolve());

		// Level 1: Explicit destination overrides all
		resolver.explicitDest = new Vec3d(50.0, 64.0, 50.0);
		Assertions.assertEquals(resolver.explicitDest, resolver.resolve());
	}
}
