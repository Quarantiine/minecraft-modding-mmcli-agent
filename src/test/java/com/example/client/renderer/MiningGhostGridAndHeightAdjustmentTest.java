package com.example.client.renderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating BlueprintHologramRenderer MINE AREA mode in-world fiery ghost grid wireframes,
 * mouse scroll / bracket key height adjustments in MouseMixin and ExampleModClient,
 * and translation key completeness in en_us.json.
 */
public class MiningGhostGridAndHeightAdjustmentTest {

	@Test
	@DisplayName("Source Invariant: BlueprintHologramRenderer renders fiery orange/amber ghost grid wireframes for MINE AREA mode")
	void testMiningGhostGridSourceInvariants() throws IOException {
		Path rendererPath = Path.of("src/client/java/com/example/client/renderer/BlueprintHologramRenderer.java");
		Assertions.assertTrue(Files.exists(rendererPath), "BlueprintHologramRenderer.java must exist");
		String content = Files.readString(rendererPath);

		// Verified method definitions
		Assertions.assertTrue(
			content.contains("public static void renderMiningGhostGrid("),
			"BlueprintHologramRenderer must define renderMiningGhostGrid helper method"
		);
		Assertions.assertTrue(
			content.contains("renderMiningBoundingBox("),
			"BlueprintHologramRenderer must define renderMiningBoundingBox helper method"
		);

		// Verified MINE AREA mode distinction
		Assertions.assertTrue(
			content.contains("isMineAreaMode"),
			"BlueprintHologramRenderer must compute isMineAreaMode boolean"
		);
		Assertions.assertTrue(
			content.contains("isMineDirectMode"),
			"BlueprintHologramRenderer must compute isMineDirectMode boolean"
		);

		// Verified complete selection invocation
		Assertions.assertTrue(
			content.contains("ClientMiningCaptureTracker.hasCompleteSelection()") &&
			content.contains("renderMiningGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 1.0F);"),
			"BlueprintHologramRenderer must render fiery ghost grid blocks when Pos1 and Pos2 mining selection is complete"
		);

		// Verified candidate aiming invocation
		Assertions.assertTrue(
			content.contains("miningTargetHit != null") &&
			content.contains("renderMiningGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 0.65F);"),
			"BlueprintHologramRenderer must render ghost grid blocks when aiming from Pos1 towards target block in AREA mode"
		);

		// Verified active mining session persistent rendering
		Assertions.assertTrue(
			content.contains("sessionData.isDismantle() && sessionData.sizeX() > 0 && sessionData.sizeY() > 0 && sessionData.sizeZ() > 0") &&
			content.contains("renderMiningBoundingBox(matrices, buffer, quarryBox, 1.0F, 0.45F, 0.05F, 0.85F, sessionData.anchorPos(), new BlockPos(maxX, maxY, maxZ));") &&
			content.contains("renderMiningGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 1.0F);"),
			"BlueprintHologramRenderer must persistently render fiery quarry bounding box and ghost grid for active area dismantle sessions"
		);

		// Verified fiery orange/amber color coding
		Assertions.assertTrue(
			content.contains("1.0F, 0.45F, 0.05F"),
			"BlueprintHologramRenderer must render fiery orange/amber bounding box and grid"
		);
		Assertions.assertTrue(
			content.contains("COAL_ORE") && content.contains("1.0F, 0.85F, 0.10F"),
			"BlueprintHologramRenderer must render brilliant golden/amber ghost grid boxes for valuable ores"
		);
		Assertions.assertTrue(
			content.contains("hardness < 0.0F"),
			"BlueprintHologramRenderer must safeguard indestructible bedrock blocks in ghost grid"
		);
	}

	@Test
	@DisplayName("Source Invariant: MouseMixin wires mouse scroll height adjustment for MINE AREA mode")
	void testMouseMixinMiningHeightScroll() throws IOException {
		Path mouseMixinPath = Path.of("src/client/java/com/example/mixin/client/MouseMixin.java");
		Assertions.assertTrue(Files.exists(mouseMixinPath), "MouseMixin.java must exist");
		String content = Files.readString(mouseMixinPath);

		Assertions.assertTrue(
			content.contains("ClientMiningCaptureTracker.adjustHeight(step)"),
			"MouseMixin must call ClientMiningCaptureTracker.adjustHeight on scroll"
		);
		Assertions.assertTrue(
			content.contains("CommandMode.MINE") && content.contains("MiningMode.AREA"),
			"MouseMixin must check CommandMode.MINE with MiningMode.AREA"
		);
		Assertions.assertTrue(
			content.contains("Mining Box Height:"),
			"MouseMixin must provide actionbar feedback for mining box height adjustment"
		);
	}

	@Test
	@DisplayName("Source Invariant: ExampleModClient wires bracket keys and PageUp/PageDown for MINE AREA mode")
	void testExampleModClientMiningHeightKeys() throws IOException {
		Path clientPath = Path.of("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(Files.exists(clientPath), "ExampleModClient.java must exist");
		String content = Files.readString(clientPath);

		Assertions.assertTrue(
			content.contains("CommandMode.MINE") && content.contains("MiningMode.AREA") &&
			content.contains("ClientMiningCaptureTracker.adjustHeight(step)"),
			"ExampleModClient must adjust height on bracket keys when in MINE AREA mode"
		);
	}

	@Test
	@DisplayName("Localization Invariant: en_us.json contains all required mining mode and confirmation strings")
	void testLocalizationCompleteness() throws IOException {
		Path langPath = Path.of("src/main/resources/assets/modid-mmcli-agent-modding/lang/en_us.json");
		Assertions.assertTrue(Files.exists(langPath), "en_us.json must exist");
		String content = Files.readString(langPath);

		Assertions.assertTrue(content.contains("mining_mode.modid-mmcli-agent-modding.direct"), "Must contain direct mining mode string");
		Assertions.assertTrue(content.contains("mining_mode.modid-mmcli-agent-modding.area"), "Must contain area mining mode string");
		Assertions.assertTrue(content.contains("gui.modid-mmcli-agent-modding.mining_confirm_modal.title"), "Must contain mining confirm modal title");
		Assertions.assertTrue(content.contains("gui.modid-mmcli-agent-modding.mining_confirm_modal.confirm_button"), "Must contain confirm button string");
		Assertions.assertTrue(content.contains("message.modid-mmcli-agent-modding.mine_pos1_set"), "Must contain mine pos1 set message");
		Assertions.assertTrue(content.contains("message.modid-mmcli-agent-modding.mine_pos2_set"), "Must contain mine pos2 set message");
		Assertions.assertTrue(content.contains("message.modid-mmcli-agent-modding.mine_corners_cleared"), "Must contain mine corners cleared message");
	}
}
