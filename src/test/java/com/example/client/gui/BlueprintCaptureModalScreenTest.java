package com.example.client.gui;

import com.example.blueprint.BuildingCategory;
import com.example.blueprint.StructureBlueprint;
import com.example.client.renderer.ClientDesignCaptureTracker;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating BlueprintCaptureModalScreen logic:
 * - Corner coordinate ingestion
 * - Spatial dimension and volume validation
 * - Building category selection
 * - Modal close states
 */
public class BlueprintCaptureModalScreenTest {

	@BeforeEach
	void setUp() {
		ClientDesignCaptureTracker.clear();
	}

	@Test
	@DisplayName("Modal initializes with direct corner coordinates and validates geometry")
	void testDirectCornersInitialization() {
		BlockPos p1 = new BlockPos(0, 10, 0);
		BlockPos p2 = new BlockPos(6, 17, 6);

		BlueprintCaptureModalScreen modal = new BlueprintCaptureModalScreen(p1, p2, null, null);

		Assertions.assertEquals(p1, modal.getPos1());
		Assertions.assertEquals(p2, modal.getPos2());
		Assertions.assertTrue(modal.hasValidGeometry());
		Assertions.assertEquals(7, modal.getDimensionX());
		Assertions.assertEquals(8, modal.getDimensionY());
		Assertions.assertEquals(7, modal.getDimensionZ());
		Assertions.assertEquals(392L, modal.getVolume());
		Assertions.assertEquals(BuildingCategory.WATCHTOWER, modal.getSelectedCategory());
	}

	@Test
	@DisplayName("Modal falls back to ClientDesignCaptureTracker when initialized with defaults")
	void testTrackerFallbackInitialization() {
		BlockPos p1 = new BlockPos(100, 64, 100);
		BlockPos p2 = new BlockPos(108, 72, 108);
		ClientDesignCaptureTracker.setPos1(p1);
		ClientDesignCaptureTracker.setPos2(p2);

		BlueprintCaptureModalScreen modal = new BlueprintCaptureModalScreen();

		Assertions.assertEquals(p1, modal.getPos1());
		Assertions.assertEquals(p2, modal.getPos2());
		Assertions.assertTrue(modal.hasValidGeometry());
		Assertions.assertEquals(9, modal.getDimensionX());
		Assertions.assertEquals(9, modal.getDimensionY());
		Assertions.assertEquals(9, modal.getDimensionZ());
		Assertions.assertEquals(729L, modal.getVolume());
	}

	@Test
	@DisplayName("Modal flags oversized spatial regions as invalid geometry")
	void testOversizedGeometryValidation() {
		BlockPos origin = new BlockPos(0, 0, 0);

		// Single horizontal dimension exceeds 64 (0 to 64 = 65 blocks)
		BlockPos pTooWide = new BlockPos(64, 5, 5);
		BlueprintCaptureModalScreen modal1 = new BlueprintCaptureModalScreen(origin, pTooWide, null, null);
		Assertions.assertFalse(modal1.hasValidGeometry());

		// Single vertical height dimension exceeds 96 (0 to 96 = 97 blocks)
		BlockPos pTooTall = new BlockPos(0, 96, 0);
		BlueprintCaptureModalScreen modalHeight = new BlueprintCaptureModalScreen(origin, pTooTall, null, null);
		Assertions.assertFalse(modalHeight.hasValidGeometry());

		// Valid height up to 96 (0 to 95 = 96 blocks)
		BlockPos pValidHeight = new BlockPos(10, 95, 10); // 11x96x11 = 11616 <= 393216
		BlueprintCaptureModalScreen modalValid = new BlueprintCaptureModalScreen(origin, pValidHeight, null, null);
		Assertions.assertTrue(modalValid.hasValidGeometry());

		// Total volume exceeds 393216 (64x96x65 = 399360 > 393216)
		BlockPos pTooVoluminous = new BlockPos(63, 95, 64);
		BlueprintCaptureModalScreen modal2 = new BlueprintCaptureModalScreen(origin, pTooVoluminous, null, null);
		Assertions.assertFalse(modal2.hasValidGeometry());
	}

	@Test
	@DisplayName("Category selection updates state across all building categories")
	void testCategorySelection() {
		BlueprintCaptureModalScreen modal = new BlueprintCaptureModalScreen();

		modal.setSelectedCategory(BuildingCategory.HOME);
		Assertions.assertEquals(BuildingCategory.HOME, modal.getSelectedCategory());

		modal.setSelectedCategory(BuildingCategory.BARRICADE);
		Assertions.assertEquals(BuildingCategory.BARRICADE, modal.getSelectedCategory());

		modal.setSelectedCategory(BuildingCategory.WORKSHOP);
		Assertions.assertEquals(BuildingCategory.WORKSHOP, modal.getSelectedCategory());

		modal.setSelectedCategory(BuildingCategory.SUPPLY_DEPOT);
		Assertions.assertEquals(BuildingCategory.SUPPLY_DEPOT, modal.getSelectedCategory());

		modal.setSelectedCategory(BuildingCategory.OBELISK);
		Assertions.assertEquals(BuildingCategory.OBELISK, modal.getSelectedCategory());
	}

