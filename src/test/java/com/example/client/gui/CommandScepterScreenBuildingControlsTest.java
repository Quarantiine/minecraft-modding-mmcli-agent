package com.example.client.gui;

import com.example.blueprint.ArchitectureStyle;
import com.example.blueprint.BuildingCategory;
import com.example.component.CommandMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Command Hub GUI Architecture Style bar swapping,
 * procedural size selection, and dynamic controls visibility.
 */
public class CommandScepterScreenBuildingControlsTest {

	@Test
	@DisplayName("CommandScepterScreen initializes with BIOME_NATIVE style and MEDIUM size by default")
	void testDefaultBuildingSettings() {
		CommandScepterScreen screen = new CommandScepterScreen();
		Assertions.assertEquals(ArchitectureStyle.BIOME_NATIVE, screen.getSelectedStyle());
		Assertions.assertEquals(BuildingCategory.SIZE_MEDIUM, screen.getSelectedSize());
	}

	@Test
	@DisplayName("Architecture style selection updates state correctly")
	void testStyleSelection() {
		CommandScepterScreen screen = new CommandScepterScreen();

		screen.selectStyle(ArchitectureStyle.FORTRESS_STONE);
		Assertions.assertEquals(ArchitectureStyle.FORTRESS_STONE, screen.getSelectedStyle());

		screen.selectStyle(ArchitectureStyle.ARCANE_NETHER);
		Assertions.assertEquals(ArchitectureStyle.ARCANE_NETHER, screen.getSelectedStyle());

		screen.setSelectedStyle(ArchitectureStyle.FRONTIER_TIMBER);
		Assertions.assertEquals(ArchitectureStyle.FRONTIER_TIMBER, screen.getSelectedStyle());
	}

	@Test
	@DisplayName("Building size selection updates state correctly across presets")
	void testSizeSelection() {
		CommandScepterScreen screen = new CommandScepterScreen();

		screen.selectSize(BuildingCategory.SIZE_SMALL);
		Assertions.assertEquals(BuildingCategory.SIZE_SMALL, screen.getSelectedSize());

		screen.selectSize(BuildingCategory.SIZE_GRAND);
		Assertions.assertEquals(BuildingCategory.SIZE_GRAND, screen.getSelectedSize());

		screen.selectSize(BuildingCategory.SIZE_RANDOM);
		Assertions.assertEquals(BuildingCategory.SIZE_RANDOM, screen.getSelectedSize());
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
