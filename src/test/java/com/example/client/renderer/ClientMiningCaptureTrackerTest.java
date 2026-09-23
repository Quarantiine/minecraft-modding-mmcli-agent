package com.example.client.renderer;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating ClientMiningCaptureTracker state management,
 * coordinate bounds calculation, volume limit validation, sequential step cycling,
 * and height adjustment.
 */
public class ClientMiningCaptureTrackerTest {

	@BeforeEach
	void setUp() {
		ClientMiningCaptureTracker.clear();
	}

	@Test
	@DisplayName("Tracker initializes empty with incomplete selection")
	void testInitialState() {
		Assertions.assertNull(ClientMiningCaptureTracker.getPos1());
		Assertions.assertNull(ClientMiningCaptureTracker.getPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(0, ClientMiningCaptureTracker.getSizeX());
		Assertions.assertEquals(0, ClientMiningCaptureTracker.getSizeY());
		Assertions.assertEquals(0, ClientMiningCaptureTracker.getSizeZ());
		Assertions.assertEquals(0L, ClientMiningCaptureTracker.getVolume());
		Assertions.assertNull(ClientMiningCaptureTracker.getBlockBox());
		Assertions.assertNull(ClientMiningCaptureTracker.getBox());
		Assertions.assertEquals("Incomplete Selection", ClientMiningCaptureTracker.getDimensionString());
		Assertions.assertEquals(0, ClientMiningCaptureTracker.getDestructibleBlockCount());
	}

	@Test
	@DisplayName("Setting Pos1 and Pos2 computes dimensions and volume accurately")
	void testDimensionCalculation() {
		BlockPos p1 = new BlockPos(10, 64, 20);
		BlockPos p2 = new BlockPos(16, 70, 26);

		ClientMiningCaptureTracker.setPos1(p1);
		Assertions.assertTrue(ClientMiningCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasCompleteSelection());

		ClientMiningCaptureTracker.setPos2(p2);
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(p1, ClientMiningCaptureTracker.getPos1());
		Assertions.assertEquals(p2, ClientMiningCaptureTracker.getPos2());

		// Dimensions: 16 - 10 + 1 = 7, 70 - 64 + 1 = 7, 26 - 20 + 1 = 7
		Assertions.assertEquals(7, ClientMiningCaptureTracker.getSizeX());
		Assertions.assertEquals(7, ClientMiningCaptureTracker.getSizeY());
		Assertions.assertEquals(7, ClientMiningCaptureTracker.getSizeZ());
		Assertions.assertEquals(343L, ClientMiningCaptureTracker.getVolume());
		Assertions.assertTrue(ClientMiningCaptureTracker.isWithinLimit());

		Assertions.assertEquals("7x7x7 (343b)", ClientMiningCaptureTracker.getDimensionString());

		BlockBox blockBox = ClientMiningCaptureTracker.getBlockBox();
		Assertions.assertNotNull(blockBox);
		Assertions.assertEquals(10, blockBox.getMinX());
		Assertions.assertEquals(64, blockBox.getMinY());
		Assertions.assertEquals(20, blockBox.getMinZ());
		Assertions.assertEquals(16, blockBox.getMaxX());
		Assertions.assertEquals(70, blockBox.getMaxY());
		Assertions.assertEquals(26, blockBox.getMaxZ());

		Box box = ClientMiningCaptureTracker.getBox();
		Assertions.assertNotNull(box);
		Assertions.assertEquals(10.0D, box.minX, 1e-6);
		Assertions.assertEquals(64.0D, box.minY, 1e-6);
		Assertions.assertEquals(20.0D, box.minZ, 1e-6);
		Assertions.assertEquals(17.0D, box.maxX, 1e-6);
		Assertions.assertEquals(71.0D, box.maxY, 1e-6);
		Assertions.assertEquals(27.0D, box.maxZ, 1e-6);
	}

	@Test
	@DisplayName("Inverted coordinate order normalizes bounding box correctly")
	void testInvertedCoordinateOrder() {
		BlockPos p1 = new BlockPos(50, 80, 50);
		BlockPos p2 = new BlockPos(40, 70, 45);

		ClientMiningCaptureTracker.setPos1(p1);
		ClientMiningCaptureTracker.setPos2(p2);

		Assertions.assertEquals(11, ClientMiningCaptureTracker.getSizeX());
		Assertions.assertEquals(11, ClientMiningCaptureTracker.getSizeY());
		Assertions.assertEquals(6, ClientMiningCaptureTracker.getSizeZ());
		Assertions.assertEquals(726L, ClientMiningCaptureTracker.getVolume());

		BlockBox box = ClientMiningCaptureTracker.getBlockBox();
		Assertions.assertEquals(40, box.getMinX());
		Assertions.assertEquals(70, box.getMinY());
		Assertions.assertEquals(45, box.getMinZ());
		Assertions.assertEquals(50, box.getMaxX());
		Assertions.assertEquals(80, box.getMaxY());
		Assertions.assertEquals(50, box.getMaxZ());
	}

	@Test
	@DisplayName("Volume safety limits are enforced correctly")
	void testVolumeLimits() {
		// Valid 64x96x64 box (393216 voxels = exact max limit)
		ClientMiningCaptureTracker.setPos1(new BlockPos(0, 0, 0));
		ClientMiningCaptureTracker.setPos2(new BlockPos(63, 95, 63));
		Assertions.assertTrue(ClientMiningCaptureTracker.isWithinLimit());

		// Exceeds max horizontal dimension (> 64)
		ClientMiningCaptureTracker.setPos2(new BlockPos(64, 0, 0));
		Assertions.assertFalse(ClientMiningCaptureTracker.isWithinLimit());

		// Exceeds max height dimension (> 96)
		ClientMiningCaptureTracker.setPos2(new BlockPos(0, 96, 0));
		Assertions.assertFalse(ClientMiningCaptureTracker.isWithinLimit());

		// Exceeds max volume (> 393216)
		ClientMiningCaptureTracker.setPos2(new BlockPos(63, 95, 64));
		Assertions.assertFalse(ClientMiningCaptureTracker.isWithinLimit());
	}

	@Test
	@DisplayName("Clearing tracker resets all coordinate state")
	void testClearState() {
		ClientMiningCaptureTracker.setPos1(new BlockPos(1, 2, 3));
		ClientMiningCaptureTracker.setPos2(new BlockPos(4, 5, 6));
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());

		ClientMiningCaptureTracker.clear();
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasCompleteSelection());
		Assertions.assertNull(ClientMiningCaptureTracker.getPos1());
		Assertions.assertNull(ClientMiningCaptureTracker.getPos2());
	}

	@Test
	@DisplayName("Sequential stepCorner advances from Pos1 to Pos2 and automatically cycles")
	void testStepCornerSequentialWorkflow() {
		BlockPos p1 = new BlockPos(10, 64, 20);
		BlockPos p2 = new BlockPos(20, 70, 30);
		BlockPos p3 = new BlockPos(100, 50, 100);
		BlockPos p4 = new BlockPos(110, 60, 110);

		// Step 1: Pos1 set
		int step1 = ClientMiningCaptureTracker.stepCorner(p1);
		Assertions.assertEquals(1, step1);
		Assertions.assertEquals(p1, ClientMiningCaptureTracker.getPos1());
		Assertions.assertNull(ClientMiningCaptureTracker.getPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasCompleteSelection());

		// Step 2: Pos2 set -> Complete
		int step2 = ClientMiningCaptureTracker.stepCorner(p2);
		Assertions.assertEquals(2, step2);
		Assertions.assertEquals(p1, ClientMiningCaptureTracker.getPos1());
		Assertions.assertEquals(p2, ClientMiningCaptureTracker.getPos2());
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(11, ClientMiningCaptureTracker.getSizeX());
		Assertions.assertEquals(7, ClientMiningCaptureTracker.getSizeY());
		Assertions.assertEquals(11, ClientMiningCaptureTracker.getSizeZ());

		// Duplicate click on p2 block must retain complete selection
		int step2Duplicate = ClientMiningCaptureTracker.stepCorner(p2);
		Assertions.assertEquals(2, step2Duplicate);
		Assertions.assertEquals(p1, ClientMiningCaptureTracker.getPos1());
		Assertions.assertEquals(p2, ClientMiningCaptureTracker.getPos2());
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());

		// Step 3 (Cycle/Restart with a different block): Pos1 set to p3, Pos2 cleared
		int step3 = ClientMiningCaptureTracker.stepCorner(p3);
		Assertions.assertEquals(1, step3);
		Assertions.assertEquals(p3, ClientMiningCaptureTracker.getPos1());
		Assertions.assertNull(ClientMiningCaptureTracker.getPos2());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasCompleteSelection());

		// Step 4: Pos2 set to p4 -> Complete again
		int step4 = ClientMiningCaptureTracker.stepCorner(p4);
		Assertions.assertEquals(2, step4);
		Assertions.assertEquals(p3, ClientMiningCaptureTracker.getPos1());
		Assertions.assertEquals(p4, ClientMiningCaptureTracker.getPos2());
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());
	}

	@Test
	@DisplayName("Height adjustments dynamically update bounds and clamp correctly")
	void testHeightAdjustments() {
		BlockPos p1 = new BlockPos(10, 64, 10);
		BlockPos p2 = new BlockPos(20, 68, 20); // Height = 5 (64 to 68)

		ClientMiningCaptureTracker.setPos1(p1);
		ClientMiningCaptureTracker.setPos2(p2);
		Assertions.assertEquals(5, ClientMiningCaptureTracker.getSizeY());

		// Increase height by 5
		int newH1 = ClientMiningCaptureTracker.adjustHeight(5);
		Assertions.assertEquals(10, newH1);
		Assertions.assertEquals(10, ClientMiningCaptureTracker.getSizeY());

		// Decrease height by 2
		int newH2 = ClientMiningCaptureTracker.adjustHeight(-2);
		Assertions.assertEquals(8, newH2);
		Assertions.assertEquals(8, ClientMiningCaptureTracker.getSizeY());

		// Explicit set height
		int explicitH = ClientMiningCaptureTracker.setHeight(15);
		Assertions.assertEquals(15, explicitH);
		Assertions.assertEquals(15, ClientMiningCaptureTracker.getSizeY());

		// Clamping to MAX_HEIGHT
		int maxClamped = ClientMiningCaptureTracker.setHeight(200);
		Assertions.assertEquals(ClientMiningCaptureTracker.MAX_HEIGHT, maxClamped);
		Assertions.assertEquals(ClientMiningCaptureTracker.MAX_HEIGHT, ClientMiningCaptureTracker.getSizeY());

		// Clamping to minimum (height < 1 clamped to 1)
		int minClamped = ClientMiningCaptureTracker.adjustHeight(-500);
		Assertions.assertEquals(1, minClamped);
		Assertions.assertEquals(1, ClientMiningCaptureTracker.getSizeY());

		int explicitMin = ClientMiningCaptureTracker.setHeight(0);
		Assertions.assertEquals(1, explicitMin);
		Assertions.assertEquals(1, ClientMiningCaptureTracker.getSizeY());
	}

	@Test
	@DisplayName("Height adjustment when only Pos1 is set initializes Pos2 with adjusted height")
	void testHeightAdjustmentWithOnlyPos1() {
		BlockPos p1 = new BlockPos(100, 64, 100);
		ClientMiningCaptureTracker.setPos1(p1);
		Assertions.assertTrue(ClientMiningCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientMiningCaptureTracker.hasPos2());

		// Adjust height by +4 -> height = 1 + 4 = 5
		int newH = ClientMiningCaptureTracker.adjustHeight(4);
		Assertions.assertEquals(5, newH);
		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(p1, ClientMiningCaptureTracker.getPos1());
		Assertions.assertEquals(new BlockPos(100, 68, 100), ClientMiningCaptureTracker.getPos2());
		Assertions.assertEquals(1, ClientMiningCaptureTracker.getSizeX());
		Assertions.assertEquals(5, ClientMiningCaptureTracker.getSizeY());
		Assertions.assertEquals(1, ClientMiningCaptureTracker.getSizeZ());
	}

	@Test
	@DisplayName("Height adjustment when neither corner is set safely returns 0")
	void testHeightAdjustmentEmptyTracker() {
		Assertions.assertEquals(0, ClientMiningCaptureTracker.adjustHeight(5));
		Assertions.assertEquals(0, ClientMiningCaptureTracker.setHeight(10));
	}

	@Test
	@DisplayName("Height adjustment when Pos1.Y > Pos2.Y correctly adjusts the top coordinate")
	void testHeightAdjustmentWhenPos1HigherThanPos2() {
		BlockPos p1 = new BlockPos(10, 80, 10);
		BlockPos p2 = new BlockPos(20, 70, 20); // minY = 70, maxY = 80, height = 11

		ClientMiningCaptureTracker.setPos1(p1);
		ClientMiningCaptureTracker.setPos2(p2);
		Assertions.assertEquals(11, ClientMiningCaptureTracker.getSizeY());

		// Increase height by 4 -> height = 15, targetMaxY = 70 + 15 - 1 = 84
		int newH = ClientMiningCaptureTracker.adjustHeight(4);
		Assertions.assertEquals(15, newH);
		Assertions.assertEquals(84, ClientMiningCaptureTracker.getPos1().getY());
		Assertions.assertEquals(70, ClientMiningCaptureTracker.getPos2().getY());
	}

	@Test
	@DisplayName("setCorners method sets both corners simultaneously")
	void testSetCorners() {
		BlockPos p1 = new BlockPos(5, 10, 15);
		BlockPos p2 = new BlockPos(25, 30, 35);
		ClientMiningCaptureTracker.setCorners(p1, p2);

		Assertions.assertTrue(ClientMiningCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(p1, ClientMiningCaptureTracker.getPos1());
		Assertions.assertEquals(p2, ClientMiningCaptureTracker.getPos2());
		Assertions.assertEquals(21, ClientMiningCaptureTracker.getSizeX());
		Assertions.assertEquals(21, ClientMiningCaptureTracker.getSizeY());
		Assertions.assertEquals(21, ClientMiningCaptureTracker.getSizeZ());
	}

	@Test
	@DisplayName("stepCorner with null safely returns 0")
	void testStepCornerNull() {
		Assertions.assertEquals(0, ClientMiningCaptureTracker.stepCorner(null));
	}

	@Test
	@DisplayName("Destructible block count returns volume fallback in headless test environment")
	void testDestructibleBlockCountFallback() {
		ClientMiningCaptureTracker.setPos1(new BlockPos(0, 0, 0));
		ClientMiningCaptureTracker.setPos2(new BlockPos(3, 3, 3)); // 4x4x4 = 64
		Assertions.assertEquals(64, ClientMiningCaptureTracker.getDestructibleBlockCount());
	}
}
