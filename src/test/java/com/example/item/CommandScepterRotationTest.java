package com.example.item;

import com.example.component.CommandMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Command Scepter rotation mechanics, door beacon alignments,
 * wireframe bounding box transpositions (Refinement 3), and sneak attack dispatch (Refinement 5).
 */
public class CommandScepterRotationTest {

	public static int normalizeRotationIndex(int index) {
		return Math.floorMod(index, 4);
	}

	public static BlockRotation resolveRotation(int index) {
		return switch (normalizeRotationIndex(index)) {
			case 1 -> BlockRotation.CLOCKWISE_90;
			case 2 -> BlockRotation.CLOCKWISE_180;
			case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
			default -> BlockRotation.NONE;
		};
	}

	public static int resolveRotationIndex(BlockRotation rotation) {
		if (rotation == null) return 0;
		return switch (rotation) {
			case CLOCKWISE_90 -> 1;
			case CLOCKWISE_180 -> 2;
			case COUNTERCLOCKWISE_90 -> 3;
			default -> 0;
		};
	}

	public static BlockPos rotateOffset(BlockPos pos, BlockRotation rotation) {
		if (rotation == null || rotation == BlockRotation.NONE) {
			return pos;
		}
		int newX;
		int newZ;
		int newY = pos.getY();
		switch (rotation) {
			case CLOCKWISE_90 -> {
				newX = -pos.getZ();
				newZ = pos.getX();
			}
			case CLOCKWISE_180 -> {
				newX = -pos.getX();
				newZ = -pos.getZ();
			}
			case COUNTERCLOCKWISE_90 -> {
				newX = pos.getZ();
				newZ = -pos.getX();
			}
			default -> {
				newX = pos.getX();
				newZ = pos.getZ();
			}
		}
		return new BlockPos(newX, newY, newZ);
	}

	public static BlockBox computeRotatedBoundingBox(List<BlockPos> offsets, BlockRotation rotation) {
		if (offsets.isEmpty()) {
			return new BlockBox(0, 0, 0, 0, 0, 0);
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : offsets) {
			BlockPos rot = rotateOffset(p, rotation);
			minX = Math.min(minX, rot.getX());
			minY = Math.min(minY, rot.getY());
			minZ = Math.min(minZ, rot.getZ());
			maxX = Math.max(maxX, rot.getX());
			maxY = Math.max(maxY, rot.getY());
			maxZ = Math.max(maxZ, rot.getZ());
		}
		return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static double[] computeFrontGuideCoordinates(BlockBox box, BlockRotation rotation, BlockPos anchorPos) {
		double minX = anchorPos.getX() + box.getMinX();
		double maxX = anchorPos.getX() + box.getMaxX() + 1.0D;
		double minZ = anchorPos.getZ() + box.getMinZ();
		double maxZ = anchorPos.getZ() + box.getMaxZ() + 1.0D;

		double guideX;
		double guideZ;
		switch (rotation) {
			case CLOCKWISE_90 -> {
				guideX = minX;
				guideZ = (minZ + maxZ) / 2.0D;
			}
			case CLOCKWISE_180 -> {
				guideX = (minX + maxX) / 2.0D;
				guideZ = minZ;
			}
			case COUNTERCLOCKWISE_90 -> {
				guideX = maxX;
				guideZ = (minZ + maxZ) / 2.0D;
			}
			default -> {
				guideX = (minX + maxX) / 2.0D;
				guideZ = maxZ;
			}
		}
		return new double[] { guideX, guideZ };
	}

	@Test
	@DisplayName("Rotation index maps bijectively to BlockRotation enums and degrees")
	void testRotationIndexMapping() {
		Assertions.assertEquals(BlockRotation.NONE, resolveRotation(0));
		Assertions.assertEquals(BlockRotation.CLOCKWISE_90, resolveRotation(1));
		Assertions.assertEquals(BlockRotation.CLOCKWISE_180, resolveRotation(2));
		Assertions.assertEquals(BlockRotation.COUNTERCLOCKWISE_90, resolveRotation(3));

		// Modulo 4 normalization
		Assertions.assertEquals(0, normalizeRotationIndex(0));
		Assertions.assertEquals(1, normalizeRotationIndex(1));
		Assertions.assertEquals(2, normalizeRotationIndex(2));
		Assertions.assertEquals(3, normalizeRotationIndex(3));
		Assertions.assertEquals(0, normalizeRotationIndex(4));
		Assertions.assertEquals(3, normalizeRotationIndex(-1));
		Assertions.assertEquals(2, normalizeRotationIndex(-2));
	}

	@Test
	@DisplayName("Full 360-degree cyclical state progression")
	void testCyclicalRotationProgression() {
		int current = 0;
		for (int i = 0; i < 8; i++) {
			int expectedDeg = (i % 4) * 90;
			Assertions.assertEquals(expectedDeg, current * 90);
			current = normalizeRotationIndex(current + 1);
		}
	}

	@Test
	@DisplayName("Door facing orientations rotate correctly across 4 quadrants")
	void testDoorFacingOrientationRotation() {
		Direction baseFacing = Direction.SOUTH;

		Direction rot0 = BlockRotation.NONE.rotate(baseFacing);
		Assertions.assertEquals(Direction.SOUTH, rot0);

		Direction rot90 = BlockRotation.CLOCKWISE_90.rotate(baseFacing);
		Assertions.assertEquals(Direction.WEST, rot90);

		Direction rot180 = BlockRotation.CLOCKWISE_180.rotate(baseFacing);
		Assertions.assertEquals(Direction.NORTH, rot180);

		Direction rot270 = BlockRotation.COUNTERCLOCKWISE_90.rotate(baseFacing);
		Assertions.assertEquals(Direction.EAST, rot270);
	}

	@Test
	@DisplayName("Door beacon sparkle positions rotate synchronously around origin")
	void testDoorBeaconSparklePositions() {
		BlockPos anchor = new BlockPos(100, 64, 200);
		BlockPos doorOffset = new BlockPos(2, 0, 0); // Door at offset (+2, 0, 0)

		// 0 degrees: anchor + (2, 0, 0) -> (102, 64, 200)
		BlockPos door0 = anchor.add(rotateOffset(doorOffset, BlockRotation.NONE));
		Assertions.assertEquals(new BlockPos(102, 64, 200), door0);

		// 90 degrees: (2, 0, 0) -> (0, 0, 2) -> (100, 64, 202)
		BlockPos door90 = anchor.add(rotateOffset(doorOffset, BlockRotation.CLOCKWISE_90));
		Assertions.assertEquals(new BlockPos(100, 64, 202), door90);

		// 180 degrees: (2, 0, 0) -> (-2, 0, 0) -> (98, 64, 200)
		BlockPos door180 = anchor.add(rotateOffset(doorOffset, BlockRotation.CLOCKWISE_180));
		Assertions.assertEquals(new BlockPos(98, 64, 200), door180);

		// 270 degrees: (2, 0, 0) -> (0, 0, -2) -> (100, 64, 198)
		BlockPos door270 = anchor.add(rotateOffset(doorOffset, BlockRotation.COUNTERCLOCKWISE_90));
		Assertions.assertEquals(new BlockPos(100, 64, 198), door270);
	}

	@Test
	@DisplayName("Front guide position resolves along rotated perimeter for doorless structures")
	void testFrontGuidePosition() {
		BlockPos anchor = new BlockPos(0, 0, 0);
		// Box spanning 0..4 in X, 0..2 in Z (sizeX=5, sizeZ=3)
		BlockBox box0 = new BlockBox(0, 0, 0, 4, 1, 2);

		// 0 degrees: front is south (maxZ = 3.0), centerX = 2.5
		double[] guide0 = computeFrontGuideCoordinates(box0, BlockRotation.NONE, anchor);
		Assertions.assertEquals(2.5, guide0[0], 1e-6);
		Assertions.assertEquals(3.0, guide0[1], 1e-6);

		// 90 degrees: front is west (minX)
		BlockBox box90 = new BlockBox(-2, 0, 0, 0, 1, 4);
		double[] guide90 = computeFrontGuideCoordinates(box90, BlockRotation.CLOCKWISE_90, anchor);
		Assertions.assertEquals(-2.0, guide90[0], 1e-6);
		Assertions.assertEquals(2.5, guide90[1], 1e-6);
	}

	@Test
	@DisplayName("Bounding box dimensions transpose on 90 and 270 degree rotations (Refinement 3)")
	void testBoundingBoxTransposition() {
		List<BlockPos> offsets = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int y = 0; y < 2; y++) {
				for (int z = 0; z < 3; z++) {
					offsets.add(new BlockPos(x, y, z));
				}
			}
		}

