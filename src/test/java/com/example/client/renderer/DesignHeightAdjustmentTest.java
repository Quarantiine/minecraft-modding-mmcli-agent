package com.example.client.renderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests validating DESIGN mode in-world height adjustment (no blocks needed),
 * ground offset ergonomics (excluding terrain dirt from captured blueprints),
 * and legacy component migration registrations.
 */
public class DesignHeightAdjustmentTest {

	@BeforeEach
	void setUp() {
		ClientDesignCaptureTracker.clear();
	}

	@Test
	@DisplayName("Pos1 ground offset: clicking top face of ground at Y=64 anchors at Y=65")
	void testPos1GroundOffset() {
		BlockPos groundBlock = new BlockPos(100, 64, 200);
		Direction clickedFace = Direction.UP;

		BlockPos effectivePos = groundBlock.offset(clickedFace);
		Assertions.assertEquals(65, effectivePos.getY(), "Ground offset must place Pos1 at Y=65 (floor level)");
		Assertions.assertEquals(100, effectivePos.getX());
		Assertions.assertEquals(200, effectivePos.getZ());
	}

	@Test
	@DisplayName("adjustHeight raises and lowers vertical height on ground selection")
	void testAdjustHeight() {
		// Both corners set on the ground (floor level Y=65)
		BlockPos p1 = new BlockPos(10, 65, 10);
		BlockPos p2 = new BlockPos(20, 65, 20);

		ClientDesignCaptureTracker.setPos1(p1);
		ClientDesignCaptureTracker.setPos2(p2);
		Assertions.assertEquals(1, ClientDesignCaptureTracker.getSizeY(), "Initial ground height must be 1 block");

		// Raise height by 13 blocks -> total height 14 blocks
		int newH = ClientDesignCaptureTracker.adjustHeight(13);
		Assertions.assertEquals(14, newH);
		Assertions.assertEquals(14, ClientDesignCaptureTracker.getSizeY());
		Assertions.assertEquals(65, Math.min(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY()));
		Assertions.assertEquals(78, Math.max(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY()));

		// Lower height by 2 blocks -> total height 12 blocks
		int loweredH = ClientDesignCaptureTracker.adjustHeight(-2);
		Assertions.assertEquals(12, loweredH);
		Assertions.assertEquals(12, ClientDesignCaptureTracker.getSizeY());
		Assertions.assertEquals(76, Math.max(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY()));
	}

	@Test
	@DisplayName("adjustHeight enforces minimum 1 block and maximum MAX_HEIGHT (96) clamping")
	void testAdjustHeightClamping() {
		BlockPos p1 = new BlockPos(10, 65, 10);
		BlockPos p2 = new BlockPos(20, 65, 20);
		ClientDesignCaptureTracker.setPos1(p1);
		ClientDesignCaptureTracker.setPos2(p2);

		// Attempt to reduce below 1
		int h = ClientDesignCaptureTracker.adjustHeight(-50);
		Assertions.assertEquals(1, h, "Height must clamp to minimum 1 block");
		Assertions.assertEquals(1, ClientDesignCaptureTracker.getSizeY());

		// Attempt to exceed MAX_HEIGHT (96)
		h = ClientDesignCaptureTracker.adjustHeight(500);
		Assertions.assertEquals(ClientDesignCaptureTracker.MAX_HEIGHT, h, "Height must clamp to MAX_HEIGHT (96)");
		Assertions.assertEquals(ClientDesignCaptureTracker.MAX_HEIGHT, ClientDesignCaptureTracker.getSizeY());
	}

	@Test
	@DisplayName("setHeight explicitly sets target height accurately")
	void testSetHeight() {
		BlockPos p1 = new BlockPos(0, 70, 0);
		BlockPos p2 = new BlockPos(5, 70, 5);
		ClientDesignCaptureTracker.setPos1(p1);
		ClientDesignCaptureTracker.setPos2(p2);

		int h = ClientDesignCaptureTracker.setHeight(25);
		Assertions.assertEquals(25, h);
		Assertions.assertEquals(25, ClientDesignCaptureTracker.getSizeY());
		Assertions.assertEquals(70, Math.min(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY()));
		Assertions.assertEquals(94, Math.max(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY()));
	}

	@Test
	@DisplayName("Legacy migration components exist in ModDataComponents to prevent NBT errors")
	void testLegacyComponentsRegistered() throws Exception {
		Path path = Path.of("src/main/java/com/example/component/ModDataComponents.java");
		Assertions.assertTrue(Files.exists(path), "ModDataComponents.java must exist");
		String content = Files.readString(path);
		Assertions.assertTrue(content.contains("LEGACY_ARCHITECTURE_STYLE"), "ModDataComponents must define LEGACY_ARCHITECTURE_STYLE");
		Assertions.assertTrue(content.contains("\"architecture_style\""), "ModDataComponents must register architecture_style identifier");
		Assertions.assertTrue(content.contains("LEGACY_BUILDING_SIZE"), "ModDataComponents must define LEGACY_BUILDING_SIZE");
		Assertions.assertTrue(content.contains("\"building_size\""), "ModDataComponents must register building_size identifier");
	}

	@Test
	@DisplayName("Source Invariant: MouseMixin is registered in modid.mixins.json")
	void testMouseMixinRegistered() throws Exception {
		Path mixinsJson = Path.of("src/main/resources/modid.mixins.json");
		Assertions.assertTrue(Files.exists(mixinsJson), "modid.mixins.json must exist");
		String content = Files.readString(mixinsJson);
		Assertions.assertTrue(content.contains("client.MouseMixin"), "MouseMixin must be registered under client in modid.mixins.json");
	}
}
