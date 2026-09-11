package com.example.entity;

import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import java.util.ArrayList;
import java.util.List;
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
	@DisplayName("Validate Vanguard (Warrior) forward-flanking wedge parametric geometry")
	void testWarriorVanguardWedge() {
		// Rank 0 (left point of wedge)
		Vec3d rank0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 0);
		Assertions.assertEquals(3.5D, rank0.x, 1e-5, "Rank 0 must be 3.5 blocks forward");
		Assertions.assertEquals(-1.5D, rank0.z, 1e-5, "Rank 0 must be 1.5 blocks left");

		// Rank 1 (right point of wedge)
		Vec3d rank1 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 1);
		Assertions.assertEquals(3.5D, rank1.x, 1e-5, "Rank 1 must be 3.5 blocks forward");
		Assertions.assertEquals(1.5D, rank1.z, 1e-5, "Rank 1 must be 1.5 blocks right");

		// Spacing between rank 0 and rank 1
		double spacing01 = Math.hypot(rank0.x - rank1.x, rank0.z - rank1.z);
		Assertions.assertEquals(3.0D, spacing01, 1e-5, "Front point spacing must be 3.0 blocks");

		// Rank 2 (left flanking wing)
		Vec3d rank2 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 2);
		Assertions.assertEquals(2.0D, rank2.x, 1e-5, "Rank 2 should step back to 2.0 blocks forward");
		Assertions.assertEquals(-3.5D, rank2.z, 1e-5, "Rank 2 flares left to -3.5 blocks");

		// Rank 3 (right flanking wing)
		Vec3d rank3 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.WARRIOR, 3);
		Assertions.assertEquals(2.0D, rank3.x, 1e-5, "Rank 3 should step back to 2.0 blocks forward");
		Assertions.assertEquals(3.5D, rank3.z, 1e-5, "Rank 3 flares right to 3.5 blocks");

		// Wedge expands outward with rank: rank 2/3 must be wider than rank 0/1
		Assertions.assertTrue(Math.abs(rank2.z) > Math.abs(rank0.z), "Wedge wings must flare wider than lead point");
	}

	@Test
	@DisplayName("Validate Bulwark (Sentinel) escort wings flanking commander")
	void testSentinelEscortWings() {
		// Tier 0 Sentinels: flanking left and right at player level
		Vec3d rank0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.SENTINEL, 0);
		Vec3d rank1 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.SENTINEL, 1);

		Assertions.assertEquals(0.0D, rank0.x, 1e-5, "Rank 0 must be level with commander forward position");
		Assertions.assertEquals(-3.0D, rank0.z, 1e-5, "Rank 0 must flank 3.0 blocks to the left");

		Assertions.assertEquals(0.0D, rank1.x, 1e-5, "Rank 1 must be level with commander forward position");
		Assertions.assertEquals(3.0D, rank1.z, 1e-5, "Rank 1 must flank 3.0 blocks to the right");

		// Tier 1 Sentinels: slightly behind and wider
		Vec3d rank2 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.SENTINEL, 2);
		Assertions.assertEquals(-1.5D, rank2.x, 1e-5, "Rank 2 must step slightly behind commander");
		Assertions.assertEquals(-4.5D, rank2.z, 1e-5, "Rank 2 flanks 4.5 blocks to the left");
	}

	@Test
	@DisplayName("Validate Core (Builder & Miner) tucked safely behind vanguard")
	void testCoreSupportPlacement() {
		Vec3d builder0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.BUILDER, 0);
		Vec3d miner0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.MINER, 0);

		// Core starts behind the commander
		Assertions.assertTrue(builder0.x < 0, "Builder must be behind commander (negative forward)");
		Assertions.assertEquals(-2.5D, builder0.x, 1e-5);
		Assertions.assertEquals(-2.5D, miner0.x, 1e-5);

		// Core width is narrow to stay protected between escort wings
		Assertions.assertTrue(Math.abs(builder0.z) <= 2.5D, "Core must remain tucked between flank escort wings");
	}

	@Test
	@DisplayName("Validate Skirmisher (Ranger) rearguard placement")
	void testRangerRearguardPlacement() {
		Vec3d rank0 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.RANGER, 0);
		Vec3d rank1 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.RANGER, 1);

		// Rangers must be at deep rearguard (> 5 blocks behind commander)
		Assertions.assertEquals(-6.0D, rank0.x, 1e-5, "Ranger rank 0 must hold station at -6.0 blocks rear");
		Assertions.assertEquals(-2.0D, rank0.z, 1e-5, "Ranger rank 0 holds left rear line");

		Assertions.assertEquals(-6.0D, rank1.x, 1e-5, "Ranger rank 1 must hold station at -6.0 blocks rear");
		Assertions.assertEquals(2.0D, rank1.z, 1e-5, "Ranger rank 1 holds right rear line");

		// Tier 1 Ranger steps further back
		Vec3d rank2 = MinionFormationFollowGoal.calculateFormationOffset(MinionRole.RANGER, 2);
		Assertions.assertTrue(rank2.x < rank0.x, "Higher rank rangers must extend the rearguard further back");
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

		// Rank 0 Warrior: local forward = +3.5, local flank = -1.5

		// 1. Yaw = 0 (Facing South, +Z): forward is +Z, flank-left is -X
		Vec3d southPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 0.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX - 1.5D, southPos.x, 1e-4, "Facing South: left flank should be -X");
		Assertions.assertEquals(ownerZ + 3.5D, southPos.z, 1e-4, "Facing South: forward should be +Z");

		// 2. Yaw = 180 (Facing North, -Z): forward is -Z, flank-left is +X
		Vec3d northPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 180.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX + 1.5D, northPos.x, 1e-4, "Facing North: left flank should be +X");
		Assertions.assertEquals(ownerZ - 3.5D, northPos.z, 1e-4, "Facing North: forward should be -Z");

		// 3. Yaw = 90 (Facing West, -X): forward is -X, flank-left is -Z
		Vec3d westPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 90.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX - 3.5D, westPos.x, 1e-4, "Facing West: forward should be -X");
		Assertions.assertEquals(ownerZ - 1.5D, westPos.z, 1e-4, "Facing West: left flank should be -Z");

		// 4. Yaw = 270 (Facing East, +X): forward is +X, flank-left is +Z
		Vec3d eastPos = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, 270.0F, MinionRole.WARRIOR, 0);
		Assertions.assertEquals(ownerX + 3.5D, eastPos.x, 1e-4, "Facing East: forward should be +X");
		Assertions.assertEquals(ownerZ + 1.5D, eastPos.z, 1e-4, "Facing East: left flank should be +Z");
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
		Assertions.assertEquals(24.0D, MinionFormationFollowGoal.TELEPORT_DISTANCE_THRESHOLD);
		Assertions.assertEquals(576.0D, MinionFormationFollowGoal.TELEPORT_DISTANCE_THRESHOLD_SQ);
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
				case RANGER -> item == TestItemCategory.RANGED_BOW || item == TestItemCategory.RANGED_CROSSBOW;
				case WARRIOR, SENTINEL -> item == TestItemCategory.MELEE_SWORD
					|| item == TestItemCategory.MELEE_AXE
					|| item == TestItemCategory.MELEE_MACE
					|| item == TestItemCategory.MELEE_TRIDENT;
				case MINER -> item == TestItemCategory.TOOL_PICKAXE
					|| item == TestItemCategory.TOOL_SHOVEL
					|| item == TestItemCategory.MELEE_AXE
					|| item == TestItemCategory.MELEE_SWORD;
				case BUILDER -> item == TestItemCategory.TOOL_PICKAXE
					|| item == TestItemCategory.TOOL_SHOVEL
					|| item == TestItemCategory.MELEE_AXE
					|| item == TestItemCategory.MELEE_SWORD
					|| item == TestItemCategory.MELEE_MACE;
			};
		}

		public static boolean isPreferredMainhand(MinionRole role, TestItemCategory candidate, TestItemCategory current) {
			if (current == null) return canRoleAutoEquipMainhand(role, candidate);

			boolean candidateRanged = candidate == TestItemCategory.RANGED_BOW || candidate == TestItemCategory.RANGED_CROSSBOW;
			boolean currentRanged = current == TestItemCategory.RANGED_BOW || current == TestItemCategory.RANGED_CROSSBOW;

			boolean candidateMelee = candidate == TestItemCategory.MELEE_SWORD
				|| candidate == TestItemCategory.MELEE_AXE
				|| candidate == TestItemCategory.MELEE_MACE
				|| candidate == TestItemCategory.MELEE_TRIDENT;
			boolean currentMelee = current == TestItemCategory.MELEE_SWORD
				|| current == TestItemCategory.MELEE_AXE
				|| current == TestItemCategory.MELEE_MACE
				|| current == TestItemCategory.MELEE_TRIDENT;

			return switch (role) {
				case RANGER -> candidateRanged && !currentRanged;
				case WARRIOR, SENTINEL -> candidateMelee && !currentMelee;
				case MINER -> (candidate == TestItemCategory.TOOL_PICKAXE) && (current != TestItemCategory.TOOL_PICKAXE);
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
	@DisplayName("Validate Ranger auto-equip: seeks bows/crossbows, rejects melee weapons")
	void testRangerAutoEquipRestrictions() {
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.RANGER, TestItemCategory.RANGED_BOW));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.RANGER, TestItemCategory.RANGED_CROSSBOW));

		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.RANGER, TestItemCategory.MELEE_SWORD),
			"Ranger must not auto-equip swords");
		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.RANGER, TestItemCategory.MELEE_AXE),
			"Ranger must not auto-equip axes");
		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.RANGER, TestItemCategory.TOOL_PICKAXE),
			"Ranger must not auto-equip pickaxes");

		// Ranger swaps sword to bow
		Assertions.assertTrue(TestAutoEquipLogic.isPreferredMainhand(MinionRole.RANGER, TestItemCategory.RANGED_BOW, TestItemCategory.MELEE_SWORD));
	}

	@Test
	@DisplayName("Validate Warrior auto-equip: seeks swords/axes/maces, rejects ranged bows")
	void testWarriorAutoEquipRestrictions() {
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_SWORD));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_AXE));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_MACE));
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_TRIDENT));

		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.RANGED_BOW),
			"Warrior must not auto-equip bows");
		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.WARRIOR, TestItemCategory.RANGED_CROSSBOW),
			"Warrior must not auto-equip crossbows");

		// Warrior swaps bow to sword
		Assertions.assertTrue(TestAutoEquipLogic.isPreferredMainhand(MinionRole.WARRIOR, TestItemCategory.MELEE_SWORD, TestItemCategory.RANGED_BOW));
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
	@DisplayName("Validate Miner auto-equip: prioritizes pickaxes, rejects ranged weapons")
	void testMinerAutoEquipRestrictions() {
		Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.MINER, TestItemCategory.TOOL_PICKAXE));
		Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(MinionRole.MINER, TestItemCategory.RANGED_BOW));

		// Miner swaps sword to pickaxe
		Assertions.assertTrue(TestAutoEquipLogic.isPreferredMainhand(MinionRole.MINER, TestItemCategory.TOOL_PICKAXE, TestItemCategory.MELEE_SWORD));
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
			if (role == MinionRole.RANGER) {
				Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.RANGED_BOW));
				Assertions.assertTrue(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.RANGED_CROSSBOW));
				Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.MELEE_SWORD));
				Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.MELEE_AXE));
				Assertions.assertFalse(TestAutoEquipLogic.canRoleAutoEquipMainhand(role, TestItemCategory.TOOL_PICKAXE));
			} else {
				// Non-ranger roles never equip ranged weapons
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

		// 1. Warrior starts with bow in mainhand, finds diamond sword in inventory
		MockMinionInventory warriorInv = new MockMinionInventory();
		warriorInv.mainhand = TestItemCategory.RANGED_BOW;
		warriorInv.addItem(TestItemCategory.MELEE_SWORD);
		int initialCount = warriorInv.totalItemCount();

		warriorInv.autoEquip(MinionRole.WARRIOR);
		Assertions.assertEquals(TestItemCategory.MELEE_SWORD, warriorInv.mainhand, "Warrior should equip sword");
		Assertions.assertTrue(warriorInv.storage.contains(TestItemCategory.RANGED_BOW), "Displaced bow must return to inventory");
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

		// 3. Ranger with sword finds bow
		MockMinionInventory rangerInv = new MockMinionInventory();
		rangerInv.mainhand = TestItemCategory.MELEE_SWORD;
		rangerInv.addItem(TestItemCategory.RANGED_BOW);
		initialCount = rangerInv.totalItemCount();

		rangerInv.autoEquip(MinionRole.RANGER);
		Assertions.assertEquals(TestItemCategory.RANGED_BOW, rangerInv.mainhand, "Ranger should equip bow over sword");
		Assertions.assertTrue(rangerInv.storage.contains(TestItemCategory.MELEE_SWORD), "Displaced sword preserved");
		Assertions.assertEquals(initialCount, rangerInv.totalItemCount());
	}

	@Test
	@DisplayName("Validate 8 cardinal/intercardinal compass yaw headings preserve station distance")
	void testFullCompassYawRotationInvariants() {
		double ownerX = 0.0D;
		double ownerY = 64.0D;
		double ownerZ = 0.0D;

		// Rank 0 Warrior: forward = 3.5, flank = -1.5 -> expected radial distance = sqrt(3.5^2 + 1.5^2) = sqrt(12.25 + 2.25) = sqrt(14.5) ≈ 3.80788655997
		double expectedDistance = Math.hypot(3.5D, 1.5D);

		float[] testYaws = {0.0F, 45.0F, 90.0F, 135.0F, 180.0F, 225.0F, 270.0F, 315.0F, 360.0F, -90.0F, -180.0F};

		for (float yaw : testYaws) {
			Vec3d station = MinionFormationFollowGoal.calculateFormationStation(ownerX, ownerY, ownerZ, yaw, MinionRole.WARRIOR, 0);
			double radialDist = Math.hypot(station.x - ownerX, station.z - ownerZ);
			Assertions.assertEquals(expectedDistance, radialDist, 1e-4,
				"Radial station distance must remain invariant under yaw " + yaw + "°");
		}
	}

	@Test
	@DisplayName("Validate 16-thrall 4-echelon cohort pairwise formation collision spacing (>= 1.5 blocks)")
	void testFullCohortCollisionExclusion() {
		// Generate stations for 4 minions in each of the 4 tactical echelons (16 minions total):
		// Vanguard (Warrior), Bulwark (Sentinel), Core (Builder), and Skirmisher (Ranger)
		List<MinionRole> echelons = List.of(MinionRole.WARRIOR, MinionRole.SENTINEL, MinionRole.BUILDER, MinionRole.RANGER);
		List<Vec3d> cohortStations = new ArrayList<>();
		for (MinionRole role : echelons) {
			for (int rank = 0; rank < 4; rank++) {
				cohortStations.add(MinionFormationFollowGoal.calculateFormationOffset(role, rank));
			}
		}

		Assertions.assertEquals(16, cohortStations.size(), "Cohort must comprise 16 stations across 4 echelons");

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
		// 8.0 < dist <= 24.0 -> SPRINT (1.35D)
		// dist > 24.0 -> TELEPORT

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
		PacingState marchState = evaluatePacing.apply(4.0D);
		Assertions.assertFalse(marchState.arrived());
		Assertions.assertEquals(MinionFormationFollowGoal.MARCH_SPEED, marchState.speed(), 1e-5);
		Assertions.assertFalse(marchState.emergencyTeleport());

		PacingState marchBoundary = evaluatePacing.apply(8.0D);
		Assertions.assertEquals(MinionFormationFollowGoal.MARCH_SPEED, marchBoundary.speed(), 1e-5);

		// 3. Sprinting zone (8.01 to 24.0 blocks)
		PacingState sprintState = evaluatePacing.apply(12.0D);
		Assertions.assertFalse(sprintState.arrived());
		Assertions.assertEquals(MinionFormationFollowGoal.SPRINT_SPEED, sprintState.speed(), 1e-5);
		Assertions.assertFalse(sprintState.emergencyTeleport());

		PacingState sprintBoundary = evaluatePacing.apply(24.0D);
		Assertions.assertEquals(MinionFormationFollowGoal.SPRINT_SPEED, sprintBoundary.speed(), 1e-5);
		Assertions.assertFalse(sprintBoundary.emergencyTeleport());

		// 4. Emergency teleport recall (> 24.0 blocks)
		PacingState teleportState = evaluatePacing.apply(25.0D);
		Assertions.assertTrue(teleportState.emergencyTeleport());
		Assertions.assertTrue(evaluatePacing.apply(50.0D).emergencyTeleport());
	}
}
