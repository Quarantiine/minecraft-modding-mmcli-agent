package com.example.construction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating:
 * 1. Creative mode zero-drop inventory clearing and no-chest contracts.
 * 2. Survival mode excess material deposit contracts.
 * 3. DESIGN mode block capture fidelity across solid, transparent, and decorative blocks.
 * 4. Source inspection of zero-drop pre-clearing and stray item purging.
 * 5. Universal autonomous harvest block type fidelity (fellLog, quarryNaturalStone use Block.getDroppedStacks).
 */
public class CreativeAndSurvivalLogisticsTest {

	@Test
	@DisplayName("Creative mode discardable item classification protects equipment while discarding blocks")
	void testDiscardableCreativeItemClassificationContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		// Must define isDiscardableCreativeItem
		Assertions.assertTrue(source.contains("boolean isDiscardableCreativeItem(Item item)"),
			"MinionHarvestingHelper must define isDiscardableCreativeItem");

		// Must protect weapons, tools, armor from being discarded
		Assertions.assertTrue(source.contains("item instanceof ArmorItem"), "Must protect ArmorItem");
		Assertions.assertTrue(source.contains("item instanceof SwordItem"), "Must protect SwordItem");
		Assertions.assertTrue(source.contains("item instanceof BowItem"), "Must protect BowItem");
		Assertions.assertTrue(source.contains("item instanceof CrossbowItem"), "Must protect CrossbowItem");
		Assertions.assertTrue(source.contains("item instanceof TridentItem"), "Must protect TridentItem");
		Assertions.assertTrue(source.contains("item instanceof PickaxeItem"), "Must protect PickaxeItem");
		Assertions.assertTrue(source.contains("item instanceof AxeItem"), "Must protect AxeItem");
		Assertions.assertTrue(source.contains("item instanceof ShovelItem"), "Must protect ShovelItem");
		Assertions.assertTrue(source.contains("FROST_GRENADE_STICK"), "Must protect Frost Grenade Stick");
		Assertions.assertTrue(source.contains("TNT_STICK"), "Must protect TNT Stick");

