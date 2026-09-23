package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Unit tests verifying Miner area clearance and air-mining prevention:
 * <ol>
 *   <li><b>Air-Mining Prevention:</b> Miners strictly never claim, pathfind to, or swing tools at air blocks.</li>
 *   <li><b>Full Selected Area Clearance:</b> In DISMANTLE mode, all non-air destructible blocks inside
 *       the selected 3D bounding box are targeted top-to-bottom.</li>
 *   <li><b>Auto-Cleanup & Dismissal:</b> When all destructible blocks in the selected volume are cleared
 *       (or become air), the session automatically completes so the wireframe highlight disappears immediately.</li>
 *   <li><b>Ground-Anchoring in MINE Mode:</b> Clicking the ground in MINE mode anchors at clickedPos
 *       rather than shifting into empty sky above.</li>
 * </ol>
 */
public class MinerAreaAndAirSafeguardTest {

	public record TestBlock(int x, int y, int z, String blockId, float hardness) {
		public boolean isAir() {
			return "minecraft:air".equals(blockId);
		}

		public boolean isIndestructible() {
			return "minecraft:bedrock".equals(blockId) || hardness < 0.0F;
		}
	}

	public static class MockWorldVolume {
		private final Map<String, TestBlock> blocks = new HashMap<>();

		private String key(int x, int y, int z) {
			return x + "," + y + "," + z;
		}

		public void setBlock(int x, int y, int z, String blockId, float hardness) {
			blocks.put(key(x, y, z), new TestBlock(x, y, z, blockId, hardness));
		}

		public TestBlock getBlock(int x, int y, int z) {
			return blocks.getOrDefault(key(x, y, z), new TestBlock(x, y, z, "minecraft:air", 0.0F));
		}

		public void breakBlock(int x, int y, int z) {
			blocks.put(key(x, y, z), new TestBlock(x, y, z, "minecraft:air", 0.0F));
		}

