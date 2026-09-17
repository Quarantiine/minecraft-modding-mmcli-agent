package com.example.blueprint;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Classifies Minecraft biomes into environmental archetypes and provides
 * semantic block translations to achieve authentic vernacular architecture.
 */
public class BiomePalette {

	public enum EnvironmentType {
		PLAINS_FOREST,
		DESERT_BADLANDS,
		TAIGA_SNOWY,
		JUNGLE_SWAMP,
		SUBTERRANEAN_CAVE,
		NETHER,
		END
	}

	/**
	 * Detects the local environmental archetype based on world coordinate and biome.
	 */
	public static EnvironmentType getEnvironment(World world, BlockPos pos) {
		if (world == null || pos == null) {
			return EnvironmentType.PLAINS_FOREST;
		}

		// Cave & Subterranean check: Y <= 45 in the Overworld
		if (world.getRegistryKey() == World.OVERWORLD && pos.getY() <= 45) {
			return EnvironmentType.SUBTERRANEAN_CAVE;
		}

		if (world.getRegistryKey() == World.NETHER) {
			return EnvironmentType.NETHER;
		}

		if (world.getRegistryKey() == World.END) {
			return EnvironmentType.END;
		}

		RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
		String biomeKey = biomeEntry.getKey().map(k -> k.getValue().getPath().toLowerCase()).orElse("");

		if (biomeKey.contains("desert") || biomeKey.contains("badlands") || biomeKey.contains("mesa")) {
			return EnvironmentType.DESERT_BADLANDS;
		}
		if (biomeKey.contains("taiga") || biomeKey.contains("snow") || biomeKey.contains("ice") || biomeKey.contains("frozen") || biomeKey.contains("tundra")) {
			return EnvironmentType.TAIGA_SNOWY;
		}
		if (biomeKey.contains("jungle") || biomeKey.contains("swamp") || biomeKey.contains("mangrove")) {
			return EnvironmentType.JUNGLE_SWAMP;
		}
		if (biomeKey.contains("cave") || biomeKey.contains("dark") || biomeKey.contains("dripstone")) {
			return EnvironmentType.SUBTERRANEAN_CAVE;
		}

		return EnvironmentType.PLAINS_FOREST;
	}

	/**
	 * Resolves a base block state into the target environment/style block state.
	 * Preserves stairs, doors, slabs, and wall orientations.
	 */
	public static BlockState adaptBlock(BlockState original, EnvironmentType env, ArchitectureStyle style) {
		if (original == null) {
			return Blocks.AIR.getDefaultState();
		}

		EnvironmentType effectiveEnv = env;
		if (style == ArchitectureStyle.FORTRESS_STONE) {
			effectiveEnv = EnvironmentType.PLAINS_FOREST;
		} else if (style == ArchitectureStyle.FRONTIER_TIMBER) {
			effectiveEnv = EnvironmentType.TAIGA_SNOWY;
		} else if (style == ArchitectureStyle.ARCANE_NETHER) {
			effectiveEnv = EnvironmentType.NETHER;
		}

		Block block = original.getBlock();

		// 1. STAIRS
		if (block instanceof StairsBlock) {
			Block newStairs = resolveStairs(block, effectiveEnv);
			if (newStairs != block) {
				return copyStairProperties(original, newStairs.getDefaultState());
			}
			return original;
		}

		// 2. DOORS
		if (block instanceof DoorBlock) {
			Block newDoor = resolveDoor(effectiveEnv);
			if (newDoor != block) {
				return copyDoorProperties(original, newDoor.getDefaultState());
			}
			return original;
		}

		// 3. SLABS
		if (block instanceof SlabBlock) {
			Block newSlab = resolveSlab(block, effectiveEnv);
			if (newSlab != block) {
				return copySlabProperties(original, newSlab.getDefaultState());
			}
			return original;
		}

		// 4. WALLS
		if (block instanceof WallBlock) {
			Block newWall = resolveWall(effectiveEnv);
			if (newWall != block) {
				return newWall.getDefaultState();
			}
			return original;
		}

		// 5. SOLID WALLS & PLANKS
		return resolveSolidBlock(original, effectiveEnv);
	}

	private static Block resolveStairs(Block oldStairs, EnvironmentType env) {
		return switch (env) {
			case DESERT_BADLANDS -> Blocks.SANDSTONE_STAIRS;
			case TAIGA_SNOWY -> Blocks.SPRUCE_STAIRS;
			case JUNGLE_SWAMP -> Blocks.MUD_BRICK_STAIRS;
			case SUBTERRANEAN_CAVE -> Blocks.DEEPSLATE_BRICK_STAIRS;
			case NETHER -> Blocks.NETHER_BRICK_STAIRS;
			case END -> Blocks.PURPUR_STAIRS;
			default -> oldStairs;
		};
	}

	private static Block resolveDoor(EnvironmentType env) {
		return switch (env) {
			case DESERT_BADLANDS -> Blocks.ACACIA_DOOR;
			case TAIGA_SNOWY -> Blocks.SPRUCE_DOOR;
			case JUNGLE_SWAMP -> Blocks.MANGROVE_DOOR;
			case SUBTERRANEAN_CAVE -> Blocks.DARK_OAK_DOOR;
			case NETHER -> Blocks.WARPED_DOOR;
			case END -> Blocks.IRON_DOOR;
			default -> Blocks.OAK_DOOR;
		};
	}

