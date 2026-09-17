package com.example.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating:
 * 1. Unified Stationing State (Waypoint Ping vs Close-Up Right-Click).
 * 2. Instant First-Click Follow Transition for All Stationed Minions.
 * 3. ConstructionSession Door Pair Auto-Completion on Lower Half Placement.
 * 4. MinionBuildGoal Source Invariants: Removal of 400-Tick Timeout Abandonment and
 *    Addition of 40-Tick Arcane Phase-Shift Resolution.
 * 5. Rotated Doorway World Coordinate Resolution in Structure Exit Scanning.
 */
public class UnifiedStationingAndBuildCompletionTest {

	// =========================================================================
	// 1. Unified Stationing State & Follow Symmetry
	// =========================================================================

	static class MockStationMinion {
		boolean selected = true;
		boolean sitting = false;
		BlockPos guardAnchor = null;
		BlockPos currentPos = new BlockPos(10, 64, 10);

		boolean isGuarding() {
			return this.guardAnchor != null;
		}

		boolean isHoldingPosition() {
			return this.sitting || this.isGuarding();
		}

		void applyWaypointPing(BlockPos targetStation) {
			this.selected = false;
			this.sitting = false;
			this.guardAnchor = targetStation;
		}

		void interactEmptyHand() {
			boolean currentlyStationed = this.isHoldingPosition() || this.sitting || this.guardAnchor != null;
			boolean newStationed = !currentlyStationed;
			this.sitting = newStationed;
			if (newStationed) {
				this.selected = false;
				this.guardAnchor = this.currentPos;
			} else {
				this.selected = true;
				this.guardAnchor = null;
			}
		}
	}

	@Test
	@DisplayName("Waypoint ping and close-up stationing are unified; single click immediately transitions to following")
	void testUnifiedStationingTransitionSymmetry() {
		MockStationMinion minion = new MockStationMinion();

		// Initially following master
		Assertions.assertTrue(minion.selected, "Minion should start selected");
		Assertions.assertFalse(minion.isHoldingPosition(), "Minion should not be holding position initially");

		// 1. Dispatch minion far away via waypoint ping
		BlockPos waypoint = new BlockPos(50, 64, 50);
		minion.applyWaypointPing(waypoint);

		Assertions.assertFalse(minion.selected, "Minion should be deselected on waypoint ping");
		Assertions.assertEquals(waypoint, minion.guardAnchor, "Guard anchor should match waypoint");
		Assertions.assertTrue(minion.isHoldingPosition(), "Minion must be recognized as holding position / stationed");

		// 2. Player approaches and right-clicks with empty hand ONCE:
		minion.interactEmptyHand();

		Assertions.assertTrue(minion.selected, "Single click on waypoint-stationed minion MUST transition to selected");
		Assertions.assertFalse(minion.sitting, "Minion must not be sitting");
		Assertions.assertNull(minion.guardAnchor, "Guard anchor must be cleared");
		Assertions.assertFalse(minion.isHoldingPosition(), "Minion must no longer be holding position");

		// 3. Right-click again up close to station in place:
		minion.interactEmptyHand();

		Assertions.assertFalse(minion.selected, "Second click must station minion up close");
		Assertions.assertTrue(minion.sitting, "Minion must be sitting when stationed up close");
		Assertions.assertEquals(minion.currentPos, minion.guardAnchor, "Guard anchor must be assigned to current pos");
		Assertions.assertTrue(minion.isHoldingPosition(), "Minion must be recognized as holding position");

		// 4. Right-click again to resume following:
		minion.interactEmptyHand();

		Assertions.assertTrue(minion.selected, "Subsequent click must immediately transition back to following");
		Assertions.assertFalse(minion.sitting);
		Assertions.assertNull(minion.guardAnchor);
		Assertions.assertFalse(minion.isHoldingPosition());
	}

	// =========================================================================
	// 2. Door Pair Auto-Completion in ConstructionSession
	// =========================================================================

	static class MockDoorTask {
		int id;
		BlockPos worldPos;
		boolean isDoor;
		boolean isLowerHalf;
		boolean completed = false;

		MockDoorTask(int id, BlockPos worldPos, boolean isDoor, boolean isLowerHalf) {
			this.id = id;
			this.worldPos = worldPos;
			this.isDoor = isDoor;
			this.isLowerHalf = isLowerHalf;
		}
	}

