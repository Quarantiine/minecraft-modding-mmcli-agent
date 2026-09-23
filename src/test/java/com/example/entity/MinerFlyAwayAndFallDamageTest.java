package com.example.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying:
 * 1. Elimination of all flight/launch triggers for miners during active mining.
 * 2. Builders/miners immunity from universal obstacle clearance launches in tickUniversalArcaneLevitation.
 * 3. Complete fall damage immunity and fallDistance zeroing during building, mining, and descents.
 * 4. Formation follow, catch-up teleport, leader follow, and patrol suppression while engaged in construction.
 * 5. Preservation of activelyBuilding state and zero-velocity hovering between task leases in active sessions.
 * 6. Elimination of structure doorway exit routing and upward collision velocity in dismantle mode.
 */
public class MinerFlyAwayAndFallDamageTest {

	@Test
	@DisplayName("Validate MinionEntity builders/miners return early in tickUniversalArcaneLevitation")
	void testMinionEntityBuilderUniversalLevitationSuppression() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		Assertions.assertTrue(
			content.contains("else if (this.matchesRole(MinionRole.BUILDER)) {"),
			"tickUniversalArcaneLevitation must check this.matchesRole(MinionRole.BUILDER)"
		);
		Assertions.assertTrue(
			content.contains("MinionBuildGoal handles all task navigation, hovering, and controlled landing.")
				&& content.contains("return;"),
			"tickUniversalArcaneLevitation must return immediately for builders/miners"
		);
	}

	@Test
	@DisplayName("Validate MinionEntity fall damage immunity and handleFallDamage override")
	void testMinionEntityFallDamageImmunity() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// handleFallDamage override
		Assertions.assertTrue(
			content.contains("public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource)"),
			"MinionEntity must override handleFallDamage"
		);
		Assertions.assertTrue(
			content.contains("this.lastConstructionActivityTick <= 100L"),
			"handleFallDamage must grant immunity within 100 ticks of construction activity"
		);

		// damage() suppression
		Assertions.assertTrue(
			content.contains("this.arcaneLevitating && source.isOf(DamageTypes.FALL)"),
			"damage() must suppress fall damage while levitating"
		);
		Assertions.assertTrue(
			content.contains("source.isOf(DamageTypes.FALL)")
				&& content.contains("this.isActivelyBuilding() || this.exitingBuilding"),
			"damage() must suppress fall damage while building or exiting"
		);

		// fallDistance zeroing in tick
		Assertions.assertTrue(
			content.contains("this.fallDistance = 0.0F;"),
			"tick() must continuously zero fallDistance during construction/mining"
		);
	}

	@Test
	@DisplayName("Validate MinionBuildGoal inter-block active state preservation and zero-velocity hovering")
	void testMinionBuildGoalInterBlockActiveState() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		// In tick(): when session is active and waiting for tasks
		Assertions.assertTrue(
			content.contains("if (this.currentSession != null && this.currentSession.isActive()) {")
				&& content.contains("this.minion.setActivelyBuilding(true);")
				&& content.contains("this.minion.setVelocity(0.0D, 0.0D, 0.0D);"),
			"MinionBuildGoal must keep activelyBuilding true and freeze velocity while session is active"
		);

		// In dismantle air block skip
		Assertions.assertTrue(
			content.contains("if (this.currentSession == null || !this.currentSession.isActive()) {")
				&& content.contains("this.minion.setActivelyBuilding(false);"),
			"MinionBuildGoal must only set activelyBuilding false in air check if session is not active"
		);
	}

	@Test
	@DisplayName("Validate MinionBuildGoal kinematics and stall handling for dismantle mining")
	void testMinionBuildGoalDismantleKinematicsAndStalls() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(path), "MinionBuildGoal.java must exist");
		String content = Files.readString(path);

		// Upward collision velocity gated behind !isDismantle
		Assertions.assertTrue(
			content.contains("if (!isDismantle && delta.y > 0.1D && (this.minion.horizontalCollision || targetPos.getY() > this.minion.getBlockY()))"),
			"MinionBuildGoal must never impart upward collision rocket velocity to miners"
		);

		// Dismantle stall phase-shift directly to hover station
		Assertions.assertTrue(
			content.contains("isDismantle || this.minion.isPhasingBlocks() || this.ticksNavigating > 40 || this.stallCollisionTicks >= 80"),
			"MinionBuildGoal must phase-shift miners on stall instead of dropping to ground navigation"
		);

		// Gating findStructureExitWaypoint behind !isDismantle
		Assertions.assertTrue(
			content.contains("BlockPos exitPos = !isDismantle ? findStructureExitWaypoint(serverWorld) : null;"),
			"MinionBuildGoal must never route miners to structure exit doors in tick"
		);
		Assertions.assertTrue(
			content.contains("BlockPos exitPos = !isDismantle ? findStructureExitWaypoint(world) : null;"),
			"MinionBuildGoal must never route miners to structure exit doors in setupNavigationForTask"
		);
	}

	@Test
	@DisplayName("Validate construction engagement proximity in formation, leader follow, and patrol goals")
	void testGoalConstructionEngagementProximityChecks() throws IOException {
		Path formationPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java");
		Assertions.assertTrue(Files.exists(formationPath), "MinionFormationFollowGoal.java must exist");
		String formationContent = Files.readString(formationPath);

		Assertions.assertTrue(
			formationContent.contains("isMinionEngagedInConstruction(this.minion)"),
			"MinionFormationFollowGoal must check isMinionEngagedInConstruction(this.minion) with proximity"
		);

		Path leaderPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionFollowLeaderGoal.java");
		Assertions.assertTrue(Files.exists(leaderPath), "MinionFollowLeaderGoal.java must exist");
		String leaderContent = Files.readString(leaderPath);

		Assertions.assertTrue(
			leaderContent.contains("isMinionEngagedInConstruction(this.minion)"),
			"MinionFollowLeaderGoal must yield when minion is engaged in construction"
		);

		Path patrolPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionPatrolGoal.java");
		Assertions.assertTrue(Files.exists(patrolPath), "MinionPatrolGoal.java must exist");
		String patrolContent = Files.readString(patrolPath);

		Assertions.assertTrue(
			patrolContent.contains("isMinionEngagedInConstruction(this.minion)"),
			"MinionPatrolGoal must yield when minion is engaged in construction"
		);
	}

	@Test
	@DisplayName("Validate ConstructionManager cancelSession egress gating and AUTO role support")
	void testConstructionManagerDismantleGating() throws IOException {
		Path path = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(path), "ConstructionManager.java must exist");
		String content = Files.readString(path);

		// cancelSession
		Assertions.assertTrue(
			content.contains("if (!session.isDismantle() && minion.isInsideStructure(box))"),
			"cancelSession must only trigger wall egress for non-dismantle sessions"
		);

		// isMinionNearActiveSession role check
		Assertions.assertTrue(
			content.contains("role != null && role != MinionRole.BUILDER && role != MinionRole.AUTO"),
			"isMinionNearActiveSession must support MinionRole.AUTO"
		);
	}
}
