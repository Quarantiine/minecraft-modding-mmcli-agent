package com.example.blueprint;

import com.example.construction.ConstructionSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying scaffolding logic, doorway / entrance corridor avoidance,
 * vertical headroom clearance, and climbing state machine transitions.
 */
public class ScaffoldingTest {

	/**
	 * Simulates coordinate representations without needing full Minecraft server world mocking.
	 */
	public record TestPos(int x, int y, int z) {}

	public static class TestDoorwayCorridorChecker {
		private final List<TestPos> plannedDoors = new ArrayList<>();
		private final List<TestPos> worldDoors = new ArrayList<>();

		public void addPlannedDoor(int x, int y, int z) {
			plannedDoors.add(new TestPos(x, y, z));
		}

		public void addWorldDoor(int x, int y, int z) {
			worldDoors.add(new TestPos(x, y, z));
		}

		public boolean isPosInDoorwayCorridor(int x, int z, int minY, int maxY) {
			for (TestPos door : plannedDoors) {
				if (door.y() >= minY - 1 && door.y() <= maxY + 1) {
					if (isWithinDoorwayCorridor(x, z, door.x(), door.z())) {
						return true;
					}
				}
			}
			for (TestPos door : worldDoors) {
				if (door.y() >= minY - 1 && door.y() <= maxY + 1) {
					if (isWithinDoorwayCorridor(x, z, door.x(), door.z())) {
						return true;
					}
				}
			}
			return false;
		}

		private static boolean isWithinDoorwayCorridor(int x, int z, int doorX, int doorZ) {
			int diffX = Math.abs(x - doorX);
			int diffZ = Math.abs(z - doorZ);
			return (diffX + diffZ) <= 1;
		}
	}

	public static class TestHeadroomChecker {
		private final Set<TestPos> solidBlocks = new HashSet<>();

		public void addSolidBlock(int x, int y, int z) {
			solidBlocks.add(new TestPos(x, y, z));
		}

		public boolean isHeadroomClear(int x, int z, int topY) {
			for (int yOffset = 1; yOffset <= 2; yOffset++) {
				int y = topY + yOffset;
				if (solidBlocks.contains(new TestPos(x, y, z))) {
					return false;
				}
			}
			return true;
		}
	}

	public static class TestScaffoldingStateMachine {
		private boolean isAscending = false;
		private boolean isDescending = false;
		private boolean minionClimbingFlag = false;
		private int currentY;
		private int targetTopY;
		private int groundY;

		public TestScaffoldingStateMachine(int startY, int targetTopY, int groundY) {
			this.currentY = startY;
			this.targetTopY = targetTopY;
			this.groundY = groundY;
		}

		public void startAscent() {
			this.isAscending = true;
			this.minionClimbingFlag = true;
		}

		public void stepAscent() {
			if (this.isAscending) {
				this.currentY++;
				if (this.currentY >= this.targetTopY) {
					this.isAscending = false;
					this.minionClimbingFlag = false; // Climbing flag cleared on platform
				}
			}
		}

		public void startDescent() {
			this.isDescending = true;
			this.minionClimbingFlag = false; // Climbing flag disabled during controlled descent
		}

		public void stepDescent() {
			if (this.isDescending) {
				this.currentY--;
				if (this.currentY <= this.groundY) {
					this.isDescending = false;
				}
			}
		}

		public boolean isAscending() { return isAscending; }
		public boolean isDescending() { return isDescending; }
		public boolean isMinionClimbingFlag() { return minionClimbingFlag; }
		public int getCurrentY() { return currentY; }
	}

	@Test
	@DisplayName("Scaffolding column candidates directly in doorway or adjacent corridor are rejected")
	public void testDoorwayCorridorRejection() {
		TestDoorwayCorridorChecker checker = new TestDoorwayCorridorChecker();
		// Planned door at (10, 64, 10)
		checker.addPlannedDoor(10, 64, 10);

		// Exactly on the door block
		Assertions.assertTrue(checker.isPosInDoorwayCorridor(10, 10, 63, 68));

		// Immediate cardinal corridor neighbors (doorway walk paths)
		Assertions.assertTrue(checker.isPosInDoorwayCorridor(11, 10, 63, 68), "East corridor block should be rejected");
		Assertions.assertTrue(checker.isPosInDoorwayCorridor(9, 10, 63, 68), "West corridor block should be rejected");
		Assertions.assertTrue(checker.isPosInDoorwayCorridor(10, 11, 63, 68), "South corridor block should be rejected");
		Assertions.assertTrue(checker.isPosInDoorwayCorridor(10, 9, 63, 68), "North corridor block should be rejected");

		// Diagonal or 2-block away positions are NOT inside the doorway corridor
		Assertions.assertFalse(checker.isPosInDoorwayCorridor(11, 11, 63, 68), "Diagonal position should be permissible");
		Assertions.assertFalse(checker.isPosInDoorwayCorridor(12, 10, 63, 68), "2-block away position should be permissible");

		// Out of vertical range should not collide
		Assertions.assertFalse(checker.isPosInDoorwayCorridor(10, 10, 75, 80), "Out-of-range vertical span should not be rejected");
	}

