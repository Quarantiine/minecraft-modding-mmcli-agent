package com.example.client.gui;

import com.example.component.CommandMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Command Hub GUI building controls,
 * blueprint rotation selection, and mode-specific state handling.
 */
public class CommandScepterScreenBuildingControlsTest {

	@Test
	@DisplayName("CommandScepterScreen initializes with default rotation and valid mode")
	void testDefaultBuildingSettings() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertEquals(0, screen.getSelectedRotation());
		Assertions.assertEquals(CommandMode.FOLLOW, screen.getSelectedMode());
	}

	@Test
	@DisplayName("Blueprint rotation selection and GUI cycling updates state correctly")
	void testRotationControls() {
		CommandScepterScreen screen = new CommandScepterScreen();

		// Default rotation is 0 (0° / North)
		Assertions.assertEquals(0, screen.getSelectedRotation());

		// Cycle 90° clockwise
		screen.cycleRotationGui();
		Assertions.assertEquals(1, screen.getSelectedRotation());

		screen.cycleRotationGui();
		Assertions.assertEquals(2, screen.getSelectedRotation());

		screen.cycleRotationGui();
		Assertions.assertEquals(3, screen.getSelectedRotation());

		// Wrap-around to 0°
		screen.cycleRotationGui();
		Assertions.assertEquals(0, screen.getSelectedRotation());

		// Direct assignment
		screen.setSelectedRotation(2);
		Assertions.assertEquals(2, screen.getSelectedRotation());

		// Modulo normalization
		screen.setSelectedRotation(7);
		Assertions.assertEquals(3, screen.getSelectedRotation());

		screen.setSelectedRotation(-1);
		Assertions.assertEquals(3, screen.getSelectedRotation());
	}
}