	private static Block resolveSlab(Block oldSlab, EnvironmentType env) {
		return switch (env) {
			case DESERT_BADLANDS -> Blocks.CUT_SANDSTONE_SLAB;
			case TAIGA_SNOWY -> Blocks.SPRUCE_SLAB;
			case JUNGLE_SWAMP -> Blocks.MUD_BRICK_SLAB;
			case SUBTERRANEAN_CAVE -> Blocks.COBBLED_DEEPSLATE_SLAB;
			case NETHER -> Blocks.POLISHED_BLACKSTONE_SLAB;
			case END -> Blocks.PURPUR_SLAB;
			default -> oldSlab;
		};
	}

	private static Block resolveWall(EnvironmentType env) {
		return switch (env) {
			case DESERT_BADLANDS -> Blocks.SANDSTONE_WALL;
			case TAIGA_SNOWY -> Blocks.COBBLESTONE_WALL;
			case JUNGLE_SWAMP -> Blocks.MUD_BRICK_WALL;
			case SUBTERRANEAN_CAVE -> Blocks.DEEPSLATE_BRICK_WALL;
			case NETHER -> Blocks.POLISHED_BLACKSTONE_WALL;
			case END -> Blocks.END_STONE_BRICK_WALL;
			default -> Blocks.STONE_BRICK_WALL;
		};
	}

	private static BlockState resolveSolidBlock(BlockState original, EnvironmentType env) {
		Block b = original.getBlock();

		// Stone Bricks & Cobblestone
		if (b == Blocks.STONE_BRICKS || b == Blocks.COBBLESTONE) {
			return switch (env) {
				case DESERT_BADLANDS -> Blocks.SMOOTH_SANDSTONE.getDefaultState();
				case TAIGA_SNOWY -> (b == Blocks.STONE_BRICKS ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE).getDefaultState();
				case JUNGLE_SWAMP -> Blocks.MUD_BRICKS.getDefaultState();
				case SUBTERRANEAN_CAVE -> Blocks.DEEPSLATE_BRICKS.getDefaultState();
				case NETHER -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
				case END -> Blocks.END_STONE_BRICKS.getDefaultState();
				default -> original;
			};
		}

		// Wood Planks
		if (b == Blocks.OAK_PLANKS) {
			return switch (env) {
				case DESERT_BADLANDS -> Blocks.ACACIA_PLANKS.getDefaultState();
				case TAIGA_SNOWY -> Blocks.SPRUCE_PLANKS.getDefaultState();
				case JUNGLE_SWAMP -> Blocks.JUNGLE_PLANKS.getDefaultState();
				case SUBTERRANEAN_CAVE -> Blocks.DARK_OAK_PLANKS.getDefaultState();
				case NETHER -> Blocks.CRIMSON_PLANKS.getDefaultState();
				case END -> Blocks.PURPUR_BLOCK.getDefaultState();
				default -> original;
			};
		}

		// Wood Logs
		if (b == Blocks.OAK_LOG) {
			return switch (env) {
				case DESERT_BADLANDS -> Blocks.STRIPPED_ACACIA_LOG.getDefaultState();
				case TAIGA_SNOWY -> Blocks.SPRUCE_LOG.getDefaultState();
				case JUNGLE_SWAMP -> Blocks.MANGROVE_LOG.getDefaultState();
				case SUBTERRANEAN_CAVE -> Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
				case NETHER -> Blocks.WARPED_STEM.getDefaultState();
				case END -> Blocks.PURPUR_PILLAR.getDefaultState();
				default -> original;
			};
		}

		// Deepslate
		if (b == Blocks.DEEPSLATE_BRICKS || b == Blocks.POLISHED_DEEPSLATE) {
			return switch (env) {
				case DESERT_BADLANDS -> Blocks.CUT_SANDSTONE.getDefaultState();
				case NETHER -> Blocks.POLISHED_BLACKSTONE.getDefaultState();
				default -> original;
			};
		}

		return original;
	}

	private static BlockState copyStairProperties(BlockState src, BlockState dst) {
		if (src.contains(StairsBlock.FACING) && dst.contains(StairsBlock.FACING)) {
			dst = dst.with(StairsBlock.FACING, src.get(StairsBlock.FACING));
		}
		if (src.contains(StairsBlock.HALF) && dst.contains(StairsBlock.HALF)) {
			dst = dst.with(StairsBlock.HALF, src.get(StairsBlock.HALF));
		}
		if (src.contains(StairsBlock.SHAPE) && dst.contains(StairsBlock.SHAPE)) {
			dst = dst.with(StairsBlock.SHAPE, src.get(StairsBlock.SHAPE));
		}
		return dst;
	}

	private static BlockState copyDoorProperties(BlockState src, BlockState dst) {
		if (src.contains(DoorBlock.FACING) && dst.contains(DoorBlock.FACING)) {
			dst = dst.with(DoorBlock.FACING, src.get(DoorBlock.FACING));
		}
		if (src.contains(DoorBlock.HALF) && dst.contains(DoorBlock.HALF)) {
			dst = dst.with(DoorBlock.HALF, src.get(DoorBlock.HALF));
		}
		if (src.contains(DoorBlock.HINGE) && dst.contains(DoorBlock.HINGE)) {
			dst = dst.with(DoorBlock.HINGE, src.get(DoorBlock.HINGE));
		}
		if (src.contains(DoorBlock.OPEN) && dst.contains(DoorBlock.OPEN)) {
			dst = dst.with(DoorBlock.OPEN, src.get(DoorBlock.OPEN));
		}
		return dst;
	}

	private static BlockState copySlabProperties(BlockState src, BlockState dst) {
		if (src.contains(SlabBlock.TYPE) && dst.contains(SlabBlock.TYPE)) {
			dst = dst.with(SlabBlock.TYPE, src.get(SlabBlock.TYPE));
		}
		return dst;
	}
}
