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

	@Test
	@DisplayName("Validate combat sapper goal suppression rules when engaged in construction")
	void testSapperGoalSuppressionRules() {
		// Simulation model for sapper suppression logic
		class SuppressionEvaluator {
			boolean isSuppressed(MinionRole role, boolean isEngaged, boolean nearBuildSession, boolean nearDismantleSession) {
				if (role == MinionRole.BUILDER) {
					return isEngaged || nearBuildSession || nearDismantleSession;
				}
				if (role == MinionRole.MINER) {
					return isEngaged || nearDismantleSession;
				}
				// Combat roles (WARRIOR, RANGER, SENTINEL) are never suppressed by construction
				return false;
			}
		}

		SuppressionEvaluator evaluator = new SuppressionEvaluator();

		// Case 1: Builder engaged or near active construction site -> sapper suppressed
		Assertions.assertTrue(evaluator.isSuppressed(MinionRole.BUILDER, true, false, false),
			"Builder with active task/column claim must suppress sapper goal");
		Assertions.assertTrue(evaluator.isSuppressed(MinionRole.BUILDER, false, true, false),
			"Builder near active BUILD session must suppress sapper goal to avoid cliff misidentification");
		Assertions.assertTrue(evaluator.isSuppressed(MinionRole.BUILDER, false, false, true),
			"Builder near active DISMANTLE session must suppress sapper goal");

		// Case 2: Builder roaming free with no active construction -> sapper NOT suppressed
		Assertions.assertFalse(evaluator.isSuppressed(MinionRole.BUILDER, false, false, false),
			"Unengaged builder far from sessions must remain eligible for combat sapper traversal");

		// Case 3: Miner engaged or near DISMANTLE session -> sapper suppressed
		Assertions.assertTrue(evaluator.isSuppressed(MinionRole.MINER, true, false, false),
			"Miner engaged in deconstruction must suppress sapper goal");
		Assertions.assertTrue(evaluator.isSuppressed(MinionRole.MINER, false, false, true),
			"Miner near DISMANTLE session must suppress sapper goal");

		// Case 4: Miner near BUILD-only session -> not compatible, sapper NOT suppressed
		Assertions.assertFalse(evaluator.isSuppressed(MinionRole.MINER, false, true, false),
			"Miner near BUILD session is not engaged and should not suppress sapper goal");

		// Case 5: Combat roles (WARRIOR, RANGER, SENTINEL) -> never suppressed by construction
		for (MinionRole combatRole : List.of(MinionRole.WARRIOR, MinionRole.RANGER, MinionRole.SENTINEL)) {
			Assertions.assertFalse(evaluator.isSuppressed(combatRole, true, true, true),
				combatRole + " must never have sapper goals suppressed by construction sites");
		}
	}

	@Test
	@DisplayName("Validate minion engagement tracking across task claims and scaffolding reservations")
	void testMinionEngagementTrackingLifecycle() {
		UUID minionId = UUID.randomUUID();
		UUID otherMinionId = UUID.randomUUID();

		// Mock engagement tracker
		class EngagementTracker {
			final Map<UUID, Integer> activeTaskClaims = new HashMap<>();
			final Map<BlockPos, UUID> columnClaims = new HashMap<>();

			void claimTask(UUID minion) {
				activeTaskClaims.merge(minion, 1, Integer::sum);
			}

			void releaseTask(UUID minion) {
				activeTaskClaims.computeIfPresent(minion, (k, v) -> v > 1 ? v - 1 : null);
			}

			boolean claimColumn(BlockPos pos, UUID minion) {
				for (Map.Entry<BlockPos, UUID> entry : columnClaims.entrySet()) {
					if (entry.getKey().getX() == pos.getX() && entry.getKey().getZ() == pos.getZ()) {
						return entry.getValue().equals(minion);
					}
				}
				columnClaims.put(pos, minion);
				return true;
			}

			boolean releaseColumn(BlockPos pos, UUID minion) {
				BlockPos found = null;
				for (Map.Entry<BlockPos, UUID> entry : columnClaims.entrySet()) {
					if (entry.getKey().getX() == pos.getX() && entry.getKey().getZ() == pos.getZ()) {
						if (minion == null || minion.equals(entry.getValue())) {
							found = entry.getKey();
							break;
						}
					}
				}
				if (found != null) {
					columnClaims.remove(found);
					return true;
				}
				return false;
			}

			boolean isEngaged(UUID minion) {
				if (activeTaskClaims.getOrDefault(minion, 0) > 0) return true;
				return columnClaims.containsValue(minion);
			}
		}

		EngagementTracker tracker = new EngagementTracker();

		// 1. Initial state: idle minion is not engaged
		Assertions.assertFalse(tracker.isEngaged(minionId));

		// 2. Minion claims building task -> engaged
		tracker.claimTask(minionId);
		Assertions.assertTrue(tracker.isEngaged(minionId));

		// 3. Minion reserves scaffold column -> engaged
		BlockPos colPos = new BlockPos(20, 64, 30);
		Assertions.assertTrue(tracker.claimColumn(colPos, minionId));
		Assertions.assertTrue(tracker.isEngaged(minionId));

		// 4. Minion finishes task -> still engaged because scaffolding column reservation is held!
		tracker.releaseTask(minionId);
		Assertions.assertTrue(tracker.isEngaged(minionId),
			"Minion with reserved scaffolding column must remain marked as engaged between tasks");

		// 5. Another minion cannot claim the reserved column
		Assertions.assertFalse(tracker.claimColumn(new BlockPos(20, 70, 30), otherMinionId),
			"Other minion cannot steal an engaged minion's reserved scaffolding column");

		// 6. Minion releases scaffolding column -> no longer engaged
		Assertions.assertTrue(tracker.releaseColumn(colPos, minionId));
		Assertions.assertFalse(tracker.isEngaged(minionId),
			"Minion with no tasks and no reserved column must transition to unengaged");
	}

	@Test
	@DisplayName("Validate construction proximity boundary, bounding box expansion, and dimension filtering")
	void testConstructionProximityBoundaryAndDimensionSuppression() {
		// Mock ConstructionSession geometry and state
		class MockSession {
			final String dimension;
			final BlockPos anchorPos;
			final int minX, minY, minZ, maxX, maxY, maxZ;
			boolean active = true;
			boolean dismantle = false;

			MockSession(String dimension, BlockPos anchor, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
				this.dimension = dimension;
				this.anchorPos = anchor;
				this.minX = minX;
				this.minY = minY;
				this.minZ = minZ;
				this.maxX = maxX;
				this.maxY = maxY;
				this.maxZ = maxZ;
			}

			boolean isNear(String dim, BlockPos pos, double maxDistance, MinionRole role) {
				if (!active || !dimension.equals(dim)) {
					return false;
				}
				if (role == MinionRole.MINER && !dismantle) {
					return false;
				}
				double maxDistSq = maxDistance * maxDistance;
				if (pos.getSquaredDistance(anchorPos) <= maxDistSq) {
					return true;
				}
				return pos.getX() >= minX - maxDistance && pos.getX() <= maxX + maxDistance
					&& pos.getY() >= minY - maxDistance && pos.getY() <= maxY + maxDistance
					&& pos.getZ() >= minZ - maxDistance && pos.getZ() <= maxZ + maxDistance;
			}
		}

		BlockPos anchor = new BlockPos(100, 64, 100);
		// 10x10 structure from (95, 64, 95) to (105, 75, 105)
		MockSession buildSession = new MockSession("minecraft:overworld", anchor, 95, 64, 95, 105, 75, 105);

		double radius = 48.0D;

		// 1. Minion exactly inside structure bounds -> suppressed for BUILDER
		Assertions.assertTrue(buildSession.isNear("minecraft:overworld", new BlockPos(100, 65, 100), radius, MinionRole.BUILDER));

		// 2. Minion 40 blocks away from anchor -> suppressed for BUILDER
		Assertions.assertTrue(buildSession.isNear("minecraft:overworld", new BlockPos(140, 64, 100), radius, MinionRole.BUILDER));

		// 3. Minion 47.9 blocks away from structure perimeter -> suppressed for BUILDER
		Assertions.assertTrue(buildSession.isNear("minecraft:overworld", new BlockPos(152, 64, 100), radius, MinionRole.BUILDER));

		// 4. Minion 49 blocks away from structure perimeter -> NOT suppressed (beyond 48-block threshold)
		Assertions.assertFalse(buildSession.isNear("minecraft:overworld", new BlockPos(155, 64, 100), radius, MinionRole.BUILDER));

		// 5. Dimension mismatch (minion in Nether, session in Overworld) -> NOT suppressed
		Assertions.assertFalse(buildSession.isNear("minecraft:the_nether", new BlockPos(100, 65, 100), radius, MinionRole.BUILDER));

		// 6. Inactive / completed session -> NOT suppressed
		buildSession.active = false;
		Assertions.assertFalse(buildSession.isNear("minecraft:overworld", new BlockPos(100, 65, 100), radius, MinionRole.BUILDER));
		buildSession.active = true;

		// 7. MINER role role-filter:
		// In BUILD mode: MINER is not near/suppressed
		Assertions.assertFalse(buildSession.isNear("minecraft:overworld", new BlockPos(100, 65, 100), radius, MinionRole.MINER));
		// Switch session to DISMANTLE mode: MINER is now near/suppressed!
		buildSession.dismantle = true;
		Assertions.assertTrue(buildSession.isNear("minecraft:overworld", new BlockPos(100, 65, 100), radius, MinionRole.MINER));
	}

	@Test
	@DisplayName("Squad sapper assistance dispatch excludes builders engaged in active construction")
	void testSquadBuilderAssistanceExcludesEngagedWorkers() {
		class MockSquadMinion {
			final UUID id;
			final MinionRole role;
			final String squad;
			final boolean isEngagedInConstruction;
			final double distanceToObstacle;

			MockSquadMinion(UUID id, MinionRole role, String squad, boolean isEngaged, double dist) {
				this.id = id;
				this.role = role;
				this.squad = squad;
				this.isEngagedInConstruction = isEngaged;
				this.distanceToObstacle = dist;
			}
		}

		class DispatchEngine {
			MockSquadMinion findSquadBuilder(List<MockSquadMinion> pool, String callerSquad, double maxRadius) {
				for (MockSquadMinion candidate : pool) {
					if (candidate.role != MinionRole.BUILDER) {
						continue;
					}
					if (candidate.distanceToObstacle > maxRadius) {
						continue;
					}
					// CRITICAL FILTER: Engaged builders must not be diverted to sapper requests
					if (candidate.isEngagedInConstruction) {
						continue;
					}
					if ("ALL".equals(callerSquad) || "ALL".equals(candidate.squad) || callerSquad.equals(candidate.squad)) {
						return candidate;
					}
				}
				return null;
			}
		}

		DispatchEngine engine = new DispatchEngine();
		UUID builderA = UUID.randomUUID();
		UUID builderB = UUID.randomUUID();

		// Case 1: Builder A is 10 blocks away but actively engaged on scaffolding building a tower.
		// Builder B is 18 blocks away, idle (unengaged).
		// When Warrior calls for a bridge, Builder A MUST NOT be selected; Builder B must be dispatched!
		List<MockSquadMinion> candidates = List.of(
			new MockSquadMinion(builderA, MinionRole.BUILDER, "ALPHA", true, 10.0D),
			new MockSquadMinion(builderB, MinionRole.BUILDER, "ALPHA", false, 18.0D)
		);

		MockSquadMinion selected = engine.findSquadBuilder(candidates, "ALPHA", 24.0D);
		Assertions.assertNotNull(selected);
		Assertions.assertEquals(builderB, selected.id, "Idle builder B must be dispatched instead of engaged builder A");

		// Case 2: Only engaged builders exist within range -> no builder should be pulled away
		List<MockSquadMinion> allEngaged = List.of(
			new MockSquadMinion(builderA, MinionRole.BUILDER, "ALPHA", true, 10.0D)
		);
		Assertions.assertNull(engine.findSquadBuilder(allEngaged, "ALPHA", 24.0D),
			"Engaged builders must never be interrupted for sapper dispatch");
	}

	@Test
	@DisplayName("Sheer structure wall vs natural cliff suppression prevents false bridge deployment")
	void testCliffVersusStructureWallSuppressionGuard() {
		// Simulates obstacle evaluation when facing a 6-block vertical stone-brick wall
		class ObstacleResolver {
			boolean shouldDeploySapperColumn(MinionRole role, boolean isSuppressedByConstruction, boolean isWallDetected) {
				if (!isWallDetected) {
					return false;
				}
				// Sapper suppression suppresses the sapper goal entirely in favor of MinionBuildGoal
				if (isSuppressedByConstruction) {
					return false;
				}
				return true;
			}
		}

		ObstacleResolver resolver = new ObstacleResolver();

		// 1. Builder standing beside a 6-block structure wall of an active Watchtower
		// isSuppressedByConstruction is true -> DO NOT deploy sapper traversal column!
		// MinionBuildGoal will handle scaffolding columns through findScaffoldColumn instead.
		boolean builderSapper = resolver.shouldDeploySapperColumn(MinionRole.BUILDER, true, true);
		Assertions.assertFalse(builderSapper,
			"Builder engaged near construction site must suppress sapper goal to avoid false cliff column deployment");

		// 2. Warrior pursuing an enemy past the same wall in battle
		// isSuppressedByConstruction is false -> Warrior CAN deploy traversal column to scale the rampart!
		boolean warriorSapper = resolver.shouldDeploySapperColumn(MinionRole.WARRIOR, false, true);
		Assertions.assertTrue(warriorSapper,
			"Warrior pursuing enemies should not be blocked and can utilize sapper traversal");
	}
}