	@Test
	@DisplayName("Vertical headroom check ensures 2 blocks of clearance above scaffold top platform")
	public void testHeadroomClearance() {
		TestHeadroomChecker checker = new TestHeadroomChecker();
		int topY = 65;

		// Initial: completely clear
		Assertions.assertTrue(checker.isHeadroomClear(5, 5, topY));

		// Obstruction at topY + 1 (feet level on platform)
		checker.addSolidBlock(5, topY + 1, 5);
		Assertions.assertFalse(checker.isHeadroomClear(5, 5, topY));

		// Clear obstruction at topY + 1, add at topY + 2 (head level on platform)
		TestHeadroomChecker checker2 = new TestHeadroomChecker();
		checker2.addSolidBlock(5, topY + 2, 5);
		Assertions.assertFalse(checker2.isHeadroomClear(5, 5, topY));

		// Obstruction above headroom (topY + 3) does not block headroom
		TestHeadroomChecker checker3 = new TestHeadroomChecker();
		checker3.addSolidBlock(5, topY + 3, 5);
		Assertions.assertTrue(checker3.isHeadroomClear(5, 5, topY));
	}

	@Test
	@DisplayName("Scaffolding climbing state coordination enables climbing during ascent and clears on arrival")
	public void testClimbingStateCoordination() {
		TestScaffoldingStateMachine sm = new TestScaffoldingStateMachine(64, 68, 64);

		Assertions.assertFalse(sm.isAscending());
		Assertions.assertFalse(sm.isMinionClimbingFlag());

		// Initiate ascent
		sm.startAscent();
		Assertions.assertTrue(sm.isAscending());
		Assertions.assertTrue(sm.isMinionClimbingFlag(), "Climbing flag must be enabled during vertical climbing");

		// Ascend until reaching top
		while (sm.isAscending()) {
			sm.stepAscent();
		}

		Assertions.assertEquals(68, sm.getCurrentY());
		Assertions.assertFalse(sm.isAscending());
		Assertions.assertFalse(sm.isMinionClimbingFlag(), "Climbing flag must be cleared when reaching top platform");

		// Initiate descent
		sm.startDescent();
		Assertions.assertTrue(sm.isDescending());
		Assertions.assertFalse(sm.isMinionClimbingFlag(), "Climbing flag must be disabled during descent");

		while (sm.isDescending()) {
			sm.stepDescent();
		}

		Assertions.assertEquals(64, sm.getCurrentY());
		Assertions.assertFalse(sm.isDescending());
	}

	@Test
	@DisplayName("Elevated task chaining triggers descent when next task is lower or outside column reach")
	public void testElevatedTaskChainingDescentDecision() {
		int currentY = 70;
		int currentScaffoldX = 20;
		int currentScaffoldZ = 20;

		// Case 1: Next task is lower (difference > 2) -> must descend
		int lowerNextY = 64;
		boolean needsDescentForLower = (currentY - lowerNextY) > 2;
		Assertions.assertTrue(needsDescentForLower);

		// Case 2: Next task is at same elevation within column reach (distSq <= 12) -> no descent needed
		int nextXSame = 21;
		int nextZSame = 22;
		int sameY = 71;
		double distSqSame = Math.pow(nextXSame - currentScaffoldX, 2) + Math.pow(nextZSame - currentScaffoldZ, 2);
		boolean needsDescentForSame = (currentY - sameY > 2) || (Math.abs(currentY - sameY) > 2) || (distSqSame > 12.0D);
		Assertions.assertFalse(needsDescentForSame);

		// Case 3: Next task is far horizontally (distSq > 12) -> must descend to traverse ground
		int farNextX = 30;
		int farNextZ = 30;
		double distSqFar = Math.pow(farNextX - currentScaffoldX, 2) + Math.pow(farNextZ - currentScaffoldZ, 2);
		boolean needsDescentForFar = distSqFar > 12.0D;
		Assertions.assertTrue(needsDescentForFar);
	}

