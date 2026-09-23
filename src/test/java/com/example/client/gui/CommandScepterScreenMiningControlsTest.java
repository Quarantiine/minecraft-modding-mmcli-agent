package com.example.client.gui;

import com.example.client.renderer.ClientMiningCaptureTracker;
import com.example.component.CommandMode;
import com.example.component.MiningMode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Command Hub GUI MINE mode controls,
 * mining sub-mode toggles (DIRECT vs AREA), modal hooks, and mining corner reset.
 */
public class CommandScepterScreenMiningControlsTest {

	@BeforeEach
	void setUp() {
		ClientMiningCaptureTracker.clear();
	}

	@Test
	@DisplayName("Mining mode defaults to AREA and toggles cleanly between AREA and DIRECT")
	void testMiningModeToggling() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertEquals(MiningMode.AREA, screen.getSelectedMiningMode());

		screen.toggleMiningModeGui();
		Assertions.assertEquals(MiningMode.DIRECT, screen.getSelectedMiningMode());

		screen.toggleMiningModeGui();
		Assertions.assertEquals(MiningMode.AREA, screen.getSelectedMiningMode());

		screen.setSelectedMiningMode(MiningMode.DIRECT);
		Assertions.assertEquals(MiningMode.DIRECT, screen.getSelectedMiningMode());
	}

	@Test
	@DisplayName("Clearing mining corners via GUI resets ClientMiningCaptureTracker")
	void testClearMiningCornersGui() {
		ClientMiningCaptureTracker.setPos1(new BlockPos(10, 20, 30));
		ClientMiningCaptureTracker.setPos2(new BlockPos(15, 25, 35));
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());

		CommandScepterScreen screen = new CommandScepterScreen();
		screen.clearMineCornersGui();

		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasCompleteSelection());
	}

	@Test
	@DisplayName("Source contract: CommandScepterScreen renders distinct sub-headers for DIRECT vs AREA mining")
	void testMiningSubHeaderRenderingSourceContract() throws java.io.IOException {
		java.nio.file.Path screenPath = java.nio.file.Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		String screenContent = java.nio.file.Files.readString(screenPath);

		Assertions.assertTrue(
			screenContent.contains("Direct / Structure Mining Mode"),
			"CommandScepterScreen must render Direct / Structure subheader in DIRECT mining mode"
		);
		Assertions.assertTrue(
			screenContent.contains("Custom Area Boundary Quarry"),
			"CommandScepterScreen must render Custom Area subheader in AREA mining mode"
		);
	}
}
