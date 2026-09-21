package com.example.blueprint;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.Direction;

/**
 * Registry and catalog of curated architectural blueprints.
 * Provides lookup, ordered cycling, and pre-sorted multiblock blueprints
 * for minion construction directives.
 */
public class BlueprintRegistry {

	private static final Map<String, StructureBlueprint> REGISTRY = new LinkedHashMap<>();

	public static final String WATCHTOWER_ID = "watchtower";
	public static final String OBELISK_ID = "obelisk";
	public static final String BARRICADE_ID = "barricade";
	public static final String HOME_ID = "home";
	public static final String WORKSHOP_ID = "workshop";
	public static final String SUPPLY_DEPOT_ID = "supply_depot";

	public static final StructureBlueprint WATCHTOWER;
	public static final StructureBlueprint OBELISK;
	public static final StructureBlueprint BARRICADE;
	public static final StructureBlueprint HOME;
	public static final StructureBlueprint WORKSHOP;
	public static final StructureBlueprint SUPPLY_DEPOT;

	static {
		WATCHTOWER = register(createOverlordWatchtower());
		OBELISK = register(createArcaneObelisk());
		BARRICADE = register(createDefensiveBarricade());
		HOME = register(BuildingCategory.HOME.createBlueprint(BuildingCategory.SIZE_MEDIUM, 42L));
		WORKSHOP = register(BuildingCategory.WORKSHOP.createBlueprint(BuildingCategory.SIZE_MEDIUM, 42L));
		SUPPLY_DEPOT = register(BuildingCategory.SUPPLY_DEPOT.createBlueprint(BuildingCategory.SIZE_MEDIUM, 42L));

		// Register canonical category names so blueprints resolve directly
		REGISTRY.put(HOME_ID, HOME);
		REGISTRY.put(WORKSHOP_ID, WORKSHOP);
		REGISTRY.put(SUPPLY_DEPOT_ID, SUPPLY_DEPOT);

		// Register all size variants for every category so getOrDefault never falls back to Watchtower
		for (BuildingCategory cat : BuildingCategory.values()) {
			StructureBlueprint small = cat.createBlueprint(BuildingCategory.SIZE_SMALL, 42L);
			StructureBlueprint med = cat.createBlueprint(BuildingCategory.SIZE_MEDIUM, 42L);
			StructureBlueprint grand = cat.createBlueprint(BuildingCategory.SIZE_GRAND, 42L);
			register(small);
			register(med);
			register(grand);
		}
	}

	/**
	 * Registers a blueprint into the static registry.
	 *
	 * @param blueprint The structure blueprint to register.
	 * @return The registered blueprint.
	 */
	public static StructureBlueprint register(StructureBlueprint blueprint) {
		REGISTRY.put(blueprint.getId().toLowerCase(), blueprint);
		return blueprint;
	}

	/**
	 * Looks up a blueprint by case-insensitive identifier or category.
	 *
	 * @param id The blueprint identifier.
	 * @return Optional containing the blueprint if found.
	 */
	public static Optional<StructureBlueprint> get(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String cleanId = id.toLowerCase().trim();
		StructureBlueprint direct = REGISTRY.get(cleanId);
		if (direct != null) {
			return Optional.of(direct);
		}

		// 1. Direct Category match (e.g. "barricade", "home", "watchtower", "workshop", "supply_depot", "obelisk")
		for (BuildingCategory cat : BuildingCategory.values()) {
			if (cat.getId().equalsIgnoreCase(cleanId)) {
				return Optional.of(cat.createBlueprint(BuildingCategory.SIZE_MEDIUM, 42L));
			}
		}

		// 2. Category + Size prefix/suffix match: handles IDs like "barricade_small", "barricade_grand",
		// "home_small", "home_grand", "workshop_5", "workshop_7", "workshop_9", "depot_5", "depot_7", "depot_9", etc.
		for (BuildingCategory cat : BuildingCategory.values()) {
			String catId = cat.getId().toLowerCase();
			if (cleanId.startsWith(catId) || (cat == BuildingCategory.SUPPLY_DEPOT && cleanId.startsWith("depot"))) {
				int size = BuildingCategory.SIZE_MEDIUM;
				if (cleanId.contains("small") || cleanId.endsWith("_5") || cleanId.endsWith("_3")) {
					size = BuildingCategory.SIZE_SMALL;
				} else if (cleanId.contains("grand") || cleanId.endsWith("_9") || (cleanId.endsWith("_7") && cat == BuildingCategory.OBELISK)) {
					size = BuildingCategory.SIZE_GRAND;
				}
				return Optional.of(cat.createBlueprint(size, 42L));
			}
		}

		return Optional.empty();
	}