	@Test
	@DisplayName("Modal correctly normalizes inverted coordinate order")
	void testInvertedCoordinateOrder() {
		BlockPos p1 = new BlockPos(100, 80, 50);
		BlockPos p2 = new BlockPos(90, 70, 40);

		BlueprintCaptureModalScreen modal = new BlueprintCaptureModalScreen(p1, p2, null, null);

		Assertions.assertTrue(modal.hasValidGeometry());
		Assertions.assertEquals(11, modal.getDimensionX());
		Assertions.assertEquals(11, modal.getDimensionY());
		Assertions.assertEquals(11, modal.getDimensionZ());
		Assertions.assertEquals(1331L, modal.getVolume());
	}

	@Test
	@DisplayName("Modal handles single voxel selection (1x1x1)")
	void testSingleVoxelSelection() {
		BlockPos p1 = new BlockPos(10, 20, 30);
		BlockPos p2 = new BlockPos(10, 20, 30);

		BlueprintCaptureModalScreen modal = new BlueprintCaptureModalScreen(p1, p2, null, null);

		Assertions.assertTrue(modal.hasValidGeometry());
		Assertions.assertEquals(1, modal.getDimensionX());
		Assertions.assertEquals(1, modal.getDimensionY());
		Assertions.assertEquals(1, modal.getDimensionZ());
		Assertions.assertEquals(1L, modal.getVolume());
	}

	@Test
	@DisplayName("Modal flags incomplete coordinate selection as invalid geometry")
	void testIncompleteCoordinateSelection() {
		BlueprintCaptureModalScreen modal1 = new BlueprintCaptureModalScreen(new BlockPos(0, 0, 0), null, null, null);
		Assertions.assertFalse(modal1.hasValidGeometry());
		Assertions.assertEquals(0, modal1.getDimensionX());
		Assertions.assertEquals(0L, modal1.getVolume());

		BlueprintCaptureModalScreen modal2 = new BlueprintCaptureModalScreen(null, new BlockPos(0, 0, 0), null, null);
		Assertions.assertFalse(modal2.hasValidGeometry());
		Assertions.assertEquals(0, modal2.getDimensionY());
		Assertions.assertEquals(0L, modal2.getVolume());
	}

	@Test
	@DisplayName("Modal accepts precompiled blueprints directly")
	void testPrecompiledBlueprintModal() {
		StructureBlueprint bp = StructureBlueprint.builder("test_bp", "Test Obelisk")
			.description("Test description")
			.build();

		BlueprintCaptureModalScreen modal = new BlueprintCaptureModalScreen(bp, null, null);

		Assertions.assertEquals(bp, modal.getPrecompiledBlueprint());
		Assertions.assertFalse(modal.isClosed());
		modal.close();
		Assertions.assertTrue(modal.isClosed());
	}

	@Test
	@DisplayName("Description is optional and defaults to empty string without dropping blocks")
	void testOptionalDescriptionHandling() {
		BlueprintCaptureModalScreen modal = new BlueprintCaptureModalScreen();
		Assertions.assertEquals("", modal.getBlueprintDescription());

		// Test StructureBlueprint builder handles null, empty, and whitespace descriptions as empty string
		StructureBlueprint bpNullDesc = StructureBlueprint.builder("bp_null", "Null Desc")
			.description(null)
			.build();
		Assertions.assertEquals("", bpNullDesc.getDescription());

		StructureBlueprint bpEmptyDesc = StructureBlueprint.builder("bp_empty", "Empty Desc")
			.description("")
			.build();
		Assertions.assertEquals("", bpEmptyDesc.getDescription());

		StructureBlueprint bpBlankDesc = StructureBlueprint.builder("bp_blank", "Blank Desc")
			.description("   ")
			.build();
		Assertions.assertEquals("", bpBlankDesc.getDescription());

		StructureBlueprint bpWithDesc = StructureBlueprint.builder("bp_valid", "Valid Desc")
			.description("  Sturdy stone watchtower  ")
			.build();
		Assertions.assertEquals("Sturdy stone watchtower", bpWithDesc.getDescription());
	}

	@Test
	@DisplayName("Precompiled blueprint preserves all blocks and non-air block count regardless of description")
	void testPrecompiledBlueprintBlockCountPreserved() {
		// Test spatial selection volume and non-air block count fallback
		BlockPos p1 = new BlockPos(0, 0, 0);
		BlockPos p2 = new BlockPos(2, 2, 2);
		BlueprintCaptureModalScreen spatialModal = new BlueprintCaptureModalScreen(p1, p2, null, null);
		Assertions.assertEquals(27, spatialModal.getNonAirBlockCount());
		Assertions.assertEquals(27L, spatialModal.getVolume());
		Assertions.assertTrue(spatialModal.hasValidGeometry());

		// Test precompiled blueprint with empty description preserves empty description and block list
		StructureBlueprint precompiled = StructureBlueprint.builder("bp_precompiled", "Precompiled Watchtower")
			.description("")
			.build();
		BlueprintCaptureModalScreen precompiledModal = new BlueprintCaptureModalScreen(precompiled, null, null);
		Assertions.assertEquals(0, precompiledModal.getNonAirBlockCount());
		Assertions.assertEquals(0L, precompiledModal.getVolume());
		Assertions.assertEquals("", precompiledModal.getBlueprintDescription());
		Assertions.assertEquals("Precompiled Watchtower", precompiledModal.getPrecompiledBlueprint().getName());
	}
}

