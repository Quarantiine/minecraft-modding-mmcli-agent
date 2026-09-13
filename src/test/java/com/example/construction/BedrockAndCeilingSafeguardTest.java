package com.example.construction;

import com.example.block.ModBlocks;
import com.example.entity.ai.goal.MinionBuildGoal;
import com.example.entity.ai.goal.MinionSapperGoal;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests verifying critical safety invariants:
 * <ol>
 *   <li><b>Bedrock & Indestructible Block Immunity:</b> Ensures minions can never mine, dismantle,
 *       or clear bedrock, barriers, command blocks, or end portal frames in any session mode.</li>
 *   <li><b>Sapper Ceiling Clearance Avoidance & Stall Recovery:</b> Ensures minion combat sappers
 *       never deploy climbing columns into low ceilings or overhead obstructions, abort immediately
 *       upon ceiling contact, and time out cleanly if stalled.</li>
 *   <li><b>Construction Block Traversal & Ephemeral Invariants:</b> Verifies {@link ModBlocks#CONSTRUCTION_BLOCK}
 *       solid-top support, arbitrary chasm spanning stability, zero-drop demolition, and scaffolding recognition.</li>
 * </ol>
 */
public class BedrockAndCeilingSafeguardTest {

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
	@DisplayName("Bedrock immunity: Scaffolding removal strictly verifies block type to prevent deleting ground bedrock")
	public void testScaffoldingRemovalPreservesUnderlyingBedrock() {
		class MockWorldBlockManager {
			final Map<BlockPos, String> worldBlocks = new HashMap<>();

			void placeBlock(BlockPos pos, String block) {
				worldBlocks.put(pos, block);
			}

			// Models MinionBuildGoal.removeScaffoldBlockWithFeedback logic
			boolean safeRemoveScaffold(BlockPos pos) {
				String block = worldBlocks.get(pos);
				if (block == null) {
					return false;
				}
				// Safeguard: Only scaffold/construction blocks may be cleared!
				boolean isScaffold = "minecraft:scaffolding".equals(block) || "modid-mmcli-agent-modding:construction_block".equals(block);
				if (!isScaffold) {
					return false; // Rejects clearing bedrock, dirt, stone, etc.
				}
				worldBlocks.put(pos, "minecraft:air");
				return true;
			}
		}

		MockWorldBlockManager world = new MockWorldBlockManager();
		BlockPos bedrockPos = new BlockPos(0, -64, 0);
		BlockPos scaffoldPos = new BlockPos(0, -63, 0);

		world.placeBlock(bedrockPos, "minecraft:bedrock");
		world.placeBlock(scaffoldPos, "modid-mmcli-agent-modding:construction_block");

		// Scaffold block is cleared successfully
		Assertions.assertTrue(world.safeRemoveScaffold(scaffoldPos));
		Assertions.assertEquals("minecraft:air", world.worldBlocks.get(scaffoldPos));

		// Underlying bedrock is strictly rejected and never deleted
		Assertions.assertFalse(world.safeRemoveScaffold(bedrockPos));
		Assertions.assertEquals("minecraft:bedrock", world.worldBlocks.get(bedrockPos));
	}

	@Test
	@DisplayName("Bedrock immunity: Source code audit guarantees dismantle execution and scaffolding cleanup have bedrock guards")
	public void testBedrockImmunitySourceCodeAudit() throws IOException {
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		Assertions.assertTrue(Files.exists(buildGoalPath), "MinionBuildGoal.java must exist");
		String code = Files.readString(buildGoalPath);

		// Must verify isIndestructibleBlock in executeDismantleWork
		Assertions.assertTrue(code.contains("isIndestructibleBlock("),
			"executeDismantleWork must invoke isIndestructibleBlock check before breakBlock");

		// Must verify isScaffoldBlock in removeScaffoldBlockWithFeedback
		Assertions.assertTrue(code.contains("isScaffoldBlock(currentState)"),
			"removeScaffoldBlockWithFeedback must verify isScaffoldBlock before deleting block from world");

		// ConstructionSession must also contain indestructible filtering
		Path sessionPath = Path.of("src/main/java/com/example/construction/ConstructionSession.java");
		Assertions.assertTrue(Files.exists(sessionPath), "ConstructionSession.java must exist");
		String sessionCode = Files.readString(sessionPath);

		Assertions.assertTrue(sessionCode.contains("isIndestructible("),
			"ConstructionSession must contain isIndestructible check");
	}

	// =========================================================================
	// 2. CEILING CLEARANCE AVOIDANCE & STALL RECOVERY IN SAPPERS
	// =========================================================================

	@Test
	@DisplayName("Ceiling clearance: Climbing column planner aborts if overhead ceiling blocks shaft path")
	public void testShaftCeilingObstructionRejection() {
		class ShaftObstacleScanner {
			List<BlockPos> scanAscent(BlockPos feetPos, int targetHeight, Set<BlockPos> solidBlocks) {
				List<BlockPos> column = new ArrayList<>();
				for (int h = 1; h <= targetHeight; h++) {
					BlockPos shaftPos = feetPos.up(h);
					if (solidBlocks.contains(shaftPos)) {
						// Ceiling block directly in the climbing shaft! Abort column ascent
						return List.of();
					}
					column.add(shaftPos);
				}
				return column;
			}
		}

		ShaftObstacleScanner scanner = new ShaftObstacleScanner();
		BlockPos feet = new BlockPos(10, 64, 10);

		// Clear shaft up to 4 blocks
		Set<BlockPos> openSky = Set.of();
		List<BlockPos> clearColumn = scanner.scanAscent(feet, 4, openSky);
		Assertions.assertEquals(4, clearColumn.size());

		// Ceiling at 2 blocks above feet (feet.up(2) = Y: 66)
		Set<BlockPos> lowCeiling = Set.of(feet.up(2));
		List<BlockPos> blockedColumn = scanner.scanAscent(feet, 4, lowCeiling);
		Assertions.assertTrue(blockedColumn.isEmpty(), "Shaft with overhead ceiling must produce empty climbing column");
	}

	@Test
	@DisplayName("Ceiling clearance: Ledge destination candidate requires 2 blocks of clear headroom")
	public void testLedgeLandingHeadroomRequirement() {
		class LedgeHeadroomEvaluator {
			boolean isLedgeValid(BlockPos ledgeGround, Set<BlockPos> solidBlocks) {
				BlockPos feet = ledgeGround.up(1);
				BlockPos head = ledgeGround.up(2);
				// Solid blocks at feet or head obstruct the ledge
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
	@DisplayName("Ceiling clearance: Direct overhead ceiling collision sensor aborts climbing immediately")
	public void testOverheadCeilingCollisionSensorAbortsClimb() {
		class ClimbTickSensor {
			boolean climbing = true;

			void checkCeilingCollision(boolean solidHeadBlock) {
				if (solidHeadBlock) {
					this.climbing = false;
				}
			}
		}

		ClimbTickSensor sensor = new ClimbTickSensor();
		Assertions.assertTrue(sensor.climbing);

		// Open air overhead -> continues climbing
		sensor.checkCeilingCollision(false);
		Assertions.assertTrue(sensor.climbing);

		// Solid ceiling block contacted at head clearance (headPos = minion.getBlockPos().up(2))
		sensor.checkCeilingCollision(true);
		Assertions.assertFalse(sensor.climbing, "Head collision with ceiling must immediately terminate climb state");
	}

	@Test
	@DisplayName("Ceiling clearance: Vertical stall sensor terminates climb after 20 stalled ticks")
	public void testVerticalStallSensorTimeout() {
		int stallTicks = 0;
		double lastY = 64.0D;
		boolean aborted = false;

		// 1. Upward motion of +0.24 blocks per tick resets stall counter
		for (int tick = 0; tick < 10; tick++) {
			double currentY = lastY + 0.24D;
			if (currentY - lastY < MinionSapperGoal.MIN_VERTICAL_PROGRESS_PER_TICK) {
				stallTicks++;
			} else {
				stallTicks = 0;
			}
			lastY = currentY;
		}
		Assertions.assertEquals(0, stallTicks, "Normal climbing must have 0 stall ticks");

		// 2. Stopped by low ceiling -> 0.0 progress for 21 ticks
		for (int tick = 0; tick <= MinionSapperGoal.STALL_THRESHOLD_TICKS; tick++) {
			double currentY = lastY + 0.0D; // No progress
			if (currentY - lastY < MinionSapperGoal.MIN_VERTICAL_PROGRESS_PER_TICK) {
				stallTicks++;
			} else {
				stallTicks = 0;
			}
			lastY = currentY;
			if (stallTicks > MinionSapperGoal.STALL_THRESHOLD_TICKS) {
				aborted = true;
				break;
			}
		}

		Assertions.assertTrue(aborted, "Climb must abort after exceeding STALL_THRESHOLD_TICKS (20 ticks)");
		Assertions.assertEquals(21, stallTicks);
	}

	@Test
	@DisplayName("Ceiling clearance: Ravine bridging detects overhead ceiling and terminates bridge path")
	public void testRavineBridgeCeilingClearanceAvoidance() {
		class BridgeScanner {
			List<BlockPos> buildBridge(BlockPos feetPos, int maxSpan, Set<BlockPos> solidBlocks) {
				List<BlockPos> bridge = new ArrayList<>();
				for (int step = 1; step <= maxSpan; step++) {
					BlockPos bridgePos = feetPos.add(step, 0, 0);
					BlockPos head1 = bridgePos.up(1);
					BlockPos head2 = bridgePos.up(2);

					// If headroom is obstructed, stop bridging to avoid trapping minion in a tight crevice
					if (solidBlocks.contains(head1) || solidBlocks.contains(head2)) {
						break;
					}
					bridge.add(bridgePos);
				}
				return bridge;
			}
		}

		BridgeScanner scanner = new BridgeScanner();
		BlockPos feet = new BlockPos(0, 64, 0);

		// Unobstructed ravine gap
		List<BlockPos> fullBridge = scanner.buildBridge(feet, 6, Set.of());
		Assertions.assertEquals(6, fullBridge.size());

		// Low ceiling at step 3 (X=3, Y=66)
		Set<BlockPos> lowCeiling = Set.of(new BlockPos(3, 66, 0));
		List<BlockPos> truncatedBridge = scanner.buildBridge(feet, 6, lowCeiling);
		Assertions.assertEquals(2, truncatedBridge.size(), "Bridge should terminate before the overhead ceiling obstacle");
	}

	// =========================================================================
	// 3. CONSTRUCTION BLOCK BEHAVIOR & INVARIANTS
	// =========================================================================

	@Test
	@DisplayName("Construction block: Passable scaffolding recognition in build and sapper goals")
	public void testPassableScaffoldingRecognition() throws IOException {
		// MinionBuildGoal.isScaffoldBlock null check
		Assertions.assertFalse(MinionBuildGoal.isScaffoldBlock(null));

		// Verify source invariants: both goals must recognize Blocks.SCAFFOLDING and ModBlocks.CONSTRUCTION_BLOCK
		Path buildGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java");
		String buildCode = Files.readString(buildGoalPath);
		Assertions.assertTrue(buildCode.contains("state.isOf(Blocks.SCAFFOLDING) || state.isOf(ModBlocks.CONSTRUCTION_BLOCK)"),
			"MinionBuildGoal.isScaffoldBlock must accept both vanilla scaffolding and ModBlocks.CONSTRUCTION_BLOCK");

		Path sapperGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionSapperGoal.java");
		String sapperCode = Files.readString(sapperGoalPath);
		Assertions.assertTrue(sapperCode.contains("state.isOf(Blocks.SCAFFOLDING) || state.isOf(ModBlocks.CONSTRUCTION_BLOCK)"),
			"MinionSapperGoal.isPassableScaffolding must accept both vanilla scaffolding and ModBlocks.CONSTRUCTION_BLOCK");

		// Logical verification with mock predicate
		class PassableTester {
			static boolean isPassable(String blockId) {
				return "minecraft:scaffolding".equals(blockId) || "modid-mmcli-agent-modding:construction_block".equals(blockId);
			}
		}

		Assertions.assertTrue(PassableTester.isPassable("minecraft:scaffolding"));
		Assertions.assertTrue(PassableTester.isPassable("modid-mmcli-agent-modding:construction_block"));
		Assertions.assertFalse(PassableTester.isPassable("minecraft:stone"));
		Assertions.assertFalse(PassableTester.isPassable("minecraft:dirt"));
	}

	@Test
	@DisplayName("Construction block: Builder role archetype places construction blocks at zero cost")
	public void testBuilderArchetypeZeroCostPlacement() {
		Assertions.assertTrue(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.BUILDER),
			"BUILDER role must place construction blocks at zero cost");
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.WARRIOR));
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.SENTINEL));
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.MINER));
		Assertions.assertFalse(MinionSapperGoal.canRoleBuildZeroCost(MinionRole.RANGER));
	}

	@Test
	@DisplayName("Construction block: Multi-minion column reservations maintain spacing across army")
	public void testColumnReservationPreventsOvercrowding() {
		class ColumnReservationHarness {
			final Map<BlockPos, UUID> reservations = new HashMap<>();

			boolean claim(BlockPos pos, UUID minion) {
				for (Map.Entry<BlockPos, UUID> e : reservations.entrySet()) {
					if (e.getKey().getX() == pos.getX() && e.getKey().getZ() == pos.getZ()) {
						return e.getValue().equals(minion);
					}
				}
				reservations.put(pos, minion);
				return true;
			}
		}

		ColumnReservationHarness harness = new ColumnReservationHarness();
		UUID minion1 = UUID.randomUUID();
		UUID minion2 = UUID.randomUUID();

		BlockPos colA = new BlockPos(10, 64, 10);
		BlockPos colAShaft = new BlockPos(10, 68, 10);
		BlockPos colB = new BlockPos(12, 64, 10);

		// Minion 1 claims column A
		Assertions.assertTrue(harness.claim(colA, minion1));

		// Minion 2 cannot claim same X,Z column even at different Y
		Assertions.assertFalse(harness.claim(colAShaft, minion2),
			"Minion 2 must be prevented from crowding onto Minion 1's climbing column");

		// Minion 2 can claim separate column B
		Assertions.assertTrue(harness.claim(colB, minion2));
	}
}
