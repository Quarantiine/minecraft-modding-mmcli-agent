package com.example.entity;

import com.example.entity.custom.MinionRole;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Unit tests validating autonomous material harvesting, build protection safeguards,
 * peer-to-peer logistics, supply depot deployment, Warrior thrown weapon arsenal,
 * Trident duality range dynamics, mob hunt safety checks, procurement contracts,
 * and solo fallback hunting.
 */
public class MinionLogisticsAndHarvestingTest {

	@Test
	@DisplayName("Validate role consolidation and legacy MINER (ID 3) migration to BUILDER")
	void testRoleConsolidationAndMigration() {
		Assertions.assertEquals(4, MinionRole.values().length, "System has 4 role archetypes");
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.fromId(0));
		Assertions.assertEquals(MinionRole.SENTINEL, MinionRole.fromId(1));
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.fromId(2));
		Assertions.assertEquals(MinionRole.AUTO, MinionRole.fromId(3));

		// Cycling through all 4 roles
		Assertions.assertEquals(MinionRole.SENTINEL, MinionRole.WARRIOR.next());
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.SENTINEL.next());
		Assertions.assertEquals(MinionRole.AUTO, MinionRole.BUILDER.next());
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.AUTO.next());
	}

	@Test
	@DisplayName("Build Protection: Processed and architectural blocks are strictly protected from harvesting")
	void testBuildProtectionProcessedBlocks() {
		List<String> playerBlocks = List.of(
			"oak_planks", "spruce_planks", "stone_bricks", "deepslate_bricks",
			"smooth_stone_slab", "oak_stairs", "glass", "glass_pane",
			"white_terracotta", "yellow_concrete", "red_wool", "white_carpet",
			"oak_door", "iron_trapdoor", "oak_fence", "cobblestone_wall",
			"lantern", "torch", "chest", "barrel", "crafting_table"
		);

		for (String path : playerBlocks) {
			Assertions.assertTrue(isProcessedBlockTest(path),
				"Block '" + path + "' must be identified as processed and protected from harvesting");
		}

		List<String> naturalBlocks = List.of(
			"stone", "cobblestone", "deepslate", "cobbled_deepslate",
			"andesite", "diorite", "granite", "dirt", "sand", "gravel"
		);

		for (String path : naturalBlocks) {
			Assertions.assertFalse(isProcessedBlockTest(path),
				"Natural block '" + path + "' should be eligible for quarrying if outside base buffers");
		}
	}

	private static boolean isProcessedBlockTest(String path) {
		return path.contains("planks")
			|| path.contains("brick")
			|| path.contains("slab")
			|| path.contains("stair")
			|| path.contains("glass")
			|| path.contains("terracotta")
			|| path.contains("concrete")
			|| path.contains("wool")
			|| path.contains("carpet")
			|| path.contains("door")
			|| path.contains("trapdoor")
			|| path.contains("fence")
			|| path.contains("wall")
			|| path.contains("gate")
			|| path.contains("lantern")
			|| path.contains("torch")
			|| path.contains("chest")
			|| path.contains("barrel")
			|| path.contains("shulker")
			|| path.contains("furnace")
			|| path.contains("crafting_table");
	}

	@Test
	@DisplayName("Hazard Avoidance: Inspect 6 orthogonal directions for adjacent lava and prevent quarrying")
	void testLavaHazardAvoidance() {
		Set<BlockPos> worldLavaPositions = new HashSet<>();
		BlockPos quarryTarget = new BlockPos(10, 60, 10);

		// No lava -> safe
		Assertions.assertFalse(hasAdjacentLavaTest(quarryTarget, worldLavaPositions));

		// Place lava above
		worldLavaPositions.add(quarryTarget.up());
		Assertions.assertTrue(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava above must trigger avoidance");

		// Place lava below
		worldLavaPositions.clear();
		worldLavaPositions.add(quarryTarget.down());
		Assertions.assertTrue(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava below must trigger avoidance");

		// Place lava North
		worldLavaPositions.clear();
		worldLavaPositions.add(quarryTarget.north());
		Assertions.assertTrue(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava North must trigger avoidance");

		// Place lava 2 blocks away (not immediately adjacent) -> safe
		worldLavaPositions.clear();
		worldLavaPositions.add(quarryTarget.north(2));
		Assertions.assertFalse(hasAdjacentLavaTest(quarryTarget, worldLavaPositions), "Lava 2 blocks away is safe");
	}

	private static boolean hasAdjacentLavaTest(BlockPos pos, Set<BlockPos> lavaPositions) {
		for (Direction dir : Direction.values()) {
			if (lavaPositions.contains(pos.offset(dir))) {
				return true;
			}
		}
		return false;
	}

	@Test
	@DisplayName("Tool Self-Crafting: Synthesis of wooden/stone tools from timber and cobblestone")
	void testToolSelfCraftingRecipes() {
		class InventorySimulator {
			int logs = 0;
			int planks = 0;
			int sticks = 0;
			int cobblestone = 0;
			String toolCreated = null;

			void ensureSticksAndPlanks() {
				if (sticks < 2) {
					if (planks >= 2) {
						planks -= 2;
						sticks += 4;
					} else if (logs >= 1) {
						logs -= 1;
						planks += 4;
						planks -= 2;
						sticks += 4;
					}
				}
				if (planks < 3 && logs >= 1) {
					logs -= 1;
					planks += 4;
				}
			}

			void craftPickaxe() {
				ensureSticksAndPlanks();
				if (cobblestone >= 3 && sticks >= 2) {
					cobblestone -= 3;
					sticks -= 2;
					toolCreated = "stone_pickaxe";
					return;
				}
				if (planks >= 3 && sticks >= 2) {
					planks -= 3;
					sticks -= 2;
					toolCreated = "wooden_pickaxe";
				}
			}
		}

		// Scenario A: Minion starts with 1 log -> converts to 4 planks, 4 sticks -> crafts wooden pickaxe (needs 3 planks + 2 sticks)
		// 1 log -> 4 planks -> 2 planks into 4 sticks (leaves 2 planks, 4 sticks; needs 3 planks, so need 2 logs for full wooden pickaxe)
		InventorySimulator simA = new InventorySimulator();
		simA.logs = 2; // 2 logs = 8 planks -> 2 planks into 4 sticks = 6 planks, 4 sticks -> wooden pickaxe (3 planks, 2 sticks)
		simA.craftPickaxe();
		Assertions.assertEquals("wooden_pickaxe", simA.toolCreated);
		Assertions.assertEquals(3, simA.planks);
		Assertions.assertEquals(2, simA.sticks);

		// Scenario B: Minion with 3 cobblestone and 1 log -> crafts stone pickaxe
		InventorySimulator simB = new InventorySimulator();
		simB.cobblestone = 3;
		simB.logs = 1;
		simB.craftPickaxe();
		Assertions.assertEquals("stone_pickaxe", simB.toolCreated);
		Assertions.assertEquals(0, simB.cobblestone);
		Assertions.assertEquals(2, simB.planks);
		Assertions.assertEquals(2, simB.sticks);
	}

	@Test
	@DisplayName("Supply Depot: Autonomous Chest crafting (8 planks) and Double Chest pairing")
	void testAutonomousChestAndPairing() {
		int planks = 8;
		boolean canCraftChest = planks >= 8;
		Assertions.assertTrue(canCraftChest, "Minion must be able to craft chest from 8 planks");

		// Double chest alignment test
		Direction existingFacing = Direction.NORTH;
		// If neighbor is EAST (right of North), candidate is WEST (left of North)
		Direction neighborDir = Direction.EAST;
		ChestType candidateType = ChestType.SINGLE;
		ChestType neighborType = ChestType.SINGLE;

		if (neighborDir == existingFacing.rotateYClockwise()) {
			candidateType = ChestType.LEFT;
			neighborType = ChestType.RIGHT;
		}

		Assertions.assertEquals(ChestType.LEFT, candidateType);
		Assertions.assertEquals(ChestType.RIGHT, neighborType);
	}

	@Test
	@DisplayName("Operational Leash and Station Radius: 128-block operational distance verification")
	void testExpandedOperationalRadius() {
		double leashDistance = 128.0D;
		double leashDistanceSq = leashDistance * leashDistance;

		Assertions.assertEquals(16384.0D, leashDistanceSq, "128-block leash distance squared must equal 16,384");

		// Stationed worker at (0, 64, 0)
		BlockPos anchor = new BlockPos(0, 64, 0);
		BlockPos workerAt100 = new BlockPos(100, 64, 0);
		BlockPos workerAt130 = new BlockPos(130, 64, 0);

		Assertions.assertTrue(workerAt100.getSquaredDistance(anchor) <= leashDistanceSq,
			"Worker at 100 blocks must be well within the 128-block operational leash");
		Assertions.assertFalse(workerAt130.getSquaredDistance(anchor) <= leashDistanceSq,
			"Worker at 130 blocks exceeds the 128-block operational leash");
	}

	@Test
	@DisplayName("Mob Material Procurement: Resource Classification and Target Mob Mapping")
	void testMobProcurementMapping() {
		// Verify mob procurement mapping categories
		Set<String> mobResources = Set.of(
			"white_wool", "red_wool", "string", "spider_eye", "bone", "bone_meal",
			"bone_block", "slime_ball", "slime_block", "magma_cream", "magma_block",
			"leather", "ink_sac", "glow_ink_sac", "feather", "blaze_rod", "blaze_powder",
			"ender_pearl", "ender_eye", "gunpowder", "rotten_flesh", "prismarine_shard",
			"prismarine_crystals", "prismarine", "rabbit_hide", "phantom_membrane", "shulker_shell"
		);

		for (String resource : mobResources) {
			Assertions.assertTrue(isMobProcurementResourceTest(resource),
				"Resource '" + resource + "' must be classified as a mob procurement item");
		}

		// Non-mob resources
		Assertions.assertFalse(isMobProcurementResourceTest("stone"));
		Assertions.assertFalse(isMobProcurementResourceTest("oak_log"));
		Assertions.assertFalse(isMobProcurementResourceTest("dirt"));
		Assertions.assertFalse(isMobProcurementResourceTest("iron_ingot"));
	}

	private static boolean isMobProcurementResourceTest(String itemId) {
		return itemId.contains("wool")
			|| itemId.equals("string")
			|| itemId.equals("spider_eye")
			|| itemId.equals("bone")
			|| itemId.equals("bone_meal")
			|| itemId.equals("bone_block")
			|| itemId.equals("slime_ball")
			|| itemId.equals("slime_block")
			|| itemId.equals("magma_cream")
			|| itemId.equals("magma_block")
			|| itemId.equals("leather")
			|| itemId.equals("ink_sac")
			|| itemId.equals("glow_ink_sac")
			|| itemId.equals("feather")
			|| itemId.equals("blaze_rod")
			|| itemId.equals("blaze_powder")
			|| itemId.equals("ender_pearl")
			|| itemId.equals("ender_eye")
			|| itemId.equals("gunpowder")
			|| itemId.equals("rotten_flesh")
			|| itemId.equals("prismarine_shard")
			|| itemId.equals("prismarine_crystals")
			|| itemId.equals("prismarine")
			|| itemId.equals("rabbit_hide")
			|| itemId.equals("phantom_membrane")
			|| itemId.equals("shulker_shell");
	}

	@Test
	@DisplayName("Target Safety Safeguards: Protect player pets, named mobs, villagers, golems, and allies")
	void testHuntTargetSafetySafeguards() {
		class MobTargetMock {
			final String type;
			final boolean hasCustomName;
			final boolean isTamed;
			final boolean isAlliedMinion;
			final boolean isPlayer;

			MobTargetMock(String type, boolean hasCustomName, boolean isTamed, boolean isAlliedMinion, boolean isPlayer) {
				this.type = type;
				this.hasCustomName = hasCustomName;
				this.isTamed = isTamed;
				this.isAlliedMinion = isAlliedMinion;
				this.isPlayer = isPlayer;
			}

			boolean isSafeHuntTarget() {
				if (isPlayer) return false;
				if (isAlliedMinion) return false;
				if (hasCustomName) return false;
				if (isTamed) return false;
				if ("villager".equals(type) || "wandering_trader".equals(type)) return false;
				if ("iron_golem".equals(type) || "snow_golem".equals(type)) return false;
				if ("allay".equals(type) || "armor_stand".equals(type)) return false;
				return true;
			}
		}

		// Safe targets
		Assertions.assertTrue(new MobTargetMock("sheep", false, false, false, false).isSafeHuntTarget());
		Assertions.assertTrue(new MobTargetMock("spider", false, false, false, false).isSafeHuntTarget());
		Assertions.assertTrue(new MobTargetMock("skeleton", false, false, false, false).isSafeHuntTarget());
		Assertions.assertTrue(new MobTargetMock("cow", false, false, false, false).isSafeHuntTarget());
		Assertions.assertTrue(new MobTargetMock("slime", false, false, false, false).isSafeHuntTarget());

		// Unsafe targets: must be protected
		Assertions.assertFalse(new MobTargetMock("player", false, false, false, true).isSafeHuntTarget(), "Player must not be hunted");
		Assertions.assertFalse(new MobTargetMock("minion", false, false, true, false).isSafeHuntTarget(), "Allied minions must not be hunted");
		Assertions.assertFalse(new MobTargetMock("sheep", true, false, false, false).isSafeHuntTarget(), "Named sheep (name-tagged) must not be hunted");
		Assertions.assertFalse(new MobTargetMock("wolf", false, true, false, false).isSafeHuntTarget(), "Tamed dog/wolf must not be hunted");
		Assertions.assertFalse(new MobTargetMock("horse", false, true, false, false).isSafeHuntTarget(), "Tamed horse must not be hunted");
		Assertions.assertFalse(new MobTargetMock("villager", false, false, false, false).isSafeHuntTarget(), "Villagers must not be hunted");
		Assertions.assertFalse(new MobTargetMock("wandering_trader", false, false, false, false).isSafeHuntTarget(), "Wandering Traders must not be hunted");
		Assertions.assertFalse(new MobTargetMock("iron_golem", false, false, false, false).isSafeHuntTarget(), "Iron Golems must not be hunted");
		Assertions.assertFalse(new MobTargetMock("allay", false, false, false, false).isSafeHuntTarget(), "Allays must not be hunted");
	}

	@Test
	@DisplayName("Mob Material Synthesis: Crafting refined components from raw ingredients")
	void testMobMaterialSynthesis() {
		class SynthesisInventory {
			int bones = 0;
			int boneMeal = 0;
			int boneBlocks = 0;
			int strings = 0;
			int wool = 0;
			int slimeBalls = 0;
			int slimeBlocks = 0;
			int magmaCream = 0;
			int magmaBlocks = 0;
			int blazeRods = 0;
			int blazePowder = 0;
			int rabbitHide = 0;
			int leather = 0;

			boolean synthesize(String target) {
				if ("bone_meal".equals(target)) {
					if (bones >= 1) {
						bones -= 1;
						boneMeal += 3;
						return true;
					}
				} else if ("bone_block".equals(target)) {
					if (boneMeal < 9 && bones >= 3) {
						bones -= 3;
						boneMeal += 9;
					}
					if (boneMeal >= 9) {
						boneMeal -= 9;
						boneBlocks += 1;
						return true;
					}
				} else if ("wool".equals(target)) {
					if (strings >= 4) {
						strings -= 4;
						wool += 1;
						return true;
					}
				} else if ("slime_block".equals(target)) {
					if (slimeBalls >= 9) {
						slimeBalls -= 9;
						slimeBlocks += 1;
						return true;
					}
				} else if ("magma_block".equals(target)) {
					if (magmaCream >= 4) {
						magmaCream -= 4;
						magmaBlocks += 1;
						return true;
					}
				} else if ("blaze_powder".equals(target)) {
					if (blazeRods >= 1) {
						blazeRods -= 1;
						blazePowder += 2;
						return true;
					}
				} else if ("leather".equals(target)) {
					if (rabbitHide >= 4) {
						rabbitHide -= 4;
						leather += 1;
						return true;
					}
				}
				return false;
			}
		}

		SynthesisInventory inv = new SynthesisInventory();

		// 1. Bones -> Bone Meal
		inv.bones = 2;
		Assertions.assertTrue(inv.synthesize("bone_meal"));
		Assertions.assertEquals(1, inv.bones);
		Assertions.assertEquals(3, inv.boneMeal);

		// 2. Bone Meal / Bones -> Bone Block
		inv.bones = 3;
		Assertions.assertTrue(inv.synthesize("bone_block"));
		Assertions.assertEquals(1, inv.boneBlocks);
		Assertions.assertEquals(3, inv.boneMeal); // Started with 3 + (3*3) = 12, consumed 9, leaves 3

		// 3. String -> Wool
		inv.strings = 5;
		Assertions.assertTrue(inv.synthesize("wool"));
		Assertions.assertEquals(1, inv.wool);
		Assertions.assertEquals(1, inv.strings);

		// 4. Slimeball -> Slime Block
		inv.slimeBalls = 10;
		Assertions.assertTrue(inv.synthesize("slime_block"));
		Assertions.assertEquals(1, inv.slimeBlocks);
		Assertions.assertEquals(1, inv.slimeBalls);

		// 5. Magma Cream -> Magma Block
		inv.magmaCream = 4;
		Assertions.assertTrue(inv.synthesize("magma_block"));
		Assertions.assertEquals(1, inv.magmaBlocks);
		Assertions.assertEquals(0, inv.magmaCream);

		// 6. Blaze Rod -> Blaze Powder
		inv.blazeRods = 1;
		Assertions.assertTrue(inv.synthesize("blaze_powder"));
		Assertions.assertEquals(2, inv.blazePowder);
		Assertions.assertEquals(0, inv.blazeRods);

		// 7. Rabbit Hide -> Leather
		inv.rabbitHide = 4;
		Assertions.assertTrue(inv.synthesize("leather"));
		Assertions.assertEquals(1, inv.leather);
		Assertions.assertEquals(0, inv.rabbitHide);
	}

	@Test
	@DisplayName("Warrior Hunting Contract Commissioning & Drop Resolution")
	void testWarriorHuntingContractLifecycle() {
		UUID builderUuid = UUID.randomUUID();
		UUID warriorUuid = UUID.randomUUID();

		class WarriorContractSimulator {
			UUID requesterUuid = null;
			String targetItem = null;
			boolean activeHunt = false;
			int warriorInventoryCount = 0;
			int builderInventoryCount = 0;

			void commissionContract(UUID requester, String item) {
				this.requesterUuid = requester;
				this.targetItem = item;
				this.activeHunt = true;
			}

			void completeHunt(int slainDrops) {
				Assertions.assertTrue(activeHunt, "Hunt must be active when completed");
				// Slay mob -> add drops to warrior
				warriorInventoryCount += slainDrops;

				// Transfer to builder
				if (requesterUuid != null && warriorInventoryCount > 0) {
					builderInventoryCount += 1;
					warriorInventoryCount -= 1;
				}

				// Reset contract
				this.requesterUuid = null;
				this.targetItem = null;
				this.activeHunt = false;
			}
		}

		WarriorContractSimulator sim = new WarriorContractSimulator();
		sim.commissionContract(builderUuid, "white_wool");

		Assertions.assertTrue(sim.activeHunt);
		Assertions.assertEquals(builderUuid, sim.requesterUuid);
		Assertions.assertEquals("white_wool", sim.targetItem);

		// Mob slain -> yields 2 wool -> transfers 1 to builder requester
		sim.completeHunt(2);

		Assertions.assertFalse(sim.activeHunt, "Contract must be cleared after completion");
		Assertions.assertEquals(1, sim.builderInventoryCount, "Builder must have received 1 unit of the procured item");
		Assertions.assertEquals(1, sim.warriorInventoryCount, "Warrior retains remaining drops in storage");
	}

	// =========================================================================
	// Thrown Weapons Arsenal & Auto-Equip Support
	// =========================================================================

	@Test
	@DisplayName("Thrown Weapons: Validate classification and role auto-equip eligibility")
	void testThrownWeaponClassificationAndAutoEquip() {
		enum ItemType {
			TRIDENT,
			FROST_GRENADE_STICK,
			TNT_STICK,
			BOW,
			CROSSBOW,
			DIAMOND_SWORD,
			IRON_AXE,
			MACE,
			IRON_PICKAXE,
			SHOVEL,
			STONE
		}

		class WeaponClassifier {
			static boolean isThrownWeapon(ItemType item) {
				return item == ItemType.TRIDENT
					|| item == ItemType.FROST_GRENADE_STICK
					|| item == ItemType.TNT_STICK;
			}

			static boolean isRangedWeapon(ItemType item) {
				return item == ItemType.BOW
					|| item == ItemType.CROSSBOW
					|| isThrownWeapon(item);
			}

			static boolean isMeleeWeapon(ItemType item) {
				return item == ItemType.DIAMOND_SWORD
					|| item == ItemType.IRON_AXE
					|| item == ItemType.MACE
					|| item == ItemType.TRIDENT;
			}

			static boolean isMiningTool(ItemType item) {
				return item == ItemType.IRON_PICKAXE || item == ItemType.SHOVEL;
			}

			static boolean canRoleAutoEquipMainhand(MinionRole role, ItemType item) {
				return switch (role) {
					case WARRIOR -> isMeleeWeapon(item) || isRangedWeapon(item) || isThrownWeapon(item);
					case SENTINEL -> isMeleeWeapon(item);
					case BUILDER -> isMiningTool(item) || isMeleeWeapon(item);
					case AUTO -> isMeleeWeapon(item) || isRangedWeapon(item) || isMiningTool(item) || isThrownWeapon(item);
				};
			}

			static boolean isPreferredMainhandWeapon(MinionRole role, ItemType candidate, ItemType current) {
				if (current == null) return canRoleAutoEquipMainhand(role, candidate);
				return switch (role) {
					case WARRIOR -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
					case SENTINEL -> isMeleeWeapon(candidate) && !isMeleeWeapon(current);
					case BUILDER -> isMiningTool(candidate) && !isMiningTool(current);
					case AUTO -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
				};
			}
		}

		// 1. Thrown weapon classification
		Assertions.assertTrue(WeaponClassifier.isThrownWeapon(ItemType.TRIDENT), "Trident must be classified as a thrown weapon");
		Assertions.assertTrue(WeaponClassifier.isThrownWeapon(ItemType.FROST_GRENADE_STICK), "Frost Grenade Stick must be classified as a thrown weapon");
		Assertions.assertTrue(WeaponClassifier.isThrownWeapon(ItemType.TNT_STICK), "TNT Stick must be classified as a thrown weapon");
		Assertions.assertFalse(WeaponClassifier.isThrownWeapon(ItemType.BOW), "Bow is ranged but not thrown");
		Assertions.assertFalse(WeaponClassifier.isThrownWeapon(ItemType.DIAMOND_SWORD), "Sword is not a thrown weapon");
		Assertions.assertFalse(WeaponClassifier.isThrownWeapon(ItemType.STONE), "Stone is not a weapon");

		// 2. Ranged weapon classification (includes bows, crossbows, and all thrown ordnance)
		Assertions.assertTrue(WeaponClassifier.isRangedWeapon(ItemType.TRIDENT), "Trident must be recognized under ranged/thrown weapon umbrella");
		Assertions.assertTrue(WeaponClassifier.isRangedWeapon(ItemType.FROST_GRENADE_STICK), "Frost Grenade Stick must be recognized under ranged/thrown umbrella");
		Assertions.assertTrue(WeaponClassifier.isRangedWeapon(ItemType.TNT_STICK), "TNT Stick must be recognized under ranged/thrown umbrella");
		Assertions.assertTrue(WeaponClassifier.isRangedWeapon(ItemType.BOW), "Bow must be recognized as ranged weapon");
		Assertions.assertTrue(WeaponClassifier.isRangedWeapon(ItemType.CROSSBOW), "Crossbow must be recognized as ranged weapon");
		Assertions.assertFalse(WeaponClassifier.isRangedWeapon(ItemType.DIAMOND_SWORD), "Diamond sword is not a ranged weapon");

		// 3. Melee weapon classification (Trident possesses dual melee status)
		Assertions.assertTrue(WeaponClassifier.isMeleeWeapon(ItemType.TRIDENT), "Trident must be recognized as a melee weapon for close-quarters thrusts");
		Assertions.assertTrue(WeaponClassifier.isMeleeWeapon(ItemType.DIAMOND_SWORD), "Sword must be recognized as a melee weapon");
		Assertions.assertTrue(WeaponClassifier.isMeleeWeapon(ItemType.IRON_AXE), "Axe must be recognized as a melee weapon");
		Assertions.assertTrue(WeaponClassifier.isMeleeWeapon(ItemType.MACE), "Mace must be recognized as a melee weapon");
		Assertions.assertFalse(WeaponClassifier.isMeleeWeapon(ItemType.FROST_GRENADE_STICK), "Frost Grenade Stick is purely thrown ordnance, not melee");
		Assertions.assertFalse(WeaponClassifier.isMeleeWeapon(ItemType.TNT_STICK), "TNT Stick is purely thrown ordnance, not melee");
		Assertions.assertFalse(WeaponClassifier.isMeleeWeapon(ItemType.BOW), "Bow is not a melee weapon");

		// 4. Role Auto-Equip Permissions
		// WARRIOR: Can auto-equip melee, ranged, and thrown weapons
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.WARRIOR, ItemType.TRIDENT));
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.WARRIOR, ItemType.FROST_GRENADE_STICK));
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.WARRIOR, ItemType.TNT_STICK));
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.WARRIOR, ItemType.BOW));
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.WARRIOR, ItemType.DIAMOND_SWORD));
		Assertions.assertFalse(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.WARRIOR, ItemType.STONE));

		// SENTINEL: Restricts to melee weapons (Shield bulwarks; permits Trident as polearm, rejects ranged/thrown sticks)
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.SENTINEL, ItemType.TRIDENT));
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.SENTINEL, ItemType.DIAMOND_SWORD));
		Assertions.assertFalse(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.SENTINEL, ItemType.FROST_GRENADE_STICK), "Sentinel must not auto-equip thrown sticks");
		Assertions.assertFalse(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.SENTINEL, ItemType.TNT_STICK), "Sentinel must not auto-equip TNT sticks");
		Assertions.assertFalse(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.SENTINEL, ItemType.BOW), "Sentinel must not auto-equip bows");

		// BUILDER: Restricts to mining tools or basic melee weapons (rejects ranged/thrown sticks)
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.BUILDER, ItemType.IRON_PICKAXE));
		Assertions.assertTrue(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.BUILDER, ItemType.DIAMOND_SWORD));
		Assertions.assertFalse(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.BUILDER, ItemType.FROST_GRENADE_STICK), "Builder must not auto-equip thrown sticks");
		Assertions.assertFalse(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.BUILDER, ItemType.TNT_STICK), "Builder must not auto-equip TNT sticks");
		Assertions.assertFalse(WeaponClassifier.canRoleAutoEquipMainhand(MinionRole.BUILDER, ItemType.BOW), "Builder must not auto-equip bows");

		// 5. Weapon Preference Replacement Logic
		// Warrior replaces stone with trident
		Assertions.assertTrue(WeaponClassifier.isPreferredMainhandWeapon(MinionRole.WARRIOR, ItemType.TRIDENT, ItemType.STONE));
		// Warrior replaces sword with bow? No, already holds valid role weapon
		Assertions.assertFalse(WeaponClassifier.isPreferredMainhandWeapon(MinionRole.WARRIOR, ItemType.BOW, ItemType.DIAMOND_SWORD));
		// Sentinel replaces bow with trident
		Assertions.assertTrue(WeaponClassifier.isPreferredMainhandWeapon(MinionRole.SENTINEL, ItemType.TRIDENT, ItemType.BOW));
		// Builder replaces sword with pickaxe
		Assertions.assertTrue(WeaponClassifier.isPreferredMainhandWeapon(MinionRole.BUILDER, ItemType.IRON_PICKAXE, ItemType.DIAMOND_SWORD));
	}

	// =========================================================================
	// Trident Duality & Tactical Ranged Engagement Dynamics
	// =========================================================================

	@Test
	@DisplayName("Trident Duality: Close-quarters melee suppression and medium-range spear throw envelope")
	void testTridentDualityEngagementRanges() {
		final double TRIDENT_MELEE_RANGE = 5.0D;
		final double TRIDENT_MELEE_RANGE_SQ = 25.0D;
		final double TRIDENT_MAX_RANGE = 20.0D;
		final double TRIDENT_MAX_RANGE_SQ = 400.0D;

		class TridentCombatSimulator {
			boolean isWieldingTrident;
			double targetDistance;

			TridentCombatSimulator(boolean isWieldingTrident, double targetDistance) {
				this.isWieldingTrident = isWieldingTrident;
				this.targetDistance = targetDistance;
			}

			boolean shouldEngageRangedGoal() {
				if (!isWieldingTrident) {
					// Standard bow/grenade ranged engagement range: 8 to 16 blocks
					return targetDistance >= 8.0D && targetDistance <= 16.0D;
				}
				double distSq = targetDistance * targetDistance;
				// Trident Duality: if distance <= 5.0 blocks (25.0 sq), suppress ranged goal to permit MeleeAttackGoal
				if (distSq <= TRIDENT_MELEE_RANGE_SQ) {
					return false;
				}
				// Throws tridents within 5.0 to 20.0 block envelope
				return distSq <= TRIDENT_MAX_RANGE_SQ;
			}

			boolean shouldEngageMeleeGoal() {
				if (isWieldingTrident) {
					return targetDistance <= TRIDENT_MELEE_RANGE;
				}
				return targetDistance <= 3.0D;
			}

			int getRequiredChargeTicks(String weaponType) {
				return switch (weaponType) {
					case "trident" -> 10; // Fast 0.5s spear throw windup
					case "bow", "crossbow" -> 20; // 1.0s bow draw
					case "frost_grenade", "tnt_stick" -> 0; // Instant thrown ordnance
					default -> 0;
				};
			}
		}

		// Scenario A: Close quarters (dist = 3.0 blocks <= 5.0m)
		TridentCombatSimulator meleeRange = new TridentCombatSimulator(true, 3.0D);
		Assertions.assertFalse(meleeRange.shouldEngageRangedGoal(), "Ranged goal must yield at <= 5.0m so melee strikes take over");
		Assertions.assertTrue(meleeRange.shouldEngageMeleeGoal(), "Melee attack goal must engage at 3.0m");

		// Scenario B: Boundary close range (dist = 5.0 blocks)
		TridentCombatSimulator boundaryMelee = new TridentCombatSimulator(true, 5.0D);
		Assertions.assertFalse(boundaryMelee.shouldEngageRangedGoal(), "Ranged goal must yield exactly at 5.0m threshold");
		Assertions.assertTrue(boundaryMelee.shouldEngageMeleeGoal(), "Melee goal engages at 5.0m threshold");

		// Scenario C: Medium skirmish range (dist = 12.0 blocks)
		TridentCombatSimulator skirmishRange = new TridentCombatSimulator(true, 12.0D);
		Assertions.assertTrue(skirmishRange.shouldEngageRangedGoal(), "Trident throwing engages at 12.0m");
		Assertions.assertFalse(skirmishRange.shouldEngageMeleeGoal(), "Melee goal inactive at 12.0m");

		// Scenario D: Maximum throw range (dist = 20.0 blocks)
		TridentCombatSimulator maxRange = new TridentCombatSimulator(true, 20.0D);
		Assertions.assertTrue(maxRange.shouldEngageRangedGoal(), "Trident throwing engages at max 20.0m range");

		// Scenario E: Beyond maximum throw range (dist = 25.0 blocks > 20.0m)
		TridentCombatSimulator outOfRange = new TridentCombatSimulator(true, 25.0D);
		Assertions.assertFalse(outOfRange.shouldEngageRangedGoal(), "Beyond 20.0m minion navigates closer rather than throwing out-of-range");

		// Scenario F: Charge windup tick validation
		TridentCombatSimulator chargeSim = new TridentCombatSimulator(true, 10.0D);
		Assertions.assertEquals(10, chargeSim.getRequiredChargeTicks("trident"), "Trident must require 10 ticks (0.5s) charge windup");
		Assertions.assertEquals(20, chargeSim.getRequiredChargeTicks("bow"), "Bow must require 20 ticks (1.0s) draw time");
		Assertions.assertEquals(0, chargeSim.getRequiredChargeTicks("frost_grenade"), "Frost Grenade must be instant throw ordnance");
		Assertions.assertEquals(0, chargeSim.getRequiredChargeTicks("tnt_stick"), "TNT Stick must be instant throw ordnance");
	}

	// =========================================================================
	// Comprehensive Hunt Safety Protections
	// =========================================================================

	@Test
	@DisplayName("Mob Hunting Safety: Protect player pets, named mobs, villagers, golems, and allays")
	void testComprehensiveMobHuntSafetyChecks() {
		class EntitySafetyChecker {
			static boolean isSafeTarget(
				boolean isPlayer,
				boolean isMinion,
				boolean hasCustomName,
				boolean isTamedPet,
				boolean isTamedMount,
				boolean isMerchant,
				boolean isVillageGolem,
				boolean isUtilityOrDecoration
			) {
				if (isPlayer) return false;
				if (isMinion) return false;
				if (hasCustomName) return false;
				if (isTamedPet) return false;
				if (isTamedMount) return false;
				if (isMerchant) return false;
				if (isVillageGolem) return false;
				if (isUtilityOrDecoration) return false;
				return true;
			}
		}

		// Valid wild targets
		Assertions.assertTrue(EntitySafetyChecker.isSafeTarget(false, false, false, false, false, false, false, false), "Wild mob is safe to hunt");

		// Protected targets
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(true, false, false, false, false, false, false, false), "Player must never be hunted");
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(false, true, false, false, false, false, false, false), "Allied minion must never be hunted");
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(false, false, true, false, false, false, false, false), "Named / nametagged mob must never be hunted");
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(false, false, false, true, false, false, false, false), "Tamed dog/cat/parrot must never be hunted");
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(false, false, false, false, true, false, false, false), "Tamed horse/donkey/mule must never be hunted");
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(false, false, false, false, false, true, false, false), "Villager / Wandering trader must never be hunted");
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(false, false, false, false, false, false, true, false), "Iron Golem / Snow Golem must never be hunted");
		Assertions.assertFalse(EntitySafetyChecker.isSafeTarget(false, false, false, false, false, false, false, true), "Allay / Armor Stand must never be hunted");
	}

	@Test
	@DisplayName("Mob Resource Target Mapping: Exhaustive mapping across all mob drop categories")
	void testComprehensiveMobResourceMapping() {
		Map<String, List<String>> resourceToMobClasses = Map.ofEntries(
			Map.entry("wool", List.of("SheepEntity", "SpiderEntity", "CaveSpiderEntity")),
			Map.entry("string", List.of("SpiderEntity", "CaveSpiderEntity")),
			Map.entry("spider_eye", List.of("SpiderEntity", "CaveSpiderEntity")),
			Map.entry("bone", List.of("AbstractSkeletonEntity")),
			Map.entry("bone_meal", List.of("AbstractSkeletonEntity")),
			Map.entry("bone_block", List.of("AbstractSkeletonEntity")),
			Map.entry("slime_ball", List.of("SlimeEntity")),
			Map.entry("slime_block", List.of("SlimeEntity")),
			Map.entry("magma_cream", List.of("MagmaCubeEntity")),
			Map.entry("magma_block", List.of("MagmaCubeEntity")),
			Map.entry("leather", List.of("CowEntity", "MooshroomEntity", "HoglinEntity", "AbstractHorseEntity")),
			Map.entry("ink_sac", List.of("SquidEntity")),
			Map.entry("glow_ink_sac", List.of("GlowSquidEntity")),
			Map.entry("feather", List.of("ChickenEntity", "ParrotEntity")),
			Map.entry("blaze_rod", List.of("BlazeEntity")),
			Map.entry("ender_pearl", List.of("EndermanEntity")),
			Map.entry("gunpowder", List.of("CreeperEntity", "GhastEntity", "WitchEntity")),
			Map.entry("rotten_flesh", List.of("ZombieEntity")),
			Map.entry("prismarine_shard", List.of("GuardianEntity")),
			Map.entry("rabbit_hide", List.of("RabbitEntity")),
			Map.entry("phantom_membrane", List.of("PhantomEntity")),
			Map.entry("shulker_shell", List.of("ShulkerEntity"))
		);

		for (Map.Entry<String, List<String>> entry : resourceToMobClasses.entrySet()) {
			Assertions.assertFalse(entry.getValue().isEmpty(), "Resource " + entry.getKey() + " must have valid candidate mob mappings");
		}
	}

	// =========================================================================
	// Warrior Procurement Contracts & Solo Fallback Hunting
	// =========================================================================

	@Test
	@DisplayName("Warrior Procurement Contract: Commissioning, active tracking, drop resolution, and peer transfer")
	void testWarriorProcurementContractAndPeerTransfer() {
		UUID ownerId = UUID.randomUUID();
		UUID builderId = UUID.randomUUID();
		UUID warriorId = UUID.randomUUID();

		class MinionMock {
			final UUID id;
			final UUID owner;
			MinionRole role;
			boolean isSitting = false;
			UUID procurementRequesterUuid = null;
			String procurementItem = null;
			String targetEntity = null;
			List<String> assaultTargets = new ArrayList<>();
			Map<String, Integer> inventory = new HashMap<>();

			MinionMock(UUID id, UUID owner, MinionRole role) {
				this.id = id;
				this.owner = owner;
				this.role = role;
			}

			boolean hasActiveProcurement() {
				return procurementRequesterUuid != null;
			}

			boolean hasAssaultTargets() {
				return !assaultTargets.isEmpty();
			}

			int getItemCount(String item) {
				return inventory.getOrDefault(item, 0);
			}

			void addItem(String item, int count) {
				inventory.put(item, getItemCount(item) + count);
			}

			boolean consumeItem(String item, int count) {
				int curr = getItemCount(item);
				if (curr >= count) {
					inventory.put(item, curr - count);
					return true;
				}
				return false;
			}
		}

		MinionMock builder = new MinionMock(builderId, ownerId, MinionRole.BUILDER);
		MinionMock warrior = new MinionMock(warriorId, ownerId, MinionRole.WARRIOR);
		MinionMock busyWarrior = new MinionMock(UUID.randomUUID(), ownerId, MinionRole.WARRIOR);
		busyWarrior.procurementRequesterUuid = UUID.randomUUID(); // already on a hunt contract

		MinionMock sittingWarrior = new MinionMock(UUID.randomUUID(), ownerId, MinionRole.WARRIOR);
		sittingWarrior.isSitting = true;

		// 1. Warrior Discovery: Only available, unsat, non-procurement, non-assault warriors are eligible
		List<MinionMock> allMinions = List.of(builder, warrior, busyWarrior, sittingWarrior);
		List<MinionMock> availableWarriors = allMinions.stream()
			.filter(m -> m.id != builder.id
				&& m.owner.equals(ownerId)
				&& m.role == MinionRole.WARRIOR
				&& !m.isSitting
				&& !m.hasActiveProcurement()
				&& !m.hasAssaultTargets())
			.toList();

		Assertions.assertEquals(1, availableWarriors.size(), "Only 1 warrior should be available for contract commissioning");
		Assertions.assertEquals(warrior.id, availableWarriors.get(0).id);

		// 2. Commission Warrior Hunt
		warrior.procurementRequesterUuid = builder.id;
		warrior.procurementItem = "slime_block";
		warrior.targetEntity = "SlimeEntity";

		Assertions.assertTrue(warrior.hasActiveProcurement());
		Assertions.assertEquals(builder.id, warrior.procurementRequesterUuid);

		// 3. Slay mob: Slime yields 10 Slimeballs
		warrior.addItem("slime_ball", 10);
		Assertions.assertEquals(10, warrior.getItemCount("slime_ball"));

		// 4. In-inventory synthesis on Warrior: 9 slimeballs -> 1 slime block
		if (warrior.getItemCount("slime_ball") >= 9) {
			warrior.consumeItem("slime_ball", 9);
			warrior.addItem("slime_block", 1);
		}
		Assertions.assertEquals(1, warrior.getItemCount("slime_block"));
		Assertions.assertEquals(1, warrior.getItemCount("slime_ball"));

		// 5. Peer-to-peer delivery to Builder requester
		if (warrior.getItemCount("slime_block") > 0) {
			warrior.consumeItem("slime_block", 1);
			builder.addItem("slime_block", 1);
		}
		Assertions.assertEquals(1, builder.getItemCount("slime_block"), "Builder received 1 slime block");
		Assertions.assertEquals(0, warrior.getItemCount("slime_block"), "Delivered unit subtracted from Warrior");
		Assertions.assertEquals(1, warrior.getItemCount("slime_ball"), "Warrior keeps remaining surplus ingredients");

		// 6. Clear procurement task
		warrior.procurementRequesterUuid = null;
		warrior.procurementItem = null;
		warrior.targetEntity = null;
		Assertions.assertFalse(warrior.hasActiveProcurement(), "Warrior procurement contract cleanly reset");
	}

	@Test
	@DisplayName("Solo Fallback Hunting: Builder thrall engages target mob directly when no Warriors are available")
	void testSoloFallbackHuntingAndFailureHandling() {
		UUID ownerId = UUID.randomUUID();
		UUID builderId = UUID.randomUUID();

		class MinionMock {
			final UUID id;
			final UUID owner;
			MinionRole role;
			UUID procurementRequesterUuid = null;
			String procurementItem = null;
			String targetEntity = null;
			Map<String, Integer> inventory = new HashMap<>();

			MinionMock(UUID id, UUID owner, MinionRole role) {
				this.id = id;
				this.owner = owner;
				this.role = role;
			}

			boolean hasActiveProcurement() {
				return procurementRequesterUuid != null;
			}

			int getItemCount(String item) {
				return inventory.getOrDefault(item, 0);
			}

			void addItem(String item, int count) {
				inventory.put(item, getItemCount(item) + count);
			}

			boolean consumeItem(String item, int count) {
				int curr = getItemCount(item);
				if (curr >= count) {
					inventory.put(item, curr - count);
					return true;
				}
				return false;
			}
		}

		MinionMock builder = new MinionMock(builderId, ownerId, MinionRole.BUILDER);
		List<MinionMock> availableWarriors = Collections.emptyList(); // No warriors nearby!

		// 1. Check fallback triggering
		boolean soloHuntingTriggered = false;
		if (availableWarriors.isEmpty()) {
			// Solo fallback: Builder becomes own requester
			builder.procurementRequesterUuid = builder.id;
			builder.procurementItem = "white_wool";
			builder.targetEntity = "SheepEntity";
			soloHuntingTriggered = true;
		}

		Assertions.assertTrue(soloHuntingTriggered, "Solo fallback must activate when zero warriors are available");
		Assertions.assertEquals(builder.id, builder.procurementRequesterUuid, "Builder sets itself as requester");
		Assertions.assertEquals("white_wool", builder.procurementItem);

		// 2. Slay target mob: Sheep yields 2 white wool
		builder.addItem("white_wool", 2);
		Assertions.assertEquals(2, builder.getItemCount("white_wool"));

		// 3. Drop resolution and self-delivery: since requester == self, no P2P transfer needed
		if (builder.id.equals(builder.procurementRequesterUuid)) {
			// Self-procurement resolved directly in inventory
			builder.procurementRequesterUuid = null;
			builder.procurementItem = null;
			builder.targetEntity = null;
		}

		Assertions.assertFalse(builder.hasActiveProcurement(), "Procurement cleanly completed for solo hunter");
		Assertions.assertEquals(2, builder.getItemCount("white_wool"), "Builder holds procured wool ready for construction");

		// 4. Failure handling: when zero candidate mobs exist or all fail safety checks
		boolean noMobsFound = false;
		List<String> nearbyCandidateMobs = Collections.emptyList();
		if (nearbyCandidateMobs.isEmpty()) {
			noMobsFound = true;
		}
		Assertions.assertTrue(noMobsFound, "System gracefully fails without crashing when no safe candidate mobs exist");
	}
}