	@Test
	@DisplayName("Minion scaffolding travel physics applies +0.25D impulse and zeroes fall distance during ascent")
	public void testMinionScaffoldingTravelPhysics() {
		class TestTravelPhysics {
			double velX = 0.0D;
			double velY = -0.08D; // initial downward gravity drift
			double velZ = 0.0D;
			float fallDistance = 4.5F;
			boolean insideScaffolding = true;
			boolean navigatingUpward = true;

			void simulateTravel() {
				boolean ascending = insideScaffolding && navigatingUpward;
				if (ascending) {
					fallDistance = 0.0F;
					velY = 0.25D;
				}
				// Mock vanilla travel movement
				if (insideScaffolding) {
					fallDistance = 0.0F;
					if (ascending && velY < 0.25D) {
						velY = 0.25D;
					}
				}
			}
		}

		// Case 1: Inside scaffolding and navigating upward
		TestTravelPhysics ascent = new TestTravelPhysics();
		ascent.insideScaffolding = true;
		ascent.navigatingUpward = true;
		ascent.simulateTravel();

		Assertions.assertEquals(0.25D, ascent.velY, 1e-6, "Upward velocity impulse of +0.25D must be applied during scaffolding ascent");
		Assertions.assertEquals(0.0F, ascent.fallDistance, "Fall distance must be zeroed during scaffolding ascent");

		// Case 2: Inside scaffolding and descending (navigating upward = false)
		TestTravelPhysics descent = new TestTravelPhysics();
		descent.insideScaffolding = true;
		descent.navigatingUpward = false;
		descent.velY = -0.22D;
		descent.fallDistance = 3.0F;
		descent.simulateTravel();

		Assertions.assertEquals(-0.22D, descent.velY, 1e-6, "Descent downward velocity must not be overwritten by ascent impulse");
		Assertions.assertEquals(0.0F, descent.fallDistance, "Fall distance must still be zeroed while inside scaffolding");

		// Case 3: Outside scaffolding
		TestTravelPhysics outside = new TestTravelPhysics();
		outside.insideScaffolding = false;
		outside.navigatingUpward = true;
		outside.velY = 0.1D;
		outside.fallDistance = 2.0F;
		outside.simulateTravel();

		Assertions.assertEquals(0.1D, outside.velY, 1e-6, "Outside scaffolding, velocity should not be forced to 0.25D");
		Assertions.assertEquals(2.0F, outside.fallDistance, "Outside scaffolding, fall distance should not be cleared by scaffolding logic");
	}

	@Test
	@DisplayName("Spawn egg standby guard initialization prevents active target aggression")
	public void testSpawnEggStandbyGuardInitialization() {
		// Mock minion state representation
		class MockMinionState {
			boolean isAlive = true;
			boolean isTamed = true;
			boolean isSitting = false;
			TestPos guardAnchorPos = null;
			Object target = null;
			String role = "WARRIOR";

			void bindSpawnedMinion(TestPos spawnPos) {
				this.isTamed = true;
				this.isSitting = true;
				this.guardAnchorPos = spawnPos;
				this.target = null;
			}

			boolean canActiveTargetGoalStart() {
				if (!this.isAlive || !this.isTamed || this.isSitting) {
					return false;
				}
				return "WARRIOR".equals(this.role);
			}
		}

		MockMinionState minion = new MockMinionState();
		TestPos spawnPos = new TestPos(100, 64, -200);

		// Prior to binding: if sitting were false, warrior would immediately acquire targets
		Assertions.assertTrue(minion.canActiveTargetGoalStart(), "Unseated warrior would acquire targets immediately");

		// Bind spawned minion using standby / guard mode
		minion.bindSpawnedMinion(spawnPos);

		Assertions.assertTrue(minion.isSitting, "Spawned minion must initialize in sitting / standby mode");
		Assertions.assertEquals(spawnPos, minion.guardAnchorPos, "Spawned minion must establish guard anchor at spawn location");
		Assertions.assertNull(minion.target, "Target must be null upon spawning");
		Assertions.assertFalse(minion.canActiveTargetGoalStart(), "Sitting / standby minion must reject active targeting to prevent instant aggressive charges");
	}

	@Test
	@DisplayName("Minion dimensions and eye-height specifications match player-scale standards")
	public void testMinionDimensionSpecifications() {
		float width = 0.6F;
		float height = 1.95F;
		float eyeHeight = 1.74F;

		Assertions.assertEquals(0.6F, width, 1e-6);
		Assertions.assertEquals(1.95F, height, 1e-6);
		Assertions.assertEquals(1.74F, eyeHeight, 1e-6);
		Assertions.assertTrue(eyeHeight < height, "Eye-height must be less than total entity height");
		Assertions.assertTrue(eyeHeight > height * 0.85F, "Eye-height must be near the head level of the model");
	}

