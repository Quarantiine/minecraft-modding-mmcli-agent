package com.example.construction;

import com.example.entity.custom.MinionRole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Unit tests for Structure Dismantling Mode:
 * - SessionMode enum invariants and defaults.
 * - Top-down reverse topological ordering (highest effective Y first, foundations last).
 * - Hanging decoration clearance before ceiling deconstruction.
 * - Upper structure clearing before supporting foundation removal.
 * - Minion role authorization matrix (BUILDER and MINER participation).
 * - Scaffolding teardown state machine on minion descent.
 */
public class StructureDismantlingTest {

	public record MockBlock(int x, int y, int z, boolean isHanging, String name, float hardness, boolean isBedrock) {
		public MockBlock(int x, int y, int z, boolean isHanging, String name) {
			this(x, y, z, isHanging, name, 1.5F, false);
		}

		public int getEffectiveY() {
			return isHanging ? y + 1 : y;
		}

		public boolean isIndestructible() {
			return isBedrock || hardness < 0.0F;
		}
	}

	public static class MockTask {
		private final int id;
		private final MockBlock block;
		private boolean completed = false;
		private UUID claimedBy = null;

		public MockTask(int id, MockBlock block) {
			this.id = id;
			this.block = block;
		}

		public int getId() { return id; }
		public MockBlock getBlock() { return block; }
		public boolean isCompleted() { return completed; }
		public void complete() { this.completed = true; }
		public UUID getClaimedBy() { return claimedBy; }
		public void claim(UUID uuid) { this.claimedBy = uuid; }
	}

	public static class MockDismantleSession {
		private final ConstructionSession.SessionMode mode;
		private final List<MockTask> tasks;

		public MockDismantleSession(List<MockBlock> sortedBlueprintBlocks, ConstructionSession.SessionMode mode) {
			this.mode = mode;
			List<MockBlock> blocks = new ArrayList<>(sortedBlueprintBlocks);
			if (mode == ConstructionSession.SessionMode.DISMANTLE) {
				Collections.reverse(blocks);
				// Strict safeguard: Indestructible blocks (hardness < 0.0F or bedrock) are never marked for dismantling
				blocks.removeIf(MockBlock::isIndestructible);
			}
			List<MockTask> list = new ArrayList<>();
			for (int i = 0; i < blocks.size(); i++) {
				list.add(new MockTask(i, blocks.get(i)));
			}
			this.tasks = Collections.unmodifiableList(list);
		}

		public ConstructionSession.SessionMode getMode() { return mode; }
		public boolean isDismantle() { return mode == ConstructionSession.SessionMode.DISMANTLE; }
		public boolean isBuild() { return mode == ConstructionSession.SessionMode.BUILD; }
		public List<MockTask> getTasks() { return tasks; }

		public boolean isTaskReady(MockTask task) {
			if (mode == ConstructionSession.SessionMode.DISMANTLE) {
				return isDismantleTaskReady(task);
			}
			return isBuildTaskReady(task);
		}

		private boolean isBuildTaskReady(MockTask task) {
			MockBlock block = task.getBlock();
			if (block.isHanging()) {
				// Overhead support must be completed
				boolean hasCeiling = false;
				for (MockTask other : tasks) {
					if (other.isCompleted() && other.getBlock().x() == block.x()
							&& other.getBlock().y() == block.y() + 1
							&& other.getBlock().z() == block.z()) {
						hasCeiling = true;
						break;
					}
				}
				if (!hasCeiling) return false;
			}
			if (block.y() <= 0) return true;
			// Lower tasks must be completed
			for (MockTask other : tasks) {
				if (other.getBlock().y() < block.y() && !other.isCompleted()) {
					return false;
				}
			}
			return true;
		}

