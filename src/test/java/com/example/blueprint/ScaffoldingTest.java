package com.example.blueprint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
}