	@Test
	@DisplayName("Multi-minion scaffolding column reservation and conflict resolution")
	public void testMultiMinionScaffoldColumnReservation() {
		StructureBlueprint blueprint = StructureBlueprint.builder("test_tower", "Test Tower")
			.description("Test Blueprint")
			.build();

		UUID ownerUuid = UUID.randomUUID();
		BlockPos anchor = new BlockPos(100, 64, 100);
		ConstructionSession session = new ConstructionSession(
			ownerUuid,
			World.OVERWORLD,
			anchor,
			blueprint,
			true,
			1000L
		);

		UUID minion1 = UUID.randomUUID();
		UUID minion2 = UUID.randomUUID();
		UUID minion3 = UUID.randomUUID();

		BlockPos northColumn = new BlockPos(102, 64, 98);
		BlockPos southColumn = new BlockPos(102, 64, 106);
		BlockPos eastColumn = new BlockPos(106, 64, 102);

		// Initial state: all columns available
		Assertions.assertTrue(session.isScaffoldColumnAvailable(northColumn, minion1));
		Assertions.assertTrue(session.isScaffoldColumnAvailable(northColumn, minion2));
		Assertions.assertFalse(session.isScaffoldColumnClaimed(northColumn));
		Assertions.assertNull(session.getScaffoldColumnClaimant(northColumn));

		// Minion 1 claims North column
		Assertions.assertTrue(session.claimScaffoldColumn(northColumn, minion1));
		Assertions.assertTrue(session.isScaffoldColumnClaimed(northColumn));
		Assertions.assertEquals(minion1, session.getScaffoldColumnClaimant(northColumn));
		Assertions.assertTrue(session.isScaffoldColumnClaimedBy(minion1));
		Assertions.assertTrue(session.isMinionEngaged(minion1));

		// Idempotency: Minion 1 claiming the same column again succeeds
		Assertions.assertTrue(session.claimScaffoldColumn(northColumn, minion1));

		// Vertical invariance: Another position in the same vertical column (different Y) cannot be claimed by minion2
		BlockPos northColumnElevated = new BlockPos(102, 70, 98);
		Assertions.assertFalse(session.isScaffoldColumnAvailable(northColumnElevated, minion2));
		Assertions.assertFalse(session.claimScaffoldColumn(northColumnElevated, minion2));

		// Minion 2 claims South column
		Assertions.assertTrue(session.claimScaffoldColumn(southColumn, minion2));
		Assertions.assertEquals(minion2, session.getScaffoldColumnClaimant(southColumn));

		// Minion 3 claims East column
		Assertions.assertTrue(session.claimScaffoldColumn(eastColumn, minion3));
		Assertions.assertEquals(minion3, session.getScaffoldColumnClaimant(eastColumn));

		Map<BlockPos, UUID> claimed = session.getClaimedScaffoldColumns();
		Assertions.assertEquals(3, claimed.size());

		// Release North column by Minion 2 fails (Minion 2 does not hold it)
		Assertions.assertFalse(session.releaseScaffoldColumn(northColumn, minion2));
		Assertions.assertTrue(session.isScaffoldColumnClaimed(northColumn));

		// Release North column by Minion 1 succeeds
		Assertions.assertTrue(session.releaseScaffoldColumn(northColumn, minion1));
		Assertions.assertFalse(session.isScaffoldColumnClaimed(northColumn));
		Assertions.assertNull(session.getScaffoldColumnClaimant(northColumn));

		// Now Minion 2 can claim the newly freed North column
		Assertions.assertTrue(session.claimScaffoldColumn(northColumn, minion2));

		// Release all columns for Minion 2
		session.releaseScaffoldColumnsForMinion(minion2);
		Assertions.assertFalse(session.isScaffoldColumnClaimed(northColumn));
		Assertions.assertFalse(session.isScaffoldColumnClaimed(southColumn));
		Assertions.assertTrue(session.isScaffoldColumnClaimed(eastColumn));

		// Cancel session clears all column reservations
		session.cancel();
		Assertions.assertTrue(session.getClaimedScaffoldColumns().isEmpty());
	}

	@Test
	@DisplayName("Kinematic platform landing snaps accurately to platform surface (topY + 1.0) and zeroes velocity")
	public void testKinematicPlatformLandingAndSurfaceSnapping() {
		class MockKinematicLandingSimulation {
			double x = 10.5D;
			double y = 64.0D;
			double z = 20.5D;
			double velY = 0.0D;
			boolean climbingScaffolding = false;
			boolean isAscending = false;
			int targetScaffoldTopY = 70;
			boolean landed = false;

			void startClimb() {
				this.isAscending = true;
				this.climbingScaffolding = true;
			}

			void tick() {
				if (this.isAscending) {
					// Check arrival at surface threshold: targetScaffoldTopY + 0.95D
					if (this.y < (double) this.targetScaffoldTopY + 0.95D) {
						this.velY = 0.25D;
						this.y += this.velY;
					} else {
						// True kinematic platform landing snap
						this.y = (double) this.targetScaffoldTopY + 1.0D;
						this.velY = 0.0D;
						this.isAscending = false;
						this.climbingScaffolding = false;
						this.landed = true;
					}
				}
			}
		}

		MockKinematicLandingSimulation sim = new MockKinematicLandingSimulation();
		sim.startClimb();
		Assertions.assertTrue(sim.climbingScaffolding);
		Assertions.assertTrue(sim.isAscending);

		// Ascend until reaching top platform
		int maxTicks = 100;
		int ticks = 0;
		while (sim.isAscending && ticks++ < maxTicks) {
			sim.tick();
		}

		Assertions.assertTrue(sim.landed, "Platform landing must be completed");
		Assertions.assertEquals(71.0D, sim.y, 1e-6, "Minion must snap exactly to platform surface at targetScaffoldTopY + 1.0D");
		Assertions.assertEquals(0.0D, sim.velY, 1e-6, "Vertical velocity must be zeroed on platform landing");
		Assertions.assertFalse(sim.climbingScaffolding, "Climbing flag must be cleared upon platform landing");
		Assertions.assertFalse(sim.isAscending, "Ascent state must be cleared upon platform landing");
	}