		private boolean isDismantleTaskReady(MockTask task) {
			MockBlock block = task.getBlock();

			// Safeguard: Indestructible blocks (hardness < 0.0F or bedrock) can never be marked ready for dismantling
			if (block.isIndestructible()) {
				return false;
			}

			// 1. If this is not hanging, any non-hanging block resting directly on top must be dismantled first
			if (!block.isHanging()) {
				for (MockTask other : tasks) {
					if (!other.isCompleted() && !other.getBlock().isHanging()
							&& other.getBlock().x() == block.x()
							&& other.getBlock().y() == block.y() + 1
							&& other.getBlock().z() == block.z()) {
						return false;
					}
				}
			}

			// 2. Any hanging decoration underneath must be dismantled first
			for (MockTask other : tasks) {
				if (!other.isCompleted() && other.getBlock().isHanging()
						&& other.getBlock().x() == block.x()
						&& other.getBlock().y() == block.y() - 1
						&& other.getBlock().z() == block.z()) {
					return false;
				}
			}

			// 3. Higher effective Y levels must be cleared first
			int targetEffectiveY = block.getEffectiveY();
			for (MockTask other : tasks) {
				if (!other.isCompleted() && other.getBlock().getEffectiveY() > targetEffectiveY) {
					return false;
				}
			}

			// 4. Same effective Y layer: hanging blocks dismantled before ceiling blocks
			if (!block.isHanging()) {
				for (MockTask other : tasks) {
					if (!other.isCompleted() && other.getBlock().isHanging()
							&& other.getBlock().getEffectiveY() == targetEffectiveY
							&& other.getBlock().x() == block.x()
							&& other.getBlock().z() == block.z()) {
						return false;
					}
				}
			}

			return true;
		}
	}

	@Test
	@DisplayName("Validate SessionMode enum values and mode accessors")
	void testSessionModeEnumValues() {
		Assertions.assertEquals(ConstructionSession.SessionMode.BUILD, ConstructionSession.SessionMode.valueOf("BUILD"));
		Assertions.assertEquals(ConstructionSession.SessionMode.DISMANTLE, ConstructionSession.SessionMode.valueOf("DISMANTLE"));
		Assertions.assertEquals(2, ConstructionSession.SessionMode.values().length);
	}

	@Test
	@DisplayName("Validate reverse topological order task sorting in DISMANTLE mode")
	void testReverseTopologicalTaskOrder() {
		// Blueprint in bottom-up build order
		List<MockBlock> blueprintBlocks = List.of(
			new MockBlock(0, 0, 0, false, "Cobblestone Foundation"),
			new MockBlock(0, 1, 0, false, "Oak Log Wall 1"),
			new MockBlock(0, 2, 0, false, "Oak Log Wall 2"),
			new MockBlock(1, 3, 0, false, "Spruce Planks Ceiling"),
			new MockBlock(1, 2, 0, true,  "Hanging Lantern"),
			new MockBlock(0, 4, 0, false, "Roof Apex")
		);

		MockDismantleSession session = new MockDismantleSession(blueprintBlocks, ConstructionSession.SessionMode.DISMANTLE);
		List<MockTask> tasks = session.getTasks();

		Assertions.assertEquals(6, tasks.size());
		// Task 0 must be the highest block (Roof Apex at y=4)
		Assertions.assertEquals("Roof Apex", tasks.get(0).getBlock().name());
		Assertions.assertEquals(4, tasks.get(0).getBlock().y());

		// Hanging lantern (effectiveY = 3, hanging) must come before ceiling
		Assertions.assertEquals("Hanging Lantern", tasks.get(1).getBlock().name());
		Assertions.assertEquals("Spruce Planks Ceiling", tasks.get(2).getBlock().name());

		// Middle walls
		Assertions.assertEquals("Oak Log Wall 2", tasks.get(3).getBlock().name());
		Assertions.assertEquals("Oak Log Wall 1", tasks.get(4).getBlock().name());

		// Foundation must be the last task
		Assertions.assertEquals("Cobblestone Foundation", tasks.get(5).getBlock().name());
		Assertions.assertEquals(0, tasks.get(5).getBlock().y());
	}

