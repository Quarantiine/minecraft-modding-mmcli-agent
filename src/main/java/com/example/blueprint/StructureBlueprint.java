package com.example.blueprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

/**
 * Represents a complete multiblock structure blueprint for minion construction.
 * Contains topologically ordered {@link BlueprintBlock} specifications, metadata,
 * bounding geometry, and material requirement aggregations.
 */
public class StructureBlueprint {

	private final String id;
	private final String name;
	private final String description;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<BlueprintBlock> blocks;
	private final List<BlockPos> doorOffsets;
	private final Map<Item, Integer> requiredItems;
	private final BlockBox boundingBox;
	private final BlockRotation rotation;

	private StructureBlueprint(
		String id,
		String name,
		String description,
		int sizeX,
		int sizeY,
		int sizeZ,
		List<BlueprintBlock> blocks,
		Map<Item, Integer> requiredItems,
		BlockBox boundingBox
	) {
		this(id, name, description, sizeX, sizeY, sizeZ, blocks, requiredItems, boundingBox, BlockRotation.NONE);
	}

	public StructureBlueprint(
		String id,
		String name,
		String description,
		int sizeX,
		int sizeY,
		int sizeZ,
		List<BlueprintBlock> blocks,
		Map<Item, Integer> requiredItems,
		BlockBox boundingBox,
		BlockRotation rotation
	) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.blocks = Collections.unmodifiableList(blocks);
		this.requiredItems = Collections.unmodifiableMap(requiredItems);
		this.boundingBox = boundingBox;
		this.rotation = rotation != null ? rotation : BlockRotation.NONE;

