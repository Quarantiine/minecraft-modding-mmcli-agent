package com.example.entity;

import com.example.entity.ai.goal.MinionFormationFollowGoal;
import com.example.entity.ai.goal.SentinelHealAllyGoal;
import com.example.item.custom.CommandScepterItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Unit tests validating Sentinel Combat-Medic Healing AI, GUI cleanup, and extended command range:
 * 1. Healing parameters, thresholds, and timers (Aegis of Restoration)
 * 2. Wounded ally qualification and priority selection (lowest HP ratio)
 * 3. Range calculations (10-block radius)
 * 4. Extended selected minion teleport and command thresholds (64 blocks)
 * 5. Source code invariants for goal registration, attributes, and GUI cleanup
 */
public class SentinelHealGoalTest {

	@Test
	@DisplayName("Validate Sentinel heal constants, thresholds, and buffs")
	void testHealConstants() {
		Assertions.assertEquals(10.0D, SentinelHealAllyGoal.HEAL_RANGE, 0.01D,
				"Heal range should be 10 blocks");
		Assertions.assertEquals(100.0D, SentinelHealAllyGoal.HEAL_RANGE_SQ, 0.01D,
				"Heal range squared should be 100.0");
		Assertions.assertEquals(0.70F, SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD, 0.01F,
				"Ally health qualification threshold should be 70% max health");
		Assertions.assertEquals(0.40F, SentinelHealAllyGoal.SELF_HEALTH_THRESHOLD, 0.01F,
				"Self-heal fallback qualification threshold should be 40% max health");
		Assertions.assertEquals(6.0F, SentinelHealAllyGoal.HEAL_AMOUNT, 0.01F,
				"Heal amount should be 6.0 HP (3 hearts)");
		Assertions.assertEquals(20, SentinelHealAllyGoal.CHANNEL_TICKS_REQUIRED,
				"Channeling time should be 20 ticks (1.0 second)");
		Assertions.assertEquals(120, SentinelHealAllyGoal.HEAL_COOLDOWN_TICKS,
				"Cooldown between heals should be 120 ticks (6.0 seconds)");
		Assertions.assertEquals(100, SentinelHealAllyGoal.REGEN_DURATION_TICKS,
				"Regen duration should be 100 ticks (5.0 seconds)");
		Assertions.assertEquals(1, SentinelHealAllyGoal.REGEN_AMPLIFIER,
				"Regen amplifier should be 1 (Regeneration II)");
	}

	@Test
	@DisplayName("Validate ally health qualification logic and prioritization")
	void testAllyHealthPrioritization() {
		record MockMinion(String name, float health, float maxHealth, double distSq) {
			float healthRatio() {
				return health / maxHealth;
			}
			boolean isEligible(float threshold, double maxDistSq) {
				return distSq <= maxDistSq && health < maxHealth * threshold;
			}
		}

		List<MockMinion> allies = List.of(
				new MockMinion("HealthyWarrior", 40.0F, 40.0F, 16.0D),
				new MockMinion("ScratchedSentinel", 32.0F, 40.0F, 9.0D),  // 80% HP -> Not eligible
				new MockMinion("WoundedWarrior", 24.0F, 40.0F, 25.0D),    // 60% HP -> Eligible
				new MockMinion("DyingMiner", 10.0F, 40.0F, 36.0D),        // 25% HP -> Eligible, most critical
				new MockMinion("DistantWounded", 8.0F, 40.0F, 144.0D)     // 20% HP -> Out of range (12 blocks)
		);

		List<MockMinion> eligible = new ArrayList<>();
		for (MockMinion ally : allies) {
			if (ally.isEligible(SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD, SentinelHealAllyGoal.HEAL_RANGE_SQ)) {
				eligible.add(ally);
			}
		}

		// Only WoundedWarrior and DyingMiner should be eligible
		Assertions.assertEquals(2, eligible.size(), "Exactly 2 allies should be eligible");
		Assertions.assertTrue(eligible.stream().anyMatch(m -> m.name.equals("WoundedWarrior")));
		Assertions.assertTrue(eligible.stream().anyMatch(m -> m.name.equals("DyingMiner")));
		Assertions.assertFalse(eligible.stream().anyMatch(m -> m.name.equals("ScratchedSentinel")), "80% HP must not qualify");
		Assertions.assertFalse(eligible.stream().anyMatch(m -> m.name.equals("DistantWounded")), "12-block distant ally must be out of range");

		// Prioritize the lowest health ratio
		eligible.sort(Comparator.comparingDouble(MockMinion::healthRatio));
		Assertions.assertEquals("DyingMiner", eligible.get(0).name, "DyingMiner (25% HP) must be prioritized over WoundedWarrior (60% HP)");
	}

	@Test
	@DisplayName("Validate extended selected minion follow, teleport, and command ranges")
	void testExtendedCommandRanges() {
		Assertions.assertEquals(64.0D, MinionFormationFollowGoal.TELEPORT_DISTANCE_THRESHOLD,
				"Selected minion teleport distance threshold must be extended to 64 blocks");
		Assertions.assertEquals(4096.0D, MinionFormationFollowGoal.TELEPORT_DISTANCE_THRESHOLD_SQ,
				"Teleport threshold squared must be 4096.0 (64 * 64)");
		Assertions.assertEquals(256.0D, MinionFormationFollowGoal.COMBAT_LEASH_OVERRIDE_SQ,
				"Combat leash override squared must be 256.0 (16 blocks)");
		Assertions.assertEquals(2304.0D, MinionFormationFollowGoal.ASSAULT_LEASH_OVERRIDE_SQ,
				"Assault leash override squared must be 2304.0 (48 blocks)");
		Assertions.assertEquals(64.0D, CommandScepterItem.MINION_COMMAND_RADIUS,
				"Command Scepter raycast and broadcast radius must be 64 blocks");
	}

	@Test
	@DisplayName("Validate source invariants: SentinelHealAllyGoal registered and Equipment label removed")
	void testSourceInvariants() throws Exception {
		// 1. MinionEntity registers SentinelHealAllyGoal and uses 64-block follow range
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath), "MinionEntity.java must exist");
		String minionContent = Files.readString(minionEntityPath);
		Assertions.assertTrue(minionContent.contains("new SentinelHealAllyGoal(this)"),
				"MinionEntity must register SentinelHealAllyGoal");
		Assertions.assertTrue(minionContent.contains(".add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D)"),
				"MinionEntity must configure GENERIC_FOLLOW_RANGE to 64.0D");

		// 2. MinionScreen no longer renders "Equipment" category header
		Path screenPath = Path.of("src/client/java/com/example/client/gui/MinionScreen.java");
		Assertions.assertTrue(Files.exists(screenPath), "MinionScreen.java must exist");
		String screenContent = Files.readString(screenPath);
		Assertions.assertFalse(screenContent.contains("minion.equipment"),
				"MinionScreen must no longer draw minion.equipment label");
		Assertions.assertTrue(screenContent.contains("minion.inventory"),
				"MinionScreen must preserve minion.inventory category header");
	}
}
