package com.example.client.gui;

import com.example.client.renderer.ClientMiningCaptureTracker;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating MiningConfirmModalScreen logic:
 * - Corner coordinate ingestion
 * - Spatial dimension and volume validation
 * - Oversized geometry rejection
 * - Tracker fallback
 * - Modal close states
 */
public class MiningConfirmModalScreenTest {

	@BeforeEach
	void setUp() {
		ClientMiningCaptureTracker.clear();
	}

	@Test
	@DisplayName("Modal initializes with direct corner coordinates and validates geometry")
	void testDirectCornersInitialization() {
		BlockPos p1 = new BlockPos(0, 10, 0);
		BlockPos p2 = new BlockPos(6, 17, 6);

		MiningConfirmModalScreen modal = new MiningConfirmModalScreen(p1, p2, null, null);

		Assertions.assertEquals(p1, modal.getPos1());
		Assertions.assertEquals(p2, modal.getPos2());
		Assertions.assertTrue(modal.hasValidGeometry());
		Assertions.assertEquals(7, modal.getDimensionX());
		Assertions.assertEquals(8, modal.getDimensionY());
		Assertions.assertEquals(7, modal.getDimensionZ());
		Assertions.assertEquals(392L, modal.getVolume());
	}

	@Test
	@DisplayName("Modal falls back to ClientMiningCaptureTracker when initialized with defaults")
	void testTrackerFallbackInitialization() {
		BlockPos p1 = new BlockPos(100, 64, 100);
		BlockPos p2 = new BlockPos(108, 72, 108);
		ClientMiningCaptureTracker.setPos1(p1);
		ClientMiningCaptureTracker.setPos2(p2);

		MiningConfirmModalScreen modal = new MiningConfirmModalScreen();

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
		MiningConfirmModalScreen modal1 = new MiningConfirmModalScreen(origin, pTooWide, null, null);
		Assertions.assertFalse(modal1.hasValidGeometry());

		// Single vertical height dimension exceeds 96 (0 to 96 = 97 blocks)
		BlockPos pTooTall = new BlockPos(0, 96, 0);
		MiningConfirmModalScreen modalHeight = new MiningConfirmModalScreen(origin, pTooTall, null, null);
		Assertions.assertFalse(modalHeight.hasValidGeometry());

		// Valid height up to 96 (0 to 95 = 96 blocks)
		BlockPos pValidHeight = new BlockPos(10, 95, 10);
		MiningConfirmModalScreen modalValid = new MiningConfirmModalScreen(origin, pValidHeight, null, null);
		Assertions.assertTrue(modalValid.hasValidGeometry());

		// Total volume exceeds 393216 (64x96x65 = 399360 > 393216)
		BlockPos pTooVoluminous = new BlockPos(63, 95, 64);
		MiningConfirmModalScreen modal2 = new MiningConfirmModalScreen(origin, pTooVoluminous, null, null);
		Assertions.assertFalse(modal2.hasValidGeometry());
	}

	@Test
	@DisplayName("Modal correctly normalizes inverted coordinate order")
	void testInvertedCoordinateOrder() {
		BlockPos p1 = new BlockPos(100, 80, 50);
		BlockPos p2 = new BlockPos(90, 70, 40);

		MiningConfirmModalScreen modal = new MiningConfirmModalScreen(p1, p2, null, null);

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

		MiningConfirmModalScreen modal = new MiningConfirmModalScreen(p1, p2, null, null);

		Assertions.assertTrue(modal.hasValidGeometry());
		Assertions.assertEquals(1, modal.getDimensionX());
		Assertions.assertEquals(1, modal.getDimensionY());
		Assertions.assertEquals(1, modal.getDimensionZ());
		Assertions.assertEquals(1L, modal.getVolume());
	}

