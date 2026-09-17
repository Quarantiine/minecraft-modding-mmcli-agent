package com.example.blueprint;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;

/**
 * Defines functional building classes with procedural size variations.
 * Supports villager-ready residences with beds and job-site workstations,
 * military fortifications, supply depots, workshops, and arcane monuments.
 */
public enum BuildingCategory implements StringIdentifiable {
	HOME("home", "🏡 Home", Formatting.GREEN, "Living quarters for villagers with beds, doors, and crafting workstations."),
	WATCHTOWER("watchtower", "🗼 Watchtower", Formatting.AQUA, "Elevated tactical observation post with arrow slits and parapets."),
	BARRICADE("barricade", "🛡 Barricade", Formatting.GOLD, "Frontline barrier with firing steps and defensive spikes."),
	WORKSHOP("workshop", "⚒ Workshop", Formatting.YELLOW, "Blacksmith forge with furnaces, anvils, and tool crafting stations."),
	SUPPLY_DEPOT("supply_depot", "📦 Supply Depot", Formatting.LIGHT_PURPLE, "Storage warehouse with double chests and logistics barrels."),
	OBELISK("obelisk", "🔮 Obelisk", Formatting.DARK_PURPLE, "Arcane monument focusing mystical energy and restorative aura.");

	public static final int SIZE_SMALL = 0;
	public static final int SIZE_MEDIUM = 1;
	public static final int SIZE_GRAND = 2;
	public static final int SIZE_RANDOM = 3;

	public static final Codec<BuildingCategory> CODEC = StringIdentifiable.createCodec(BuildingCategory::values);
	public static final PacketCodec<ByteBuf, BuildingCategory> PACKET_CODEC = PacketCodecs.indexed(
		i -> values()[Math.floorMod(i, values().length)],
		BuildingCategory::ordinal
	);

	private final String id;
	private final String displayName;
	private final Formatting color;
	private final String description;