	/**
	 * Returns the blueprint with the given id, or the default watchtower if not found.
	 *
	 * @param id The blueprint identifier.
	 * @return The resolved StructureBlueprint.
	 */
	public static StructureBlueprint getOrDefault(String id) {
		return get(id).orElse(WATCHTOWER);
	}

	/**
	 * Resolves a blueprint dynamically based on category, size, and seed.
	 * If the identifier matches a registered category, generates a procedural blueprint for that size.
	 * Otherwise returns the standard registered blueprint.
	 *
	 * @param id   The blueprint identifier or category name.
	 * @param size The size preset (0 = Small, 1 = Medium, 2 = Grand, 3 = Random).
	 * @param seed The generation seed for procedural randomness.
	 * @return The resolved StructureBlueprint.
	 */
	public static StructureBlueprint resolveCategoryBlueprint(String id, int size, long seed) {
		if (id == null) {
			return WATCHTOWER;
		}
		String cleanId = id.toLowerCase().trim();
		for (BuildingCategory cat : BuildingCategory.values()) {
			if (cat.getId().equalsIgnoreCase(cleanId) || cleanId.startsWith(cat.getId().toLowerCase())
					|| (cat == BuildingCategory.SUPPLY_DEPOT && cleanId.startsWith("depot"))) {
				return cat.createBlueprint(size, seed);
			}
		}
		return getOrDefault(id);
	}

	/**
	 * Primary curated category blueprints in canonical progression.
	 */
	public static final List<StructureBlueprint> PRIMARY_CATALOG = List.of(
		WATCHTOWER, OBELISK, BARRICADE, HOME, WORKSHOP, SUPPLY_DEPOT
	);

	/**
	 * Cycles to the next blueprint in the registered catalog.
	 * Follows the sequence: WATCHTOWER -> OBELISK -> BARRICADE -> HOME -> WORKSHOP -> SUPPLY_DEPOT.
	 *
	 * @param currentId The current active blueprint identifier.
	 * @return The next StructureBlueprint in sequence.
	 */
	public static StructureBlueprint getNext(String currentId) {
		if (currentId == null) {
			return WATCHTOWER;
		}
		String cleanId = currentId.toLowerCase().trim();
		for (int i = 0; i < PRIMARY_CATALOG.size(); i++) {
			StructureBlueprint bp = PRIMARY_CATALOG.get(i);
			if (bp.getId().equalsIgnoreCase(cleanId) || cleanId.startsWith(bp.getId().toLowerCase())
					|| (bp == SUPPLY_DEPOT && cleanId.startsWith("depot"))) {
				return PRIMARY_CATALOG.get((i + 1) % PRIMARY_CATALOG.size());
			}
		}
		return WATCHTOWER;
	}

	/**
	 * Cycles to the previous blueprint in the registered catalog.
	 *
	 * @param currentId The current active blueprint identifier.
	 * @return The previous StructureBlueprint in sequence.
	 */
	public static StructureBlueprint getPrevious(String currentId) {
		StructureBlueprint[] array = REGISTRY.values().toArray(new StructureBlueprint[0]);
		if (array.length == 0) {
			return WATCHTOWER;
		}

		int currentIndex = 0;
		for (int i = 0; i < array.length; i++) {
			if (array[i].getId().equalsIgnoreCase(currentId)) {
				currentIndex = i;
				break;
			}
		}

		int prevIndex = (currentIndex - 1 + array.length) % array.length;
		return array[prevIndex];
	}

	/**
	 * Returns all registered blueprints.
	 *
	 * @return Unmodifiable collection of blueprints.
	 */
	public static Collection<StructureBlueprint> getAll() {
		return Collections.unmodifiableCollection(REGISTRY.values());
	}

	/**
	 * Returns the default blueprint (Overlord Watchtower).
	 *
	 * @return The default StructureBlueprint.
	 */
	public static StructureBlueprint getDefaultBlueprint() {
		return WATCHTOWER;
	}

	// -----------------------------------------------------------------------------------------
	// CURATED STRUCTURE BUILDERS
	// -----------------------------------------------------------------------------------------

