package com.example.client.gui;

import com.example.client.renderer.ClientDesignCaptureTracker;
import com.example.component.CommandMode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Command Hub GUI DESIGN mode controls,
 * modal opening hooks, and corner reset behavior.
 */
public class CommandScepterScreenDesignControlsTest {

	@BeforeEach
	void setUp() {
		ClientDesignCaptureTracker.clear();
	}

	@Test
	@DisplayName("Clearing corners via GUI resets ClientDesignCaptureTracker")
	void testClearCornersGui() {
		ClientDesignCaptureTracker.setPos1(new BlockPos(10, 20, 30));
		ClientDesignCaptureTracker.setPos2(new BlockPos(15, 25, 35));
		Assertions.assertTrue(ClientDesignCaptureTracker.hasCompleteSelection());

		CommandScepterScreen screen = new CommandScepterScreen();
		screen.clearCornersGui();

		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasCompleteSelection());
	}

	@Test
	@DisplayName("CommandScepterScreen close state tracks properly")
	void testScreenClose() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertFalse(screen.isClosed());
		screen.close();
		Assertions.assertTrue(screen.isClosed());
	}

	@Test
	@DisplayName("Source contract: Confirmation modal uses elevated Z-layering (Z=400) to prevent background bleed")
	void testConfirmationModalZLayeringSourceContract() throws java.io.IOException {
		java.nio.file.Path screenPath = java.nio.file.Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		String screenContent = java.nio.file.Files.readString(screenPath);

		Assertions.assertTrue(
			screenContent.contains("context.getMatrices().push();\n\t\t\tcontext.getMatrices().translate(0, 0, 400.0F);") ||
			screenContent.contains("context.getMatrices().translate(0, 0, 400.0F);"),
			"Confirmation modal must translate matrices along Z-axis by 400.0F"
		);
		Assertions.assertTrue(
			screenContent.contains("context.getMatrices().pop();"),
			"Confirmation modal must pop matrix stack"
		);
	}

	@Test
	@DisplayName("Source contract: Design mode description fits within 144px box and uses dynamic scaling")
	void testDesignModeDescriptionFittingSourceContract() throws java.io.IOException {
		java.nio.file.Path screenPath = java.nio.file.Path.of("src/client/java/com/example/client/gui/CommandScepterScreen.java");
		String screenContent = java.nio.file.Files.readString(screenPath);

		Assertions.assertTrue(
			screenContent.contains("case DESIGN -> \"§7Capture spatial volume.\";"),
			"Design mode description must use concise text"
		);
		Assertions.assertTrue(
			screenContent.contains("maxBoxWidth = 144 - 8;"),
			"Single-line banner fitting must enforce maxBoxWidth of 144 - 8"
		);
		Assertions.assertTrue(
			screenContent.contains("fittedScale = (rawWidth * scale > maxBoxWidth) ? ((float) maxBoxWidth / (float) rawWidth) : scale;"),
			"Dynamic scale fitting must be applied to banner text"
		);
	}
}