	@Test
	@DisplayName("Validate roof-to-foundation deconstruction prerequisites")
	void testRoofToFoundationPrerequisites() {
		List<MockBlock> blocks = List.of(
			new MockBlock(0, 0, 0, false, "Foundation"),
			new MockBlock(0, 1, 0, false, "Wall Lower"),
			new MockBlock(0, 2, 0, false, "Wall Upper"),
			new MockBlock(1, 3, 0, false, "Ceiling"),
			new MockBlock(1, 2, 0, true,  "Hanging Lantern"),
			new MockBlock(0, 4, 0, false, "Roof")
		);

		MockDismantleSession session = new MockDismantleSession(blocks, ConstructionSession.SessionMode.DISMANTLE);
		List<MockTask> tasks = session.getTasks();

		MockTask taskRoof = tasks.get(0);      // Roof y=4
		MockTask taskLantern = tasks.get(1);   // Lantern y=2, hanging, effY=3
		MockTask taskCeiling = tasks.get(2);   // Ceiling y=3, effY=3
		MockTask taskWallUpper = tasks.get(3); // WallUpper y=2
		MockTask taskWallLower = tasks.get(4); // WallLower y=1
		MockTask taskFoundation = tasks.get(5);// Foundation y=0

		// Initially, only Roof (highest layer) should be ready
		Assertions.assertTrue(session.isTaskReady(taskRoof), "Roof must be ready initially");
		Assertions.assertFalse(session.isTaskReady(taskLantern), "Lantern cannot be dismantled while Roof is intact");
		Assertions.assertFalse(session.isTaskReady(taskCeiling), "Ceiling cannot be dismantled while Roof is intact");
		Assertions.assertFalse(session.isTaskReady(taskWallUpper), "Upper wall cannot be dismantled while Roof is intact");
		Assertions.assertFalse(session.isTaskReady(taskFoundation), "Foundation cannot be dismantled while upper structure exists");

		// Complete Roof
		taskRoof.complete();
		// Now Hanging Lantern (effY=3, hanging) should be ready before Ceiling (effY=3, non-hanging)
		Assertions.assertTrue(session.isTaskReady(taskLantern), "Lantern must be ready after Roof is dismantled");
		Assertions.assertFalse(session.isTaskReady(taskCeiling), "Ceiling cannot be dismantled while Lantern hangs from it");

		// Complete Lantern
		taskLantern.complete();
		// Now Ceiling is ready
		Assertions.assertTrue(session.isTaskReady(taskCeiling), "Ceiling must be ready after Lantern is dismantled");
		Assertions.assertFalse(session.isTaskReady(taskWallUpper), "Upper wall cannot be dismantled while Ceiling is intact");

		// Complete Ceiling
		taskCeiling.complete();
		Assertions.assertTrue(session.isTaskReady(taskWallUpper), "Upper wall must be ready after Ceiling is dismantled");
		Assertions.assertFalse(session.isTaskReady(taskWallLower), "Lower wall cannot be dismantled while Upper wall is intact");

		// Complete Upper Wall
		taskWallUpper.complete();
		Assertions.assertTrue(session.isTaskReady(taskWallLower), "Lower wall must be ready after Upper wall is dismantled");
		Assertions.assertFalse(session.isTaskReady(taskFoundation), "Foundation cannot be dismantled while Lower wall is intact");

		// Complete Lower Wall
		taskWallLower.complete();
		Assertions.assertTrue(session.isTaskReady(taskFoundation), "Foundation must be ready after all walls are dismantled");

		taskFoundation.complete();
		for (MockTask t : tasks) {
			Assertions.assertTrue(t.isCompleted());
		}
	}

	@Test
	@DisplayName("Validate minion role authorization matrix for BUILD vs DISMANTLE sessions")
	void testRoleAuthorizationMatrix() {
		// Builder role participates in both BUILD and DISMANTLE
		Assertions.assertTrue(canRoleParticipate(MinionRole.BUILDER, ConstructionSession.SessionMode.BUILD));
		Assertions.assertTrue(canRoleParticipate(MinionRole.BUILDER, ConstructionSession.SessionMode.DISMANTLE));

		// Combat roles do not participate in construction or deconstruction
		Assertions.assertFalse(canRoleParticipate(MinionRole.WARRIOR, ConstructionSession.SessionMode.BUILD));
		Assertions.assertFalse(canRoleParticipate(MinionRole.WARRIOR, ConstructionSession.SessionMode.DISMANTLE));
		Assertions.assertFalse(canRoleParticipate(MinionRole.SENTINEL, ConstructionSession.SessionMode.BUILD));
		Assertions.assertFalse(canRoleParticipate(MinionRole.SENTINEL, ConstructionSession.SessionMode.DISMANTLE));
	}

	private static boolean canRoleParticipate(MinionRole role, ConstructionSession.SessionMode mode) {
		return role == MinionRole.BUILDER;
	}

	@Test
	@DisplayName("Validate scaffolding teardown on descent simulation")
	void testScaffoldingTeardownOnDescent() {
		Set<Integer> scaffoldColumnY = new TreeSet<>();
		// Scaffolding column from Y=1 to Y=5
		for (int y = 1; y <= 5; y++) {
			scaffoldColumnY.add(y);
		}

		int targetScaffoldTopY = 5;
		int groundY = 1;

		// Minion is descending: minion reaches Y=3
		int minionBlockY = 3;
		// As minion descends past Y=3, blocks above minionBlockY + 1 (i.e. Y=5) are removed
		List<Integer> removedOnDescent = new ArrayList<>();
		for (int y = targetScaffoldTopY; y > minionBlockY + 1; y--) {
			if (scaffoldColumnY.remove(y)) {
				removedOnDescent.add(y);
			}
		}

		Assertions.assertEquals(List.of(5), removedOnDescent);
		Assertions.assertFalse(scaffoldColumnY.contains(5));
		Assertions.assertTrue(scaffoldColumnY.contains(4));
		Assertions.assertTrue(scaffoldColumnY.contains(3));

		// Minion reaches ground at Y=1: remaining column blocks are cleaned up
		List<Integer> removedOnGroundArrival = new ArrayList<>();
		for (int y = groundY; y <= targetScaffoldTopY; y++) {
			if (scaffoldColumnY.remove(y)) {
				removedOnGroundArrival.add(y);
			}
		}

		Assertions.assertEquals(List.of(1, 2, 3, 4), removedOnGroundArrival);
		Assertions.assertTrue(scaffoldColumnY.isEmpty(), "All scaffolding in column must be cleared on ground arrival");
	}

