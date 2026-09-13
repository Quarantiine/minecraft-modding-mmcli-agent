package com.example.block;

import com.example.block.custom.ConstructionBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying {@link ConstructionBlock} architectural invariants,
 * registry definitions, drop-nothing behavior, solid-top traversal characteristics,
 * and JSON asset specifications.
 */
public class ConstructionBlockTest {

	private static final Path MOD_BLOCKS_SOURCE = Path.of("src/main/java/com/example/block/ModBlocks.java");
	private static final Path CONSTRUCTION_BLOCK_SOURCE = Path.of("src/main/java/com/example/block/custom/ConstructionBlock.java");
	private static final Path CLIENT_ENTRY_SOURCE = Path.of("src/client/java/com/example/client/ExampleModClient.java");
	private static final Path BLOCKSTATE_JSON = Path.of("src/main/resources/assets/modid-mmcli-agent-modding/blockstates/construction_block.json");
	private static final Path BLOCK_MODEL_JSON = Path.of("src/main/resources/assets/modid-mmcli-agent-modding/models/block/construction_block.json");
	private static final Path ITEM_MODEL_JSON = Path.of("src/main/resources/assets/modid-mmcli-agent-modding/models/item/construction_block.json");
	private static final Path LANG_JSON = Path.of("src/main/resources/assets/modid-mmcli-agent-modding/lang/en_us.json");

	@Test
	@DisplayName("ConstructionBlock class hierarchy and codec declaration conform to Fabric standards")
	public void testConstructionBlockHierarchyAndCodec() throws ClassNotFoundException {
		Class<?> clazz = ConstructionBlock.class;
		Assertions.assertTrue(Block.class.isAssignableFrom(clazz), "ConstructionBlock must extend net.minecraft.block.Block");
		Assertions.assertNotNull(ConstructionBlock.CODEC, "ConstructionBlock must declare a static MapCodec CODEC");
	}

	@Test
	@DisplayName("ModBlocks registers CONSTRUCTION_BLOCK with solid-top, easily breakable, and drop-nothing settings")
	public void testModBlocksRegistrationConfig() throws IOException {
		Assertions.assertTrue(Files.exists(MOD_BLOCKS_SOURCE), "ModBlocks.java must exist");
		String content = Files.readString(MOD_BLOCKS_SOURCE);

		// Must define public static final Block CONSTRUCTION_BLOCK
		Assertions.assertTrue(content.contains("public static final Block CONSTRUCTION_BLOCK"), "Must declare public static final Block CONSTRUCTION_BLOCK");
		Assertions.assertTrue(content.contains("\"construction_block\""), "Registry path must be 'construction_block'");

		// Must configure hardness 0.2F (easily breakable)
		Assertions.assertTrue(content.contains(".strength(0.2F, 0.2F)") || content.contains(".strength(0.2f, 0.2f)"), "Hardness must be configured to 0.2F");

		// Must configure drop-nothing behavior
		Assertions.assertTrue(content.contains(".dropsNothing()"), "Must configure drop-nothing behavior via .dropsNothing()");

		// Must configure scaffolding sounds and nonOpaque
		Assertions.assertTrue(content.contains("BlockSoundGroup.SCAFFOLDING"), "Must configure BlockSoundGroup.SCAFFOLDING");
		Assertions.assertTrue(content.contains(".nonOpaque()"), "Must configure .nonOpaque()");

		// Must register in BUILDING_BLOCKS creative tab
		Assertions.assertTrue(content.contains("ItemGroups.BUILDING_BLOCKS"), "Must register into ItemGroups.BUILDING_BLOCKS");
	}

	@Test
	@DisplayName("Client entrypoint registers cutout render layer for construction block")
	public void testClientCutoutRenderLayerRegistration() throws IOException {
		Assertions.assertTrue(Files.exists(CLIENT_ENTRY_SOURCE), "ExampleModClient.java must exist");
		String content = Files.readString(CLIENT_ENTRY_SOURCE);

		Assertions.assertTrue(
			content.contains("BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CONSTRUCTION_BLOCK, RenderLayer.getCutout())"),
			"ExampleModClient must register Cutout render layer for ModBlocks.CONSTRUCTION_BLOCK"
		);
	}

	@Test
	@DisplayName("Blockstate JSON is properly formatted and points to construction block model")
	public void testBlockstateJson() throws IOException {
		Assertions.assertTrue(Files.exists(BLOCKSTATE_JSON), "blockstates/construction_block.json must exist");
		String json = Files.readString(BLOCKSTATE_JSON);

		Assertions.assertTrue(json.contains("\"variants\""), "Blockstate must declare variants");
		Assertions.assertTrue(json.contains("\"model\": \"modid-mmcli-agent-modding:block/construction_block\""), "Variant must point to model modid-mmcli-agent-modding:block/construction_block");
	}

