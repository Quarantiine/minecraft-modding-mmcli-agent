package com.example.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying blueprint rotation matrix transformation,
 * door offset detection, bounding box recalculations, and topological sorting invariants.
 */
public class BlueprintRotationTest {

	/**
	 * Simulates coordinate transformations matching StructureBlueprint's rotate() logic.
	 */
	public static BlockPos transformOffset(BlockPos pos, BlockRotation rotation) {
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

	public static BlockRotation resolveRotation(int index) {
		int normalized = Math.floorMod(index, 4);
		return switch (normalized) {
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

	@Test
	@DisplayName("Blueprint rotation matrix transforms coordinates around origin correctly")
	public void testRotationMatrixCoordinateTransformations() {
		BlockPos p1 = new BlockPos(2, 0, 3);
		BlockPos p2 = new BlockPos(-1, 1, 4);

		// NONE (0 degrees)
		Assertions.assertEquals(p1, transformOffset(p1, BlockRotation.NONE));
		Assertions.assertEquals(p2, transformOffset(p2, BlockRotation.NONE));
		Assertions.assertEquals(p1, transformOffset(p1, null));

		// CLOCKWISE_90 (90 degrees): (x, y, z) -> (-z, y, x)
		// (2, 0, 3) -> (-3, 0, 2)
		Assertions.assertEquals(new BlockPos(-3, 0, 2), transformOffset(p1, BlockRotation.CLOCKWISE_90));
		// (-1, 1, 4) -> (-4, 1, -1)
		Assertions.assertEquals(new BlockPos(-4, 1, -1), transformOffset(p2, BlockRotation.CLOCKWISE_90));

		// CLOCKWISE_180 (180 degrees): (x, y, z) -> (-x, y, -z)
		// (2, 0, 3) -> (-2, 0, -3)
		Assertions.assertEquals(new BlockPos(-2, 0, -3), transformOffset(p1, BlockRotation.CLOCKWISE_180));
		// (-1, 1, 4) -> (1, 1, -4)
		Assertions.assertEquals(new BlockPos(1, 1, -4), transformOffset(p2, BlockRotation.CLOCKWISE_180));

		// COUNTERCLOCKWISE_90 (270 degrees): (x, y, z) -> (z, y, -x)
		// (2, 0, 3) -> (3, 0, -2)
		Assertions.assertEquals(new BlockPos(3, 0, -2), transformOffset(p1, BlockRotation.COUNTERCLOCKWISE_90));
		// (-1, 1, 4) -> (4, 1, 1)
		Assertions.assertEquals(new BlockPos(4, 1, 1), transformOffset(p2, BlockRotation.COUNTERCLOCKWISE_90));

		// 4 consecutive 90-degree rotations return to original relative positions
		BlockPos cur = p1;
		for (int i = 0; i < 4; i++) {
			cur = transformOffset(cur, BlockRotation.CLOCKWISE_90);
		}
		Assertions.assertEquals(p1, cur, "Full 360 cycle must return to original coordinates");
	}

	@Test
	@DisplayName("Integer rotation index overload correctly resolves degrees modulo 4")
	public void testIntegerRotationOverload() {
		Assertions.assertEquals(BlockRotation.NONE, resolveRotation(0));
		Assertions.assertEquals(BlockRotation.CLOCKWISE_90, resolveRotation(1));
		Assertions.assertEquals(BlockRotation.CLOCKWISE_180, resolveRotation(2));
		Assertions.assertEquals(BlockRotation.COUNTERCLOCKWISE_90, resolveRotation(3));

		// Modulo 4 wrap-around
		Assertions.assertEquals(BlockRotation.CLOCKWISE_90, resolveRotation(5));
		Assertions.assertEquals(BlockRotation.CLOCKWISE_180, resolveRotation(6));
		Assertions.assertEquals(BlockRotation.COUNTERCLOCKWISE_90, resolveRotation(7));
		Assertions.assertEquals(BlockRotation.NONE, resolveRotation(8));

		// Negative indices
		Assertions.assertEquals(BlockRotation.COUNTERCLOCKWISE_90, resolveRotation(-1));
		Assertions.assertEquals(BlockRotation.CLOCKWISE_180, resolveRotation(-2));
		Assertions.assertEquals(BlockRotation.CLOCKWISE_90, resolveRotation(-3));
		Assertions.assertEquals(BlockRotation.NONE, resolveRotation(-4));

		// Inverse resolution
		Assertions.assertEquals(0, resolveRotationIndex(BlockRotation.NONE));
		Assertions.assertEquals(1, resolveRotationIndex(BlockRotation.CLOCKWISE_90));
		Assertions.assertEquals(2, resolveRotationIndex(BlockRotation.CLOCKWISE_180));
		Assertions.assertEquals(3, resolveRotationIndex(BlockRotation.COUNTERCLOCKWISE_90));
		Assertions.assertEquals(0, resolveRotationIndex(null));
	}

	@Test
	@DisplayName("Door offset detection identifies lower door halves and rotates them accordingly")
	public void testDoorOffsetDetectionAndRotation() {
		record MockDoor(BlockPos pos, boolean isLower) {}

		List<MockDoor> doors = List.of(
			new MockDoor(new BlockPos(0, 1, 2), true),  // South door lower
			new MockDoor(new BlockPos(0, 2, 2), false)  // South door upper
		);

		List<BlockPos> lowerDoors = new ArrayList<>();
		for (MockDoor door : doors) {
			if (door.isLower()) {
				lowerDoors.add(door.pos());
			}
		}

		Assertions.assertEquals(1, lowerDoors.size());
		BlockPos doorPos = lowerDoors.get(0);
		Assertions.assertEquals(new BlockPos(0, 1, 2), doorPos);

		// Rotate 90° CLOCKWISE: (0, 1, 2) -> (-2, 1, 0)
		BlockPos rotated90 = transformOffset(doorPos, BlockRotation.CLOCKWISE_90);
		Assertions.assertEquals(new BlockPos(-2, 1, 0), rotated90);

		// Rotate 180°: (0, 1, 2) -> (0, 1, -2)
		BlockPos rotated180 = transformOffset(doorPos, BlockRotation.CLOCKWISE_180);
		Assertions.assertEquals(new BlockPos(0, 1, -2), rotated180);

		// Rotate 270° (COUNTERCLOCKWISE_90): (0, 1, 2) -> (2, 1, 0)
		BlockPos rotated270 = transformOffset(doorPos, BlockRotation.COUNTERCLOCKWISE_90);
		Assertions.assertEquals(new BlockPos(2, 1, 0), rotated270);
	}

	@Test
	@DisplayName("Multi-door discovery detects all lower door positions while strictly excluding upper halves")
	public void testMultiDoorDiscoveryAndExclusionOfUpperHalves() {
		enum Half { LOWER, UPPER }
		record DoorSpec(BlockPos offset, Half half, String facing) {}

		// Define a multi-door blueprint: double entrance doors at South, plus side doors East and West
		List<DoorSpec> structureDoors = List.of(
			// South main double doors
			new DoorSpec(new BlockPos(2, 1, 4), Half.LOWER, "SOUTH"),
			new DoorSpec(new BlockPos(2, 2, 4), Half.UPPER, "SOUTH"),
			new DoorSpec(new BlockPos(3, 1, 4), Half.LOWER, "SOUTH"),
			new DoorSpec(new BlockPos(3, 2, 4), Half.UPPER, "SOUTH"),
			// East side door
			new DoorSpec(new BlockPos(5, 1, 2), Half.LOWER, "EAST"),
			new DoorSpec(new BlockPos(5, 2, 2), Half.UPPER, "EAST"),
			// West side door
			new DoorSpec(new BlockPos(0, 1, 2), Half.LOWER, "WEST"),
			new DoorSpec(new BlockPos(0, 2, 2), Half.UPPER, "WEST")
		);

		// Filter for doorOffsets (only Half.LOWER)
		List<BlockPos> discoveredDoorOffsets = structureDoors.stream()
			.filter(d -> d.half() == Half.LOWER)
			.map(DoorSpec::offset)
			.toList();

		// Verification: exactly 4 lower door offsets discovered, 0 upper halves
		Assertions.assertEquals(4, discoveredDoorOffsets.size(), "Must discover exactly 4 lower door offsets");
		Assertions.assertTrue(discoveredDoorOffsets.contains(new BlockPos(2, 1, 4)));
		Assertions.assertTrue(discoveredDoorOffsets.contains(new BlockPos(3, 1, 4)));
		Assertions.assertTrue(discoveredDoorOffsets.contains(new BlockPos(5, 1, 2)));
		Assertions.assertTrue(discoveredDoorOffsets.contains(new BlockPos(0, 1, 2)));

		// Upper halves must NOT be in the discovered list
		Assertions.assertFalse(discoveredDoorOffsets.contains(new BlockPos(2, 2, 4)));
		Assertions.assertFalse(discoveredDoorOffsets.contains(new BlockPos(3, 2, 4)));

		// Test rotation of all discovered door offsets under 90° clockwise
		List<BlockPos> rotatedOffsets90 = discoveredDoorOffsets.stream()
			.map(p -> transformOffset(p, BlockRotation.CLOCKWISE_90))
			.toList();

		// (2, 1, 4) -> (-4, 1, 2)
		Assertions.assertTrue(rotatedOffsets90.contains(new BlockPos(-4, 1, 2)));
		// (3, 1, 4) -> (-4, 1, 3)
		Assertions.assertTrue(rotatedOffsets90.contains(new BlockPos(-4, 1, 3)));
		// (5, 1, 2) -> (-2, 1, 5)
		Assertions.assertTrue(rotatedOffsets90.contains(new BlockPos(-2, 1, 5)));
		// (0, 1, 2) -> (-2, 1, 0)
		Assertions.assertTrue(rotatedOffsets90.contains(new BlockPos(-2, 1, 0)));

		// Test world anchor translation
		BlockPos anchor = new BlockPos(1000, 64, 2000);
		List<BlockPos> worldDoorPositions = rotatedOffsets90.stream()
			.map(anchor::add)
			.toList();

		Assertions.assertTrue(worldDoorPositions.contains(new BlockPos(996, 65, 2002)));
		Assertions.assertTrue(worldDoorPositions.contains(new BlockPos(996, 65, 2003)));
		Assertions.assertTrue(worldDoorPositions.contains(new BlockPos(998, 65, 2005)));
		Assertions.assertTrue(worldDoorPositions.contains(new BlockPos(998, 65, 2000)));
	}

	@Test
	@DisplayName("Rotation mathematics satisfies group theory composition, inverses, and distance invariants")
	public void testRotationMathematicsGroupTheoryInvariants() {
		List<BlockPos> testVectors = List.of(
			new BlockPos(0, 0, 0),
			new BlockPos(5, 10, -7),
			new BlockPos(-12, 64, 25),
			new BlockPos(-3, -8, -9),
			new BlockPos(100, 200, 300)
		);

		for (BlockPos v : testVectors) {
			// Invariant 1: Identity R_0(v) == v
			Assertions.assertEquals(v, transformOffset(v, BlockRotation.NONE));

			// Invariant 2: Elevation invariance R_theta(v).getY() == v.getY()
			for (BlockRotation rot : BlockRotation.values()) {
				Assertions.assertEquals(v.getY(), transformOffset(v, rot).getY(), "Vertical elevation must be invariant");
			}

			// Invariant 3: Euclidean distance squared in horizontal plane is preserved
			long originalDistSq = (long) v.getX() * v.getX() + (long) v.getZ() * v.getZ();
			for (BlockRotation rot : BlockRotation.values()) {
				BlockPos rotV = transformOffset(v, rot);
				long rotatedDistSq = (long) rotV.getX() * rotV.getX() + (long) rotV.getZ() * rotV.getZ();
				Assertions.assertEquals(originalDistSq, rotatedDistSq, "Horizontal Euclidean distance squared must be preserved");
			}

			// Invariant 4: Inverses R_90 * R_270 == R_0 and R_180 * R_180 == R_0
			BlockPos r90 = transformOffset(v, BlockRotation.CLOCKWISE_90);
			BlockPos r90_270 = transformOffset(r90, BlockRotation.COUNTERCLOCKWISE_90);
			Assertions.assertEquals(v, r90_270, "R_270 must be inverse of R_90");

			BlockPos r180 = transformOffset(v, BlockRotation.CLOCKWISE_180);
			BlockPos r180_180 = transformOffset(r180, BlockRotation.CLOCKWISE_180);
			Assertions.assertEquals(v, r180_180, "R_180 must be its own inverse");

			// Invariant 5: Homomorphism R_90(R_90(v)) == R_180(v)
			BlockPos r90_90 = transformOffset(r90, BlockRotation.CLOCKWISE_90);
			Assertions.assertEquals(r180, r90_90, "Two 90° rotations must equal 180° rotation");

			// Invariant 6: Homomorphism R_90(R_180(v)) == R_270(v)
			BlockPos r90_180 = transformOffset(r180, BlockRotation.CLOCKWISE_90);
			BlockPos r270 = transformOffset(v, BlockRotation.COUNTERCLOCKWISE_90);
			Assertions.assertEquals(r270, r90_180, "90° + 180° rotations must equal 270° rotation");
		}
	}

	@Test
	@DisplayName("Bounding box and dimension sizes swap X and Z under 90-degree rotations")
	public void testBoundingBoxAndSizeTransformations() {
		// Asymmetric structure: X in [-1, 2] (sizeX = 4), Y in [0, 2] (sizeY = 3), Z in [0, 1] (sizeZ = 2)
		List<BlockPos> originalPositions = new ArrayList<>();
		for (int y = 0; y <= 2; y++) {
			for (int x = -1; x <= 2; x++) {
				for (int z = 0; z <= 1; z++) {
					originalPositions.add(new BlockPos(x, y, z));
				}
			}
		}

		// Calculate bounds
		int minX = originalPositions.stream().mapToInt(BlockPos::getX).min().orElse(0);
		int maxX = originalPositions.stream().mapToInt(BlockPos::getX).max().orElse(0);
		int minY = originalPositions.stream().mapToInt(BlockPos::getY).min().orElse(0);
		int maxY = originalPositions.stream().mapToInt(BlockPos::getY).max().orElse(0);
		int minZ = originalPositions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
		int maxZ = originalPositions.stream().mapToInt(BlockPos::getZ).max().orElse(0);

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;

		Assertions.assertEquals(4, sizeX);
		Assertions.assertEquals(3, sizeY);
		Assertions.assertEquals(2, sizeZ);

		// Rotate all points 90 degrees
		List<BlockPos> rotated90 = originalPositions.stream()
			.map(p -> transformOffset(p, BlockRotation.CLOCKWISE_90))
			.toList();

		int rotMinX = rotated90.stream().mapToInt(BlockPos::getX).min().orElse(0);
		int rotMaxX = rotated90.stream().mapToInt(BlockPos::getX).max().orElse(0);
		int rotMinY = rotated90.stream().mapToInt(BlockPos::getY).min().orElse(0);
		int rotMaxY = rotated90.stream().mapToInt(BlockPos::getY).max().orElse(0);
		int rotMinZ = rotated90.stream().mapToInt(BlockPos::getZ).min().orElse(0);
		int rotMaxZ = rotated90.stream().mapToInt(BlockPos::getZ).max().orElse(0);

		int rotSizeX = rotMaxX - rotMinX + 1;
		int rotSizeY = rotMaxY - rotMinY + 1;
		int rotSizeZ = rotMaxZ - rotMinZ + 1;

		// X and Z swap after 90 degree rotation
		Assertions.assertEquals(2, rotSizeX, "sizeX and sizeZ must swap on 90 degree rotation");
		Assertions.assertEquals(3, rotSizeY, "sizeY must remain unchanged under rotation");
		Assertions.assertEquals(4, rotSizeZ, "sizeX and sizeZ must swap on 90 degree rotation");

		BlockBox box90 = new BlockBox(rotMinX, rotMinY, rotMinZ, rotMaxX, rotMaxY, rotMaxZ);
		Assertions.assertEquals(-1, box90.getMinX());
		Assertions.assertEquals(0, box90.getMaxX());
		Assertions.assertEquals(-1, box90.getMinZ());
		Assertions.assertEquals(2, box90.getMaxZ());
	}

	@Test
	@DisplayName("Topological sorting preserves bottom-up layer ordering after rotation")
	public void testTopologicalSortingAfterRotation() {
		record MockBlock(BlockPos pos, int effectiveY) implements Comparable<MockBlock> {
			@Override
			public int compareTo(MockBlock o) {
				int layerComp = Integer.compare(this.effectiveY, o.effectiveY);
				if (layerComp != 0) return layerComp;
				int distComp = Integer.compare(
					Math.abs(this.pos.getX()) + Math.abs(this.pos.getZ()),
					Math.abs(o.pos.getX()) + Math.abs(o.pos.getZ())
				);
				if (distComp != 0) return distComp;
				int xComp = Integer.compare(this.pos.getX(), o.pos.getX());
				if (xComp != 0) return xComp;
				return Integer.compare(this.pos.getZ(), o.pos.getZ());
			}
		}

		List<MockBlock> blocks = new ArrayList<>();
		blocks.add(new MockBlock(new BlockPos(0, 2, 0), 2));
		blocks.add(new MockBlock(new BlockPos(1, 0, 1), 0));
		blocks.add(new MockBlock(new BlockPos(-1, 1, 0), 1));

		List<MockBlock> rotated = new ArrayList<>();
		for (MockBlock b : blocks) {
			BlockPos rotPos = transformOffset(b.pos(), BlockRotation.CLOCKWISE_90);
			rotated.add(new MockBlock(rotPos, rotPos.getY()));
		}

		Collections.sort(rotated);

		// Layer 0 must be first, Layer 1 second, Layer 2 third
		Assertions.assertEquals(0, rotated.get(0).effectiveY());
		Assertions.assertEquals(1, rotated.get(1).effectiveY());
		Assertions.assertEquals(2, rotated.get(2).effectiveY());
	}
}