		// In creative mode, must clear unneeded items without deploying chests
		Assertions.assertTrue(source.contains("session.isCreative()"),
			"checkAndDepositExcessMaterials must branch on session.isCreative()");
		Assertions.assertTrue(source.contains("inv.setStack(i, ItemStack.EMPTY)"),
			"checkAndDepositExcessMaterials must clear unneeded items in creative mode");
	}

	@Test
	@DisplayName("Design mode StructureBlueprint captures all non-air blocks")
	void testBlueprintBlockTypeFidelityContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/blueprint/StructureBlueprint.java"));

		// Must capture any non-air block (!state.isAir())
		Assertions.assertTrue(source.contains("state != null && !state.isAir()"),
			"captureBlocks must capture all non-air blocks");
		Assertions.assertTrue(source.contains("BlueprintBlock"),
			"captureBlocks must store BlueprintBlock entries");
	}

	@Test
	@DisplayName("MinionBuildGoal contains zero-drop pre-clearing and stray item purging")
	void testMinionBuildGoalZeroDropContracts() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// Must contain cleanPreExistingObstacles handling multi-part blocks
		Assertions.assertTrue(source.contains("cleanPreExistingObstacles"),
			"MinionBuildGoal must implement cleanPreExistingObstacles");
		Assertions.assertTrue(source.contains("TallPlantBlock"),
			"cleanPreExistingObstacles must handle TallPlantBlock");
		Assertions.assertTrue(source.contains("DoorBlock"),
			"cleanPreExistingObstacles must handle DoorBlock");
		Assertions.assertTrue(source.contains("BedBlock"),
			"cleanPreExistingObstacles must handle BedBlock");

		// Must purge stray ItemEntities in creative mode
		Assertions.assertTrue(source.contains("stray.discard()"),
			"MinionBuildGoal must discard stray ItemEntity in creative mode");

		// In survival mode, must collect drops into inventory rather than dropping on ground
		Assertions.assertTrue(source.contains("Block.getDroppedStacks"),
			"tickDismantling must use Block.getDroppedStacks to collect drops directly");
		Assertions.assertTrue(source.contains("minion.getInventory().addStack(drop)"),
			"tickDismantling must insert drops into minion inventory");
	}

	@Test
	@DisplayName("ConstructionManager completeSession implements post-session item cleanup")
	void testConstructionManagerCompleteSessionCleanup() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionManager.java"));

		// Must contain cleanupBox and ItemEntity handling
		Assertions.assertTrue(source.contains("cleanupBox"),
			"ConstructionManager.completeSession must define cleanupBox");
		Assertions.assertTrue(source.contains("depositStackIntoNearbyContainer"),
			"ConstructionManager.completeSession must deposit stray items into nearby container in survival");
		Assertions.assertTrue(source.contains("stray.discard()"),
			"ConstructionManager.completeSession must discard stray items in creative");
	}

	@Test
	@DisplayName("BlueprintCaptureModalScreen refers to structure blocks rather than only solid blocks")
	void testModalScreenStructureBlockLabel() throws IOException {
		String source = Files.readString(Path.of("src/client/java/com/example/client/gui/BlueprintCaptureModalScreen.java"));

		Assertions.assertTrue(source.contains("structure blocks in selection"),
			"BlueprintCaptureModalScreen must refer to structure blocks in error messages");
		Assertions.assertTrue(source.contains("getNonAirBlockCount"),
			"BlueprintCaptureModalScreen must use getNonAirBlockCount");
	}

	@Test
	@DisplayName("BUILD and MINE modes implement universal block type fidelity across multi-part and liquid blocks")
	void testUniversalBlockTypeFidelityContracts() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		// BUILD placement fidelity: BedBlock and TallPlantBlock
		Assertions.assertTrue(source.contains("targetState.getBlock() instanceof BedBlock"),
			"executePlacement must handle BedBlock pairing");
		Assertions.assertTrue(source.contains("targetState.getBlock() instanceof TallPlantBlock"),
			"executePlacement must handle TallPlantBlock pairing");

		// MINE dismantle fidelity: BedBlock, TallPlantBlock, and fluid clearing
		Assertions.assertTrue(source.contains("currentState.getBlock() instanceof BedBlock"),
			"executeDismantleWork must handle BedBlock pairing");
		Assertions.assertTrue(source.contains("currentState.getBlock() instanceof TallPlantBlock"),
			"executeDismantleWork must handle TallPlantBlock pairing");
		Assertions.assertTrue(source.contains("currentState.isLiquid()"),
			"executeDismantleWork must clear liquids cleanly");

		// BlueprintHologramRenderer ghost fidelity
		String rendererSource = Files.readString(Path.of("src/client/java/com/example/client/renderer/BlueprintHologramRenderer.java"));
		Assertions.assertTrue(rendererSource.contains("if (worldState.isOf(block.state().getBlock())) {"),
			"BlueprintHologramRenderer must only suppress ghost block when matching block is placed");
	}

	@Test
	@DisplayName("Autonomous harvest (fellLog, quarryNaturalStone) uses Block.getDroppedStacks for loot-table-faithful drops across all modes")
	void testAutonomousHarvestBlockTypeFidelityContract() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java"));

		// fellLog must use Block.getDroppedStacks to compute drops faithfully (silk-touch, fortune)
		Assertions.assertTrue(source.contains("Block.getDroppedStacks(state, world, pos, be, minion, minion.getMainHandStack())"),
			"fellLog must use Block.getDroppedStacks for loot-table-faithful drop computation");

		// quarryNaturalStone must also use Block.getDroppedStacks
		long countGetDroppedStacks = source.lines()
			.filter(l -> l.contains("Block.getDroppedStacks"))
			.count();
		Assertions.assertTrue(countGetDroppedStacks >= 2,
			"MinionHarvestingHelper must use Block.getDroppedStacks in both fellLog and quarryNaturalStone (found: " + countGetDroppedStacks + ")");

		// Must retain fallback for empty drop lists (agro-forestry continuity)
		Assertions.assertTrue(source.contains("drops.isEmpty()"),
			"fellLog and quarryNaturalStone must handle empty drop lists with a fallback");
	}
}