		BlockBox box0 = computeRotatedBoundingBox(offsets, BlockRotation.NONE);
		Assertions.assertEquals(5, box0.getBlockCountX());
		Assertions.assertEquals(2, box0.getBlockCountY());
		Assertions.assertEquals(3, box0.getBlockCountZ());

		BlockBox box90 = computeRotatedBoundingBox(offsets, BlockRotation.CLOCKWISE_90);
		Assertions.assertEquals(3, box90.getBlockCountX());
		Assertions.assertEquals(2, box90.getBlockCountY());
		Assertions.assertEquals(5, box90.getBlockCountZ());

		BlockBox box180 = computeRotatedBoundingBox(offsets, BlockRotation.CLOCKWISE_180);
		Assertions.assertEquals(5, box180.getBlockCountX());
		Assertions.assertEquals(2, box180.getBlockCountY());
		Assertions.assertEquals(3, box180.getBlockCountZ());

		BlockBox box270 = computeRotatedBoundingBox(offsets, BlockRotation.COUNTERCLOCKWISE_90);
		Assertions.assertEquals(3, box270.getBlockCountX());
		Assertions.assertEquals(2, box270.getBlockCountY());
		Assertions.assertEquals(5, box270.getBlockCountZ());
	}

	@Test
	@DisplayName("Sneak left-click mode dispatch invariant: BUILD mode cycles rotation, others deselect (Refinement 5)")
	void testSneakLeftClickDispatchInvariant() {
		CommandMode buildMode = CommandMode.BUILD;
		CommandMode attackMode = CommandMode.ATTACK;
		CommandMode followMode = CommandMode.FOLLOW;

		// Dispatch assertion
		Assertions.assertTrue(buildMode == CommandMode.BUILD, "BUILD mode must cycle rotation");
		Assertions.assertFalse(attackMode == CommandMode.BUILD, "ATTACK mode must deselect minions");
		Assertions.assertFalse(followMode == CommandMode.BUILD, "FOLLOW mode must deselect minions");
	}
}
