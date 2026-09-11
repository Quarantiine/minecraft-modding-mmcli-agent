package com.example.entity;

import com.example.construction.TraversalScaffoldingManager;
import com.example.entity.ai.goal.MinionSapperGoal;
import com.example.entity.custom.MinionRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit test suite for Terrain Navigation Scaffolding (Phase 2):
 * <ul>
 *   <li>Ravine / chasm bridging coordinate calculation across cardinal and diagonal headings.</li>
 *   <li>Cliff / mountain ascent vertical climbing column generation.</li>
 *   <li>Combat sapper role archetype gating (BUILDER zero-cost vs standard thrall item consumption).</li>
 *   <li>Squad sapper assistance dispatch and request timeout lifecycle.</li>
 *   <li>Traversal scaffolding decay state machine and entity safety extension guards.</li>
 * </ul>
 */
public class MinionSapperAndScaffoldingTest {

	@Test
	@DisplayName("Validate ravine bridge coordinate generation along cardinal East heading")
	void testRavineBridgeCardinalEast() {
		BlockPos originFeet = new BlockPos(10, 64, 20);
		// Heading directly East (dirX = 1.0, dirZ = 0.0)
		List<BlockPos> bridge = MinionSapperGoal.calculateRavineBridgeCoordinates(originFeet, 1.0D, 0.0D, 4);

		Assertions.assertEquals(4, bridge.size(), "Should calculate exactly 4 bridge positions");
		Assertions.assertEquals(new BlockPos(11, 64, 20), bridge.get(0));
		Assertions.assertEquals(new BlockPos(12, 64, 20), bridge.get(1));
		Assertions.assertEquals(new BlockPos(13, 64, 20), bridge.get(2));
		Assertions.assertEquals(new BlockPos(14, 64, 20), bridge.get(3));

		// Verify Y level remains unchanged (flat bridge at foot height)
		for (BlockPos pos : bridge) {
			Assertions.assertEquals(64, pos.getY(), "Bridge block should maintain minion foot elevation");
		}
	}

	@Test
	@DisplayName("Validate ravine bridge coordinate generation along cardinal North heading")
	void testRavineBridgeCardinalNorth() {
		BlockPos originFeet = new BlockPos(0, 70, 0);
		// Heading directly North (dirX = 0.0, dirZ = -1.0)
		List<BlockPos> bridge = MinionSapperGoal.calculateRavineBridgeCoordinates(originFeet, 0.0D, -1.0D, 6);

		Assertions.assertEquals(6, bridge.size(), "Should calculate 6 bridge positions for max span");
		for (int i = 0; i < 6; i++) {
			Assertions.assertEquals(0, bridge.get(i).getX());
			Assertions.assertEquals(70, bridge.get(i).getY());
			Assertions.assertEquals(-(i + 1), bridge.get(i).getZ());
		}
	}

	@Test
	@DisplayName("Validate ravine bridge coordinate generation along diagonal heading")
	void testRavineBridgeDiagonal() {
		BlockPos originFeet = new BlockPos(100, 65, 100);
		// Heading North-East (dirX = 1.0, dirZ = -1.0)
		List<BlockPos> bridge = MinionSapperGoal.calculateRavineBridgeCoordinates(originFeet, 1.0D, -1.0D, 3);

		Assertions.assertFalse(bridge.isEmpty(), "Bridge positions should not be empty");
		Assertions.assertTrue(bridge.size() <= 3, "Bridge span should not exceed maxSpan");

		// All coordinates should have foot elevation and progress towards positive X and negative Z
		for (BlockPos pos : bridge) {
			Assertions.assertEquals(65, pos.getY());
			Assertions.assertTrue(pos.getX() >= 100);
			Assertions.assertTrue(pos.getZ() <= 100);
		}
	}

	@Test
	@DisplayName("Validate zero vector heading returns empty bridge list")
	void testRavineBridgeZeroVector() {
		BlockPos originFeet = new BlockPos(5, 64, 5);
		List<BlockPos> bridge = MinionSapperGoal.calculateRavineBridgeCoordinates(originFeet, 0.0D, 0.0D, 6);
		Assertions.assertTrue(bridge.isEmpty(), "Zero heading vector should produce empty bridge list");
	}

