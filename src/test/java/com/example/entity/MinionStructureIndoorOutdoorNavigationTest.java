package com.example.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating builder minion indoor/outdoor structure traversal, doorway pathfinding,
 * auto-opening of closed doors, exterior exit waypoint resolution, and emergency arcane phase egress.
 */
public class MinionStructureIndoorOutdoorNavigationTest {

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal defines findStructureExitWaypoint and autoOpenNearbyDoors")
	void testStructureExitNavigationMethodsExist() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath), "MinionBuildGoal.java must exist");
		String content = Files.readString(buildGoalPath);

		// 1. Structure exit waypoint method
		Assertions.assertTrue(
			content.contains("public BlockPos findStructureExitWaypoint(ServerWorld world)"),
			"MinionBuildGoal must define findStructureExitWaypoint"
		);

		// 2. Auto-open nearby doors
		Assertions.assertTrue(
			content.contains("public void autoOpenNearbyDoors(ServerWorld world)"),
			"MinionBuildGoal must define autoOpenNearbyDoors"
		);

		// 3. Door interaction checks
		Assertions.assertTrue(
			content.contains("state.getBlock() instanceof DoorBlock && !state.get(DoorBlock.OPEN)"),
			"autoOpenNearbyDoors must detect and open closed doors"
		);
		Assertions.assertTrue(
			content.contains("((DoorBlock) state.getBlock()).setOpen(this.minion, world, state, checkPos, true)"),
			"autoOpenNearbyDoors must trigger setOpen on closed doors"
		);
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal navigates to exterior exit when indoor minion targets roof")
	void testIndoorToRoofExitNavigationInvariants() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String content = Files.readString(buildGoalPath);

		// Exit waypoint resolution when direct path/LOS fails
		Assertions.assertTrue(
			content.contains("BlockPos exitPos = findStructureExitWaypoint(serverWorld)"),
			"MinionBuildGoal must resolve structure exit waypoint when path/LOS fails"
		);

		// Navigation towards exit
		Assertions.assertTrue(
			content.contains("this.minion.getNavigation().startMovingTo(\n\t\t\t\t\t\t\t\texitPos.getX() + 0.5D"),
			"MinionBuildGoal must route minion towards exit waypoint"
		);

		// Levitation engagement once outside
		Assertions.assertTrue(
			content.contains("boolean reachedOutside = distToExitSq <= 3.0D || (!hasCeilingAboveMinion(serverWorld, 2) && hasLineOfSightToStation(serverWorld, candidateStation))"),
			"MinionBuildGoal must detect reaching outside or gaining LOS"
		);
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal glides to entrance doorstep when outdoor minion targets interior")
	void testOutdoorToIndoorDescentInvariants() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String content = Files.readString(buildGoalPath);

		// Exterior minion gliding down to entrance doorstep
		Assertions.assertTrue(
			content.contains("if (exitPos != null && this.minion.getY() > exitPos.getY() + 1.5D)"),
			"MinionBuildGoal must check if elevated minion needs to descend to entrance"
		);
		Assertions.assertTrue(
			content.contains("this.hoverStationVec = Vec3d.ofBottomCenter(exitPos)"),
			"MinionBuildGoal must glide towards entrance doorstep"
		);
	}

	@Test
	@DisplayName("Source Invariant: MinionBuildGoal includes Emergency Arcane Phase Egress for sealed rooms")
	void testEmergencyArcanePhaseEgressInvariants() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String content = Files.readString(buildGoalPath);

		// Timeout threshold for egress
		Assertions.assertTrue(
			content.contains("if (this.exitTraverseTicks > 35)"),
			"MinionBuildGoal must trigger emergency phase after 35 exit traverse ticks"
		);

		// Portal particles and teleport
		Assertions.assertTrue(
			content.contains("ParticleTypes.PORTAL"),
			"MinionBuildGoal must spawn portal particles during arcane phase"
		);
		Assertions.assertTrue(
			content.contains("SoundEvents.ENTITY_ENDERMAN_TELEPORT"),
			"MinionBuildGoal must play enderman teleport sound during arcane phase"
		);
		Assertions.assertTrue(
			content.contains("this.minion.requestTeleport(exitPos.getX() + 0.5D, exitPos.getY(), exitPos.getZ() + 0.5D)"),
			"MinionBuildGoal must teleport minion outside to exitPos"
		);
	}
}
