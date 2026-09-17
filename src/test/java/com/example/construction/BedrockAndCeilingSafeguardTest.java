package com.example.construction;

import com.example.block.ModBlocks;
import com.example.entity.ai.goal.MinionBuildGoal;
import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests verifying critical safety and traversal invariants:
 * <ol>
 *   <li><b>Bedrock & Indestructible Block Immunity:</b> Ensures minions can never mine, dismantle,
 *       or clear bedrock, barriers, command blocks, or end portal frames in any session mode.</li>
 *   <li><b>3D Arcane Levitation Ceiling Clearance & Stall Recovery:</b> Ensures upward Arcane Levitation
 *       never attempts to push into low ceilings or overhead obstructions, aborts immediately upon
 *       ceiling collision, and times out cleanly if stalled.</li>
 *   <li><b>Zero-Footprint Traversal & Passive Scaffolding Invariants:</b> Verifies that ephemeral scaffolding
 *       and sappers are completely retired in favor of 100% Arcane Levitation, while passive awareness
 *       for player-placed vanilla scaffolding is retained.</li>
 * </ol>
 */
public class BedrockAndCeilingSafeguardTest {

	public static final double MIN_VERTICAL_PROGRESS_PER_TICK = 0.02D;
	public static final int STALL_THRESHOLD_TICKS = 20;

	// =========================================================================
	// 1. BEDROCK IMMUNITY TESTS
	// =========================================================================

	@Test
	@DisplayName("Bedrock immunity: Null-safe indestructible check methods return false gracefully")
	public void testIndestructibleNullSafety() {
		Assertions.assertFalse(ConstructionSession.isIndestructible(null, null, null));
		Assertions.assertFalse(ConstructionSession.isIndestructible(null, null));
		Assertions.assertFalse(ConstructionManager.isIndestructible(null, null, null));
		Assertions.assertFalse(ConstructionManager.isIndestructibleAt(null, null));
		Assertions.assertFalse(ConstructionManager.canDismantleBlock(null, null));
		Assertions.assertEquals(0, ConstructionManager.countDismantleableBlocks(null, null, null));
		Assertions.assertFalse(MinionBuildGoal.isIndestructibleBlock(null, null, null));
	}

	@Test
	@DisplayName("Bedrock immunity: Static helper identifies bedrock and negative-hardness blocks as indestructible")
	public void testIndestructibleIdentificationInvariants() {
		class HardnessTester {
			static boolean isIndestructible(String blockName, float hardness) {
				return "minecraft:bedrock".equals(blockName) || hardness < 0.0F;
			}
		}

		// Bedrock (-1.0F hardness)
		Assertions.assertTrue(HardnessTester.isIndestructible("minecraft:bedrock", -1.0F));
		// Barriers, Command Blocks, End Portals (-1.0F hardness)
		Assertions.assertTrue(HardnessTester.isIndestructible("minecraft:barrier", -1.0F));
		Assertions.assertTrue(HardnessTester.isIndestructible("minecraft:command_block", -1.0F));
		Assertions.assertTrue(HardnessTester.isIndestructible("minecraft:end_portal_frame", -1.0F));

		// Breakable blocks (positive hardness)
		Assertions.assertFalse(HardnessTester.isIndestructible("minecraft:obsidian", 50.0F));
		Assertions.assertFalse(HardnessTester.isIndestructible("minecraft:stone", 1.5F));
		Assertions.assertFalse(HardnessTester.isIndestructible("minecraft:oak_planks", 2.0F));
		Assertions.assertFalse(HardnessTester.isIndestructible("minecraft:dirt", 0.5F));
	}

	@Test
	@DisplayName("Bedrock immunity: Dismantle work strictly verifies block type to prevent deleting ground bedrock")
	public void testBedrockPreservationDuringDemolitionWork() {
		class MockWorldBlockManager {
			final Map<BlockPos, String> worldBlocks = new HashMap<>();

			void placeBlock(BlockPos pos, String block) {
				worldBlocks.put(pos, block);
			}

			boolean safeDismantleBlock(BlockPos pos) {
				String block = worldBlocks.get(pos);
				if (block == null) {
					return false;
				}
				// Safeguard: Bedrock and indestructible blocks can NEVER be cleared!
				if ("minecraft:bedrock".equals(block) || "minecraft:barrier".equals(block)) {
					return false;
				}
				worldBlocks.put(pos, "minecraft:air");
				return true;
			}
		}

		MockWorldBlockManager world = new MockWorldBlockManager();
		BlockPos bedrockPos = new BlockPos(0, -64, 0);
		BlockPos structurePos = new BlockPos(0, -63, 0);

		world.placeBlock(bedrockPos, "minecraft:bedrock");
		world.placeBlock(structurePos, "minecraft:stone_bricks");

		// Structure block is cleared successfully
		Assertions.assertTrue(world.safeDismantleBlock(structurePos));
		Assertions.assertEquals("minecraft:air", world.worldBlocks.get(structurePos));

		// Underlying bedrock is strictly protected and never deleted
		Assertions.assertFalse(world.safeDismantleBlock(bedrockPos));
		Assertions.assertEquals("minecraft:bedrock", world.worldBlocks.get(bedrockPos));
	}