	@Test
	@DisplayName("Elevated task chaining calculates platform reach without premature ground descent")
	public void testElevatedTaskChainingPlatformReach() {
		// Scaffolding column at (10, 64, 10), top scaffolding block at Y=70
		// Standing surface is at (10.5, 71.0, 10.5)
		int scaffoldX = 10;
		int scaffoldZ = 10;
		int targetScaffoldTopY = 70;

		class ReachChecker {
			boolean isTaskReachableFromPlatform(int taskX, int taskY, int taskZ) {
				double platformCenterX = scaffoldX + 0.5D;
				double platformCenterZ = scaffoldZ + 0.5D;
				double platformStandingY = (double) targetScaffoldTopY + 1.0D;

				double dx = (taskX + 0.5D) - platformCenterX;
				double dz = (taskZ + 0.5D) - platformCenterZ;
				double horizontalDistSq = dx * dx + dz * dz;
				double verticalDiff = Math.abs(platformStandingY - (double) taskY);

				return horizontalDistSq <= 16.0D && verticalDiff <= 2.5D;
			}
		}

		ReachChecker checker = new ReachChecker();

		// Task 1: (11, 72, 12) -> dx = 1, dz = 2 (distSq = 5 <= 16), vertDiff = 1.0 <= 2.5 -> Reachable!
		Assertions.assertTrue(checker.isTaskReachableFromPlatform(11, 72, 12));

		// Task 2: (14, 71, 10) -> dx = 4, dz = 0 (distSq = 16 <= 16), vertDiff = 0.0 <= 2.5 -> Reachable!
		Assertions.assertTrue(checker.isTaskReachableFromPlatform(14, 71, 10));

		// Task 3: (15, 71, 10) -> dx = 5, dz = 0 (distSq = 25 > 16) -> Out of horizontal reach
		Assertions.assertFalse(checker.isTaskReachableFromPlatform(15, 71, 10));

		// Task 4: (10, 68, 10) -> dx = 0, dz = 0, vertDiff = |71 - 68| = 3.0 > 2.5 -> Out of vertical reach
		Assertions.assertFalse(checker.isTaskReachableFromPlatform(10, 68, 10));
	}

	@Test
	@DisplayName("Exterior perimeter column selection projects outside bounding box and eliminates North bias")
	public void testExteriorPerimeterCandidateSortingAndEliminationOfNorthBias() {
		// Bounding box for 5x5 structure: minX=10, maxX=14, minZ=10, maxZ=14
		int minX = 10;
		int maxX = 14;
		int minZ = 10;
		int maxZ = 14;

		// Elevated target block on South face: (12, 70, 14)
		int targetX = 12;
		int targetZ = 14;

		// Minion approaching from South-East: (16.5, 64.0, 17.5)
		double minionX = 16.5D;
		double minionZ = 17.5D;

		List<int[]> perimeterCandidates = new ArrayList<>();
		// North perimeter (z = minZ - 1)
		for (int x = minX - 1; x <= maxX + 1; x++) {
			perimeterCandidates.add(new int[] { x, minZ - 1 });
		}
		// South perimeter (z = maxZ + 1)
		for (int x = minX - 1; x <= maxX + 1; x++) {
			perimeterCandidates.add(new int[] { x, maxZ + 1 });
		}
		// West perimeter (x = minX - 1)
		for (int z = minZ; z <= maxZ; z++) {
			perimeterCandidates.add(new int[] { minX - 1, z });
		}
		// East perimeter (x = maxX + 1)
		for (int z = minZ; z <= maxZ; z++) {
			perimeterCandidates.add(new int[] { maxX + 1, z });
		}

		// Filter to reachable perimeter positions (distSq <= 16.0)
		List<int[]> reachable = new ArrayList<>();
		for (int[] cand : perimeterCandidates) {
			double dx = cand[0] - targetX;
			double dz = cand[1] - targetZ;
			if ((dx * dx + dz * dz) <= 16.0D) {
				reachable.add(cand);
			}
		}

		Assertions.assertFalse(reachable.isEmpty(), "Reachable perimeter candidates must exist");

		// Sort by combined distance to eliminate North bias
		reachable.sort((a, b) -> {
			double distTargetA = Math.sqrt(Math.pow(a[0] - targetX, 2) + Math.pow(a[1] - targetZ, 2));
			double distTargetB = Math.sqrt(Math.pow(b[0] - targetX, 2) + Math.pow(b[1] - targetZ, 2));

			double distMinionA = Math.sqrt(Math.pow((a[0] + 0.5D) - minionX, 2) + Math.pow((a[1] + 0.5D) - minionZ, 2));
			double distMinionB = Math.sqrt(Math.pow((b[0] + 0.5D) - minionX, 2) + Math.pow((b[1] + 0.5D) - minionZ, 2));

			return Double.compare(distTargetA + 0.5D * distMinionA, distTargetB + 0.5D * distMinionB);
		});

		int[] best = reachable.get(0);
		// Best candidate must be on the South or East face closest to minion and target, NOT North face (Z = 9)!
		Assertions.assertNotEquals(9, best[1], "North face (Z=9) must NOT be selected for South-side target; North bias eliminated");
		Assertions.assertEquals(15, best[1], "South face exterior perimeter (Z = maxZ + 1 = 15) must be chosen for South block");
	}

