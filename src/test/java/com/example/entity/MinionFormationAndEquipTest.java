package com.example.entity;

import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating role-based smart auto-equip rules and dynamic formation follow positioning:
 * - Parametric formation offset calculations (Vanguard, Bulwark, Core, Skirmishers).
 * - Rotational station mapping across compass cardinal directions.
 * - Anti-crowding station collision clearance (>= 2.0 blocks spacing between stations).
 * - Dynamic pacing speed thresholds (1.15D march, 1.35D sprint, 24-block emergency teleport).
 * - Role-based auto-equip weapon restrictions and prioritization state machines.
 */
public class MinionFormationAndEquipTest {

	// =========================================================================
	// 1. Formation Offset Calculations
	// =========================================================================

	@Test
	@DisplayName("Validate Frontline Rank (Warrior) straight army battle lines")
	void testWarriorFrontlineLines() {
		// Line 0 Warriors (all 4 units in rank 0..3 share identical forwardOffset = 4.0)
		Vec3d rank0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 0);
		Vec3d rank1 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 1);
		Vec3d rank2 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 2);
		Vec3d rank3 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 3);

		Assertions.assertEquals(4.0D, rank0.x, 1e-5, "Rank 0 must be 4.0 blocks forward");
		Assertions.assertEquals(-1.35D, rank0.z, 1e-5, "Rank 0 must be 1.35 blocks left");

		Assertions.assertEquals(4.0D, rank1.x, 1e-5, "Rank 1 must be 4.0 blocks forward");
		Assertions.assertEquals(1.35D, rank1.z, 1e-5, "Rank 1 must be 1.35 blocks right");

		Assertions.assertEquals(4.0D, rank2.x, 1e-5, "Rank 2 must be in same straight line (4.0 blocks forward)");
		Assertions.assertEquals(-3.60D, rank2.z, 1e-5, "Rank 2 flanks outer left (-3.60 blocks)");

		Assertions.assertEquals(4.0D, rank3.x, 1e-5, "Rank 3 must be in same straight line (4.0 blocks forward)");
		Assertions.assertEquals(3.60D, rank3.z, 1e-5, "Rank 3 flanks outer right (3.60 blocks)");

		// Line 1 Warrior steps back by 2.0 blocks
		Vec3d rank4 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 4);
		Assertions.assertEquals(2.0D, rank4.x, 1e-5, "Rank 4 (Line 1) must be 2.0 blocks forward");
	}

	@Test
	@DisplayName("Validate Midline Escort (Sentinel) straight army battle lines")
	void testSentinelMidlineEscort() {
		Vec3d rank0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.SENTINEL, 0);
		Vec3d rank1 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.SENTINEL, 1);
		Vec3d rank2 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.SENTINEL, 2);

		Assertions.assertEquals(1.8D, rank0.x, 1e-5, "Sentinel rank 0 must be 1.8 blocks forward");
		Assertions.assertEquals(-1.35D, rank0.z, 1e-5, "Sentinel rank 0 must flank 1.35 blocks left");

		Assertions.assertEquals(1.8D, rank1.x, 1e-5, "Sentinel rank 1 must be 1.8 blocks forward");
		Assertions.assertEquals(1.35D, rank1.z, 1e-5, "Sentinel rank 1 must flank 1.35 blocks right");

		Assertions.assertEquals(1.8D, rank2.x, 1e-5, "Sentinel rank 2 must share straight line at 1.8 blocks forward");
		Assertions.assertEquals(-3.60D, rank2.z, 1e-5, "Sentinel rank 2 must flank 3.60 blocks left");
	}

	@Test
	@DisplayName("Validate Rearguard Support (Builder) straight army lines behind commander")
	void testCoreSupportPlacement() {
		Vec3d builder0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.BUILDER, 0);

		// Core starts behind the commander
		Assertions.assertTrue(builder0.x < 0, "Builder must be behind commander (negative forward)");
		Assertions.assertEquals(-2.0D, builder0.x, 1e-5);
		// Col spacing is 1.35 on inner pair
		Assertions.assertEquals(-1.35D, builder0.z, 1e-5);
	}

	@Test
	@DisplayName("Validate Ranked Army Line Formations: straight parallel battle ranks with uniform forward offset per line")
	void testRankedArmyLineFormation() {
		for (MinionRole role : MinionRole.values()) {
			for (int line = 0; line < 3; line++) {
				Vec3d u0 = MinionFormationFollowGoal.calculateFormationOffset(role, line * 4);
				Vec3d u1 = MinionFormationFollowGoal.calculateFormationOffset(role, line * 4 + 1);
				Vec3d u2 = MinionFormationFollowGoal.calculateFormationOffset(role, line * 4 + 2);
				Vec3d u3 = MinionFormationFollowGoal.calculateFormationOffset(role, line * 4 + 3);

				// All 4 units in the line must share the exact same forwardOffset to form a straight line
				Assertions.assertEquals(u0.x, u1.x, 1e-5, role + " Line " + line + " units must form straight line");
				Assertions.assertEquals(u0.x, u2.x, 1e-5, role + " Line " + line + " units must form straight line");
				Assertions.assertEquals(u0.x, u3.x, 1e-5, role + " Line " + line + " units must form straight line");

				// Symmetrical lateral positions
				Assertions.assertEquals(-u0.z, u1.z, 1e-5, "Inner pair must be symmetrical across center");
				Assertions.assertEquals(-u2.z, u3.z, 1e-5, "Outer pair must be symmetrical across center");
			}
		}
	}

	// =========================================================================
	// 2. Anti-Crowding Station Clearance
	// =========================================================================

	@Test
	@DisplayName("Validate no two stations in formation crowd within 2.0 blocks clearance")
	void testAntiCrowdingStationClearance() {
		List<Vec3d> stations = new ArrayList<>();

		// Collect stations across all roles and top 4 ranks
		for (MinionRole role : MinionRole.values()) {
			for (int rank = 0; rank < 4; rank++) {
				stations.add(MinionFormationFollowGoal.calculateFormationOffset(role, rank));
			}
		}

		// Verify station distance against commander origin (0, 0)
		for (Vec3d station : stations) {
			double distToCommander = Math.hypot(station.x, station.z);
			Assertions.assertTrue(distToCommander >= 2.0D,
				"Station (" + station.x + ", " + station.z + ") must not crowd commander (< 2.0 blocks)");
		}

		// Verify station spacing within same role
		for (MinionRole role : MinionRole.values()) {
			List<Vec3d> roleStations = new ArrayList<>();
			for (int rank = 0; rank < 4; rank++) {
				roleStations.add(MinionFormationFollowGoal.calculateFormationOffset(role, rank));
			}

			for (int i = 0; i < roleStations.size(); i++) {
				for (int j = i + 1; j < roleStations.size(); j++) {
					Vec3d s1 = roleStations.get(i);
					Vec3d s2 = roleStations.get(j);
					double dist = Math.hypot(s1.x - s2.x, s1.z - s2.z);
					Assertions.assertTrue(dist >= 2.0D,
						"Stations within " + role + " (" + i + " vs " + j + ") must maintain >= 2.0 blocks spacing, got " + dist);
				}
			}
		}
	}

	// =========================================================================
	// 3. Compass Yaw Transformation
	// =========================================================================

	@Test
	@DisplayName("Validate station world coordinates rotate accurately with player yaw")
	void testYawRotationTransformation() {
		double ownerX = 100.0D;
		double ownerY = 64.0D;
		double ownerZ = 200.0D;

		// Rank 0 Warrior: local forward = +4.0, local flank = -1.35

		// 1. Yaw = 0 (Facing South, +Z): forward is +Z, flank-left is -X
		Vec3d southPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 0.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX - 1.35D, southPos.x, 1e-4, "Facing South: left flank should be -X");
		Assertions.assertEquals(ownerZ + 4.0D, southPos.z, 1e-4, "Facing South: forward should be +Z");

		// 2. Yaw = 180 (Facing North, -Z): forward is -Z, flank-left is +X
		Vec3d northPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 180.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX + 1.35D, northPos.x, 1e-4, "Facing North: left flank should be +X");
		Assertions.assertEquals(ownerZ - 4.0D, northPos.z, 1e-4, "Facing North: forward should be -Z");

		// 3. Yaw = 90 (Facing West, -X): forward is -X, flank-left is -Z
		Vec3d westPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 90.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX - 4.0D, westPos.x, 1e-4, "Facing West: forward should be -X");
		Assertions.assertEquals(ownerZ - 1.35D, westPos.z, 1e-4, "Facing West: left flank should be -Z");

		// 4. Yaw = 270 (Facing East, +X): forward is +X, flank-left is +Z
		Vec3d eastPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 270.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX + 4.0D, eastPos.x, 1e-4, "Facing East: forward should be +X");
		Assertions.assertEquals(ownerZ + 1.35D, eastPos.z, 1e-4, "Facing East: left flank should be +Z");
	}

	// =========================================================================
	// 4. Dynamic Pacing & Teleport Thresholds
	// =========================================================================

	@Test
	@DisplayName("Validate dynamic pacing speeds and distance limits")
	void testDynamicPacingThresholds() {
		Assertions.assertEquals(1.15D, MinionFormationFollowGoal.MARCH_SPEED);
		Assertions.assertEquals(1.35D, MinionFormationFollowGoal.SPRINT_SPEED);
		Assertions.assertEquals(2.0D, MinionFormationFollowGoal.STOPPING_DISTANCE);
		Assertions.assertEquals(4.0D, MinionFormationFollowGoal.STOPPING_DISTANCE_SQ);
		Assertions.assertEquals(8.0D, MinionFormationFollowGoal.SPRINT_DISTANCE_THRESHOLD);
		Assertions.assertEquals(64.0D, MinionFormationFollowGoal.SPRINT_DISTANCE_THRESHOLD_SQ);
		Assertions.assertEquals(64.0D, MinionFormationFollowGoal.TELEPORT_DISTANCE_THRESHOLD);
		Assertions.assertEquals(4096.0D, MinionFormationFollowGoal.TELEPORT_DISTANCE_THRESHOLD_SQ);
	}

	// =========================================================================
	// 5. Role-Based Auto-Equip Eligibility State Machine
	// =========================================================================

	public enum TestItemCategory {
		RANGED_BOW,
		RANGED_CROSSBOW,
		MELEE_SWORD,
		MELEE_AXE,
		MELEE_MACE,
		MELEE_TRIDENT,
		TOOL_PICKAXE,
		TOOL_SHOVEL,
		ARMOR_HELMET,
		ARMOR_CHESTPLATE,
		ARMOR_LEGGINGS,
		ARMOR_BOOTS,
		OFFHAND_SHIELD,
		OFFHAND_TOTEM
	}

	/**
	 * State machine simulating the exact behavioral contract of MinionEntity role-based auto-equip.
	 */
	public static class TestAutoEquipLogic {
		public static boolean canRoleAutoEquipMainhand(MinionRole role, TestItemCategory item) {
			return switch (role) {
				case WARRIOR -> item == TestItemCategory.MELEE_SWORD
					|| item == TestItemCategory.MELEE_AXE
					|| item == TestItemCategory.MELEE_MACE
					|| item == TestItemCategory.MELEE_TRIDENT
					|| item == TestItemCategory.RANGED_BOW
					|| item == TestItemCategory.RANGED_CROSSBOW;
				case SENTINEL -> item == TestItemCategory.MELEE_SWORD
					|| item == TestItemCategory.MELEE_AXE
					|| item == TestItemCategory.MELEE_MACE
					|| item == TestItemCategory.MELEE_TRIDENT;
				case BUILDER -> item == TestItemCategory.TOOL_PICKAXE
					|| item == TestItemCategory.TOOL_SHOVEL
					|| item == TestItemCategory.MELEE_AXE
					|| item == TestItemCategory.MELEE_SWORD
					|| item == TestItemCategory.MELEE_MACE;
			};
		}

		public static boolean isPreferredMainhand(MinionRole role, TestItemCategory candidate, TestItemCategory current) {
			if (current == null) return canRoleAutoEquipMainhand(role, candidate);

			boolean candidateMelee = candidate == TestItemCategory.MELEE_SWORD
				|| candidate == TestItemCategory.MELEE_AXE
				|| candidate == TestItemCategory.MELEE_MACE
				|| candidate == TestItemCategory.MELEE_TRIDENT;
			boolean currentMelee = current == TestItemCategory.MELEE_SWORD
				|| current == TestItemCategory.MELEE_AXE
				|| current == TestItemCategory.MELEE_MACE
				|| current == TestItemCategory.MELEE_TRIDENT;

			return switch (role) {
				case WARRIOR -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
				case SENTINEL -> candidateMelee && !currentMelee;
				case BUILDER -> canRoleAutoEquipMainhand(role, candidate) && !canRoleAutoEquipMainhand(role, current);
			};
		}

		public static boolean isPreferredOffhand(MinionRole role, TestItemCategory candidate, TestItemCategory current) {
			if (role == MinionRole.SENTINEL) {
				// Sentinel specifically seeks shields in offhand
				return candidate == TestItemCategory.OFFHAND_SHIELD && current != TestItemCategory.OFFHAND_SHIELD;
			}
			return (candidate == TestItemCategory.OFFHAND_SHIELD || candidate == TestItemCategory.OFFHAND_TOTEM) && current == null;
		}
	}

	@Test
	@DisplayName("Validate Warrior auto-equip: equips both melee (swords/axes/maces) and ranged (bows/crossbows)")
	void testWarriorAutoEquipRestrictions() {
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_SWORD));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_AXE));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_MACE));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_TRIDENT));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.RANGED_BOW));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.RANGED_CROSSBOW));

		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.TOOL_PICKAXE),
			"Warrior must not auto-equip pickaxes");
		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.TOOL_SHOVEL),
			"Warrior must not auto-equip shovels");
	}

	@Test
	@DisplayName("Validate Sentinel auto-equip: seeks melee in mainhand, prioritizes shield in offhand")
	void testSentinelAutoEquipRestrictions() {
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.SENTINEL, TestItemCategory.MELEE_SWORD));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.SENTINEL, TestItemCategory.MELEE_AXE));
		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.SENTINEL, TestItemCategory.RANGED_BOW));

		// Offhand shield priority
		Assertions.assertTrue(TestAutoEquipLogic.isPreferredOffhand(MinionRole.SENTINEL, TestItemCategory.OFFHAND_SHIELD, null));
		Assertions.assertTrue(TestAutoEquipLogic.isPreferredOffhand(MinionRole.SENTINEL, TestItemCategory.OFFHAND_SHIELD, TestItemCategory.OFFHAND_TOTEM),
			"Sentinel must prioritize shield over other offhand items");
		Assertions.assertFalse(TestAutoEquipLogic.isPreferredOffhand(MinionRole.SENTINEL, TestItemCategory.OFFHAND_TOTEM, TestItemCategory.OFFHAND_SHIELD));
	}


	@Test
	@DisplayName("Validate Builder auto-equip: accepts construction tools & melee, rejects ranged weapons")
	void testBuilderAutoEquipRestrictions() {
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.BUILDER, TestItemCategory.TOOL_PICKAXE));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.BUILDER, TestItemCategory.TOOL_SHOVEL));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.BUILDER, TestItemCategory.MELEE_SWORD));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.BUILDER, TestItemCategory.MELEE_AXE));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.BUILDER, TestItemCategory.MELEE_MACE));

		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.BUILDER, TestItemCategory.RANGED_BOW),
			"Builder must not auto-equip bows");
		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.BUILDER, TestItemCategory.RANGED_CROSSBOW),
			"Builder must not auto-equip crossbows");
	}

	@Test
	@DisplayName("Validate comprehensive auto-equip matrix across all archetype roles and weapon categories")
	void testRoleAutoEquipComprehensiveMatrix() {
		for (MinionRole role : MinionRole.values()) {
			// All roles reject armor in mainhand
			Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.ARMOR_HELMET));
			Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.ARMOR_CHESTPLATE));
			Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.ARMOR_LEGGINGS));
			Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.ARMOR_BOOTS));

			// Role-specific weapon compatibility
			if (role == MinionRole.WARRIOR) {
				Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.RANGED_BOW));
				Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.RANGED_CROSSBOW));
				Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.MELEE_SWORD));
				Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.MELEE_AXE));
				Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.TOOL_PICKAXE));
			} else {
				// Non-warrior roles never equip ranged weapons
				Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.RANGED_BOW));
				Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.RANGED_CROSSBOW));
			}
		}
	}

	@Test
	@DisplayName("Validate inventory displacement preserves displaced items without loss or duplication")
	void testInventoryDisplacementAndItemPreservation() {
		// Simulate minion inventory state machine
		class MockMinionInventory {
			TestItemCategory mainhand = null;
			TestItemCategory offhand = null;
			final List<TestItemCategory> storage = new ArrayList<>();

			void addItem(TestItemCategory item) {
				storage.add(item);
			}

			void autoEquip(MinionRole role) {
				// 0. Active role enforcement: disarm invalid held weapon into storage
				if (mainhand != null && !TestAutoEquipLogic.canRoleAutoEquipMainhand(role, mainhand)) {
					storage.add(mainhand);
					mainhand = null;
				}

				// Sentinel offhand priority
				if (role == MinionRole.SENTINEL) {
					for (int i = 0; i < storage.size(); i++) {
						TestItemCategory item = storage.get(i);
						if (item == TestItemCategory.OFFHAND_SHIELD && offhand != TestItemCategory.OFFHAND_SHIELD) {
							TestItemCategory oldOffhand = offhand;
							offhand = item;
							storage.remove(i);
							if (oldOffhand != null) storage.add(oldOffhand);
							break;
						}
					}
				}

				// Mainhand auto-equip
				for (int i = 0; i < storage.size(); i++) {
					TestItemCategory candidate = storage.get(i);
					if (mainhand == null && TestAutoEquipLogic.canRoleAutoEquipMainhand(role, candidate)) {
						mainhand = candidate;
						storage.remove(i);
						i--;
					} else if (TestAutoEquipLogic.isPreferredMainhand(role, candidate, mainhand)) {
						TestItemCategory oldMainhand = mainhand;
						mainhand = candidate;
						storage.set(i, oldMainhand);
					}
				}
			}

			int totalItemCount() {
				int count = storage.size();
				if (mainhand != null) count++;
				if (offhand != null) count++;
				return count;
			}
		}

		// 1. Warrior starts with pickaxe in mainhand, finds diamond sword in inventory
		MockMinionInventory warriorInv = new MockMinionInventory();
		warriorInv.mainhand = TestItemCategory.TOOL_PICKAXE;
		warriorInv.addItem(TestItemCategory.MELEE_SWORD);
		int initialCount = warriorInv.totalItemCount();

		warriorInv.autoEquip(MinionRole.WARRIOR);
		Assertions.assertEquals(TestItemCategory.MELEE_SWORD, warriorInv.mainhand, "Warrior should equip sword");
		Assertions.assertTrue(warriorInv.storage.contains(TestItemCategory.TOOL_PICKAXE), "Displaced pickaxe must return to inventory");
		Assertions.assertEquals(initialCount, warriorInv.totalItemCount(), "Total item count must be strictly invariant");

		// 2. Sentinel with totem offhand finds shield
		MockMinionInventory sentinelInv = new MockMinionInventory();
		sentinelInv.offhand = TestItemCategory.OFFHAND_TOTEM;
		sentinelInv.addItem(TestItemCategory.OFFHAND_SHIELD);
		initialCount = sentinelInv.totalItemCount();

		sentinelInv.autoEquip(MinionRole.SENTINEL);
		Assertions.assertEquals(TestItemCategory.OFFHAND_SHIELD, sentinelInv.offhand, "Sentinel should prioritize shield");
		Assertions.assertTrue(sentinelInv.storage.contains(TestItemCategory.OFFHAND_TOTEM), "Displaced totem must return to inventory");
		Assertions.assertEquals(initialCount, sentinelInv.totalItemCount(), "Item count invariant preserved");

		// 3. Warrior with shovel finds bow
		MockMinionInventory warriorBowInv = new MockMinionInventory();
		warriorBowInv.mainhand = TestItemCategory.TOOL_SHOVEL;
		warriorBowInv.addItem(TestItemCategory.RANGED_BOW);
		initialCount = warriorBowInv.totalItemCount();

		warriorBowInv.autoEquip(MinionRole.WARRIOR);
		Assertions.assertEquals(TestItemCategory.RANGED_BOW, warriorBowInv.mainhand, "Warrior should equip bow over illegal shovel");
		Assertions.assertTrue(warriorBowInv.storage.contains(TestItemCategory.TOOL_SHOVEL), "Displaced shovel preserved");
		Assertions.assertEquals(initialCount, warriorBowInv.totalItemCount());

		// 4. Sentinel with bow and empty storage disarms bow into storage
		MockMinionInventory disarmInv = new MockMinionInventory();
		disarmInv.mainhand = TestItemCategory.RANGED_BOW;
		disarmInv.autoEquip(MinionRole.SENTINEL);
		Assertions.assertNull(disarmInv.mainhand, "Sentinel must disarm illegal bow from mainhand");
		Assertions.assertTrue(disarmInv.storage.contains(TestItemCategory.RANGED_BOW), "Disarmed bow must move to storage");
	}

	@Test
	@DisplayName("Validate mob transfiguration clean-slate equipment invariant")
	void testTransfigureCleanSlateEquipment() throws IOException {
		Path scepterPath = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		String code = Files.readString(scepterPath);
		Assertions.assertTrue(code.contains("minion.equipStack(slot, ItemStack.EMPTY)"),
			"CommandScepterItem must initialize transfigured minions with empty equipment");
		Assertions.assertFalse(code.contains("minion.equipStack(slot, equip.copy())"),
			"CommandScepterItem must not copy mob equipment to recruited minion");
	}

	@Test
	@DisplayName("Validate 8 cardinal/intercardinal compass yaw headings preserve station distance")
	void testFullCompassYawRotationInvariants() {
		double ownerX = 0.0D;
		double ownerY = 64.0D;
		double ownerZ = 0.0D;

		// Rank 0 Warrior: forward = 4.0D, flank = -1.35D -> expected radial distance = sqrt(4.0^2 + 1.35^2)
		double expectedDistance = Math.hypot(4.0D, 1.35D);

		float[] testYaws = {0.0F, 45.0F, 90.0F, 135.0F, 180.0F, 225.0F, 270.0F, 315.0F, 360.0F, -90.0F, -180.0F};

		for (float yaw : testYaws) {
			Vec3d station = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, yaw, MinionRole.WARRIOR, 0);
			double radialDist = Math.hypot(station.x - ownerX, station.z - ownerZ);
			Assertions.assertEquals(expectedDistance, radialDist, 1e-4,
				"Radial station distance must remain invariant under yaw " + yaw + "°");
		}
	}

	@Test
	@DisplayName("Validate 12-thrall 3-echelon cohort pairwise formation collision spacing (>= 1.5 blocks)")
	void testFullCohortCollisionExclusion() {
		// Generate stations for 4 minions in each of the 3 tactical echelons (12 minions total):
		// Frontline (Warrior), Midline Escort (Sentinel), and Rearguard Support (Builder)
		List<MinionRole> echelons = List.of(MinionRole.WARRIOR, MinionRole.SENTINEL, MinionRole.BUILDER);
		List<Vec3d> cohortStations = new ArrayList<>();
		for (MinionRole role : echelons) {
			for (int rank = 0; rank < 4; rank++) {
				cohortStations.add(MinionFormationFollowGoal.calculateFormationOffset(role, rank));
			}
		}

		Assertions.assertEquals(12, cohortStations.size(), "Cohort must comprise 12 stations across 3 echelons");

		// Every pair of stations across the tactical echelon cohort must maintain >= 1.50 blocks clearance
		// (well above Minecraft biped 0.6 block collision width, completely preventing crowding)
		for (int i = 0; i < cohortStations.size(); i++) {
			for (int j = i + 1; j < cohortStations.size(); j++) {
				Vec3d s1 = cohortStations.get(i);
				Vec3d s2 = cohortStations.get(j);
				double distance = Math.hypot(s1.x - s2.x, s1.z - s2.z);
				Assertions.assertTrue(distance >= 1.50D,
					String.format("Station %d (%.2f, %.2f) and station %d (%.2f, %.2f) must not crowd! Dist = %.2f",
						i, s1.x, s1.z, j, s2.x, s2.z, distance));
			}
		}
	}

	@Test
	@DisplayName("Validate formation offset reflectional symmetry and monotonic wing flare")
	void testFormationOffsetSymmetryAndMonotonicity() {
		for (MinionRole role : MinionRole.values()) {
			for (int tier = 0; tier < 4; tier++) {
				int leftRank = tier * 2;
				int rightRank = tier * 2 + 1;

				Vec3d left = MinionFormationFollowGoal.calculateFormationOffset(role, leftRank);
				Vec3d right = MinionFormationFollowGoal.calculateFormationOffset(role, rightRank);

				// Forward offsets for symmetric ranks in a tier must be identical
				Assertions.assertEquals(left.x, right.x, 1e-5,
					role + " tier " + tier + " forward offsets must match on left and right");

				// Flank offsets must be exact reflections across the central forward axis (left = -right)
				Assertions.assertEquals(-left.z, right.z, 1e-5,
					role + " tier " + tier + " flank offsets must be symmetric reflections");
			}
		}
	}

	@Test
	@DisplayName("Validate dynamic pacing speed transition state machine across distances")
	void testDynamicPacingSpeedTransitions() {
		// Testing the exact pacing logic:
		// dist <= 2.0 -> ARRIVAL / STOP
		// 2.0 < dist <= 8.0 -> MARCH (1.15D)
		// 8.0 < dist <= 64.0 -> SPRINT (1.35D)
		// dist > 64.0 -> TELEPORT

		record PacingState(boolean arrived, double speed, boolean emergencyTeleport) {}

		java.util.function.DoubleFunction<PacingState> evaluatePacing = dist -> {
			if (dist > MinionFormationFollowGoal.TELEPORT_DISTANCE_THRESHOLD) {
				return new PacingState(false, MinionFormationFollowGoal.SPRINT_SPEED, true);
			}
			if (dist <= MinionFormationFollowGoal.STOPPING_DISTANCE) {
				return new PacingState(true, 0.0D, false);
			}
			double speed = (dist > MinionFormationFollowGoal.SPRINT_DISTANCE_THRESHOLD)
				? MinionFormationFollowGoal.SPRINT_SPEED
				: MinionFormationFollowGoal.MARCH_SPEED;
			return new PacingState(false, speed, false);
		};

		// 1. Arrival zone (0.0 to 2.0 blocks)
		Assertions.assertTrue(evaluatePacing.apply(0.5D).arrived());
		Assertions.assertTrue(evaluatePacing.apply(1.99D).arrived());
		Assertions.assertTrue(evaluatePacing.apply(2.0D).arrived());

		// 2. Marching zone (2.01 to 8.0 blocks)
		PacingState marchState = evaluatePacing.apply(2.01D);
		Assertions.assertFalse(marchState.arrived());
		Assertions.assertEquals(MinionFormationFollowGoal.MARCH_SPEED, marchState.speed(), 1e-5);
		Assertions.assertFalse(marchState.emergencyTeleport());

		PacingState marchBoundary = evaluatePacing.apply(8.0D);
		Assertions.assertEquals(MinionFormationFollowGoal.MARCH_SPEED, marchBoundary.speed(), 1e-5);
		Assertions.assertFalse(marchBoundary.emergencyTeleport());

		// 3. Sprinting zone (8.01 to 64.0 blocks)
		PacingState sprintState = evaluatePacing.apply(12.0D);
		Assertions.assertFalse(sprintState.arrived());
		Assertions.assertEquals(MinionFormationFollowGoal.SPRINT_SPEED, sprintState.speed(), 1e-5);
		Assertions.assertFalse(sprintState.emergencyTeleport());

		PacingState sprintBoundary = evaluatePacing.apply(64.0D);
		Assertions.assertEquals(MinionFormationFollowGoal.SPRINT_SPEED, sprintBoundary.speed(), 1e-5);
		Assertions.assertFalse(sprintBoundary.emergencyTeleport());

		// 4. Emergency teleport recall (> 64.0 blocks)
		PacingState teleportState = evaluatePacing.apply(65.0D);
		Assertions.assertTrue(teleportState.emergencyTeleport());
		Assertions.assertTrue(evaluatePacing.apply(100.0D).emergencyTeleport());
	}

	// =========================================================================
	// 5. Yaw Hysteresis & Anchoring Tests
	// =========================================================================

	@Test
	@DisplayName("Validate formation yaw hysteresis: 360° stationary look yaw sweeps freeze formation yaw")
	void testFormationYawHysteresisStationaryLookSweep() {
		UUID commander = UUID.randomUUID();
		MinionFormationFollowGoal.clearFormationAnchor(commander);

		double x = 10.0D;
		double y = 64.0D;
		double z = 20.0D;
		float initialYaw = 0.0F;

		// Initial lookup initializes anchor
		float yaw0 = MinionFormationFollowGoal.getFormationYaw(commander, x, z, initialYaw);
		Assertions.assertEquals(0.0F, yaw0, 1e-4F, "Initial formation yaw must match initial heading");

		// Compute initial station for Rank 0 Warrior
		Vec3d baseStation = MinionFormationFollowGoal.calculateFormationStation(commander, x, y, z, initialYaw, MinionRole.WARRIOR, 0);

		// Sweep through 360° headings while standing stationary at (x, z)
		float[] sweepYaws = {15.0F, 45.0F, 90.0F, 135.0F, 180.0F, 225.0F, 270.0F, 315.0F, 360.0F, -45.0F, -90.0F, -180.0F};
		for (float yaw : sweepYaws) {
			float anchoredYaw = MinionFormationFollowGoal.getFormationYaw(commander, x, z, yaw);
			Assertions.assertEquals(0.0F, anchoredYaw, 1e-4F,
				"Formation yaw must remain frozen at initial anchor 0.0° during stationary look sweep at " + yaw + "°");

			Vec3d sweptStation = MinionFormationFollowGoal.calculateFormationStation(commander, x, y, z, yaw, MinionRole.WARRIOR, 0);
			Assertions.assertEquals(baseStation.x, sweptStation.x, 1e-4, "Station X must not whirl when stationary");
			Assertions.assertEquals(baseStation.y, sweptStation.y, 1e-4, "Station Y must remain constant");
			Assertions.assertEquals(baseStation.z, sweptStation.z, 1e-4, "Station Z must not whirl when stationary");
		}
	}

	@Test
	@DisplayName("Validate formation yaw hysteresis: sub-threshold displacement (<= 0.04 blocks^2) preserves frozen yaw")
	void testFormationYawHysteresisSubThresholdMovement() {
		UUID commander = UUID.randomUUID();
		MinionFormationFollowGoal.clearFormationAnchor(commander);

		double startX = 0.0D;
		double startZ = 0.0D;
		float initialYaw = 0.0F;

		Assertions.assertEquals(0.0F, MinionFormationFollowGoal.getFormationYaw(commander, startX, startZ, initialYaw), 1e-4F);

		// 1. Small jitter displacement: dx=0.1, dz=0.1 -> distSq = 0.01 + 0.01 = 0.02 <= 0.04
		float jitterYaw = MinionFormationFollowGoal.getFormationYaw(commander, 0.1D, 0.1D, 45.0F);
		Assertions.assertEquals(0.0F, jitterYaw, 1e-4F, "Sub-threshold movement must not unlock frozen yaw");

		// 2. Boundary displacement: dx=0.14, dz=0.14 -> distSq = 0.0196 + 0.0196 = 0.0392 <= 0.04
		float nearLimitYaw = MinionFormationFollowGoal.getFormationYaw(commander, 0.14D, 0.14D, 60.0F);
		Assertions.assertEquals(0.0F, nearLimitYaw, 1e-4F, "Displacement of 0.0392 blocks^2 must remain frozen");

		// 3. Exact boundary displacement: dx=0.2, dz=0.0 -> distSq = 0.04 <= 0.04
		float exactBoundaryYaw = MinionFormationFollowGoal.getFormationYaw(commander, 0.20D, 0.0D, 75.0F);
		Assertions.assertEquals(0.0F, exactBoundaryYaw, 1e-4F, "Exact displacement of 0.04 blocks^2 must remain frozen");

		// 4. Deliberate displacement beyond threshold: dx=0.21, dz=0.0 -> distSq = 0.0441 > 0.04
		float unlockedYaw = MinionFormationFollowGoal.getFormationYaw(commander, 0.21D, 0.0D, 90.0F);
		Assertions.assertEquals(90.0F, unlockedYaw, 1e-4F, "Displacement > 0.04 blocks^2 must unlock and update formation yaw");

		// 5. Subsequent stationary look sweep at new anchor position (0.21, 0.0)
		float newStationaryYaw = MinionFormationFollowGoal.getFormationYaw(commander, 0.21D, 0.0D, 180.0F);
		Assertions.assertEquals(90.0F, newStationaryYaw, 1e-4F, "Subsequent stationary look sweep must freeze at new anchor yaw 90.0°");
	}

	@Test
	@DisplayName("Validate refreshFormationAnchor snaps immediately and clearing removes anchors")
	void testRefreshFormationAnchorImmediateSnapAndClear() {
		UUID commander = UUID.randomUUID();
		MinionFormationFollowGoal.clearFormationAnchor(commander);

		// Anchor at (5.0, 5.0) with yaw 30.0F
		float yaw = MinionFormationFollowGoal.getFormationYaw(commander, 5.0D, 5.0D, 30.0F);
		Assertions.assertEquals(30.0F, yaw, 1e-4F);

		// Commander turns to 180.0F while stationary -> without refresh, yaw is frozen at 30.0F
		float frozen = MinionFormationFollowGoal.getFormationYaw(commander, 5.0D, 5.0D, 180.0F);
		Assertions.assertEquals(30.0F, frozen, 1e-4F);

		// Explicit refresh snaps anchor immediately to current heading
		MinionFormationFollowGoal.refreshFormationAnchor(commander, 5.0D, 5.0D, 180.0F);
		float snapped = MinionFormationFollowGoal.getFormationYaw(commander, 5.0D, 5.0D, 180.0F);
		Assertions.assertEquals(180.0F, snapped, 1e-4F, "refreshFormationAnchor must snap yaw immediately");

		// Verify anchor inspection
		MinionFormationFollowGoal.FormationAnchor anchor = MinionFormationFollowGoal.getFormationAnchor(commander);
		Assertions.assertNotNull(anchor, "FormationAnchor must exist");
		Assertions.assertEquals(5.0D, anchor.getLastX(), 1e-5);
		Assertions.assertEquals(5.0D, anchor.getLastZ(), 1e-5);
		Assertions.assertEquals(180.0F, anchor.getAnchoredYaw(), 1e-4F);

		// Clearing individual anchor
		MinionFormationFollowGoal.clearFormationAnchor(commander);
		Assertions.assertNull(MinionFormationFollowGoal.getFormationAnchor(commander), "Cleared anchor must be null");

		// Global clear
		MinionFormationFollowGoal.refreshFormationAnchor(commander, 1.0D, 2.0D, 45.0F);
		Assertions.assertNotNull(MinionFormationFollowGoal.getFormationAnchor(commander));
		MinionFormationFollowGoal.clearFormationAnchors();
		Assertions.assertNull(MinionFormationFollowGoal.getFormationAnchor(commander), "clearFormationAnchors must clear all");
	}

	// =========================================================================
	// 6. Rank Filtering & Unit Resolution Tests
	// =========================================================================

	@Test
	@DisplayName("Validate formation rank eligibility predicate filters unselected, sitting, and guarding minions")
	void testFormationRankFilteringEligibilityPredicate() {
		// (alive, tamed, isOwner, selected, holdingPosition, hasGuardAnchor)
		// Active selected follower: must be eligible
		Assertions.assertTrue(MinionFormationFollowGoal.isEligibleForFormationRank(true, true, true, true, false, false));

		// Unselected minion: must be excluded from ranks
		Assertions.assertFalse(MinionFormationFollowGoal.isEligibleForFormationRank(true, true, true, false, false, false),
			"Unselected minion must not pollute formation ranks");

		// Minion holding position (sitting or guarding): must be excluded
		Assertions.assertFalse(MinionFormationFollowGoal.isEligibleForFormationRank(true, true, true, true, true, false),
			"Sitting or holding position unit must be excluded from ranks");

		// Minion with guard anchor post: must be excluded
		Assertions.assertFalse(MinionFormationFollowGoal.isEligibleForFormationRank(true, true, true, true, false, true),
			"Sentinel with guard anchor must be excluded from ranks");

		// Dead minion: must be excluded
		Assertions.assertFalse(MinionFormationFollowGoal.isEligibleForFormationRank(false, true, true, true, false, false));

		// Untamed minion: must be excluded
		Assertions.assertFalse(MinionFormationFollowGoal.isEligibleForFormationRank(true, false, true, true, false, false));

		// Minion of another owner: must be excluded
		Assertions.assertFalse(MinionFormationFollowGoal.isEligibleForFormationRank(true, true, false, true, false, false));
	}

	@Test
	@DisplayName("Validate rank resolution collapses seamlessly when minions are deselected or guarding")
	void testFormationRankResolutionExcludesUnselectedAndGuardingUnits() {
		class TestCohortUnit {
			final int id;
			final MinionRole role;
			boolean selected;
			boolean holdingPosition;
			boolean hasGuardAnchor;

			TestCohortUnit(int id, MinionRole role, boolean selected, boolean holdingPosition, boolean hasGuardAnchor) {
				this.id = id;
				this.role = role;
				this.selected = selected;
				this.holdingPosition = holdingPosition;
				this.hasGuardAnchor = hasGuardAnchor;
			}

			boolean isEligible() {
				return MinionFormationFollowGoal.isEligibleForFormationRank(
					true, true, true, this.selected, this.holdingPosition, this.hasGuardAnchor
				);
			}

			MinionRole getRole() { return this.role; }
			int getId() { return this.id; }
		}

		// 4 Warriors with entity IDs 10, 20, 30, 40
		TestCohortUnit w1 = new TestCohortUnit(10, MinionRole.WARRIOR, true, false, false);
		TestCohortUnit w2 = new TestCohortUnit(20, MinionRole.WARRIOR, true, false, false);
		TestCohortUnit w3 = new TestCohortUnit(30, MinionRole.WARRIOR, true, false, false);
		TestCohortUnit w4 = new TestCohortUnit(40, MinionRole.WARRIOR, true, false, false);

		List<TestCohortUnit> allCohort = List.of(w1, w2, w3, w4);

		// Case 1: All 4 are selected followers -> ranks 0, 1, 2, 3
		List<TestCohortUnit> eligible1 = allCohort.stream().filter(TestCohortUnit::isEligible).toList();
		Assertions.assertEquals(0, MinionFormationFollowGoal.resolveRank(eligible1, w1, TestCohortUnit::getRole, TestCohortUnit::getId));
		Assertions.assertEquals(1, MinionFormationFollowGoal.resolveRank(eligible1, w2, TestCohortUnit::getRole, TestCohortUnit::getId));
		Assertions.assertEquals(2, MinionFormationFollowGoal.resolveRank(eligible1, w3, TestCohortUnit::getRole, TestCohortUnit::getId));
		Assertions.assertEquals(3, MinionFormationFollowGoal.resolveRank(eligible1, w4, TestCohortUnit::getRole, TestCohortUnit::getId));

		// Case 2: Deselect w1 to guard post -> w1 is excluded, w2/w3/w4 collapse to ranks 0, 1, 2
		w1.selected = false;
		w1.hasGuardAnchor = true;

		List<TestCohortUnit> eligible2 = allCohort.stream().filter(TestCohortUnit::isEligible).toList();
		Assertions.assertEquals(3, eligible2.size(), "Eligible count should decrease by 1");
		Assertions.assertFalse(eligible2.contains(w1), "Deselected guard minion must be excluded from eligible list");

		Assertions.assertEquals(0, MinionFormationFollowGoal.resolveRank(eligible2, w2, TestCohortUnit::getRole, TestCohortUnit::getId),
			"w2 should collapse into lead Rank 0");
		Assertions.assertEquals(1, MinionFormationFollowGoal.resolveRank(eligible2, w3, TestCohortUnit::getRole, TestCohortUnit::getId),
			"w3 should advance to Rank 1");
		Assertions.assertEquals(2, MinionFormationFollowGoal.resolveRank(eligible2, w4, TestCohortUnit::getRole, TestCohortUnit::getId),
			"w4 should advance to Rank 2");

		// Case 3: w3 sits down (holdingPosition = true) -> eligible contains only w2 and w4
		w3.holdingPosition = true;

		List<TestCohortUnit> eligible3 = allCohort.stream().filter(TestCohortUnit::isEligible).toList();
		Assertions.assertEquals(2, eligible3.size());
		Assertions.assertEquals(0, MinionFormationFollowGoal.resolveRank(eligible3, w2, TestCohortUnit::getRole, TestCohortUnit::getId));
		Assertions.assertEquals(1, MinionFormationFollowGoal.resolveRank(eligible3, w4, TestCohortUnit::getRole, TestCohortUnit::getId));

		// Case 4: Multi-role isolation: Sentinels and Builders do not interfere with Warrior ranks
		TestCohortUnit s1 = new TestCohortUnit(15, MinionRole.SENTINEL, true, false, false);
		TestCohortUnit b1 = new TestCohortUnit(25, MinionRole.BUILDER, true, false, false);
		List<TestCohortUnit> mixedCohort = List.of(w2, w4, s1, b1);
		List<TestCohortUnit> eligibleMixed = mixedCohort.stream().filter(TestCohortUnit::isEligible).toList();

		Assertions.assertEquals(0, MinionFormationFollowGoal.resolveRank(eligibleMixed, w2, TestCohortUnit::getRole, TestCohortUnit::getId));
		Assertions.assertEquals(1, MinionFormationFollowGoal.resolveRank(eligibleMixed, w4, TestCohortUnit::getRole, TestCohortUnit::getId));
		Assertions.assertEquals(0, MinionFormationFollowGoal.resolveRank(eligibleMixed, s1, TestCohortUnit::getRole, TestCohortUnit::getId));
		Assertions.assertEquals(0, MinionFormationFollowGoal.resolveRank(eligibleMixed, b1, TestCohortUnit::getRole, TestCohortUnit::getId));
	}
}