	@Test
	@DisplayName("Modal flags incomplete coordinate selection as invalid geometry")
	void testIncompleteCoordinateSelection() {
		MiningConfirmModalScreen modal1 = new MiningConfirmModalScreen(new BlockPos(0, 0, 0), null, null, null);
		Assertions.assertFalse(modal1.hasValidGeometry());
		Assertions.assertEquals(0, modal1.getDimensionX());
		Assertions.assertEquals(0L, modal1.getVolume());

		MiningConfirmModalScreen modal2 = new MiningConfirmModalScreen(null, new BlockPos(0, 0, 0), null, null);
		Assertions.assertFalse(modal2.hasValidGeometry());
		Assertions.assertEquals(0, modal2.getDimensionY());
		Assertions.assertEquals(0L, modal2.getVolume());
	}

	@Test
	@DisplayName("Modal close state and blur bypass contract")
	void testModalCloseAndBlurBypass() {
		MiningConfirmModalScreen modal = new MiningConfirmModalScreen();
		Assertions.assertFalse(modal.isClosed());
		modal.close();
		Assertions.assertTrue(modal.isClosed());
	}

	@Test
	@DisplayName("Modal constants and pause behavior")
	void testModalConstantsAndPause() {
		Assertions.assertEquals(300, MiningConfirmModalScreen.WINDOW_WIDTH);
		Assertions.assertEquals(170, MiningConfirmModalScreen.WINDOW_HEIGHT);

		MiningConfirmModalScreen modal = new MiningConfirmModalScreen();
		Assertions.assertFalse(modal.shouldPause(), "Mining confirm modal must not pause singleplayer game");
	}

	@Test
	@DisplayName("Modal constructor with ItemStack and parentScreen correctly reads tracker")
	void testModalScepterParentConstructor() {
		BlockPos p1 = new BlockPos(15, 60, 15);
		BlockPos p2 = new BlockPos(25, 75, 25);
		ClientMiningCaptureTracker.setPos1(p1);
		ClientMiningCaptureTracker.setPos2(p2);

		MiningConfirmModalScreen modal = new MiningConfirmModalScreen(null, null);
		Assertions.assertEquals(p1, modal.getPos1());
		Assertions.assertEquals(p2, modal.getPos2());
		Assertions.assertTrue(modal.hasValidGeometry());
		Assertions.assertEquals(11, modal.getDimensionX());
		Assertions.assertEquals(16, modal.getDimensionY());
		Assertions.assertEquals(11, modal.getDimensionZ());
		Assertions.assertEquals(1936L, modal.getVolume());
	}

	@Test
	@DisplayName("Modal clearSelection clears ClientMiningCaptureTracker and closes modal")
	void testModalClearSelection() {
		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 74, 20);
		ClientMiningCaptureTracker.setPos1(p1);
		ClientMiningCaptureTracker.setPos2(p2);
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());

		MiningConfirmModalScreen modal = new MiningConfirmModalScreen(p1, p2, null, null);
		modal.clearSelection();

		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasCompleteSelection());
		Assertions.assertTrue(modal.isClosed());
	}

	@Test
	@DisplayName("Modal startMiningArea with invalid geometry safely early-returns without exception")
	void testModalStartMiningAreaInvalidGeometryNoop() {
		MiningConfirmModalScreen invalidModal = new MiningConfirmModalScreen(null, null, null, null);
		Assertions.assertDoesNotThrow(invalidModal::startMiningArea);
		Assertions.assertFalse(invalidModal.isClosed());
	}

	@Test
	@DisplayName("Modal destructible block count returns volume fallback in headless test environment")
	void testModalDestructibleBlockCountFallback() {
		BlockPos p1 = new BlockPos(0, 0, 0);
		BlockPos p2 = new BlockPos(2, 2, 2); // 3x3x3 = 27
		MiningConfirmModalScreen modal = new MiningConfirmModalScreen(p1, p2, null, null);
		Assertions.assertEquals(27, modal.getDestructibleBlockCount());
	}
}
