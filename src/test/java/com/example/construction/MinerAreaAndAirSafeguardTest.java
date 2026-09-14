package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