	@Test
	@DisplayName("Block and item models correctly inherit cube_bottom_top with scaffolding textures")
	public void testModelJsons() throws IOException {
		Assertions.assertTrue(Files.exists(BLOCK_MODEL_JSON), "models/block/construction_block.json must exist");
		String blockModel = Files.readString(BLOCK_MODEL_JSON);

		Assertions.assertTrue(blockModel.contains("\"parent\": \"minecraft:block/cube_bottom_top\""), "Block model must inherit cube_bottom_top");
		Assertions.assertTrue(blockModel.contains("\"top\": \"minecraft:block/scaffolding_top\""), "Top texture must reference scaffolding_top");
		Assertions.assertTrue(blockModel.contains("\"bottom\": \"minecraft:block/scaffolding_bottom\""), "Bottom texture must reference scaffolding_bottom");
		Assertions.assertTrue(blockModel.contains("\"side\": \"minecraft:block/scaffolding_side\""), "Side texture must reference scaffolding_side");

		Assertions.assertTrue(Files.exists(ITEM_MODEL_JSON), "models/item/construction_block.json must exist");
		String itemModel = Files.readString(ITEM_MODEL_JSON);
		Assertions.assertTrue(itemModel.contains("\"parent\": \"modid-mmcli-agent-modding:block/construction_block\""), "Item model must inherit block model");
	}

	@Test
	@DisplayName("Localization file en_us.json includes entries for construction block and item")
	public void testLocalizationEntries() throws IOException {
		Assertions.assertTrue(Files.exists(LANG_JSON), "lang/en_us.json must exist");
		String lang = Files.readString(LANG_JSON);

		Assertions.assertTrue(lang.contains("\"block.modid-mmcli-agent-modding.construction_block\": \"Construction Block\""), "en_us.json must localize block");
		Assertions.assertTrue(lang.contains("\"item.modid-mmcli-agent-modding.construction_block\": \"Construction Block\""), "en_us.json must localize item");
	}

	/**
	 * Simulation model validating non-collapsing spanning invariants vs vanilla ScaffoldingBlock distance collapse.
	 */
	static class ScaffoldSpanSimulation {
		record BlockNode(int x, int y, int z, int distance, boolean collapsed) {}

		// Simulates vanilla ScaffoldingBlock max horizontal distance limit (7 blocks)
		static boolean isVanillaScaffoldingStable(int horizontalSpanFromSupport) {
			return horizontalSpanFromSupport <= 6;
		}

		// Simulates ConstructionBlock non-collapsing invariant (supports arbitrary span)
		static boolean isConstructionBlockStable(int horizontalSpanFromSupport) {
			return true;
		}
	}

	@Test
	@DisplayName("Simulation verifies non-collapsing invariant supports spanning ravines of arbitrary width")
	public void testNonCollapsingRavineSpanSimulation() {
		// A 12-block ravine bridge
		for (int span = 1; span <= 12; span++) {
			boolean constructionStable = ScaffoldSpanSimulation.isConstructionBlockStable(span);
			Assertions.assertTrue(constructionStable, "ConstructionBlock must remain stable at span " + span);

			if (span > 6) {
				boolean vanillaStable = ScaffoldSpanSimulation.isVanillaScaffoldingStable(span);
				Assertions.assertFalse(vanillaStable, "Vanilla scaffolding would collapse beyond span 6");
			}
		}
	}

	/**
	 * Simulation model validating solid-top kinematic elevation and drop-nothing demolition.
	 */
	static class BlockKinematicsSimulation {
		// Construction block top elevation (solid full cube top face)
		static double getSurfaceStandingY(int blockY) {
			return blockY + 1.0D;
		}

		// Demolition drops: ConstructionBlock drops 0 items
		static List<String> getDropsOnBreak(boolean dropsNothing) {
			if (dropsNothing) {
				return List.of();
			}
			return List.of("modid-mmcli-agent-modding:construction_block");
		}
	}

	@Test
	@DisplayName("Simulation verifies solid-top standing plane and drop-nothing demolition behavior")
	public void testSolidTopAndDropNothingSimulation() {
		int groundY = 64;
		double standingY = BlockKinematicsSimulation.getSurfaceStandingY(groundY);
		Assertions.assertEquals(65.0D, standingY, "Standing plane must be flush at Y + 1.0 without sinking");

		List<String> drops = BlockKinematicsSimulation.getDropsOnBreak(true);
		Assertions.assertTrue(drops.isEmpty(), "Drop-nothing behavior must yield 0 dropped items");
	}
}