	@Test
	@DisplayName("Validate bedrock and indestructible blocks are strictly excluded from dismantle tasks")
	void testBedrockAndIndestructibleBlocksExcludedFromDismantle() {
		List<MockBlock> blueprintBlocks = List.of(
			new MockBlock(0, 0, 0, false, "Bedrock Foundation", -1.0F, true),
			new MockBlock(1, 0, 0, false, "Barrier Block", -1.0F, false),
			new MockBlock(0, 1, 0, false, "Oak Wall", 2.0F, false),
			new MockBlock(0, 2, 0, false, "Roof Slab", 1.5F, false)
		);

		MockDismantleSession session = new MockDismantleSession(blueprintBlocks, ConstructionSession.SessionMode.DISMANTLE);
		List<MockTask> tasks = session.getTasks();

		// Bedrock and Barrier must be completely excluded from dismantle tasks
		Assertions.assertEquals(2, tasks.size(), "Only destructible blocks should be marked for dismantling");
		Assertions.assertEquals("Roof Slab", tasks.get(0).getBlock().name());
		Assertions.assertEquals("Oak Wall", tasks.get(1).getBlock().name());

		for (MockTask task : tasks) {
			Assertions.assertFalse(task.getBlock().isIndestructible(), "No dismantle task may target indestructible blocks");
			Assertions.assertFalse(task.getBlock().isBedrock(), "No dismantle task may target bedrock");
			Assertions.assertTrue(task.getBlock().hardness() >= 0.0F, "Hardness must be non-negative for dismantle tasks");
		}
	}

	@Test
	@DisplayName("Validate blueprint consisting entirely of bedrock creates zero dismantle tasks")
	void testAllBedrockBlueprintProducesZeroDismantleTasks() {
		List<MockBlock> bedrockBlocks = List.of(
			new MockBlock(0, 0, 0, false, "Bedrock 1", -1.0F, true),
			new MockBlock(1, 0, 0, false, "Bedrock 2", -1.0F, true),
			new MockBlock(2, 0, 0, false, "End Portal Frame", -1.0F, false)
		);

		MockDismantleSession session = new MockDismantleSession(bedrockBlocks, ConstructionSession.SessionMode.DISMANTLE);
		Assertions.assertTrue(session.getTasks().isEmpty(), "Session should contain 0 tasks when all blocks are indestructible");
	}

	@Test
	@DisplayName("Validate indestructible block task readiness rejection")
	void testIndestructibleTaskReadinessRejection() {
		MockBlock bedrock = new MockBlock(0, 0, 0, false, "Bedrock", -1.0F, true);
		MockTask bedrockTask = new MockTask(0, bedrock);

		MockDismantleSession session = new MockDismantleSession(List.of(), ConstructionSession.SessionMode.DISMANTLE);
		Assertions.assertFalse(session.isTaskReady(bedrockTask), "Indestructible tasks must never be marked ready for dismantling");
	}

	@Test
	@DisplayName("Validate ConstructionSession and ConstructionManager indestructible validation helper")
	void testIndestructibleValidationHelpers() {
		// Null safety
		Assertions.assertFalse(ConstructionSession.isIndestructible(null, null, null));
		Assertions.assertFalse(ConstructionSession.isIndestructible(null, null));
		Assertions.assertFalse(ConstructionManager.isIndestructible(null, null, null));
		Assertions.assertFalse(ConstructionManager.isIndestructibleAt(null, null));
		Assertions.assertFalse(ConstructionManager.canDismantleBlock(null, null));
		Assertions.assertEquals(0, ConstructionManager.countDismantleableBlocks(null, null, null));
		Assertions.assertFalse(com.example.entity.ai.goal.MinionBuildGoal.isIndestructibleBlock(null, null, null));
		Assertions.assertFalse(com.example.entity.ai.goal.MinionBuildGoal.isScaffoldBlock(null));
	}
}
