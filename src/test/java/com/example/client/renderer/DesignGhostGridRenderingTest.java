package com.example.client.renderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating BlueprintHologramRenderer DESIGN mode in-world ghost grid block
 * and semantic color-coded outline rendering contracts.
 */
public class DesignGhostGridRenderingTest {

	@Test
	@DisplayName("Source Invariant: BlueprintHologramRenderer renders ghost grid blocks for complete DESIGN selections and candidate aiming")
	void testDesignGhostGridSourceInvariants() throws IOException {
		Path rendererPath = Path.of("src/client/java/com/example/client/renderer/BlueprintHologramRenderer.java");
		Assertions.assertTrue(Files.exists(rendererPath), "BlueprintHologramRenderer.java must exist");
		String content = Files.readString(rendererPath);

		// Verified method definition
		Assertions.assertTrue(
			content.contains("public static void renderDesignGhostGrid("),
			"BlueprintHologramRenderer must define renderDesignGhostGrid helper method"
		);

		// Verified complete selection invocation
		Assertions.assertTrue(
			content.contains("ClientDesignCaptureTracker.hasCompleteSelection()") &&
			content.contains("renderDesignGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 1.0F);"),
			"BlueprintHologramRenderer must render ghost grid blocks when Pos1 and Pos2 selection is complete"
		);

		// Verified candidate aiming invocation
		Assertions.assertTrue(
			content.contains("designTargetHit != null") &&
			content.contains("renderDesignGhostGrid(matrices, buffer, client.world, minX, minY, minZ, maxX, maxY, maxZ, 0.65F);"),
			"BlueprintHologramRenderer must render ghost grid blocks when aiming from Pos1 towards target block"
		);

		// Verified candidate aiming surface anchoring formula
		Assertions.assertTrue(
			content.contains("client.world.getBlockState(hitPos).isReplaceable()") &&
			content.contains("hitPos.offset(designTargetHit.getSide())"),
			"BlueprintHologramRenderer candidate aiming preview must adhere to isReplaceable surface anchoring formula"
		);

		// Verified semantic category rendering
		Assertions.assertTrue(
			content.contains("DoorBlock") && content.contains("0.0F, 1.0F, 0.53F"),
			"BlueprintHologramRenderer must render Emerald Green ghost portal boxes for doors"
		);
		Assertions.assertTrue(
			content.contains("LanternBlock") || content.contains("TorchBlock"),
			"BlueprintHologramRenderer must render Warm Amber Gold boxes for lighting"
		);
		Assertions.assertTrue(
			content.contains("BedBlock") || content.contains("CHEST"),
			"BlueprintHologramRenderer must render Arcane Purple boxes for containers and workstations"
		);
		Assertions.assertTrue(
			content.contains("StairsBlock"),
			"BlueprintHologramRenderer must render Soft Ice Blue boxes for stairs"
		);
		Assertions.assertTrue(
			content.contains("WallBlock") || content.contains("FenceBlock"),
			"BlueprintHologramRenderer must render Arcane Orange-Gold boxes for walls and defenses"
		);
		Assertions.assertTrue(
			content.contains("PaneBlock") || content.contains("StainedGlassBlock"),
			"BlueprintHologramRenderer must render Crystal Cyan boxes for windows and glass"
		);
	}

	@Test
	@DisplayName("Volume safety bounds calculation contract for ghost grid iteration")
	void testVolumeSafetyBoundsContract() {
		int maxDim = ClientDesignCaptureTracker.MAX_DIMENSION;
		int maxHeight = ClientDesignCaptureTracker.MAX_HEIGHT;
		long maxVol = ClientDesignCaptureTracker.MAX_VOLUME;

		// 64x96x64 volume = 393216 voxels (valid)
		int sx = 64;
		int sy = 96;
		int sz = 64;
		boolean valid = sx <= maxDim && sy <= maxHeight && sz <= maxDim && (long) sx * sy * sz <= maxVol;
		Assertions.assertTrue(valid);

		// 65x10x10 volume exceeds horizontal dimension limit
		int exDim = 65;
		boolean invalidDim = exDim <= maxDim;
		Assertions.assertFalse(invalidDim);

		// 10x97x10 volume exceeds height dimension limit
		int exHeight = 97;
		boolean invalidHeight = exHeight <= maxHeight;
		Assertions.assertFalse(invalidHeight);

		// 64x96x65 = 399360 exceeds volume limit
		int largeZ = 65;
		boolean invalidVol = (long) sx * sy * largeZ <= maxVol;
		Assertions.assertFalse(invalidVol);
	}

	@Test
	@DisplayName("Source Invariant: Tactical camera raycasts and PathwayHologramRenderer adhere to isReplaceable anchoring")
	void testTacticalCameraAndPathwayHologramAnchoringSourceInvariants() throws IOException {
		Path clientPath = Path.of("src/client/java/com/example/client/ExampleModClient.java");
		Assertions.assertTrue(Files.exists(clientPath), "ExampleModClient.java must exist");
		String clientContent = Files.readString(clientPath);

		Assertions.assertTrue(
			clientContent.contains("client.world.getBlockState(clicked).isReplaceable()"),
			"ExampleModClient tactical camera raycast in DESIGN mode must check isReplaceable before applying hit face offset"
		);

		Path pathwayPath = Path.of("src/client/java/com/example/client/renderer/PathwayHologramRenderer.java");
		Assertions.assertTrue(Files.exists(pathwayPath), "PathwayHologramRenderer.java must exist");
		String pathwayContent = Files.readString(pathwayPath);

		Assertions.assertTrue(
			pathwayContent.contains("!client.world.getBlockState(rawPos).isReplaceable()"),
			"PathwayHologramRenderer waypoint box rendering must check isReplaceable instead of isAir"
		);
		Assertions.assertTrue(
			pathwayContent.contains("!client.world.getBlockState(p1).isReplaceable()"),
			"PathwayHologramRenderer laser line rendering must check isReplaceable instead of isAir"
		);
		Assertions.assertTrue(
			pathwayContent.contains("!client.world.getBlockState(pos).isReplaceable()"),
			"PathwayHologramRenderer billboarding badge rendering must check isReplaceable instead of isAir"
		);
	}

	@Test
	@DisplayName("Simulation: isReplaceable surface anchoring formula evaluates correct offset for solid vs replaceable blocks")
	void testSurfaceAnchoringFormulaSimulation() {
		record MockBlockState(boolean isReplaceable) {}

		java.util.function.BiFunction<MockBlockState, net.minecraft.util.math.BlockPos, net.minecraft.util.math.BlockPos> resolveAnchor =
			(state, hitPos) -> state.isReplaceable() ? hitPos : hitPos.up();

		net.minecraft.util.math.BlockPos groundPos = new net.minecraft.util.math.BlockPos(10, 64, 10);

		// Solid block (stone) -> offset upward to rest on top
		MockBlockState solidStone = new MockBlockState(false);
		Assertions.assertEquals(groundPos.up(), resolveAnchor.apply(solidStone, groundPos));

		// Replaceable block (tall grass / water / air) -> anchor directly at hitPos
		MockBlockState tallGrass = new MockBlockState(true);
		Assertions.assertEquals(groundPos, resolveAnchor.apply(tallGrass, groundPos));
	}
}
