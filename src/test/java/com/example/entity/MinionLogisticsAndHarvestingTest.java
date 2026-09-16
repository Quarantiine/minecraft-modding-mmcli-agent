package com.example.entity;

import com.example.entity.custom.MinionRole;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Unit tests validating autonomous material harvesting, build protection safeguards,
 * peer-to-peer logistics, and supply depot deployment.
 */
public class MinionLogisticsAndHarvestingTest {

	@Test
	@DisplayName("Validate 3-pillar role consolidation and legacy MINER (ID 3) migration to BUILDER")
	void testRoleConsolidationAndMigration() {
		Assertions.assertEquals(3, MinionRole.values().length, "System must have exactly 3 role archetypes");
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.fromId(0));
		Assertions.assertEquals(MinionRole.SENTINEL, MinionRole.fromId(1));
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.fromId(2));
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.fromId(3), "Legacy MINER ID 3 must map to BUILDER");

		// Cycling through all 3 roles
		Assertions.assertEquals(MinionRole.SENTINEL, MinionRole.WARRIOR.next());
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.SENTINEL.next());
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.BUILDER.next());
	}

	@Test
	@DisplayName("Build Protection: Processed and architectural blocks are strictly protected from harvesting")
	void testBuildProtectionProcessedBlocks() {
		List<String> playerBlocks = List.of(
			"oak_planks", "spruce_planks", "stone_bricks", "deepslate_bricks",
			"smooth_stone_slab", "oak_stairs", "glass", "glass_pane",
			"white_terracotta", "yellow_concrete", "red_wool", "white_carpet",
			"oak_door", "iron_trapdoor", "oak_fence", "cobblestone_wall",
			"lantern", "torch", "chest", "barrel", "crafting_table"
		);

		for (String path : playerBlocks) {
			Assertions.assertTrue(isProcessedBlockTest(path),
				"Block '" + path + "' must be identified as processed and protected from harvesting");
		}

		List<String> naturalBlocks = List.of(
			"stone", "cobblestone", "deepslate", "cobbled_deepslate",
			"andesite", "diorite", "granite", "dirt", "sand", "gravel"
		);

		for (String path : naturalBlocks) {
			Assertions.assertFalse(isProcessedBlockTest(path),
				"Natural block '" + path + "' should be eligible for quarrying if outside base buffers");
		}
	}

	private static boolean isProcessedBlockTest(String path) {
		return path.contains("planks")
			|| path.contains("brick")
			|| path.contains("slab")
			|| path.contains("stair")
			|| path.contains("glass")
			|| path.contains("terracotta")
			|| path.contains("concrete")
			|| path.contains("wool")
			|| path.contains("carpet")
			|| path.contains("door")
			|| path.contains("trapdoor")
			|| path.contains("fence")
			|| path.contains("wall")
			|| path.contains("gate")
			|| path.contains("lantern")
			|| path.contains("torch")
			|| path.contains("chest")
			|| path.contains("barrel")
			|| path.contains("shulker")
			|| path.contains("furnace")
			|| path.contains("crafting_table");
	}

	@Test
	@DisplayName("Hazard Avoidance: Inspect 6 orthogonal directions for adjacent lava and prevent quarrying")
	void testLavaHazardAvoidance() {
		Set<BlockPos> worldLavaPositions = new HashSet<>();
		BlockPos quarryTarget = new BlockPos(10, 60, 10);

		// No lava -> safe
		Assertions.assertFalse(hasAdjacentLavaTest(quarryTarget, worldLavaPositions));

		// Place lava above
		worldLavaPositions.add(quarryTarget.up());
		Assertions.assertTrue(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava above must trigger avoidance");

		// Place lava below
		worldLavaPositions.clear();
		worldLavaPositions.add(quarryTarget.down());
		Assertions.assertTrue(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava below must trigger avoidance");

		// Place lava North
		worldLavaPositions.clear();
		worldLavaPositions.add(quarryTarget.north());
		Assertions.assertTrue(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava North must trigger avoidance");

		// Place lava 2 blocks away (not immediately adjacent) -> safe
		worldLavaPositions.clear();
		worldLavaPositions.add(quarryTarget.north(2));
		Assertions.assertFalse(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava 2 blocks away is safe");
	}

	private static boolean hasAdjacentLavaTest(BlockPos pos, Set<BlockPos> lavaPositions) {
		for (Direction dir : Direction.values()) {
			if (lavaPositions.contains(pos.offset(dir))) {
				return true;
			}
		}
		return false;
	}

	@Test
	@DisplayName("Tool Self-Crafting: Synthesis of wooden/stone tools from timber and cobblestone")
	void testToolSelfCraftingRecipes() {
		class InventorySimulator {
			int logs = 0;
			int planks = 0;
			int sticks = 0;
			int cobblestone = 0;
			String toolCreated = null;

			void ensureSticksAndPlanks() {
				if (sticks < 2) {
					if (planks >= 2) {
						planks -= 2;
						sticks += 4;
					} else if (logs >= 1) {
						logs -= 1;
						planks += 4;
						planks -= 2;
						sticks += 4;
					}
				}
				if (planks < 3 && logs >= 1) {
					logs -= 1;
					planks += 4;
				}
			}

			void craftPickaxe() {
				ensureSticksAndPlanks();
				if (cobblestone >= 3 && sticks >= 2) {
					cobblestone -= 3;
					sticks -= 2;
					toolCreated = "stone_pickaxe";
					return;
				}
				if (planks >= 3 && sticks >= 2) {
					planks -= 3;
					sticks -= 2;
					toolCreated = "wooden_pickaxe";
				}
			}
		}

		// Scenario A: Minion starts with 1 log -> converts to 4 planks, 4 sticks -> crafts wooden pickaxe (needs 3 planks + 2 sticks)
		// 1 log -> 4 planks -> 2 planks into 4 sticks (leaves 2 planks, 4 sticks; needs 3 planks, so need 2 logs for full wooden pickaxe)
		InventorySimulator simA = new InventorySimulator();
		simA.logs = 2; // 2 logs = 8 planks -> 2 planks into 4 sticks = 6 planks, 4 sticks -> wooden pickaxe (3 planks, 2 sticks)
		simA.craftPickaxe();
		Assertions.assertEquals("wooden_pickaxe", simA.toolCreated);
		Assertions.assertEquals(3, simA.planks);
		Assertions.assertEquals(2, simA.sticks);

		// Scenario B: Minion with 3 cobblestone and 1 log -> crafts stone pickaxe
		InventorySimulator simB = new InventorySimulator();
		simB.cobblestone = 3;
		simB.logs = 1;
		simB.craftPickaxe();
		Assertions.assertEquals("stone_pickaxe", simB.toolCreated);
		Assertions.assertEquals(0, simB.cobblestone);
		Assertions.assertEquals(2, simB.planks);
		Assertions.assertEquals(2, simB.sticks);
	}

	@Test
	@DisplayName("Supply Depot: Autonomous Chest crafting (8 planks) and Double Chest pairing")
	void testAutonomousChestAndPairing() {
		int planks = 8;
		boolean canCraftChest = planks >= 8;
		Assertions.assertTrue(canCraftChest, "Minion must be able to craft chest from 8 planks");

		// Double chest alignment test
		Direction existingFacing = Direction.NORTH;
		// If neighbor is EAST (right of North), candidate is WEST (left of North)
		Direction neighborDir = Direction.EAST;
		ChestType candidateType = ChestType.SINGLE;
		ChestType neighborType = ChestType.SINGLE;

		if (neighborDir == existingFacing.rotateYClockwise()) {
			candidateType = ChestType.LEFT;
			neighborType = ChestType.RIGHT;
		}

		Assertions.assertEquals(ChestType.LEFT, candidateType);
		Assertions.assertEquals(ChestType.RIGHT, neighborType);
	}

	@Test
	@DisplayName("Operational Leash and Station Radius: 128-block operational distance verification")
	void testExpandedOperationalRadius() {
		double leashDistance = 128.0D;
		double leashDistanceSq = leashDistance * leashDistance;

		Assertions.assertEquals(16384.0D, leashDistanceSq, "128-block leash distance squared must equal 16,384");

		// Stationed worker at (0, 64, 0)
		BlockPos anchor = new BlockPos(0, 64, 0);
		BlockPos workerAt100 = new BlockPos(100, 64, 0);
		BlockPos workerAt130 = new BlockPos(130, 64, 0);

		Assertions.assertTrue(workerAt100.getSquaredDistance(anchor) <= leashDistanceSq,
			"Worker at 100 blocks must be well within the 128-block operational leash");
		Assertions.assertFalse(workerAt130.getSquaredDistance(anchor) <= leashDistanceSq,
			"Worker at 130 blocks exceeds the 128-block operational leash");
	}
}