	/**
	 * Constructs the Overlord Watchtower (7x7x9):
	 * Deepslate brick foundation, polished andesite pillars, inverted stone-brick stair arches,
	 * arrow slits, crenellated observation parapet, and soul lanterns.
	 */
	private static StructureBlueprint createOverlordWatchtower() {
		StructureBlueprint.Builder b = StructureBlueprint.builder(WATCHTOWER_ID, "Overlord Watchtower")
			.description("7x7x9 fortification with inverted stair arches, arrow slits, and crenellated parapet.");

		// --- Layer 0: Foundation (7x7) ---
		b.fill(-3, 0, -3, 3, 0, 3, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		// 4 solid foundation corners
		b.addBlock(-3, 0, -3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		b.addBlock(-3, 0, 3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		b.addBlock(3, 0, -3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		b.addBlock(3, 0, 3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		// Interior polished andesite floor
		b.fill(-2, 0, -2, 2, 0, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(0, 0, 0, Blocks.CHISELED_DEEPSLATE.getDefaultState());

		// --- Layer 1: Lower Tower Walls & Corner Pillars (5x5) ---
		// Corner pillars
		b.addBlock(-2, 1, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(-2, 1, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 1, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 1, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		// Walls
		b.fill(-1, 1, -2, 1, 1, -2, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(-1, 1, 2, 1, 1, 2, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(-2, 1, -1, -2, 1, 1, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(2, 1, -1, 2, 1, 1, Blocks.STONE_BRICKS.getDefaultState());
		// Entrance oak door lower half at south (0, 1, 2)
		b.addBlock(0, 1, 2, Blocks.OAK_DOOR.getDefaultState()
			.with(DoorBlock.FACING, Direction.SOUTH)
			.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		// Outer buttress stairs at Y=1
		b.addBlock(0, 1, -3, Blocks.DEEPSLATE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
		b.addBlock(-3, 1, 0, Blocks.DEEPSLATE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
		b.addBlock(3, 1, 0, Blocks.DEEPSLATE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));

		// --- Layer 2: Walls & Arrow Slits ---
		b.addBlock(-2, 2, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(-2, 2, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 2, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 2, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.fill(-1, 2, -2, 1, 2, -2, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(-1, 2, 2, 1, 2, 2, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(-2, 2, -1, -2, 2, 1, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(2, 2, -1, 2, 2, 1, Blocks.STONE_BRICKS.getDefaultState());
		// Arrow slits
		b.addBlock(0, 2, -2, Blocks.IRON_BARS.getDefaultState());
		b.addBlock(-2, 2, 0, Blocks.IRON_BARS.getDefaultState());
		b.addBlock(2, 2, 0, Blocks.IRON_BARS.getDefaultState());
		// Entrance oak door upper half at south (0, 2, 2)
		b.addBlock(0, 2, 2, Blocks.OAK_DOOR.getDefaultState()
			.with(DoorBlock.FACING, Direction.SOUTH)
			.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// --- Layer 3: Upper Walls & Arrow Slits ---
		b.addBlock(-2, 3, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(-2, 3, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 3, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 3, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.fill(-1, 3, -2, 1, 3, -2, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.fill(-1, 3, 2, 1, 3, 2, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.fill(-2, 3, -1, -2, 3, 1, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.fill(2, 3, -1, 2, 3, 1, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.addBlock(0, 3, -2, Blocks.IRON_BARS.getDefaultState());
		// Stone lintel above entrance door at (0, 3, 2)
		b.addBlock(0, 3, 2, Blocks.STONE_BRICKS.getDefaultState());
		b.addBlock(-2, 3, 0, Blocks.IRON_BARS.getDefaultState());
		b.addBlock(2, 3, 0, Blocks.IRON_BARS.getDefaultState());
		// Hanging soul lantern at entrance arch: hanging from Y=4 overhead stair
		b.addBlock(0, 3, 3, Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

		// --- Layer 4: Inverted Stair Arches (Overhang Support) ---
		b.addBlock(-2, 4, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(-2, 4, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 4, -2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.addBlock(2, 4, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
		b.fill(-1, 4, -2, 1, 4, -2, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(-1, 4, 2, 1, 4, 2, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(-2, 4, -1, -2, 4, 1, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(2, 4, -1, 2, 4, 1, Blocks.STONE_BRICKS.getDefaultState());
		// Inverted stone brick stairs facing outward along 7x7 outer rim to support observation deck
		for (int x = -2; x <= 2; x++) {
			b.addBlock(x, 4, -3, Blocks.STONE_BRICK_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.NORTH)
				.with(StairsBlock.HALF, BlockHalf.TOP));
			b.addBlock(x, 4, 3, Blocks.STONE_BRICK_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.SOUTH)
				.with(StairsBlock.HALF, BlockHalf.TOP));
		}
		for (int z = -2; z <= 2; z++) {
			b.addBlock(-3, 4, z, Blocks.STONE_BRICK_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.WEST)
				.with(StairsBlock.HALF, BlockHalf.TOP));
			b.addBlock(3, 4, z, Blocks.STONE_BRICK_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.EAST)
				.with(StairsBlock.HALF, BlockHalf.TOP));
		}

		// --- Layer 5: Observation Deck Platform (7x7) ---
		b.fill(-3, 5, -3, 3, 5, 3, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.fill(-1, 5, -1, 1, 5, 1, Blocks.POLISHED_ANDESITE.getDefaultState());

		// --- Layer 6: Parapet Base ---
		// 4 solid corner bastions
		b.addBlock(-3, 6, -3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		b.addBlock(-3, 6, 3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		b.addBlock(3, 6, -3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		b.addBlock(3, 6, 3, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		// Perimeter walls
		for (int x = -2; x <= 2; x++) {
			b.addBlock(x, 6, -3, Blocks.STONE_BRICK_WALL.getDefaultState());
			b.addBlock(x, 6, 3, Blocks.STONE_BRICK_WALL.getDefaultState());
		}
		for (int z = -2; z <= 2; z++) {
			b.addBlock(-3, 6, z, Blocks.STONE_BRICK_WALL.getDefaultState());
			b.addBlock(3, 6, z, Blocks.STONE_BRICK_WALL.getDefaultState());
		}

		// --- Layer 7: Crenellated Parapet Battlements ---
		// Crenellations (alternating merlons and embrasures)
		b.addBlock(-3, 7, -3, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.addBlock(-3, 7, 3, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.addBlock(3, 7, -3, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.addBlock(3, 7, 3, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.addBlock(0, 7, -3, Blocks.STONE_BRICKS.getDefaultState());
		b.addBlock(0, 7, 3, Blocks.STONE_BRICKS.getDefaultState());
		b.addBlock(-3, 7, 0, Blocks.STONE_BRICKS.getDefaultState());
		b.addBlock(3, 7, 0, Blocks.STONE_BRICKS.getDefaultState());

		// --- Layer 8: Soul Lanterns atop Corner Merlons ---
		b.addBlock(-3, 8, -3, Blocks.SOUL_LANTERN.getDefaultState());
		b.addBlock(-3, 8, 3, Blocks.SOUL_LANTERN.getDefaultState());
		b.addBlock(3, 8, -3, Blocks.SOUL_LANTERN.getDefaultState());
		b.addBlock(3, 8, 3, Blocks.SOUL_LANTERN.getDefaultState());

		return b.build();
	}

	/**
	 * Constructs the Arcane Obelisk (5x5x8):
	 * Stepped crying obsidian base, chiseled deepslate core, polished blackstone pillars,
	 * capital overhang, and pinnacle soul campfire brazier.
	 */
	private static StructureBlueprint createArcaneObelisk() {
		StructureBlueprint.Builder b = StructureBlueprint.builder(OBELISK_ID, "Arcane Obelisk")
			.description("5x5x8 arcane conduit with crying obsidian steps, chiseled deepslate core, and soul brazier.");

		// --- Layer 0: Foundation (5x5) ---
		b.fill(-2, 0, -2, 2, 0, 2, Blocks.CRYING_OBSIDIAN.getDefaultState());
		b.fill(-1, 0, -1, 1, 0, 1, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
		b.addBlock(0, 0, 0, Blocks.CHISELED_DEEPSLATE.getDefaultState());

		// --- Layer 1: Stepped Tier (3x3 with outer stairs) ---
		b.addBlock(0, 1, -1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
		b.addBlock(0, 1, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
		b.addBlock(-1, 1, 0, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
		b.addBlock(1, 1, 0, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
		b.addBlock(-1, 1, -1, Blocks.CRYING_OBSIDIAN.getDefaultState());
		b.addBlock(-1, 1, 1, Blocks.CRYING_OBSIDIAN.getDefaultState());
		b.addBlock(1, 1, -1, Blocks.CRYING_OBSIDIAN.getDefaultState());
		b.addBlock(1, 1, 1, Blocks.CRYING_OBSIDIAN.getDefaultState());
		b.addBlock(0, 1, 0, Blocks.CHISELED_DEEPSLATE.getDefaultState());

		// --- Layers 2-5: Arcane Monolith Shaft ---
		for (int y = 2; y <= 5; y++) {
			b.addBlock(0, y, 0, Blocks.CHISELED_DEEPSLATE.getDefaultState());

			// Crying obsidian energy nodes at Y=3 and Y=4, polished blackstone at Y=2 and Y=5
			if (y == 3 || y == 4) {
				b.addBlock(0, y, -1, Blocks.CRYING_OBSIDIAN.getDefaultState());
				b.addBlock(0, y, 1, Blocks.CRYING_OBSIDIAN.getDefaultState());
				b.addBlock(-1, y, 0, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
				b.addBlock(1, y, 0, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
			} else {
				b.addBlock(0, y, -1, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
				b.addBlock(0, y, 1, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
				b.addBlock(-1, y, 0, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
				b.addBlock(1, y, 0, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
			}
		}

		// --- Layer 6: Obelisk Capital (Inverted Stair Flaring) ---
		b.addBlock(0, 6, 0, Blocks.CHISELED_DEEPSLATE.getDefaultState());
		b.addBlock(0, 6, -1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState()
			.with(StairsBlock.FACING, Direction.NORTH)
			.with(StairsBlock.HALF, BlockHalf.TOP));
		b.addBlock(0, 6, 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState()
			.with(StairsBlock.FACING, Direction.SOUTH)
			.with(StairsBlock.HALF, BlockHalf.TOP));
		b.addBlock(-1, 6, 0, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState()
			.with(StairsBlock.FACING, Direction.WEST)
			.with(StairsBlock.HALF, BlockHalf.TOP));
		b.addBlock(1, 6, 0, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState()
			.with(StairsBlock.FACING, Direction.EAST)
			.with(StairsBlock.HALF, BlockHalf.TOP));

		// --- Layer 7: Pinnacle & Soul Brazier ---
		b.addBlock(0, 7, 0, Blocks.SOUL_CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
		b.addBlock(0, 7, -1, Blocks.SOUL_LANTERN.getDefaultState());
		b.addBlock(0, 7, 1, Blocks.SOUL_LANTERN.getDefaultState());
		b.addBlock(-1, 7, 0, Blocks.SOUL_LANTERN.getDefaultState());
		b.addBlock(1, 7, 0, Blocks.SOUL_LANTERN.getDefaultState());

		return b.build();
	}

	/**
	 * Constructs the Defensive Barricade (9x3x3):
	 * Dark oak palisades, stone brick arches, rear firing walkway, iron bar sightlines,
	 * and vigil lanterns.
	 */
	private static StructureBlueprint createDefensiveBarricade() {
		StructureBlueprint.Builder b = StructureBlueprint.builder(BARRICADE_ID, "Defensive Barricade")
			.description("9x3x3 reinforced palisade with dark oak posts, stone brick arches, and iron bar sightlines.");

		// --- Layer 0: Heavy Foundation (9x3) ---
		b.fill(-4, 0, -1, 4, 0, 1, Blocks.STONE_BRICKS.getDefaultState());
		b.fill(-3, 0, 0, 3, 0, 0, Blocks.COBBLESTONE.getDefaultState());
		b.addBlock(-4, 0, 0, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		b.addBlock(4, 0, 0, Blocks.POLISHED_DEEPSLATE.getDefaultState());

		// --- Layer 1: Palisade Posts, Sightlines & Rear Walkway ---
		// Front trenches/fences at Z = -1
		for (int x = -3; x <= 3; x++) {
			b.addBlock(x, 1, -1, Blocks.DARK_OAK_FENCE.getDefaultState());
		}
		// Core wall at Z = 0: dark oak posts and iron bar arrow slits
		for (int x = -4; x <= 4; x++) {
			if (x % 2 == 0) {
				b.addBlock(x, 1, 0, Blocks.DARK_OAK_LOG.getDefaultState());
			} else {
				b.addBlock(x, 1, 0, Blocks.IRON_BARS.getDefaultState());
			}
		}
		// Rear firing walkway steps at Z = 1
		for (int x = -4; x <= 4; x++) {
			b.addBlock(x, 1, 1, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
		}

		// --- Layer 2: Crenellated Arches & Vigil Lanterns ---
		// Corner terminal battlements
		b.addBlock(-4, 2, 0, Blocks.STONE_BRICKS.getDefaultState());
		b.addBlock(4, 2, 0, Blocks.STONE_BRICKS.getDefaultState());
		// Vigil lanterns atop the wing battlements
		b.addBlock(-3, 2, 0, Blocks.LANTERN.getDefaultState());
		b.addBlock(3, 2, 0, Blocks.LANTERN.getDefaultState());
		// Inverted stone brick arch battlements over the posts
		b.addBlock(-2, 2, 0, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.HALF, BlockHalf.TOP));
		b.addBlock(0, 2, 0, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.HALF, BlockHalf.TOP));
		b.addBlock(2, 2, 0, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.HALF, BlockHalf.TOP));
		// Crenellation sight openings
		b.addBlock(-1, 2, 0, Blocks.DARK_OAK_FENCE.getDefaultState());
		b.addBlock(1, 2, 0, Blocks.DARK_OAK_FENCE.getDefaultState());

		return b.build();
	}
}