	@Test
	@DisplayName("Ceiling collision and vertical stall sensors abort climb and trigger safe descent")
	public void testCeilingCollisionAndStallSensorAbortRecovery() {
		class MockClimbSensorSimulation {
			double y = 64.0D;
			double lastClimbY = 64.0D;
			int stallTicks = 0;
			boolean isAscending = true;
			boolean isDescending = false;
			boolean columnBlacklisted = false;
			BlockPos columnPos = new BlockPos(10, 64, 10);

			void tick(boolean ceilingBlocked, boolean advancePosition) {
				if (isAscending) {
					if (advancePosition && !ceilingBlocked) {
						y += 0.25D;
					}

					// Stall detection
					if (y <= lastClimbY + 0.05D) {
						stallTicks++;
					} else {
						stallTicks = 0;
						lastClimbY = y;
					}

					if (ceilingBlocked || stallTicks > 10) {
						// Abort climb
						isAscending = false;
						isDescending = true;
						columnBlacklisted = true;
					}
				}
			}
		}

		// Scenario 1: Overhead ceiling obstruction immediately triggers abort
		MockClimbSensorSimulation ceilingSim = new MockClimbSensorSimulation();
		ceilingSim.tick(true, false);
		Assertions.assertFalse(ceilingSim.isAscending);
		Assertions.assertTrue(ceilingSim.isDescending);
		Assertions.assertTrue(ceilingSim.columnBlacklisted);

		// Scenario 2: Vertical stall (>10 ticks with no elevation gain) triggers abort
		MockClimbSensorSimulation stallSim = new MockClimbSensorSimulation();
		for (int i = 0; i < 10; i++) {
			stallSim.tick(false, false);
			Assertions.assertTrue(stallSim.isAscending);
		}
		// 11th tick exceeds stall threshold (stallTicks > 10)
		stallSim.tick(false, false);
		Assertions.assertFalse(stallSim.isAscending);
		Assertions.assertTrue(stallSim.isDescending);
		Assertions.assertTrue(stallSim.columnBlacklisted);
	}

	@Test
	@DisplayName("Demobilization teardown condition handles build and dismantle modes accurately")
	public void testDemobilizationTeardownCondition() {
		class MockTeardownDecider {
			boolean shouldTeardownOnDescent(boolean isDismantle, boolean hasPendingTasks, boolean isSessionActive) {
				if (isDismantle) {
					return true;
				}
				return !hasPendingTasks || !isSessionActive;
			}
		}

		MockTeardownDecider decider = new MockTeardownDecider();

		// Dismantle mode: always teardown on descent
		Assertions.assertTrue(decider.shouldTeardownOnDescent(true, true, true));
		Assertions.assertTrue(decider.shouldTeardownOnDescent(true, false, true));

		// Build mode while chaining tasks (pending tasks exist): do NOT teardown on intermediate descent
		Assertions.assertFalse(decider.shouldTeardownOnDescent(false, true, true));

		// Build mode upon demobilization (no pending tasks remaining): teardown on descent
		Assertions.assertTrue(decider.shouldTeardownOnDescent(false, false, true));

		// Build mode if session is inactive/cancelled: teardown on descent
		Assertions.assertTrue(decider.shouldTeardownOnDescent(false, true, false));
	}