	BuildingCategory(String id, String displayName, Formatting color, String description) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.description = description;
	}

	@Override
	public String asString() {
		return this.id;
	}

	public String getId() {
		return this.id;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Formatting getColor() {
		return this.color;
	}

	public String getDescription() {
		return this.description;
	}

	public Text getFormattedText() {
		return Text.literal(this.displayName).formatted(this.color);
	}

	public static BuildingCategory byId(String id) {
		if (id == null) {
			return HOME;
		}
		for (BuildingCategory cat : values()) {
			if (cat.id.equalsIgnoreCase(id)) {
				return cat;
			}
		}
		return HOME;
	}

	/**
	 * Generates a StructureBlueprint for this category based on size index.
	 *
	 * @param sizeIndex 0: Small, 1: Medium, 2: Grand, 3: Random
	 * @param seed      Deterministic instance seed.
	 * @return A topologically sorted StructureBlueprint.
	 */
	public StructureBlueprint createBlueprint(int sizeIndex, long seed) {
		int effectiveSize = sizeIndex;
		if (effectiveSize == SIZE_RANDOM) {
			effectiveSize = Math.floorMod((int) (seed ^ (seed >>> 16)), 3);
		} else {
			effectiveSize = Math.floorMod(effectiveSize, 3);
		}

		return switch (this) {
			case HOME -> createHome(effectiveSize, seed);
			case WATCHTOWER -> createWatchtower(effectiveSize, seed);
			case BARRICADE -> createBarricade(effectiveSize, seed);
			case WORKSHOP -> createWorkshop(effectiveSize, seed);
			case SUPPLY_DEPOT -> createSupplyDepot(effectiveSize, seed);
			case OBELISK -> createObelisk(effectiveSize, seed);
		};
	}

	// -----------------------------------------------------------------------------------------
	// PROCEDURAL BLUEPRINT GENERATORS
	// -----------------------------------------------------------------------------------------

	private static StructureBlueprint createHome(int size, long seed) {
		if (size == SIZE_SMALL) {
			// 5x5 Cozy Cottage with 1 bed, door, furnace, crafting table, lantern, pitched roof
			StructureBlueprint.Builder b = StructureBlueprint.builder("home_small", "Cozy Cottage (5x5)")
				.description("Cozy single-villager cottage with bed, furnace, and gabled roof.");

			// Foundation & Floor (5x5)
			b.fill(-2, 0, -2, 2, 0, 2, Blocks.COBBLESTONE.getDefaultState());
			b.fill(-1, 0, -1, 1, 0, 1, Blocks.OAK_PLANKS.getDefaultState());

			// Walls & Corners (Y=1 to Y=3)
			for (int y = 1; y <= 3; y++) {
				b.addBlock(-2, y, -2, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(-2, y, 2, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(2, y, -2, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(2, y, 2, Blocks.OAK_LOG.getDefaultState());

				b.fill(-1, y, -2, 1, y, -2, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(-1, y, 2, 1, y, 2, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(-2, y, -1, -2, y, 1, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(2, y, -1, 2, y, 1, Blocks.OAK_PLANKS.getDefaultState());
			}

			// Windows at Y=2
			b.addBlock(0, 2, -2, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(-2, 2, 0, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(2, 2, 0, Blocks.GLASS_PANE.getDefaultState());

			// Entrance Door at south (0, 1..2, 2)
			b.addBlock(0, 1, 2, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			b.addBlock(0, 2, 2, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

			// Interior furnishings: Bed (Northwest corner), Crafting Table, Furnace, Lantern
			b.addBlock(-1, 1, -1, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
			b.addBlock(-1, 1, 0, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
			b.addBlock(1, 1, -1, Blocks.CRAFTING_TABLE.getDefaultState());
			b.addBlock(1, 1, 0, Blocks.FURNACE.getDefaultState());
			b.addBlock(0, 3, 0, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

			// Roof (Y=4 pitched stairs East-West)
			for (int z = -2; z <= 2; z++) {
				b.addBlock(-2, 4, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
				b.addBlock(-1, 4, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(0, 4, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(1, 4, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(2, 4, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
				b.addBlock(0, 5, z, Blocks.OAK_SLAB.getDefaultState());
			}

			return b.build();
		} else if (size == SIZE_MEDIUM) {
			// 7x7 Family Homestead with 2 beds, Barrel workstation, Fletching Table, Double Chest, Chimney
			StructureBlueprint.Builder b = StructureBlueprint.builder("home_medium", "Family Homestead (7x7)")
				.description("Spacious 2-villager homestead with workstations, double chest, and chimney.");

			b.fill(-3, 0, -3, 3, 0, 3, Blocks.COBBLESTONE.getDefaultState());
			b.fill(-2, 0, -2, 2, 0, 2, Blocks.OAK_PLANKS.getDefaultState());

			for (int y = 1; y <= 3; y++) {
				b.addBlock(-3, y, -3, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(-3, y, 3, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(3, y, -3, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(3, y, 3, Blocks.OAK_LOG.getDefaultState());

				b.fill(-2, y, -3, 2, y, -3, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(-2, y, 3, 2, y, 3, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(-3, y, -2, -3, y, 2, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(3, y, -2, 3, y, 2, Blocks.OAK_PLANKS.getDefaultState());
			}

			// Windows
			b.addBlock(-1, 2, -3, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(1, 2, -3, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(-3, 2, 0, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(3, 2, 0, Blocks.GLASS_PANE.getDefaultState());

			// Door
			b.addBlock(0, 1, 3, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			b.addBlock(0, 2, 3, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

			// Beds & Workstations for 2 villagers
			b.addBlock(-2, 1, -2, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
			b.addBlock(-2, 1, -1, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));

			b.addBlock(-1, 1, -2, Blocks.YELLOW_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
			b.addBlock(-1, 1, -1, Blocks.YELLOW_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));

			b.addBlock(2, 1, -2, Blocks.BARREL.getDefaultState());
			b.addBlock(2, 1, -1, Blocks.FLETCHING_TABLE.getDefaultState());
			b.addBlock(2, 1, 0, Blocks.CHEST.getDefaultState());
			b.addBlock(2, 1, 1, Blocks.CHEST.getDefaultState());

			// Chimney with Campfire smoke
			b.fill(1, 1, -3, 1, 4, -3, Blocks.BRICKS.getDefaultState());
			b.addBlock(1, 1, -2, Blocks.BLAST_FURNACE.getDefaultState());
			b.addBlock(1, 5, -3, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));

			// Roof (Y=4 to 5)
			for (int z = -3; z <= 3; z++) {
				b.addBlock(-3, 4, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
				b.fill(-2, 4, z, 2, 4, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(3, 4, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
				b.addBlock(-2, 5, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
				b.fill(-1, 5, z, 1, 5, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(2, 5, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
				b.addBlock(0, 6, z, Blocks.OAK_SLAB.getDefaultState());
			}

			b.addBlock(0, 3, 0, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

			return b.build();
		} else {
			// 9x7 Grand Manor with 3 beds, Cartography Table, Grindstone, Loom, Porch
			StructureBlueprint.Builder b = StructureBlueprint.builder("home_grand", "Grand Manor (9x7)")
				.description("Grand multi-villager estate with 3 beds, diverse workstations, and high roof.");

			b.fill(-4, 0, -3, 4, 0, 3, Blocks.STONE_BRICKS.getDefaultState());
			b.fill(-3, 0, -2, 3, 0, 2, Blocks.OAK_PLANKS.getDefaultState());

			for (int y = 1; y <= 4; y++) {
				b.addBlock(-4, y, -3, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(-4, y, 3, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(4, y, -3, Blocks.OAK_LOG.getDefaultState());
				b.addBlock(4, y, 3, Blocks.OAK_LOG.getDefaultState());

				b.fill(-3, y, -3, 3, y, -3, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(-3, y, 3, 3, y, 3, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(-4, y, -2, -4, y, 2, Blocks.OAK_PLANKS.getDefaultState());
				b.fill(4, y, -2, 4, y, 2, Blocks.OAK_PLANKS.getDefaultState());
			}

			// Door & Porch
			b.addBlock(0, 1, 3, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			b.addBlock(0, 2, 3, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

			// 3 Beds
			b.addBlock(-3, 1, -2, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
			b.addBlock(-3, 1, -1, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));

			b.addBlock(-2, 1, -2, Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
			b.addBlock(-2, 1, -1, Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));

			b.addBlock(-1, 1, -2, Blocks.GREEN_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
			b.addBlock(-1, 1, -1, Blocks.GREEN_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));

			// Workstations: Cartography Table, Grindstone, Loom, Blast Furnace, Double Chest
			b.addBlock(3, 1, -2, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
			b.addBlock(3, 1, -1, Blocks.GRINDSTONE.getDefaultState());
			b.addBlock(3, 1, 0, Blocks.LOOM.getDefaultState());
			b.addBlock(3, 1, 1, Blocks.BLAST_FURNACE.getDefaultState());
			b.addBlock(2, 1, -2, Blocks.CHEST.getDefaultState());
			b.addBlock(2, 1, -1, Blocks.CHEST.getDefaultState());

			// Windows
			b.addBlock(-2, 2, 3, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(2, 2, 3, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(-4, 2, 0, Blocks.GLASS_PANE.getDefaultState());
			b.addBlock(4, 2, 0, Blocks.GLASS_PANE.getDefaultState());

			// Roof (Y=5 to 7)
			for (int z = -3; z <= 3; z++) {
				b.addBlock(-4, 5, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
				b.fill(-3, 5, z, 3, 5, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(4, 5, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));

				b.addBlock(-3, 6, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
				b.fill(-2, 6, z, 2, 6, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(3, 6, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));

				b.addBlock(-2, 7, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
				b.fill(-1, 7, z, 1, 7, z, Blocks.OAK_PLANKS.getDefaultState());
				b.addBlock(2, 7, z, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
				b.addBlock(0, 8, z, Blocks.OAK_SLAB.getDefaultState());
			}

			b.addBlock(0, 4, 0, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

			return b.build();
		}
	}

	private static StructureBlueprint createWatchtower(int size, long seed) {
		if (size == SIZE_SMALL) {
			// 5x5 Sentry Post
			StructureBlueprint.Builder b = StructureBlueprint.builder("watchtower_small", "Sentry Post (5x5)")
				.description("Compact 5x5 sentry post with arrow slits and elevated observation deck.");

			b.fill(-2, 0, -2, 2, 0, 2, Blocks.COBBLESTONE.getDefaultState());
			for (int y = 1; y <= 4; y++) {
				b.addBlock(-2, y, -2, Blocks.STONE_BRICKS.getDefaultState());
				b.addBlock(-2, y, 2, Blocks.STONE_BRICKS.getDefaultState());
				b.addBlock(2, y, -2, Blocks.STONE_BRICKS.getDefaultState());
				b.addBlock(2, y, 2, Blocks.STONE_BRICKS.getDefaultState());
				b.fill(-1, y, -2, 1, y, -2, Blocks.COBBLESTONE.getDefaultState());
				b.fill(-1, y, 2, 1, y, 2, Blocks.COBBLESTONE.getDefaultState());
				b.fill(-2, y, -1, -2, y, 1, Blocks.COBBLESTONE.getDefaultState());
				b.fill(2, y, -1, 2, y, 1, Blocks.COBBLESTONE.getDefaultState());
			}
			// Arrow slits
			b.addBlock(0, 2, -2, Blocks.IRON_BARS.getDefaultState());
			b.addBlock(-2, 2, 0, Blocks.IRON_BARS.getDefaultState());
			b.addBlock(2, 2, 0, Blocks.IRON_BARS.getDefaultState());
			// Door
			b.addBlock(0, 1, 2, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			b.addBlock(0, 2, 2, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			// Parapet
			b.fill(-2, 5, -2, 2, 5, 2, Blocks.STONE_BRICKS.getDefaultState());
			b.addBlock(-2, 6, -2, Blocks.STONE_BRICK_WALL.getDefaultState());
			b.addBlock(-2, 6, 2, Blocks.STONE_BRICK_WALL.getDefaultState());
			b.addBlock(2, 6, -2, Blocks.STONE_BRICK_WALL.getDefaultState());
			b.addBlock(2, 6, 2, Blocks.STONE_BRICK_WALL.getDefaultState());
			b.addBlock(0, 6, 0, Blocks.LANTERN.getDefaultState());

			return b.build();
		} else if (size == SIZE_MEDIUM) {
			// Classic Overlord Watchtower (7x7x9)
			return BlueprintRegistry.WATCHTOWER;
		} else {
			// 9x9 Grand Citadel Spire
			StructureBlueprint.Builder b = StructureBlueprint.builder("watchtower_grand", "Citadel Spire (9x9)")
				.description("Massive 9x9 fortified citadel with double parapets and arrow battlements.");
			b.fill(-4, 0, -4, 4, 0, 4, Blocks.DEEPSLATE_BRICKS.getDefaultState());
			for (int y = 1; y <= 7; y++) {
				b.fillRing(-3, y, -3, 3, y, 3, Blocks.STONE_BRICKS.getDefaultState());
				b.addBlock(-3, y, -3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
				b.addBlock(-3, y, 3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
				b.addBlock(3, y, -3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
				b.addBlock(3, y, 3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
			}
			// Door
			b.addBlock(0, 1, 3, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			b.addBlock(0, 2, 3, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			// Parapet at Y=8
			b.fill(-4, 8, -4, 4, 8, 4, Blocks.POLISHED_DEEPSLATE.getDefaultState());
			b.fillRing(-4, 9, -4, 4, 9, 4, Blocks.DEEPSLATE_BRICK_WALL.getDefaultState());
			b.addBlock(0, 10, 0, Blocks.SOUL_LANTERN.getDefaultState());

			return b.build();
		}
	}

	private static StructureBlueprint createBarricade(int size, long seed) {
		if (size == SIZE_SMALL) {
			// 5x3 Trench
			StructureBlueprint.Builder b = StructureBlueprint.builder("barricade_small", "Spiked Trench (5x3)")
				.description("Compact 5-wide spiked timber trench.");
			b.fill(-2, 0, 0, 2, 0, 0, Blocks.COBBLESTONE.getDefaultState());
			b.fill(-2, 1, 0, 2, 1, 0, Blocks.OAK_LOG.getDefaultState());
			b.fill(-2, 2, 0, 2, 2, 0, Blocks.POINTED_DRIPSTONE.getDefaultState());
			return b.build();
		} else if (size == SIZE_MEDIUM) {
			// Classic Defensive Barricade (7x3x3)
			return BlueprintRegistry.BARRICADE;
		} else {
			// 9x4 Fortified Gatehouse
			StructureBlueprint.Builder b = StructureBlueprint.builder("barricade_grand", "Fortified Gatehouse (9x4)")
				.description("Reinforced 9-wide gatehouse with double oak doors and stone ramparts.");
			b.fill(-4, 0, -1, 4, 0, 1, Blocks.STONE_BRICKS.getDefaultState());
			for (int y = 1; y <= 3; y++) {
				b.fill(-4, y, 0, -1, y, 0, Blocks.STONE_BRICKS.getDefaultState());
				b.fill(1, y, 0, 4, y, 0, Blocks.STONE_BRICKS.getDefaultState());
			}
			b.addBlock(0, 1, 0, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			b.addBlock(0, 2, 0, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			b.addBlock(0, 3, 0, Blocks.STONE_BRICKS.getDefaultState());
			b.fill(-4, 4, 0, 4, 4, 0, Blocks.STONE_BRICK_WALL.getDefaultState());
			return b.build();
		}
	}

	private static StructureBlueprint createWorkshop(int size, long seed) {
		// Workshop / Blacksmith Forge
		int width = size == SIZE_SMALL ? 5 : (size == SIZE_MEDIUM ? 7 : 9);
		int depth = size == SIZE_SMALL ? 4 : (size == SIZE_MEDIUM ? 6 : 7);
		int hw = width / 2;
		int hd = depth / 2;

		StructureBlueprint.Builder b = StructureBlueprint.builder("workshop_" + width, "Smithy & Workshop (" + width + "x" + depth + ")")
			.description("Equipped forge with blast furnaces, anvil, grindstone, and tool bench.");

		b.fill(-hw, 0, -hd, hw, 0, hd, Blocks.COBBLESTONE.getDefaultState());
		for (int y = 1; y <= 3; y++) {
			b.addBlock(-hw, y, -hd, Blocks.COBBLESTONE.getDefaultState());
			b.addBlock(-hw, y, hd, Blocks.COBBLESTONE.getDefaultState());
			b.addBlock(hw, y, -hd, Blocks.COBBLESTONE.getDefaultState());
			b.addBlock(hw, y, hd, Blocks.COBBLESTONE.getDefaultState());
			b.fill(-hw + 1, y, -hd, hw - 1, y, -hd, Blocks.OAK_PLANKS.getDefaultState());
			b.fill(-hw + 1, y, hd, hw - 1, y, hd, Blocks.OAK_PLANKS.getDefaultState());
		}

		// Workstations
		b.addBlock(-hw + 1, 1, -hd + 1, Blocks.ANVIL.getDefaultState());
		b.addBlock(-hw + 2, 1, -hd + 1, Blocks.BLAST_FURNACE.getDefaultState());
		b.addBlock(-hw + 3, 1, -hd + 1, Blocks.GRINDSTONE.getDefaultState());
		b.addBlock(hw - 1, 1, -hd + 1, Blocks.SMITHING_TABLE.getDefaultState());
		b.addBlock(hw - 1, 1, 0, Blocks.CHEST.getDefaultState());

		// Door
		b.addBlock(0, 1, hd, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		b.addBlock(0, 2, hd, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Roof
		b.fill(-hw, 4, -hd, hw, 4, hd, Blocks.COBBLESTONE_SLAB.getDefaultState());
		b.addBlock(0, 4, 0, Blocks.COBBLESTONE.getDefaultState());
		b.addBlock(0, 3, 0, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

		return b.build();
	}

	private static StructureBlueprint createSupplyDepot(int size, long seed) {
		int width = size == SIZE_SMALL ? 5 : (size == SIZE_MEDIUM ? 7 : 9);
		int depth = size == SIZE_SMALL ? 4 : (size == SIZE_MEDIUM ? 6 : 7);
		int hw = width / 2;
		int hd = depth / 2;

		StructureBlueprint.Builder b = StructureBlueprint.builder("depot_" + width, "Supply Depot (" + width + "x" + depth + ")")
			.description("Logistics storehouse lined with double chests and storage barrels.");

		b.fill(-hw, 0, -hd, hw, 0, hd, Blocks.SPRUCE_PLANKS.getDefaultState());
		for (int y = 1; y <= 3; y++) {
			b.fillRing(-hw, y, -hd, hw, y, hd, Blocks.SPRUCE_LOG.getDefaultState());
		}
		b.addBlock(0, 1, hd, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		b.addBlock(0, 2, hd, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Chests & Barrels lining walls
		for (int x = -hw + 1; x <= hw - 1; x++) {
			b.addBlock(x, 1, -hd + 1, Blocks.CHEST.getDefaultState());
			b.addBlock(x, 2, -hd + 1, Blocks.BARREL.getDefaultState());
		}

		b.fill(-hw, 4, -hd, hw, 4, hd, Blocks.SPRUCE_SLAB.getDefaultState());
		b.addBlock(0, 4, 0, Blocks.SPRUCE_LOG.getDefaultState());
		b.addBlock(0, 3, 0, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

		return b.build();
	}

	private static StructureBlueprint createObelisk(int size, long seed) {
		if (size == SIZE_SMALL) {
			StructureBlueprint.Builder b = StructureBlueprint.builder("obelisk_small", "Arcane Shrine (3x3)")
				.description("Compact 3x3 mystical shrine radiating restorative energy.");
			b.fill(-1, 0, -1, 1, 0, 1, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
			b.addBlock(0, 1, 0, Blocks.CRYING_OBSIDIAN.getDefaultState());
			b.addBlock(0, 2, 0, Blocks.AMETHYST_BLOCK.getDefaultState());
			b.addBlock(0, 3, 0, Blocks.SOUL_LANTERN.getDefaultState());
			return b.build();
		} else if (size == SIZE_MEDIUM) {
			return BlueprintRegistry.OBELISK;
		} else {
			StructureBlueprint.Builder b = StructureBlueprint.builder("obelisk_grand", "Monumental Spire (7x7)")
				.description("Towering 7x7 arcane obelisk crowned with floating monolith crystals.");
			b.fill(-3, 0, -3, 3, 0, 3, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
			for (int y = 1; y <= 6; y++) {
				b.fill(-1, y, -1, 1, y, 1, Blocks.CRYING_OBSIDIAN.getDefaultState());
			}
			b.fill(-2, 7, -2, 2, 7, 2, Blocks.AMETHYST_BLOCK.getDefaultState());
			b.addBlock(0, 8, 0, Blocks.RESPAWN_ANCHOR.getDefaultState());
			b.addBlock(0, 9, 0, Blocks.SOUL_LANTERN.getDefaultState());
			return b.build();
		}
	}
}