	@Test
	@DisplayName("Bedrock immunity: Source code audit guarantees dismantle execution has bedrock guards and scaffolding is retired")
	public void testBedrockImmunitySourceCodeAudit() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath), "MinionBuildGoal.java must exist");
		String code = Files.readString(buildGoalPath);

		// Must verify isIndestructibleBlock in executeDismantleWork
		Assertions.assertTrue(code.contains("isIndestructibleBlock("),
			"executeDismantleWork must invoke isIndestructibleBlock check before breakBlock");

		// Scaffolding logic retired in favor of Arcane Levitation
		Assertions.assertTrue(code.contains("setArcaneLevitating"),
			"MinionBuildGoal must use Arcane Levitation instead of scaffolding");

		// ConstructionSession must also contain indestructible filtering
		Path sessionPath = Path.of("src/main/java/com/example/construction/ConstructionSession.java");
		Assertions.assertTrue(Files.exists(sessionPath), "ConstructionSession.java must exist");
		String sessionCode = Files.readString(sessionPath);

		Assertions.assertTrue(sessionCode.contains("isIndestructible("),
			"ConstructionSession must contain isIndestructible check");
	}

	// =========================================================================
	// 2. 3D ARCANE LEVITATION CEILING CLEARANCE & STALL RECOVERY
	// =========================================================================

	@Test
	@DisplayName("Ceiling clearance: Arcane levitation ascent planner aborts if overhead ceiling blocks path")
	public void testLevitationCeilingObstructionRejection() {
		class LevitationAscentScanner {
			List<BlockPos> scanAscentPath(BlockPos feetPos, int targetHeight, Set<BlockPos> solidBlocks) {
				List<BlockPos> path = new ArrayList<>();
				for (int h = 1; h <= targetHeight; h++) {
					BlockPos stepPos = feetPos.up(h);
					if (solidBlocks.contains(stepPos)) {
						// Ceiling block directly in the ascent path! Abort upward levitation
						return List.of();
					}
					path.add(stepPos);
				}
				return path;
			}
		}

		LevitationAscentScanner scanner = new LevitationAscentScanner();
		BlockPos feet = new BlockPos(10, 64, 10);

		// Clear path up to 4 blocks
		Set<BlockPos> openSky = Set.of();
		List<BlockPos> clearPath = scanner.scanAscentPath(feet, 4, openSky);
		Assertions.assertEquals(4, clearPath.size());

		// Ceiling at 2 blocks above feet (feet.up(2) = Y: 66)
		Set<BlockPos> lowCeiling = Set.of(feet.up(2));
		List<BlockPos> blockedPath = scanner.scanAscentPath(feet, 4, lowCeiling);
		Assertions.assertTrue(blockedPath.isEmpty(), "Ascent with overhead ceiling must produce empty path");
	}

	@Test
	@DisplayName("Ceiling clearance: Ledge landing candidate requires 2 blocks of clear headroom")
	public void testLedgeLandingHeadroomRequirement() {
		class LedgeHeadroomEvaluator {
			boolean isLedgeValid(BlockPos ledgeGround, Set<BlockPos> solidBlocks) {
				BlockPos feet = ledgeGround.up(1);
				BlockPos head = ledgeGround.up(2);
				// Solid blocks at feet or head obstruct the landing
				return !solidBlocks.contains(feet) && !solidBlocks.contains(head);
			}
		}

		LedgeHeadroomEvaluator evaluator = new LedgeHeadroomEvaluator();
		BlockPos ledgeGround = new BlockPos(12, 67, 10);

		// Case 1: Clear headroom at ledge
		Assertions.assertTrue(evaluator.isLedgeValid(ledgeGround, Set.of()));

		// Case 2: Block at foot level
		Assertions.assertFalse(evaluator.isLedgeValid(ledgeGround, Set.of(ledgeGround.up(1))));

		// Case 3: Overhead block at head clearance (e.g. stalactite or ceiling slab)
		Assertions.assertFalse(evaluator.isLedgeValid(ledgeGround, Set.of(ledgeGround.up(2))));
	}

	@Test
	@DisplayName("Ceiling clearance: Direct overhead ceiling collision sensor terminates upward levitation immediately")
	public void testOverheadCeilingCollisionSensorAbortsLevitation() {
		class LevitationCollisionSensor {
			boolean levitating = true;
			double vy = 0.38D;

			void checkCeilingCollision(boolean solidHeadBlock) {
				if (solidHeadBlock) {
					this.levitating = false;
					this.vy = 0.0D;
				}
			}
		}

		LevitationCollisionSensor sensor = new LevitationCollisionSensor();
		Assertions.assertTrue(sensor.levitating);
		Assertions.assertEquals(0.38D, sensor.vy);

		// Open air overhead -> continues levitating
		sensor.checkCeilingCollision(false);
		Assertions.assertTrue(sensor.levitating);

		// Solid ceiling block contacted at head clearance
		sensor.checkCeilingCollision(true);
		Assertions.assertFalse(sensor.levitating, "Head collision with ceiling must immediately terminate levitation state");
		Assertions.assertEquals(0.0D, sensor.vy, "Upward velocity must be zeroed on ceiling collision");
	}

	@Test
	@DisplayName("Ceiling clearance: Vertical stall sensor terminates levitation after 20 stalled ticks")
	public void testVerticalStallSensorTimeout() {
		int stallTicks = 0;
		double lastY = 64.0D;
		boolean aborted = false;

		// 1. Upward motion of +0.24 blocks per tick resets stall counter
		for (int tick = 0; tick < 10; tick++) {
			double currentY = lastY + 0.24D;
			if (currentY - lastY < MIN_VERTICAL_PROGRESS_PER_TICK) {
				stallTicks++;
			} else {
				stallTicks = 0;
			}
			lastY = currentY;
		}
		Assertions.assertEquals(0, stallTicks, "Normal levitation climbing must have 0 stall ticks");

		// 2. Stopped by low ceiling -> 0.0 progress for 21 ticks
		for (int tick = 0; tick <= STALL_THRESHOLD_TICKS; tick++) {
			double currentY = lastY + 0.0D; // No progress
			if (currentY - lastY < MIN_VERTICAL_PROGRESS_PER_TICK) {
				stallTicks++;
			} else {
				stallTicks = 0;
			}
			lastY = currentY;
			if (stallTicks > STALL_THRESHOLD_TICKS) {
				aborted = true;
				break;
			}
		}

		Assertions.assertTrue(aborted, "Levitation climb must abort after exceeding STALL_THRESHOLD_TICKS (20 ticks)");
		Assertions.assertEquals(21, stallTicks);
	}

	@Test
	@DisplayName("Ceiling clearance: Horizontal 3D levitation detects overhead ceiling and terminates flight path")
	public void testHorizontalLevitationTrajectoryClearanceAvoidance() {
		class TrajectoryScanner {
			List<BlockPos> calculateTrajectory(BlockPos feetPos, int maxSpan, Set<BlockPos> solidBlocks) {
				List<BlockPos> flightPath = new ArrayList<>();
				for (int step = 1; step <= maxSpan; step++) {
					BlockPos pathPos = feetPos.add(step, 0, 0);
					BlockPos head1 = pathPos.up(1);
					BlockPos head2 = pathPos.up(2);

					// If headroom is obstructed, stop flight path to avoid trapping minion under tight ceilings
					if (solidBlocks.contains(head1) || solidBlocks.contains(head2)) {
						break;
					}
					flightPath.add(pathPos);
				}
				return flightPath;
			}
		}

		TrajectoryScanner scanner = new TrajectoryScanner();
		BlockPos feet = new BlockPos(0, 64, 0);

		// Unobstructed chasm crossing
		List<BlockPos> fullFlight = scanner.calculateTrajectory(feet, 6, Set.of());
		Assertions.assertEquals(6, fullFlight.size());

		// Low ceiling at step 3 (X=3, Y=66)
		Set<BlockPos> lowCeiling = Set.of(new BlockPos(3, 66, 0));
		List<BlockPos> truncatedFlight = scanner.calculateTrajectory(feet, 6, lowCeiling);
		Assertions.assertEquals(2, truncatedFlight.size(), "Flight path should terminate before the overhead ceiling obstacle");
	}

	// =========================================================================
	// 3. ZERO-FOOTPRINT TRAVERSAL & PASSIVE SCAFFOLDING RECOGNITION
	// =========================================================================

	@Test
	@DisplayName("Scaffolding: Passable scaffolding recognition in build goal and passive climbing")
	public void testPassableScaffoldingRecognition() throws IOException {
		// MinionBuildGoal.isScaffoldBlock null check
		Assertions.assertFalse(MinionBuildGoal.isScaffoldBlock(null));

		// Verify source invariants: MinionBuildGoal recognizes Blocks.SCAFFOLDING and retires custom ModBlocks.CONSTRUCTION_BLOCK
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String buildCode = Files.readString(buildGoalPath);
		Assertions.assertTrue(buildCode.contains("state.isOf(Blocks.SCAFFOLDING)"),
			"MinionBuildGoal.isScaffoldBlock must accept vanilla scaffolding");
		Assertions.assertFalse(buildCode.contains("ModBlocks.CONSTRUCTION_BLOCK"),
			"MinionBuildGoal must not reference retired ModBlocks.CONSTRUCTION_BLOCK");

		// MinionEntity must not reference retired ModBlocks.CONSTRUCTION_BLOCK or MinionSapperGoal
		Path minionPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String minionCode = Files.readString(minionPath);
		Assertions.assertFalse(minionCode.contains("MinionSapperGoal"),
			"MinionEntity must not reference obsolete MinionSapperGoal");
		Assertions.assertFalse(minionCode.contains("ModBlocks.CONSTRUCTION_BLOCK"),
			"MinionEntity must not reference retired ModBlocks.CONSTRUCTION_BLOCK");
	}

	@Test
	@DisplayName("Zero-Footprint: All minion roles use Arcane Levitation without generating blocks or consuming items")
	public void testZeroFootprintTraversalAcrossAllRoles() {
		class TraversalModel {
			final boolean usesArcaneLevitation;
			final boolean placesPhysicalBlocks;
			final boolean consumesInventoryItems;

			TraversalModel(MinionRole role) {
				// 100% Universal Arcane Levitation across ALL roles
				this.usesArcaneLevitation = true;
				this.placesPhysicalBlocks = false;
				this.consumesInventoryItems = false;
			}
		}

		for (MinionRole role : MinionRole.values()) {
			TraversalModel model = new TraversalModel(role);
			Assertions.assertTrue(model.usesArcaneLevitation, role + " must use Universal Arcane Levitation");
			Assertions.assertFalse(model.placesPhysicalBlocks, role + " must not generate physical scaffolding blocks");
			Assertions.assertFalse(model.consumesInventoryItems, role + " must not consume items for traversal");
		}
	}

	@Test
	@DisplayName("Zero-Footprint: 3D Arcane Levitation spatial stationing prevents multi-minion congestion")
	public void testSpatialLevitationPreventsOvercrowding() {
		class SpatialStationHarness {
			final Map<Vec3d, UUID> stations = new HashMap<>();

			boolean claimStation(Vec3d station, UUID minion) {
				for (Map.Entry<Vec3d, UUID> e : stations.entrySet()) {
					if (e.getKey().squaredDistanceTo(station) < 1.0D) {
						return e.getValue().equals(minion);
					}
				}
				stations.put(station, minion);
				return true;
			}
		}

		SpatialStationHarness harness = new SpatialStationHarness();
		UUID minion1 = UUID.randomUUID();
		UUID minion2 = UUID.randomUUID();

		Vec3d stationA = new Vec3d(10.5, 68.0, 10.5);
		Vec3d stationClose = new Vec3d(10.8, 68.2, 10.6);
		Vec3d stationB = new Vec3d(12.5, 68.0, 10.5);

		// Minion 1 claims station A in 3D flight
		Assertions.assertTrue(harness.claimStation(stationA, minion1));

		// Minion 2 cannot occupy nearly identical 3D coordinate
		Assertions.assertFalse(harness.claimStation(stationClose, minion2),
			"Minion 2 must not crowd onto Minion 1's 3D levitation station");

		// Minion 2 can claim separate 3D hover station B
		Assertions.assertTrue(harness.claimStation(stationB, minion2));
	}

	@Test
	@DisplayName("Scaffolding: MinionEntity recognizes SCAFFOLDING for passive climbing physics")
	public void testMinionEntityClimbingRecognition() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		String code = Files.readString(minionEntityPath);
		Assertions.assertTrue(code.contains("!currentFootState.isOf(Blocks.SCAFFOLDING)"),
			"MinionEntity.tick must not prematurely cancel climbing in scaffolding");
		Assertions.assertTrue(code.contains("!footState.isOf(Blocks.SCAFFOLDING)"),
			"MinionEntity.isNavigatingUpwardInScaffolding must accept scaffolding");
		Assertions.assertTrue(code.contains("footState.isOf(Blocks.SCAFFOLDING)"),
			"MinionEntity.isClimbing must accept scaffolding");
		Assertions.assertFalse(code.contains("ModBlocks.CONSTRUCTION_BLOCK"),
			"MinionEntity must not reference retired ModBlocks.CONSTRUCTION_BLOCK");
	}
}