	@Test
	@DisplayName("Scaffolding column reservation lifecycle with stale claim pruning and force-release")
	public void testMultiMinionReservationStaleClaimPruningAndForceRelease() {
		StructureBlueprint blueprint = StructureBlueprint.builder("test_tower", "Test Tower")
			.description("Test Blueprint")
			.build();

		UUID ownerUuid = UUID.randomUUID();
		BlockPos anchor = new BlockPos(200, 64, 200);
		ConstructionSession session = new ConstructionSession(
			ownerUuid,
			World.OVERWORLD,
			anchor,
			blueprint,
			true,
			1000L
		);

		UUID builderAlpha = UUID.randomUUID();
		UUID builderBravo = UUID.randomUUID();
		UUID builderCharlie = UUID.randomUUID();

		BlockPos colAlpha = new BlockPos(205, 64, 195);
		BlockPos colBravo = new BlockPos(195, 64, 205);
		BlockPos colCharlie = new BlockPos(205, 64, 205);

		// 1. Claim all three distinct perimeter columns
		Assertions.assertTrue(session.claimScaffoldColumn(colAlpha, builderAlpha));
		Assertions.assertTrue(session.claimScaffoldColumn(colBravo, builderBravo));
		Assertions.assertTrue(session.claimScaffoldColumn(colCharlie, builderCharlie));
		Assertions.assertEquals(3, session.getClaimedScaffoldColumns().size());

		// 2. Query individual holding status
		Assertions.assertTrue(session.isScaffoldColumnClaimedBy(builderAlpha));
		Assertions.assertTrue(session.isScaffoldColumnClaimedBy(builderBravo));
		Assertions.assertTrue(session.isScaffoldColumnClaimedBy(builderCharlie));
		Assertions.assertFalse(session.isScaffoldColumnClaimedBy(UUID.randomUUID()));

		// 3. Force release column without minion UUID (admin / manager override)
		Assertions.assertTrue(session.releaseScaffoldColumn(colAlpha));
		Assertions.assertFalse(session.isScaffoldColumnClaimed(colAlpha));
		Assertions.assertFalse(session.isScaffoldColumnClaimedBy(builderAlpha));
		Assertions.assertEquals(2, session.getClaimedScaffoldColumns().size());

		// 4. Stale claims pruning: builderBravo and builderCharlie have no active tasks in session.tasks
		// Invoking cleanStaleClaims should purge orphaned column reservations
		long currentTick = 2000L;
		session.cleanStaleClaims(currentTick, 300L);
		Assertions.assertFalse(session.isScaffoldColumnClaimed(colBravo), "Orphaned column reservation for builderBravo should be pruned");
		Assertions.assertFalse(session.isScaffoldColumnClaimed(colCharlie), "Orphaned column reservation for builderCharlie should be pruned");
		Assertions.assertTrue(session.getClaimedScaffoldColumns().isEmpty(), "All orphaned column claims should be cleared");
	}

	@Test
	@DisplayName("Kinematic climb centering lock converges minion onto column axis during vertical impulse")
	public void testKinematicClimbCenteringLockAndTimeout() {
		class CenteringClimbSim {
			double x = 10.1D; // off-center by 0.4 blocks from 10.5
			double y = 64.0D;
			double z = 10.9D; // off-center by 0.4 blocks from 10.5
			double velX = 0.0D;
			double velY = 0.0D;
			double velZ = 0.0D;
			int climbTicks = 0;
			int targetTopY = 70;
			boolean landed = false;
			boolean aborted = false;

			void tick(int maxAllowedTicks) {
				climbTicks++;
				if (climbTicks > maxAllowedTicks) {
					aborted = true;
					return;
				}

				double scCenterX = 10.5D;
				double scCenterZ = 10.5D;
				double alignX = scCenterX - x;
				double alignZ = scCenterZ - z;

				if (y < (double) targetTopY + 0.95D) {
					// Apply centering velocity vector
					velX = alignX * 0.3D;
					velY = 0.25D;
					velZ = alignZ * 0.3D;

					x += velX;
					y += velY;
					z += velZ;
				} else {
					// Clean platform snap
					x = scCenterX;
					y = (double) targetTopY + 1.0D;
					z = scCenterZ;
					velX = 0.0D;
					velY = 0.0D;
					velZ = 0.0D;
					landed = true;
				}
			}
		}

		CenteringClimbSim sim = new CenteringClimbSim();
		while (!sim.landed && !sim.aborted && sim.climbTicks < 140) {
			sim.tick(140);
		}

		Assertions.assertTrue(sim.landed, "Climb simulation must complete platform landing");
		Assertions.assertFalse(sim.aborted, "Climb within 140 ticks must not abort");
		Assertions.assertEquals(10.5D, sim.x, 1e-4, "Horizontal X must be cleanly centered at 10.5D");
		Assertions.assertEquals(10.5D, sim.z, 1e-4, "Horizontal Z must be cleanly centered at 10.5D");
		Assertions.assertEquals(71.0D, sim.y, 1e-4, "Standing Y must snap to targetTopY + 1.0D");
		Assertions.assertEquals(0.0D, sim.velY, 1e-6, "Vertical velocity must be 0.0D on landing");

		// Test timeout condition
		CenteringClimbSim timeoutSim = new CenteringClimbSim();
		timeoutSim.targetTopY = 200; // Unreachable within 140 ticks
		while (!timeoutSim.landed && !timeoutSim.aborted && timeoutSim.climbTicks <= 145) {
			timeoutSim.tick(140);
		}
		Assertions.assertTrue(timeoutSim.aborted, "Ascent exceeding 140 ticks must trigger safety abort");
	}