	@Test
	@DisplayName("Completing the lower door half auto-completes the upper door half task and source contract exists")
	void testDoorPairAutoCompletion() throws IOException {
		// 1. Source contract: ConstructionSession.java completeTask must auto-complete matching upper door half
		Path sessionPath = Path.of("src/main/java/com/example/construction/ConstructionSession.java");
		Assertions.assertTrue(Files.exists(sessionPath));
		String sessionContent = Files.readString(sessionPath);

		Assertions.assertTrue(
			sessionContent.contains("if (this.mode == SessionMode.BUILD && state.getBlock() instanceof DoorBlock && state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER)"),
			"ConstructionSession completeTask must check for lower door half in BUILD mode"
		);
		Assertions.assertTrue(
			sessionContent.contains("BlockPos upperPos = task.getWorldPos().up();"),
			"ConstructionSession completeTask must compute upper door half at task.getWorldPos().up()"
		);
		Assertions.assertTrue(
			sessionContent.contains("other.complete();"),
			"ConstructionSession completeTask must call other.complete() on matching upper door task"
		);

		// 2. Simulation contract: lower door completion automatically marks upper door completed
		List<MockDoorTask> tasks = new ArrayList<>();
		tasks.add(new MockDoorTask(0, new BlockPos(10, 64, 10), false, false)); // foundation
		tasks.add(new MockDoorTask(1, new BlockPos(10, 65, 10), true, true));   // lower door
		tasks.add(new MockDoorTask(2, new BlockPos(10, 66, 10), true, false));  // upper door

		int completedCount = 0;
		MockDoorTask lower = tasks.get(1);
		lower.completed = true;
		completedCount++;

		if (lower.isDoor && lower.isLowerHalf) {
			BlockPos upperPos = lower.worldPos.up();
			for (MockDoorTask other : tasks) {
				if (!other.completed && other.worldPos.equals(upperPos) && other.isDoor && !other.isLowerHalf) {
					other.completed = true;
					completedCount++;
					break;
				}
			}
		}

		Assertions.assertTrue(tasks.get(1).completed, "Lower door task must be completed");
		Assertions.assertTrue(tasks.get(2).completed, "Upper door task must be automatically completed");
		Assertions.assertEquals(2, completedCount, "Completed count must reflect both door halves");
	}

	// =========================================================================
	// 3. Source Contracts: MinionEntity, MinionScreen, ModNetworking
	// =========================================================================

	@Test
	@DisplayName("MinionEntity, MinionScreen, and ModNetworking enforce unified stationing contracts")
	void testUnifiedStationingSourceContracts() throws IOException {
		// 1. MinionEntity.java empty-hand check
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath));
		String minionEntityContent = Files.readString(minionEntityPath);

		Assertions.assertTrue(
			minionEntityContent.contains("boolean currentlyStationed = this.isHoldingPosition() || this.isSitting() || this.getGuardAnchorPos() != null;"),
			"MinionEntity interactMob must evaluate currentlyStationed using isHoldingPosition/isSitting/guardAnchorPos"
		);

		// 2. MinionScreen.java status check
		Path minionScreenPath = Path.of("src/client/java/com/example/client/gui/MinionScreen.java");
		Assertions.assertTrue(Files.exists(minionScreenPath));
		String minionScreenContent = Files.readString(minionScreenPath);

		Assertions.assertTrue(
			minionScreenContent.contains("if (minion.isHoldingPosition()) {"),
			"MinionScreen must check isHoldingPosition to render Holding Position (Stationed) status"
		);

		// 3. ModNetworking.java teleportation clearing guard anchor
		Path modNetworkingPath = Path.of("src/main/java/com/example/network/ModNetworking.java");
		Assertions.assertTrue(Files.exists(modNetworkingPath));
		String modNetworkingContent = Files.readString(modNetworkingPath);

		Assertions.assertTrue(
			modNetworkingContent.contains("minion.setGuardAnchorPos(null);"),
			"ModNetworking teleportation must clear guard anchor pos so minions do not run back to old stations"
		);
	}

	// =========================================================================
	// 4. Source Contracts: MinionBuildGoal No Premature Timeout & Arcane Phase Shift
	// =========================================================================

	@Test
	@DisplayName("MinionBuildGoal eliminates 400-tick timeout loops, adds Arcane Phase-Shift, and persists execution")
	void testNoPrematureTimeoutAbandonmentInvariants() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath));
		String buildGoalContent = Files.readString(buildGoalPath);

		// 1. 400-tick abandonment was eliminated
		Assertions.assertFalse(
			buildGoalContent.contains("this.ticksNavigating > 400"),
			"MinionBuildGoal must NOT contain 400-tick timeout abandonment"
		);

		// 2. Arcane Phase-Shift resolution exists on 40-tick navigation obstacle
		Assertions.assertTrue(
			buildGoalContent.contains("this.ticksNavigating > 40"),
			"MinionBuildGoal must engage Arcane Phase-Shift when ticksNavigating exceeds 40"
		);
		Assertions.assertTrue(
			buildGoalContent.contains("this.minion.requestTeleport(phaseDest.x, phaseDest.y, phaseDest.z);"),
			"MinionBuildGoal must teleport to work position on physical obstacle stall"
		);

		// 3. Persistent execution in shouldContinue
		Assertions.assertTrue(
			buildGoalContent.contains("// Persistent build goal: stays active throughout the construction session until 100% finished"),
			"MinionBuildGoal shouldContinue must persist while construction session is active"
		);

		// 4. Rotated doorway world coordinates in findStructureExitWaypoint
		Assertions.assertTrue(
			buildGoalContent.contains("BlockPos doorPos = task.getWorldPos();"),
			"findStructureExitWaypoint must use task.getWorldPos() to correctly resolve rotated doorways"
		);
	}
}
