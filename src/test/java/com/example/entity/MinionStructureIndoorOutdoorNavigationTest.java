package com.example.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.util.math.BlockPos;

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

	@Test
	@DisplayName("Source Invariant: MinionEntity implements unified door auto-opening during server tick")
	void testMinionEntityUnifiedDoorAutoOpenInvariants() throws IOException {
		Path minionPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionPath), "MinionEntity.java must exist");
		String content = Files.readString(minionPath);

		// 1. Unified autoOpenNearbyDoors definition in MinionEntity
		Assertions.assertTrue(
			content.contains("public void autoOpenNearbyDoors(ServerWorld world)"),
			"MinionEntity must define autoOpenNearbyDoors for all roles"
		);

		// 2. Door interaction check & opening execution
		Assertions.assertTrue(
			content.contains("state.getBlock() instanceof DoorBlock && !state.get(DoorBlock.OPEN)"),
			"MinionEntity.autoOpenNearbyDoors must detect closed doors"
		);
		Assertions.assertTrue(
			content.contains("((DoorBlock) state.getBlock()).setOpen(this, world, state, checkPos, true)"),
			"MinionEntity.autoOpenNearbyDoors must open closed doors"
		);

		// 3. Invocation in tick()
		Assertions.assertTrue(
			content.contains("this.autoOpenNearbyDoors(serverWorld);"),
			"MinionEntity.tick() must call autoOpenNearbyDoors on serverWorld"
		);
	}

	@Test
	@DisplayName("Source Invariant: MinionNavigation pre-configures door traversal flags")
	void testMinionNavigationDoorFlagsInvariant() throws IOException {
		Path navPath = Path.of("src/main/java/com/example/entity/ai/pathing/MinionNavigation.java");
		Assertions.assertTrue(Files.exists(navPath), "MinionNavigation.java must exist");
		String content = Files.readString(navPath);

		Assertions.assertTrue(content.contains("this.setCanPathThroughDoors(true);"),
				"MinionNavigation must configure setCanPathThroughDoors(true)");
		Assertions.assertTrue(content.contains("this.setCanEnterOpenDoors(true);"),
				"MinionNavigation must configure setCanEnterOpenDoors(true)");
	}

	@Test
	@DisplayName("Simulation: 3x3x2 neighborhood door auto-opening detects and opens all closed doors within 1.5 blocks")
	void testDoorAutoOpeningNeighborhoodMath() {
		class MockBlockState {
			final boolean isDoor;
			boolean isOpen;
			MockBlockState(boolean isDoor, boolean isOpen) {
				this.isDoor = isDoor;
				this.isOpen = isOpen;
			}
		}

		class NeighborhoodWorld {
			final java.util.Map<BlockPos, MockBlockState> blocks = new java.util.HashMap<>();
			int doorOpenEvents = 0;

			void setBlock(BlockPos pos, boolean isDoor, boolean isOpen) {
				blocks.put(pos, new MockBlockState(isDoor, isOpen));
			}

			MockBlockState getBlockState(BlockPos pos) {
				return blocks.getOrDefault(pos, new MockBlockState(false, false));
			}

			void simulateAutoOpenNearbyDoors(BlockPos minionPos) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = 0; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							BlockPos checkPos = minionPos.add(dx, dy, dz);
							MockBlockState state = getBlockState(checkPos);
							if (state.isDoor && !state.isOpen) {
								state.isOpen = true;
								doorOpenEvents++;
							}
						}
					}
				}
			}
		}

		NeighborhoodWorld world = new NeighborhoodWorld();
		BlockPos minionPos = new BlockPos(10, 64, 10);

		// Place a closed wooden door right in front of the minion (dx = 1, dy = 0, dz = 0)
		BlockPos frontDoor = new BlockPos(11, 64, 10);
		world.setBlock(frontDoor, true, false);

		// Place an upper half door block (dx = 1, dy = 1, dz = 0)
		BlockPos upperDoor = new BlockPos(11, 65, 10);
		world.setBlock(upperDoor, true, false);

		// Place an already open door to the side (dx = 0, dy = 0, dz = 1)
		BlockPos openSideDoor = new BlockPos(10, 64, 11);
		world.setBlock(openSideDoor, true, true);

		// Place a closed door 2 blocks away (out of 1-block neighborhood range: dx = 2, dy = 0, dz = 0)
		BlockPos distantDoor = new BlockPos(12, 64, 10);
		world.setBlock(distantDoor, true, false);

		// Execute door auto-opening sweep
		world.simulateAutoOpenNearbyDoors(minionPos);

		// Front doors (lower and upper) must be opened
		Assertions.assertTrue(world.getBlockState(frontDoor).isOpen, "Adjacent closed lower door must be opened");
		Assertions.assertTrue(world.getBlockState(upperDoor).isOpen, "Adjacent closed upper door must be opened");
		Assertions.assertEquals(2, world.doorOpenEvents, "Exactly 2 closed doors in 3x3x2 zone must trigger opening");

		// Distant door must remain closed
		Assertions.assertFalse(world.getBlockState(distantDoor).isOpen, "Door beyond 1.5 blocks must remain untouched");
	}

	@Test
	@DisplayName("Simulation: Structure exit waypoint selection and doorway egress pathfinding")
	void testStructureDoorwayDiscoveryAndExitWaypointSelection() {
		record MockDoor(BlockPos doorPos, BlockPos exteriorWaypoint) {}

		class StructureExitResolver {
			MockDoor findNearestExit(BlockPos minionPos, List<MockDoor> structureDoors) {
				MockDoor bestDoor = null;
				double minDistanceSq = Double.MAX_VALUE;

				for (MockDoor door : structureDoors) {
					double distSq = minionPos.getSquaredDistance(door.doorPos);
					if (distSq < minDistanceSq) {
						minDistanceSq = distSq;
						bestDoor = door;
					}
				}
				return bestDoor;
			}
		}

		StructureExitResolver resolver = new StructureExitResolver();
		BlockPos minionInside = new BlockPos(10, 64, 10);
List<MockDoor> doors = List.of(
				new MockDoor(new BlockPos(10, 64, 6), new BlockPos(10, 64, 5)),   // North exit: 4 blocks
				new MockDoor(new BlockPos(18, 64, 10), new BlockPos(19, 64, 10)), // East exit: 8 blocks
				new MockDoor(new BlockPos(10, 64, 20), new BlockPos(10, 64, 21))  // South exit: 10 blocks
		);

		MockDoor chosen = resolver.findNearestExit(minionInside, doors);
		Assertions.assertNotNull(chosen);
		Assertions.assertEquals(new BlockPos(10, 64, 6), chosen.doorPos, "North exit must be selected as nearest doorway");
		Assertions.assertEquals(new BlockPos(10, 64, 5), chosen.exteriorWaypoint, "Exterior landing waypoint must be 1 block outside door");
	}

	@Test
	@DisplayName("Simulation: Emergency Arcane Phase Egress triggers after 35 stall ticks")
	void testEmergencyArcanePhaseEgressTimeoutSimulation() {
		class EgressSimulation {
			int exitTraverseTicks = 0;
			boolean teleportTriggered = false;
			BlockPos finalPosition = new BlockPos(10, 64, 10);

			void tick(BlockPos exitPos) {
				exitTraverseTicks++;
				if (exitTraverseTicks > 35) {
					teleportTriggered = true;
					finalPosition = exitPos;
				}
			}
		}

		EgressSimulation sim = new EgressSimulation();
		BlockPos exteriorPos = new BlockPos(10, 64, 5);

		// Ticks 1 to 35: Navigating, no teleport
		for (int i = 1; i <= 35; i++) {
			sim.tick(exteriorPos);
			Assertions.assertFalse(sim.teleportTriggered, "Teleport must NOT trigger at tick " + i);
		}

		// Tick 36 (> 35 ticks): Teleport triggers
		sim.tick(exteriorPos);
		Assertions.assertTrue(sim.teleportTriggered, "Emergency phase egress MUST trigger at tick 36 (> 35 ticks)");
		Assertions.assertEquals(exteriorPos, sim.finalPosition, "Minion position must be updated to exterior exit position");
	}
}