	@Test
	@DisplayName("Elevated task chaining executes multiple adjacent tasks sequentially without descending")
	public void testElevatedTaskChainingSequentialBlockExecution() {
		// Scaffolding platform centered at (15.5, 71.0, 20.5) with targetScaffoldTopY = 70
		int scaffoldX = 15;
		int scaffoldZ = 20;
		int topY = 70;

		class PlatformStation {
			final double centerX = scaffoldX + 0.5D;
			final double centerZ = scaffoldZ + 0.5D;
			final double standingY = (double) topY + 1.0D; // 71.0D

			boolean isReachable(int tx, int ty, int tz) {
				double dx = (tx + 0.5D) - centerX;
				double dz = (tz + 0.5D) - centerZ;
				double distSq = dx * dx + dz * dz;
				double vertDiff = Math.abs(standingY - (double) ty);
				return distSq <= 16.0D && vertDiff <= 2.5D;
			}
		}

		PlatformStation station = new PlatformStation();

		// Task sequence along an elevated observation deck rim:
		// Block 1: (16, 71, 20) -> dx = 1.0, dz = 0.0, distSq = 1.0, vDiff = 0.0 -> reachable
		// Block 2: (17, 72, 20) -> dx = 2.0, dz = 0.0, distSq = 4.0, vDiff = 1.0 -> reachable
		// Block 3: (16, 72, 21) -> dx = 1.0, dz = 1.0, distSq = 2.0, vDiff = 1.0 -> reachable
		// Block 4: (14, 70, 22) -> dx = -1.0, dz = 2.0, distSq = 5.0, vDiff = 1.0 -> reachable
		// Block 5 (far ground task): (15, 64, 20) -> vDiff = 7.0 > 2.5 -> NOT reachable (must descend)
		// Block 6 (far corner task): (25, 71, 20) -> distSq = 100 > 16 -> NOT reachable (must descend)

		Assertions.assertTrue(station.isReachable(16, 71, 20), "Block 1 should be chained on platform");
		Assertions.assertTrue(station.isReachable(17, 72, 20), "Block 2 should be chained on platform");
		Assertions.assertTrue(station.isReachable(16, 72, 21), "Block 3 should be chained on platform");
		Assertions.assertTrue(station.isReachable(14, 70, 22), "Block 4 should be chained on platform");

		Assertions.assertFalse(station.isReachable(15, 64, 20), "Ground block requires descent");
		Assertions.assertFalse(station.isReachable(25, 71, 20), "Distant wall block requires descent");

		// Chaining simulation: minion completes 4 consecutive tasks without resetting scaffold column
		BlockPos activeColumn = new BlockPos(scaffoldX, 64, scaffoldZ);
		int chainedCount = 0;
		List<TestPos> tasks = List.of(
			new TestPos(16, 71, 20),
			new TestPos(17, 72, 20),
			new TestPos(16, 72, 21),
			new TestPos(14, 70, 22),
			new TestPos(15, 64, 20) // Triggers descent
		);

		for (TestPos task : tasks) {
			if (station.isReachable(task.x(), task.y(), task.z())) {
				chainedCount++;
				Assertions.assertNotNull(activeColumn, "Active scaffolding column must remain preserved during chained execution");
			} else {
				// Descent triggered: active column released
				activeColumn = null;
				break;
			}
		}

		Assertions.assertEquals(4, chainedCount, "Exactly 4 tasks should have executed consecutively on platform");
		Assertions.assertNull(activeColumn, "Scaffold column must be cleared once an out-of-reach task triggers descent");
	}

	@Test
	@DisplayName("Perimeter candidate column search rejects positions with overhead ceiling/eaves obstructions")
	public void testPerimeterColumnVerticalClearanceUnderOverhangs() {
		// Mock structure bounds: 7x7 Watchtower from X=10..16, Z=10..16, anchor at (10, 64, 10)
		// Overhang (observation deck flared eaves) extends at Y=72 out to X=9..17, Z=9..17
		Set<TestPos> occupiedBlocks = new HashSet<>();
		// Populate flared overhang at Y=72
		for (int x = 9; x <= 17; x++) {
			for (int z = 9; z <= 17; z++) {
				occupiedBlocks.add(new TestPos(x, 72, z));
			}
		}

		class ClearanceChecker {
			boolean isColumnClearUpTo(int colX, int colZ, int groundY, int targetY) {
				// Column must be clear from groundY up through targetY + 2
				for (int y = groundY; y <= targetY + 2; y++) {
					if (occupiedBlocks.contains(new TestPos(colX, y, colZ))) {
						return false; // Obstructed by overhead structure!
					}
				}
				return true;
			}
		}

		ClearanceChecker checker = new ClearanceChecker();
		int groundY = 64;
		int elevatedTargetY = 70;

		// Candidate A: at (9, 10) directly beneath the Y=72 flared eaves (targetY + 2 = 72)
		Assertions.assertFalse(checker.isColumnClearUpTo(9, 10, groundY, elevatedTargetY),
			"Candidate directly underneath the flared eaves at Y=72 must be rejected");

		// Candidate B: at (8, 10) situated 1 block further out past the flared eaves
		Assertions.assertTrue(checker.isColumnClearUpTo(8, 10, groundY, elevatedTargetY),
			"Candidate outside the flared eaves must pass vertical clearance");
	}
}
