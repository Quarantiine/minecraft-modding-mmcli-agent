package com.example.client.renderer;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating ClientDesignCaptureTracker state management,
 * coordinate bounds calculation, volume limit validation, and formatting.
 */
public class ClientDesignCaptureTrackerTest {

	@BeforeEach
	void setUp() {
		ClientDesignCaptureTracker.clear();
	}

	@Test
	@DisplayName("Tracker initializes empty with incomplete selection")
	void testInitialState() {
		Assertions.assertNull(ClientDesignCaptureTracker.getPos1());
		Assertions.assertNull(ClientDesignCaptureTracker.getPos2());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(0, ClientDesignCaptureTracker.getSizeX());
		Assertions.assertEquals(0, ClientDesignCaptureTracker.getSizeY());
		Assertions.assertEquals(0, ClientDesignCaptureTracker.getSizeZ());
		Assertions.assertEquals(0L, ClientDesignCaptureTracker.getVolume());
		Assertions.assertNull(ClientDesignCaptureTracker.getBlockBox());
		Assertions.assertNull(ClientDesignCaptureTracker.getBox());
		Assertions.assertEquals("Incomplete Selection", ClientDesignCaptureTracker.getDimensionString());
	}

	@Test
	@DisplayName("Setting Pos1 and Pos2 computes dimensions and volume accurately")
	void testDimensionCalculation() {
		BlockPos p1 = new BlockPos(10, 64, 20);
		BlockPos p2 = new BlockPos(16, 70, 26);

		ClientDesignCaptureTracker.setPos1(p1);
		Assertions.assertTrue(ClientDesignCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasCompleteSelection());

		ClientDesignCaptureTracker.setPos2(p2);
		Assertions.assertTrue(ClientDesignCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(p1, ClientDesignCaptureTracker.getPos1());
		Assertions.assertEquals(p2, ClientDesignCaptureTracker.getPos2());

		// Dimensions: 16 - 10 + 1 = 7, 70 - 64 + 1 = 7, 26 - 20 + 1 = 7
		Assertions.assertEquals(7, ClientDesignCaptureTracker.getSizeX());
		Assertions.assertEquals(7, ClientDesignCaptureTracker.getSizeY());
		Assertions.assertEquals(7, ClientDesignCaptureTracker.getSizeZ());
		Assertions.assertEquals(343L, ClientDesignCaptureTracker.getVolume());
		Assertions.assertTrue(ClientDesignCaptureTracker.isWithinLimit());

		Assertions.assertEquals("7x7x7 (343b)", ClientDesignCaptureTracker.getDimensionString());

		BlockBox blockBox = ClientDesignCaptureTracker.getBlockBox();
		Assertions.assertNotNull(blockBox);
		Assertions.assertEquals(10, blockBox.getMinX());
		Assertions.assertEquals(64, blockBox.getMinY());
		Assertions.assertEquals(20, blockBox.getMinZ());
		Assertions.assertEquals(16, blockBox.getMaxX());
		Assertions.assertEquals(70, blockBox.getMaxY());
		Assertions.assertEquals(26, blockBox.getMaxZ());

		Box box = ClientDesignCaptureTracker.getBox();
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

		ClientDesignCaptureTracker.setPos1(p1);
		ClientDesignCaptureTracker.setPos2(p2);

		Assertions.assertEquals(11, ClientDesignCaptureTracker.getSizeX());
		Assertions.assertEquals(11, ClientDesignCaptureTracker.getSizeY());
		Assertions.assertEquals(6, ClientDesignCaptureTracker.getSizeZ());
		Assertions.assertEquals(726L, ClientDesignCaptureTracker.getVolume());

		BlockBox box = ClientDesignCaptureTracker.getBlockBox();
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
		ClientDesignCaptureTracker.setPos1(new BlockPos(0, 0, 0));
		ClientDesignCaptureTracker.setPos2(new BlockPos(63, 95, 63));
		Assertions.assertTrue(ClientDesignCaptureTracker.isWithinLimit());

		// Exceeds max horizontal dimension (> 64)
		ClientDesignCaptureTracker.setPos2(new BlockPos(64, 0, 0));
		Assertions.assertFalse(ClientDesignCaptureTracker.isWithinLimit());

		// Exceeds max height dimension (> 96)
		ClientDesignCaptureTracker.setPos2(new BlockPos(0, 96, 0));
		Assertions.assertFalse(ClientDesignCaptureTracker.isWithinLimit());

		// Exceeds max volume (> 393216)
		ClientDesignCaptureTracker.setPos2(new BlockPos(63, 95, 64)); // 64x96x65 = 399360 > 393216
		Assertions.assertFalse(ClientDesignCaptureTracker.isWithinLimit());
	}

	@Test
	@DisplayName("Clearing tracker resets all coordinate state")
	void testClearState() {
		ClientDesignCaptureTracker.setPos1(new BlockPos(1, 2, 3));
		ClientDesignCaptureTracker.setPos2(new BlockPos(4, 5, 6));
		Assertions.assertTrue(ClientDesignCaptureTracker.hasCompleteSelection());

		ClientDesignCaptureTracker.clear();
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos2());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasCompleteSelection());
		Assertions.assertNull(ClientDesignCaptureTracker.getPos1());
		Assertions.assertNull(ClientDesignCaptureTracker.getPos2());
	}

	@Test
	@DisplayName("Sequential stepCorner advances from Pos1 to Pos2 and automatically cycles")
	void testStepCornerSequentialWorkflow() {
		BlockPos p1 = new BlockPos(10, 64, 20);
		BlockPos p2 = new BlockPos(20, 70, 30);
		BlockPos p3 = new BlockPos(100, 50, 100);
		BlockPos p4 = new BlockPos(110, 60, 110);

		// Step 1: Pos1 set
		int step1 = ClientDesignCaptureTracker.stepCorner(p1);
		Assertions.assertEquals(1, step1);
		Assertions.assertEquals(p1, ClientDesignCaptureTracker.getPos1());
		Assertions.assertNull(ClientDesignCaptureTracker.getPos2());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasCompleteSelection());

		// Step 2: Pos2 set -> Complete
		int step2 = ClientDesignCaptureTracker.stepCorner(p2);
		Assertions.assertEquals(2, step2);
		Assertions.assertEquals(p1, ClientDesignCaptureTracker.getPos1());
		Assertions.assertEquals(p2, ClientDesignCaptureTracker.getPos2());
		Assertions.assertTrue(ClientDesignCaptureTracker.hasCompleteSelection());
		Assertions.assertEquals(11, ClientDesignCaptureTracker.getSizeX());
		Assertions.assertEquals(7, ClientDesignCaptureTracker.getSizeY());
		Assertions.assertEquals(11, ClientDesignCaptureTracker.getSizeZ());

		// Duplicate click on p2 block must retain complete selection and NOT collapse to 1x1x1
		int step2Duplicate = ClientDesignCaptureTracker.stepCorner(p2);
		Assertions.assertEquals(2, step2Duplicate, "Repeated click on pos2 block must retain completed volume");
		Assertions.assertEquals(p1, ClientDesignCaptureTracker.getPos1());
		Assertions.assertEquals(p2, ClientDesignCaptureTracker.getPos2());
		Assertions.assertTrue(ClientDesignCaptureTracker.hasCompleteSelection());

		// Step 3 (Cycle/Restart with a different block): Pos1 set to p3, Pos2 cleared
		int step3 = ClientDesignCaptureTracker.stepCorner(p3);
		Assertions.assertEquals(1, step3);
		Assertions.assertEquals(p3, ClientDesignCaptureTracker.getPos1());
		Assertions.assertNull(ClientDesignCaptureTracker.getPos2());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasCompleteSelection());

		// Step 4: Pos2 set to p4 -> Complete again
		int step4 = ClientDesignCaptureTracker.stepCorner(p4);
		Assertions.assertEquals(2, step4);
		Assertions.assertEquals(p3, ClientDesignCaptureTracker.getPos1());
		Assertions.assertEquals(p4, ClientDesignCaptureTracker.getPos2());
		Assertions.assertTrue(ClientDesignCaptureTracker.hasCompleteSelection());

		// Null input edge case
		int stepNull = ClientDesignCaptureTracker.stepCorner(null);
		Assertions.assertEquals(0, stepNull);
		Assertions.assertEquals(p3, ClientDesignCaptureTracker.getPos1());
		Assertions.assertEquals(p4, ClientDesignCaptureTracker.getPos2());
	}

	@Test
	@DisplayName("Clearing tracker guarantees next stepCorner starts with Pos1")
	void testClearGuaranteesNextStepIsPos1() {
		BlockPos p1 = new BlockPos(5, 60, 5);
		BlockPos p2 = new BlockPos(15, 70, 15);
		BlockPos pAfter = new BlockPos(40, 64, 40);

		// Set Pos1 only, then clear
		ClientDesignCaptureTracker.stepCorner(p1);
		Assertions.assertTrue(ClientDesignCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos2());

		ClientDesignCaptureTracker.clear();
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos1());
		Assertions.assertFalse(ClientDesignCaptureTracker.hasPos2());

		// Next step must be Pos1
		int stepA = ClientDesignCaptureTracker.stepCorner(pAfter);
		Assertions.assertEquals(1, stepA, "After clearing, next stepCorner must be step 1 (Pos1)");
		Assertions.assertEquals(pAfter, ClientDesignCaptureTracker.getPos1());
		Assertions.assertNull(ClientDesignCaptureTracker.getPos2());

		// Complete Pos2, then clear
		ClientDesignCaptureTracker.stepCorner(p2);
		Assertions.assertTrue(ClientDesignCaptureTracker.hasCompleteSelection());

		ClientDesignCaptureTracker.clear();
		Assertions.assertFalse(ClientDesignCaptureTracker.hasCompleteSelection());

		// Next step after full selection clear must also be Pos1
		BlockPos pFresh = new BlockPos(50, 64, 50);
		int stepB = ClientDesignCaptureTracker.stepCorner(pFresh);
		Assertions.assertEquals(1, stepB, "After clearing full selection, next stepCorner must be step 1 (Pos1)");
		Assertions.assertEquals(pFresh, ClientDesignCaptureTracker.getPos1());
		Assertions.assertNull(ClientDesignCaptureTracker.getPos2());
	}
}