	@Test
	@DisplayName("Validate cliff climbing column coordinate generation for 3-block ledge ascent")
	void testCliffColumn3BlockAscent() {
		BlockPos columnBase = new BlockPos(15, 64, 30);
		int startY = 64;
		int targetLedgeY = 67; // 3 blocks elevation change

		List<BlockPos> column = MinionSapperGoal.calculateCliffColumnCoordinates(columnBase, startY, targetLedgeY);

		Assertions.assertEquals(3, column.size(), "Column should have exactly 3 blocks for a 3-block ascent");
		Assertions.assertEquals(new BlockPos(15, 64, 30), column.get(0));
		Assertions.assertEquals(new BlockPos(15, 65, 30), column.get(1));
		Assertions.assertEquals(new BlockPos(15, 66, 30), column.get(2));

		// Verify top scaffold is 1 block below the ledge ground surface
		Assertions.assertEquals(targetLedgeY - 1, column.get(column.size() - 1).getY());
	}

	@Test
	@DisplayName("Validate cliff climbing column coordinate generation for 6-block mountain ascent")
	void testCliffColumn6BlockAscent() {
		BlockPos columnBase = new BlockPos(-20, 80, 50);
		int startY = 80;
		int targetLedgeY = 86; // Max 6-block ascent

		List<BlockPos> column = MinionSapperGoal.calculateCliffColumnCoordinates(columnBase, startY, targetLedgeY);

		Assertions.assertEquals(6, column.size(), "Column should span all 6 vertical blocks");
		for (int i = 0; i < column.size(); i++) {
			Assertions.assertEquals(-20, column.get(i).getX());
			Assertions.assertEquals(80 + i, column.get(i).getY());
			Assertions.assertEquals(50, column.get(i).getZ());
		}
	}

	@Test
	@DisplayName("Validate cliff climbing column handles inverted or flat elevations gracefully")
	void testCliffColumnFlatOrInverted() {
		BlockPos columnBase = new BlockPos(0, 64, 0);
		List<BlockPos> flat = MinionSapperGoal.calculateCliffColumnCoordinates(columnBase, 64, 64);
		Assertions.assertTrue(flat.isEmpty(), "Flat elevation should yield empty column");

		List<BlockPos> inverted = MinionSapperGoal.calculateCliffColumnCoordinates(columnBase, 70, 64);
		Assertions.assertTrue(inverted.isEmpty(), "Inverted elevation should yield empty column");
	}

	@Test
	@DisplayName("Validate combat sapper role archetype authorization rules")
	void testSapperRoleArchetypeRules() {
		// Builder archetype is the dedicated combat sapper: zero-cost placement
		Assertions.assertTrue(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.BUILDER),
			"BUILDER role must be authorized for zero-cost sapper scaffolding");