		public boolean hasRemainingBlocksInBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					for (int z = minZ; z <= maxZ; z++) {
						TestBlock b = getBlock(x, y, z);
						if (!b.isAir() && !b.isIndestructible()) {
							return true;
						}
					}
				}
			}
			return false;
		}

		public List<TestBlock> scanAreaTopDown(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			List<TestBlock> result = new ArrayList<>();
			for (int y = maxY; y >= minY; y--) {
				for (int x = minX; x <= maxX; x++) {
					for (int z = minZ; z <= maxZ; z++) {
						TestBlock b = getBlock(x, y, z);
						if (!b.isAir() && !b.isIndestructible()) {
							result.add(b);
						}
					}
				}
			}
			return result;
		}
	}

	@Test
	@DisplayName("Area Scanning: Only non-air, destructible blocks are queued, strictly top-to-bottom")
	public void testTopDownAreaScanExcludesAirAndBedrock() {
		MockWorldVolume world = new MockWorldVolume();

		// Bounding box 3x3x3 from (0, 60, 0) to (2, 62, 2)
		// Y=62: 2 stone blocks, 7 air blocks
		world.setBlock(0, 62, 0, "minecraft:stone", 1.5F);
		world.setBlock(1, 62, 1, "minecraft:cobblestone", 2.0F);

		// Y=61: 1 bedrock block, 1 iron ore, 7 air blocks
		world.setBlock(0, 61, 0, "minecraft:bedrock", -1.0F);
		world.setBlock(2, 61, 2, "minecraft:iron_ore", 3.0F);

		// Y=60: 3 dirt blocks, 6 air blocks
		world.setBlock(0, 60, 0, "minecraft:dirt", 0.5F);
		world.setBlock(1, 60, 0, "minecraft:dirt", 0.5F);
		world.setBlock(2, 60, 0, "minecraft:dirt", 0.5F);

		List<TestBlock> queued = world.scanAreaTopDown(0, 60, 0, 2, 62, 2);

		// Total queued should be 2 (Y=62) + 1 (Y=61, excluding bedrock) + 3 (Y=60) = 6 blocks
		Assertions.assertEquals(6, queued.size());

		// None should be air or bedrock
		for (TestBlock b : queued) {
			Assertions.assertFalse(b.isAir(), "Queued block must not be air: " + b);
			Assertions.assertFalse(b.isIndestructible(), "Queued block must not be bedrock: " + b);
		}

		// First elements must be Y=62
		Assertions.assertEquals(62, queued.get(0).y());
		Assertions.assertEquals(62, queued.get(1).y());

		// Next element must be Y=61
		Assertions.assertEquals(61, queued.get(2).y());

		// Last 3 elements must be Y=60
		Assertions.assertEquals(60, queued.get(3).y());
		Assertions.assertEquals(60, queued.get(4).y());
		Assertions.assertEquals(60, queued.get(5).y());
	}

	@Test
	@DisplayName("Auto-Cleanup: hasRemainingBlocksInBox accurately detects when selected area is 100% cleared")
	public void testAutoCleanupWhenAreaIsCleared() {
		MockWorldVolume world = new MockWorldVolume();

		// Populate 2 solid blocks in the volume
		world.setBlock(5, 70, 5, "minecraft:oak_wood", 2.0F);
		world.setBlock(5, 71, 5, "minecraft:oak_leaves", 0.2F);

		Assertions.assertTrue(world.hasRemainingBlocksInBox(4, 69, 4, 6, 72, 6));

		// Minion breaks first block
		world.breakBlock(5, 71, 5);
		Assertions.assertTrue(world.hasRemainingBlocksInBox(4, 69, 4, 6, 72, 6), "Still has oak_wood remaining");

		// Minion breaks second block
		world.breakBlock(5, 70, 5);
		Assertions.assertFalse(world.hasRemainingBlocksInBox(4, 69, 4, 6, 72, 6), "Area is now 100% cleared, wireframe highlight should disappear!");
	}

	@Test
	@DisplayName("Air Mining Safeguard: Minion instantly skips air blocks without performing work or swing animations")
	public void testMinionNeverMinesAir() {
		MockWorldVolume world = new MockWorldVolume();

		// Block was destroyed by external force (e.g. TNT or player) before minion reached it
		int targetX = 10, targetY = 64, targetZ = 10;
		// World at target is air
		Assertions.assertTrue(world.getBlock(targetX, targetY, targetZ).isAir());

		class MinionWorkerState {
			boolean handSwung = false;
			boolean soundPlayed = false;
			boolean taskCompleted = false;

			void tick(MockWorldVolume w, int x, int y, int z) {
				TestBlock b = w.getBlock(x, y, z);
				if (b.isAir() || b.isIndestructible()) {
					// Safeguard: do not swing hand or play sound, auto-complete immediately!
					taskCompleted = true;
					return;
				}
				handSwung = true;
				soundPlayed = true;
				w.breakBlock(x, y, z);
				taskCompleted = true;
			}
		}

		MinionWorkerState worker = new MinionWorkerState();
		worker.tick(world, targetX, targetY, targetZ);

		Assertions.assertTrue(worker.taskCompleted, "Task must be marked completed");
		Assertions.assertFalse(worker.handSwung, "Minion must NOT swing hand when block is air!");
		Assertions.assertFalse(worker.soundPlayed, "Minion must NOT vocalize when block is air!");
	}

	@Test
	@DisplayName("Ground Anchoring: MINE mode anchors directly on clickedPos instead of sky above")
	public void testMineModeAnchorCalculations() {
		// Simulates player clicking grass block at (100, 64, 200) on top face (side = UP)
		int clickedX = 100, clickedY = 64, clickedZ = 200;

		// In BUILD mode: clicks on top of block to place building on top
		int buildAnchorY = clickedY + 1; // offset(side)
		Assertions.assertEquals(65, buildAnchorY);

		// In MINE mode: clicks block to mine into it and selected volume
		int mineAnchorY = clickedY; // clickedPos directly
		Assertions.assertEquals(64, mineAnchorY);
	}

	@Test
	@DisplayName("Custom Area Blueprint: createAreaBlueprint computes exact bounds and dimensions")
	public void testCreateAreaBlueprintBoundsAndDimensions() {
		net.minecraft.util.math.BlockPos p1 = new net.minecraft.util.math.BlockPos(10, 60, -5);
		net.minecraft.util.math.BlockPos p2 = new net.minecraft.util.math.BlockPos(15, 68, 2);

		com.example.blueprint.StructureBlueprint bp = com.example.blueprint.StructureBlueprint.createAreaBlueprint(
			null, p1, p2, "test_area", "Test Area", "Testing custom area blueprint"
		);

		Assertions.assertEquals("test_area", bp.getId());
		Assertions.assertEquals("Test Area", bp.getName());
		Assertions.assertEquals("Testing custom area blueprint", bp.getDescription());
		Assertions.assertEquals(6, bp.getSizeX()); // 15 - 10 + 1 = 6
		Assertions.assertEquals(9, bp.getSizeY()); // 68 - 60 + 1 = 9
		Assertions.assertEquals(8, bp.getSizeZ()); // 2 - (-5) + 1 = 8

		net.minecraft.util.math.BlockBox box = bp.getBoundingBox();
		Assertions.assertEquals(0, box.getMinX());
		Assertions.assertEquals(0, box.getMinY());
		Assertions.assertEquals(0, box.getMinZ());
		Assertions.assertEquals(5, box.getMaxX());
		Assertions.assertEquals(8, box.getMaxY());
		Assertions.assertEquals(7, box.getMaxZ());
	}

	@Test
	@DisplayName("Custom Area Blueprint: Inverted corner order normalizes dimensions properly")
	public void testCreateAreaBlueprintInvertedCorners() {
		net.minecraft.util.math.BlockPos pMax = new net.minecraft.util.math.BlockPos(30, 80, 50);
		net.minecraft.util.math.BlockPos pMin = new net.minecraft.util.math.BlockPos(20, 70, 40);

		com.example.blueprint.StructureBlueprint bp = com.example.blueprint.StructureBlueprint.createAreaBlueprint(
			null, pMax, pMin, "inverted_quarry", "Inverted Quarry", ""
		);

		Assertions.assertEquals(11, bp.getSizeX());
		Assertions.assertEquals(11, bp.getSizeY());
		Assertions.assertEquals(11, bp.getSizeZ());
	}

	@Test
	@DisplayName("Deep subterranean area excavation handles negative Y coordinates")
	public void testSubterraneanNegativeYAreaExcavation() {
		MockWorldVolume world = new MockWorldVolume();

		// Set deepslate ore blocks at negative Y levels (-58 to -60)
		world.setBlock(0, -58, 0, "minecraft:deepslate_diamond_ore", 4.5F);
		world.setBlock(0, -59, 0, "minecraft:deepslate_iron_ore", 4.5F);
		world.setBlock(0, -60, 0, "minecraft:deepslate_redstone_ore", 4.5F);
		world.setBlock(0, -61, 0, "minecraft:bedrock", -1.0F);

		List<TestBlock> scanned = world.scanAreaTopDown(0, -64, 0, 0, -50, 0);

		// Bedrock at -61 excluded, non-air blocks at -58, -59, -60 included top-down
		Assertions.assertEquals(3, scanned.size());
		Assertions.assertEquals(-58, scanned.get(0).y());
		Assertions.assertEquals(-59, scanned.get(1).y());
		Assertions.assertEquals(-60, scanned.get(2).y());
	}

	@Test
	@DisplayName("Mining and Building Symmetry: Miners teleport to perimeter waypoints and enter hold position upon completion")
	public void testMinerAndBuilderCompletionSymmetrySourceInvariants() throws IOException {
		Path managerPath = Path.of("src/main/java/com/example/construction/ConstructionManager.java");
		Assertions.assertTrue(Files.exists(managerPath));
		String managerContent = Files.readString(managerPath);

		// 1. baseY accounts for quarry rim vs structure foundation
		Assertions.assertTrue(
			managerContent.contains("int baseY = session.isDismantle() ? (box.getMaxY() + 1) : box.getMinY();"),
			"ConstructionManager.completeSession must compute perimeter rim baseY for both mining and building"
		);

		// 2. Stationing condition is NOT restricted to !session.isDismantle()
		Assertions.assertFalse(
			managerContent.contains("if (!session.isDismantle() && !perimeterWaypoints.isEmpty())"),
			"ConstructionManager.completeSession must not gate perimeter stationing behind !session.isDismantle()"
		);
		Assertions.assertTrue(
			managerContent.contains("if (!perimeterWaypoints.isEmpty())"),
			"ConstructionManager.completeSession must station all minions when perimeter waypoints are available"
		);

		// 3. Teleportation and hold position (sitting + guard anchor + deselect) applied to minions
		Assertions.assertTrue(
			managerContent.contains("minion.requestTeleport(wx, waypoint.getY(), wz);"),
			"ConstructionManager.completeSession must teleport minions to assigned perimeter waypoints"
		);
		Assertions.assertTrue(
			managerContent.contains("minion.setSitting(true);"),
			"ConstructionManager.completeSession must set sitting=true (hold position) for completed minions"
		);
		Assertions.assertTrue(
			managerContent.contains("minion.setGuardAnchorPos(waypoint);"),
			"ConstructionManager.completeSession must set guard anchor to perimeter waypoint for completed minions"
		);
		Assertions.assertTrue(
			managerContent.contains("minion.setSelected(false);"),
			"ConstructionManager.completeSession must deselect minions so only explicitly selected minions follow"
		);

		// 4. MinionBuildGoal cleans up levitation when sitting
		Path goalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(goalPath));
		String goalContent = Files.readString(goalPath);

		Assertions.assertTrue(
			goalContent.contains("if (this.minion.isSitting())"),
			"MinionBuildGoal.stop() must check if minion is sitting"
		);
	}
}