		List<BlockPos> doors = new ArrayList<>();
		for (BlueprintBlock block : blocks) {
			BlockState state = block.state();
			try {
				if (state != null && state.getBlock() instanceof DoorBlock) {
					if (!state.contains(DoorBlock.HALF) || state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
						doors.add(block.offset());
					}
				}
			} catch (Throwable ignored) {}
		}
		this.doorOffsets = Collections.unmodifiableList(doors);
	}

	public String getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public int getSizeX() {
		return this.sizeX;
	}

	public int getSizeY() {
		return this.sizeY;
	}

	public int getSizeZ() {
		return this.sizeZ;
	}

	/**
	 * Returns the unmodifiable list of blueprint blocks sorted in bottom-up
	 * topological construction order.
	 *
	 * @return Topologically sorted blueprint blocks.
	 */
	public List<BlueprintBlock> getBlocks() {
		return this.blocks;
	}

	/**
	 * Returns the total number of blocks in this blueprint.
	 *
	 * @return Count of blocks to be placed.
	 */
	public int getBlockCount() {
		return this.blocks.size();
	}

	/**
	 * Returns an unmodifiable map of required items and their required counts.
	 *
	 * @return Mapping of Item to total quantity needed.
	 */
	public Map<Item, Integer> getRequiredItems() {
		return this.requiredItems;
	}

	/**
	 * Returns the local bounding box enclosing all blocks in this blueprint.
	 *
	 * @return BlockBox relative to blueprint origin (0, 0, 0).
	 */
	public BlockBox getBoundingBox() {
		return this.boundingBox;
	}

	/**
	 * Returns an unmodifiable list of relative BlockPos offsets for lower door blocks in this blueprint.
	 * Used for door beacon particle alignment and entrance guide indicators.
	 *
	 * @return Unmodifiable list of door base offsets relative to blueprint origin.
	 */
	public List<BlockPos> getDoorOffsets() {
		return this.doorOffsets;
	}

	/**
	 * Returns the applied block rotation of this blueprint.
	 *
	 * @return The BlockRotation applied to this blueprint, or NONE if unrotated.
	 */
	public BlockRotation getRotation() {
		return this.rotation != null ? this.rotation : BlockRotation.NONE;
	}

	/**
	 * Returns the integer quadrant index of this blueprint's rotation (0 = 0°, 1 = 90°, 2 = 180°, 3 = 270°).
	 *
	 * @return Rotation index modulo 4.
	 */
	public int getRotationIndex() {
		if (this.rotation == null) return 0;
		return switch (this.rotation) {
			case CLOCKWISE_90 -> 1;
			case CLOCKWISE_180 -> 2;
			case COUNTERCLOCKWISE_90 -> 3;
			default -> 0;
		};
	}

	/**
	 * Rotates this blueprint around the origin (0, 0) by the specified {@link BlockRotation}.
	 * Transforms coordinates according to standard rotation matrices:
	 * - NONE: (x, y, z)
	 * - CLOCKWISE_90: (-z, y, x)
	 * - CLOCKWISE_180: (-x, y, -z)
	 * - COUNTERCLOCKWISE_90: (z, y, -x)
	 * Rotates block states via {@link BlockState#rotate(BlockRotation)} (orienting stairs, doors, etc.),
	 * recomputes bounding box and dimensions, and re-sorts blocks in bottom-up topological order.
	 *
	 * @param rotation The BlockRotation to apply.
	 * @return A new rotated StructureBlueprint, or this if rotation is NONE or null.
	 */
	public StructureBlueprint rotate(BlockRotation rotation) {
		if (rotation == null || rotation == BlockRotation.NONE || this.blocks.isEmpty()) {
			return this;
		}

		List<BlueprintBlock> rotatedBlocks = new ArrayList<>(this.blocks.size());
		int minX = 0, minY = 0, minZ = 0;
		int maxX = 0, maxY = 0, maxZ = 0;
		boolean first = true;

		for (BlueprintBlock block : this.blocks) {
			BlockPos oldPos = block.offset();
			int newX;
			int newZ;
			int newY = oldPos.getY();

			switch (rotation) {
				case CLOCKWISE_90 -> {
					newX = -oldPos.getZ();
					newZ = oldPos.getX();
				}
				case CLOCKWISE_180 -> {
					newX = -oldPos.getX();
					newZ = -oldPos.getZ();
				}
				case COUNTERCLOCKWISE_90 -> {
					newX = oldPos.getZ();
					newZ = -oldPos.getX();
				}
				default -> {
					newX = oldPos.getX();
					newZ = oldPos.getZ();
				}
			}

			BlockPos newPos = new BlockPos(newX, newY, newZ);
			BlockState rotatedState = null;
			try {
				if (block.state() != null) {
					rotatedState = block.state().rotate(rotation);
				}
			} catch (Throwable ignored) {}
			if (rotatedState == null) {
				rotatedState = block.state();
			}

			rotatedBlocks.add(new BlueprintBlock(newPos, rotatedState));

			if (first) {
				minX = newX;
				maxX = newX;
				minY = newY;
				maxY = newY;
				minZ = newZ;
				maxZ = newZ;
				first = false;
			} else {
				minX = Math.min(minX, newX);
				maxX = Math.max(maxX, newX);
				minY = Math.min(minY, newY);
				maxY = Math.max(maxY, newY);
				minZ = Math.min(minZ, newZ);
				maxZ = Math.max(maxZ, newZ);
			}
		}

		// Topologically re-sort rotated blocks (bottom-up vertical layers, then Manhattan distance)
		Collections.sort(rotatedBlocks);

		int sizeX = first ? 0 : (maxX - minX + 1);
		int sizeY = first ? 0 : (maxY - minY + 1);
		int sizeZ = first ? 0 : (maxZ - minZ + 1);
		BlockBox box = first ? new BlockBox(0, 0, 0, 0, 0, 0) : new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);

		return new StructureBlueprint(
			this.id,
			this.name,
			this.description,
			sizeX,
			sizeY,
			sizeZ,
			rotatedBlocks,
			this.requiredItems,
			box,
			rotation
		);
	}

	/**
	 * Convenience overload to rotate this blueprint by an integer index:
	 * 0 -> 0° (NONE), 1 -> 90° (CLOCKWISE_90), 2 -> 180° (CLOCKWISE_180), 3 -> 270° (COUNTERCLOCKWISE_90).
	 *
	 * @param rotationIndex The rotation index modulo 4.
	 * @return A new rotated StructureBlueprint.
	 */
	public StructureBlueprint rotate(int rotationIndex) {
		int normalized = Math.floorMod(rotationIndex, 4);
		return switch (normalized) {
			case 1 -> rotate(BlockRotation.CLOCKWISE_90);
			case 2 -> rotate(BlockRotation.CLOCKWISE_180);
			case 3 -> rotate(BlockRotation.COUNTERCLOCKWISE_90);
			default -> rotate(BlockRotation.NONE);
		};
	}

	public static final int MAX_SPATIAL_DIMENSION = 64;
	public static final int MAX_SPATIAL_HEIGHT = 96;
	public static final int MAX_SPATIAL_VOLUME = 393216;

	/**
	 * Captures and normalizes in-world spatial blocks enclosed between two corner coordinates into a {@link StructureBlueprint}.
	 * Translates the bounding region so the lowest corner (minX, minY, minZ) aligns with relative origin (0, 0, 0),
	 * filters out air blocks, and deterministically sorts blocks in bottom-up topological construction order.
	 *
	 * @param world       World/BlockView instance to sample blocks from.
	 * @param pos1        First corner of the bounding volume.
	 * @param pos2        Second corner of the bounding volume.
	 * @param id          Unique blueprint identifier.
	 * @param name        User-facing blueprint display name.
	 * @param description User-facing blueprint description.
	 * @return Topologically sorted and normalized StructureBlueprint.
	 */
	public static StructureBlueprint captureFromWorld(
		net.minecraft.world.BlockView world,
		BlockPos pos1,
		BlockPos pos2,
		String id,
		String name,
		String description
	) {
		Objects.requireNonNull(world, "world cannot be null");
		Objects.requireNonNull(pos1, "pos1 cannot be null");
		Objects.requireNonNull(pos2, "pos2 cannot be null");

		String cleanId = id != null && !id.isBlank() ? id : "custom_capture_" + System.currentTimeMillis();
		String cleanName = name != null && !name.isBlank() ? name : "Captured Structure";
		String cleanDesc = (description != null && !description.isBlank()) ? description.trim() : "";

		List<BlueprintBlock> normalizedBlocks = captureBlocks(world, pos1, pos2);

		Builder builder = builder(cleanId, cleanName).description(cleanDesc);
		for (BlueprintBlock block : normalizedBlocks) {
			builder.addBlock(block.offset(), block.state());
		}
		return builder.build();
	}

	/**
	 * Creates a custom area blueprint for mining/quarrying enclosing the exact volume between pos1 and pos2.
	 *
	 * @param world       World/BlockView instance to sample blocks from.
	 * @param pos1        First corner of the bounding volume.
	 * @param pos2        Second corner of the bounding volume.
	 * @param id          Unique blueprint identifier.
	 * @param name        User-facing blueprint display name.
	 * @param description User-facing blueprint description.
	 * @return StructureBlueprint spanning the specified area box.
	 */
	public static StructureBlueprint createAreaBlueprint(
		net.minecraft.world.BlockView world,
		BlockPos pos1,
		BlockPos pos2,
		String id,
		String name,
		String description
	) {
		Objects.requireNonNull(pos1, "pos1 cannot be null");
		Objects.requireNonNull(pos2, "pos2 cannot be null");

		int minX = Math.min(pos1.getX(), pos2.getX());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;

		String cleanId = id != null && !id.isBlank() ? id : "mining_area_" + System.currentTimeMillis();
		String cleanName = name != null && !name.isBlank() ? name : "Mining Area (" + sizeX + "x" + sizeY + "x" + sizeZ + ")";
		String cleanDesc = (description != null && !description.isBlank()) ? description.trim() : "";

		List<BlueprintBlock> blocks = (world != null) ? captureBlocks(world, pos1, pos2) : Collections.emptyList();
		Map<Item, Integer> req = new LinkedHashMap<>();
		for (BlueprintBlock b : blocks) {
			if (b.state() != null && !b.state().isAir()) {
				Item item = b.state().getBlock().asItem();
				if (item != null && item != net.minecraft.item.Items.AIR) {
					req.merge(item, 1, Integer::sum);
				}
			}
		}
		BlockBox box = new BlockBox(0, 0, 0, sizeX - 1, sizeY - 1, sizeZ - 1);
		return new StructureBlueprint(
			cleanId,
			cleanName,
			cleanDesc,
			sizeX,
			sizeY,
			sizeZ,
			blocks,
			req,
			box,
			BlockRotation.NONE
		);
	}

	/**
	 * Captures non-air blocks within the specified spatial volume and normalizes coordinates
	 * relative to the minimum corner (minX, minY, minZ), sorting in bottom-up topological order.
	 *
	 * @param world World/BlockView to sample from.
	 * @param pos1  First corner position.
	 * @param pos2  Second corner position.
	 * @return List of normalized, topologically sorted BlueprintBlock entries.
	 */
	public static List<BlueprintBlock> captureBlocks(
		net.minecraft.world.BlockView world,
		BlockPos pos1,
		BlockPos pos2
	) {
		int minX = Math.min(pos1.getX(), pos2.getX());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;

		if (sizeX > MAX_SPATIAL_DIMENSION || sizeY > MAX_SPATIAL_HEIGHT || sizeZ > MAX_SPATIAL_DIMENSION) {
			throw new IllegalArgumentException(
				"Spatial capture dimensions exceed maximum limit of " + MAX_SPATIAL_DIMENSION + "x" + MAX_SPATIAL_HEIGHT + "x" + MAX_SPATIAL_DIMENSION + " (" + sizeX + "x" + sizeY + "x" + sizeZ + ")"
			);
		}

		long volume = (long) sizeX * sizeY * sizeZ;
		if (volume > MAX_SPATIAL_VOLUME) {
			throw new IllegalArgumentException(
				"Spatial capture volume exceeds maximum limit of " + MAX_SPATIAL_VOLUME + " blocks (" + volume + " voxels)"
			);
		}

		if (world == null) {
			return Collections.emptyList();
		}

		List<BlueprintBlock> captured = new ArrayList<>();
		BlockPos.Mutable mutablePos = new BlockPos.Mutable();

		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					mutablePos.set(x, y, z);
					BlockState state = world.getBlockState(mutablePos);
					if (state != null && !state.isAir()) {
						BlockPos normalizedOffset = new BlockPos(x - minX, y - minY, z - minZ);
						captured.add(new BlueprintBlock(normalizedOffset, state));
					}
				}
			}
		}

		Collections.sort(captured);
		return Collections.unmodifiableList(captured);
	}

	/**
	 * Normalizes an arbitrary collection of unnormalized {@link BlueprintBlock} elements
	 * so the lowest corner aligns with (0, 0, 0) and filters any air states.
	 *
	 * @param unnormalizedBlocks Collection of unnormalized blueprint blocks.
	 * @return List of normalized, topologically sorted BlueprintBlock entries.
	 */
	public static List<BlueprintBlock> normalizeBlocks(Collection<BlueprintBlock> unnormalizedBlocks) {
		if (unnormalizedBlocks == null || unnormalizedBlocks.isEmpty()) {
			return Collections.emptyList();
		}

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		boolean foundNonAir = false;

		for (BlueprintBlock block : unnormalizedBlocks) {
			if (block != null && block.state() != null) {
				boolean isAir = false;
				try {
					isAir = block.state().isAir();
				} catch (Throwable ignored) {}
				if (!isAir) {
					BlockPos pos = block.offset();
					minX = Math.min(minX, pos.getX());
					minY = Math.min(minY, pos.getY());
					minZ = Math.min(minZ, pos.getZ());
					foundNonAir = true;
				}
			}
		}

		if (!foundNonAir) {
			return Collections.emptyList();
		}

		List<BlueprintBlock> normalized = new ArrayList<>(unnormalizedBlocks.size());
		for (BlueprintBlock block : unnormalizedBlocks) {
			if (block != null && block.state() != null) {
				boolean isAir = false;
				try {
					isAir = block.state().isAir();
				} catch (Throwable ignored) {}
				if (!isAir) {
					BlockPos oldPos = block.offset();
					BlockPos normalizedOffset = new BlockPos(oldPos.getX() - minX, oldPos.getY() - minY, oldPos.getZ() - minZ);
					normalized.add(new BlueprintBlock(normalizedOffset, block.state()));
				}
			}
		}

		Collections.sort(normalized);
		return Collections.unmodifiableList(normalized);
	}

	/**
	 * Creates a new builder for constructing a StructureBlueprint.
	 *
	 * @param id   Unique blueprint identifier.
	 * @param name User-facing display name.
	 * @return A new Builder instance.
	 */
	public static Builder builder(String id, String name) {
		return new Builder(id, name);
	}

	/**
	 * Fluent builder for creating structured blueprints with automatic
	 * topological sorting, deduplication, and bounding box computation.
	 */
	public static class Builder {
		private final String id;
		private final String name;
		private String description = "";
		private final Map<BlockPos, BlockState> blockMap = new LinkedHashMap<>();

		public Builder(String id, String name) {
			this.id = Objects.requireNonNull(id, "id cannot be null");
			this.name = Objects.requireNonNull(name, "name cannot be null");
		}

		public Builder description(String description) {
			this.description = (description != null && !description.isBlank()) ? description.trim() : "";
			return this;
		}

		public Builder addBlock(int x, int y, int z, BlockState state) {
			return addBlock(new BlockPos(x, y, z), state);
		}

		public Builder addBlock(BlockPos pos, BlockState state) {
			if (!state.isAir()) {
				this.blockMap.put(pos.toImmutable(), state);
			}
			return this;
		}

		public Builder fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {
			int startX = Math.min(minX, maxX);
			int endX = Math.max(minX, maxX);
			int startY = Math.min(minY, maxY);
			int endY = Math.max(minY, maxY);
			int startZ = Math.min(minZ, maxZ);
			int endZ = Math.max(minZ, maxZ);

			for (int y = startY; y <= endY; y++) {
				for (int x = startX; x <= endX; x++) {
					for (int z = startZ; z <= endZ; z++) {
						addBlock(x, y, z, state);
					}
				}
			}
			return this;
		}

		public Builder fillRing(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {
			int startX = Math.min(minX, maxX);
			int endX = Math.max(minX, maxX);
			int startY = Math.min(minY, maxY);
			int endY = Math.max(minY, maxY);
			int startZ = Math.min(minZ, maxZ);
			int endZ = Math.max(minZ, maxZ);

			for (int y = startY; y <= endY; y++) {
				for (int x = startX; x <= endX; x++) {
					for (int z = startZ; z <= endZ; z++) {
						if (x == startX || x == endX || z == startZ || z == endZ) {
							addBlock(x, y, z, state);
						}
					}
				}
			}
			return this;
		}

		public StructureBlueprint build() {
			List<BlueprintBlock> sortedBlocks = new ArrayList<>();
			int minX = 0, minY = 0, minZ = 0;
			int maxX = 0, maxY = 0, maxZ = 0;
			boolean first = true;

			Map<Item, Integer> itemCounts = new LinkedHashMap<>();

			for (Map.Entry<BlockPos, BlockState> entry : this.blockMap.entrySet()) {
				BlockPos pos = entry.getKey();
				BlockState state = entry.getValue();

				sortedBlocks.add(new BlueprintBlock(pos, state));

				Item item = null;
				try {
					if (state != null && state.getBlock() != null) {
						item = state.getBlock().asItem();
					}
				} catch (Throwable ignored) {}
				if (item != null) {
					itemCounts.merge(item, 1, Integer::sum);
				}

				if (first) {
					minX = pos.getX();
					maxX = pos.getX();
					minY = pos.getY();
					maxY = pos.getY();
					minZ = pos.getZ();
					maxZ = pos.getZ();
					first = false;
				} else {
					minX = Math.min(minX, pos.getX());
					maxX = Math.max(maxX, pos.getX());
					minY = Math.min(minY, pos.getY());
					maxY = Math.max(maxY, pos.getY());
					minZ = Math.min(minZ, pos.getZ());
					maxZ = Math.max(maxZ, pos.getZ());
				}
			}

			// Sort blocks topographically: bottom-up vertical layers, then center-outward Manhattan distance
			Collections.sort(sortedBlocks);

			int sizeX = first ? 0 : (maxX - minX + 1);
			int sizeY = first ? 0 : (maxY - minY + 1);
			int sizeZ = first ? 0 : (maxZ - minZ + 1);

			BlockBox box = first ? new BlockBox(0, 0, 0, 0, 0, 0) : new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);

			return new StructureBlueprint(
				this.id,
				this.name,
				this.description,
				sizeX,
				sizeY,
				sizeZ,
				sortedBlocks,
				itemCounts,
				box
			);
		}
	}
}