		// All other archetypes require item consumption or sapper signaling
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.WARRIOR),
			"WARRIOR role must not place zero-cost scaffolding");
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.SENTINEL),
			"SENTINEL role must not place zero-cost scaffolding");
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.MINER),
			"MINER role must not place zero-cost scaffolding");
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.RANGER),
			"RANGER role must not place zero-cost scaffolding");
	}

	@Test
	@DisplayName("Validate sapper constants configuration")
	void testSapperConstants() {
		Assertions.assertEquals(6, MinionSapperGoal.MAX_BRIDGE_SPAN);
		Assertions.assertEquals(6, MinionSapperGoal.MAX_CLIFF_HEIGHT);
		Assertions.assertEquals(24.0D, MinionSapperGoal.SQUAD_SIGNAL_RADIUS);
		Assertions.assertEquals(200L, MinionSapperGoal.SIGNAL_TIMEOUT_TICKS);
		Assertions.assertEquals(1.0625D, MinionSapperGoal.STEP_HEIGHT);

		Assertions.assertEquals(400, TraversalScaffoldingManager.DEFAULT_DECAY_TICKS);
		Assertions.assertEquals(40, TraversalScaffoldingManager.SAFETY_DELAY_TICKS);
	}

	@Test
	@DisplayName("Validate sapper request dispatch and timeout lifecycle")
	void testSapperRequestLifecycle() {
		UUID builderId = UUID.randomUUID();
		BlockPos obstacle = new BlockPos(10, 64, 10);
		Vec3d dest = new Vec3d(15.0D, 64.0D, 10.0D);
		List<BlockPos> planned = List.of(new BlockPos(11, 64, 10), new BlockPos(12, 64, 10));

		MinionSapperGoal.SapperRequest request = new MinionSapperGoal.SapperRequest(
			null, obstacle, dest, MinionSapperGoal.ObstacleType.RAVINE_GAP,
			planned, null, -1, 1000L
		);

		MinionSapperGoal.getActiveRequests().put(builderId, request);
		Assertions.assertTrue(MinionSapperGoal.getActiveRequests().containsKey(builderId));

		// At tick 1100 (100 ticks later): request is still fresh (< 200 ticks)
		long currentTick = 1100L;
		Assertions.assertTrue(currentTick - request.requestTick() <= MinionSapperGoal.SIGNAL_TIMEOUT_TICKS);

		// At tick 1250 (250 ticks later): request has timed out (> 200 ticks)
		currentTick = 1250L;
		Assertions.assertTrue(currentTick - request.requestTick() > MinionSapperGoal.SIGNAL_TIMEOUT_TICKS);

		MinionSapperGoal.getActiveRequests().remove(builderId);
		Assertions.assertFalse(MinionSapperGoal.getActiveRequests().containsKey(builderId));
	}

	@Test
	@DisplayName("Validate traversal scaffolding decay simulation and entity safety extension")
	void testTraversalScaffoldingDecayAndSafety() {
		TestTraversalSimulation sim = new TestTraversalSimulation();

		BlockPos scaffoldA = new BlockPos(10, 64, 20);
		BlockPos scaffoldB = new BlockPos(11, 64, 20);

		sim.register(scaffoldA, 0, 400);
		sim.register(scaffoldB, 0, 400);

		Assertions.assertEquals(2, sim.size());
		Assertions.assertEquals(400, sim.getRemainingTicks(scaffoldA, 0));
		Assertions.assertEquals(400, sim.getRemainingTicks(scaffoldB, 0));

		// Advance 200 ticks
		sim.tick(200);
		Assertions.assertEquals(200, sim.getRemainingTicks(scaffoldA, 200));
		Assertions.assertEquals(200, sim.getRemainingTicks(scaffoldB, 200));

		// Simulate minion standing on scaffoldA at tick 390 (10 ticks before default expiration)
		sim.setOccupied(scaffoldA, true);
		sim.tick(390);

		// ScaffoldA decay should be extended by SAFETY_DELAY_TICKS (40 ticks from current: 390 + 40 = 430)
		Assertions.assertTrue(sim.getExpiryTick(scaffoldA) >= 430);

		// Advance to tick 405: scaffoldB (unoccupied) should have decayed and been removed
		sim.tick(405);
		Assertions.assertFalse(sim.contains(scaffoldB), "Unoccupied scaffoldB should have expired and broken at tick 400");
		Assertions.assertTrue(sim.contains(scaffoldA), "Occupied scaffoldA must remain intact while minion is crossing");

		// Minion finishes crossing scaffoldA at tick 410
		sim.setOccupied(scaffoldA, false);

		// Advance past extended expiry (tick 450 > 445): scaffoldA should now decay cleanly
		sim.tick(450);
		Assertions.assertFalse(sim.contains(scaffoldA), "ScaffoldA should decay cleanly once minion is no longer on it");
		Assertions.assertEquals(0, sim.size(), "All temporary scaffolding should be cleared");
	}

	/**
	 * Simulation harness mirroring {@link TraversalScaffoldingManager} logic for pure deterministic unit tests.
	 */
	static class TestTraversalSimulation {
		private final Map<BlockPos, SimEntry> entries = new HashMap<>();
		private final Map<BlockPos, Boolean> occupied = new HashMap<>();

		static class SimEntry {
			final long createdTick;
			long expiryTick;

			SimEntry(long createdTick, long expiryTick) {
				this.createdTick = createdTick;
				this.expiryTick = expiryTick;
			}
		}

		void register(BlockPos pos, long currentTick, int lifetime) {
			this.entries.put(pos, new SimEntry(currentTick, currentTick + lifetime));
		}

		void setOccupied(BlockPos pos, boolean isOccupied) {
			this.occupied.put(pos, isOccupied);
		}

		boolean contains(BlockPos pos) {
			return this.entries.containsKey(pos);
		}

		int size() {
			return this.entries.size();
		}

		long getExpiryTick(BlockPos pos) {
			SimEntry entry = this.entries.get(pos);
			return entry != null ? entry.expiryTick : -1;
		}

		int getRemainingTicks(BlockPos pos, long currentTick) {
			SimEntry entry = this.entries.get(pos);
			return entry != null ? (int) Math.max(0, entry.expiryTick - currentTick) : -1;
		}

		void tick(long currentTick) {
			Iterator<Map.Entry<BlockPos, SimEntry>> it = this.entries.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<BlockPos, SimEntry> mapEntry = it.next();
				BlockPos pos = mapEntry.getKey();
				SimEntry entry = mapEntry.getValue();

				// Entity safety check
				if (Boolean.TRUE.equals(this.occupied.get(pos))) {
					entry.expiryTick = Math.max(entry.expiryTick, currentTick + TraversalScaffoldingManager.SAFETY_DELAY_TICKS);
					continue;
				}

				if (currentTick >= entry.expiryTick) {
					it.remove();
				}
			}
		}
	}
}
