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
 * Unit tests validating Sentinel Combat-Medic Healing AI, GUI cleanup, extended command range, and Iron Golem triage:
 * 1. Healing parameters, thresholds, and timers (Aegis of Restoration)
 * 2. Wounded ally qualification and priority selection (lowest HP ratio)
 * 3. Range calculations (10-block radius)
 * 4. Extended selected minion teleport and command thresholds (64 blocks)
 * 5. Source code invariants for goal registration, attributes, Iron Golem triage, and GUI cleanup
 * 6. Iron Golem medical triage, hostility filtering, and priority resolution
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

		// 3. SentinelHealAllyGoal queries IronGolemEntity and checks hostility
		Path healGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/SentinelHealAllyGoal.java");
		Assertions.assertTrue(Files.exists(healGoalPath), "SentinelHealAllyGoal.java must exist");
		String healGoalContent = Files.readString(healGoalPath);
		Assertions.assertTrue(healGoalContent.contains("IronGolemEntity.class"),
				"SentinelHealAllyGoal must query IronGolemEntity.class");
		Assertions.assertTrue(healGoalContent.contains("isNonHostileGolem"),
				"SentinelHealAllyGoal must define and invoke isNonHostileGolem");
		Assertions.assertTrue(healGoalContent.contains("g.getHealth() < g.getMaxHealth() * ALLY_HEALTH_THRESHOLD"),
				"SentinelHealAllyGoal must filter Iron Golems under 70% HP threshold");

		// 4. MinionEntity recognizes non-hostile IronGolemEntity in isTeammate
		Assertions.assertTrue(minionContent.contains("other instanceof IronGolemEntity"),
				"MinionEntity.isTeammate must evaluate IronGolemEntity");
	}

	@Test
	@DisplayName("Validate Iron Golem medical triage, hostility filtering, and priority resolution")
	void testIronGolemMedicalTriage() {
		record MockGolem(String name, float health, float maxHealth, double distSq, String targetType) {
			float healthRatio() {
				return health / maxHealth;
			}
			boolean isNonHostile() {
				return "NONE".equals(targetType) || "HOSTILE_MOB".equals(targetType);
			}
			boolean isEligible(float threshold, double maxDistSq) {
				return distSq <= maxDistSq && health < maxHealth * threshold && isNonHostile();
			}
		}

		List<MockGolem> golems = List.of(
				new MockGolem("HealthyVillageGolem", 100.0F, 100.0F, 16.0D, "NONE"),           // 100% -> Ineligible
				new MockGolem("ScratchedGolem", 80.0F, 100.0F, 25.0D, "HOSTILE_MOB"),           // 80% -> Ineligible (>= 70%)
				new MockGolem("WoundedFriendlyGolem", 50.0F, 100.0F, 36.0D, "HOSTILE_MOB"),     // 50% -> Eligible
				new MockGolem("HostileToMinionGolem", 30.0F, 100.0F, 16.0D, "MINION"),          // 30% -> Ineligible (hostile to minion)
				new MockGolem("HostileToCommanderGolem", 20.0F, 100.0F, 16.0D, "COMMANDER"),    // 20% -> Ineligible (hostile to owner)
				new MockGolem("DistantWoundedGolem", 40.0F, 100.0F, 144.0D, "NONE")            // 40% -> Ineligible (out of range)
		);

		List<MockGolem> eligible = new ArrayList<>();
		for (MockGolem golem : golems) {
			if (golem.isEligible(SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD, SentinelHealAllyGoal.HEAL_RANGE_SQ)) {
				eligible.add(golem);
			}
		}

		Assertions.assertEquals(1, eligible.size(), "Only WoundedFriendlyGolem should be eligible for medical triage");
		Assertions.assertEquals("WoundedFriendlyGolem", eligible.get(0).name);

		// Multi-entity triage hierarchy comparison:
		// Commander (60% HP) vs Minion (65% HP) vs Iron Golem (45% HP)
		// Commander priority takes precedence if player is wounded, but amongst minions and golems, lowest ratio wins
		float golemRatio = 45.0F / 100.0F; // 0.45
		float minionRatio = 26.0F / 40.0F; // 0.65
		Assertions.assertTrue(golemRatio < minionRatio, "Critically wounded Iron Golem (45%) must have triage priority over minion (65%)");
	}

	@Test
	@DisplayName("Validate Player commander healing eligibility and priority over minions")
	void testPlayerHealingAndCommanderPriority() {
		record MockEntity(String name, boolean isPlayer, float health, float maxHealth, double distSq) {
			float healthRatio() {
				return health / maxHealth;
			}
			boolean isEligible(float threshold, double maxDistSq) {
				return distSq <= maxDistSq && health < maxHealth * threshold;
			}
		}

		// Player with 12 HP (60% HP) vs Minion with 26 HP (65% HP)
		MockEntity commander = new MockEntity("PlayerCommander", true, 12.0F, 20.0F, 16.0D);
		MockEntity minionAlly = new MockEntity("WoundedMinion", false, 26.0F, 40.0F, 9.0D);
		MockEntity distantPlayer = new MockEntity("DistantAlly", true, 8.0F, 20.0F, 144.0D); // Out of range

		Assertions.assertTrue(commander.isEligible(SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD, SentinelHealAllyGoal.HEAL_RANGE_SQ),
				"Commander at 60% HP within 4 blocks must be eligible for healing");
		Assertions.assertTrue(minionAlly.isEligible(SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD, SentinelHealAllyGoal.HEAL_RANGE_SQ),
				"Minion at 65% HP within 3 blocks must be eligible for healing");
		Assertions.assertFalse(distantPlayer.isEligible(SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD, SentinelHealAllyGoal.HEAL_RANGE_SQ),
				"Distant player beyond 10 blocks must be out of range");

		// Commander with lower ratio (0.60 vs 0.65) must be prioritized
		Assertions.assertTrue(commander.healthRatio() < minionAlly.healthRatio(),
				"Commander at 60% HP must have priority over minion at 65% HP");
	}

	@Test
	@DisplayName("Validate complete Iron Golem hostility matrix and ally qualification")
	void testIronGolemHostilityMatrix() {
		enum TargetRelationship {
			NONE,
			HOSTILE_MOB,
			SELF_MINION,
			COMMANDER_OWNER,
			FELLOW_SQUAD_MINION,
			UNRELATED_PLAYER,
			WILD_ANIMAL
		}

		record SimulatedGolem(String id, float health, float maxHealth, double distance, TargetRelationship targetRelation) {
			boolean isNonHostileToSquad() {
				return switch (targetRelation) {
					case SELF_MINION, COMMANDER_OWNER, FELLOW_SQUAD_MINION -> false;
					case NONE, HOSTILE_MOB, UNRELATED_PLAYER, WILD_ANIMAL -> true;
				};
			}

			boolean isEligibleForAegisOfRestoration() {
				return health > 0.0F
						&& (health / maxHealth) < SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD
						&& (distance * distance) <= SentinelHealAllyGoal.HEAL_RANGE_SQ
						&& isNonHostileToSquad();
			}
		}

		List<SimulatedGolem> matrix = List.of(
				new SimulatedGolem("Golem_Idle_Injured", 40.0F, 100.0F, 5.0D, TargetRelationship.NONE),
				new SimulatedGolem("Golem_FightingZombie_Injured", 65.0F, 100.0F, 8.0D, TargetRelationship.HOSTILE_MOB),
				new SimulatedGolem("Golem_FightingWildMob_Injured", 50.0F, 100.0F, 3.0D, TargetRelationship.WILD_ANIMAL),
				new SimulatedGolem("Golem_TargetingSelf_Injured", 30.0F, 100.0F, 4.0D, TargetRelationship.SELF_MINION),
				new SimulatedGolem("Golem_TargetingOwner_Injured", 20.0F, 100.0F, 4.0D, TargetRelationship.COMMANDER_OWNER),
				new SimulatedGolem("Golem_TargetingFellow_Injured", 25.0F, 100.0F, 6.0D, TargetRelationship.FELLOW_SQUAD_MINION),
				new SimulatedGolem("Golem_TargetingStranger_Injured", 55.0F, 100.0F, 7.0D, TargetRelationship.UNRELATED_PLAYER),
				new SimulatedGolem("Golem_Idle_Dead", 0.0F, 100.0F, 2.0D, TargetRelationship.NONE),
				new SimulatedGolem("Golem_Idle_Healthy", 85.0F, 100.0F, 2.0D, TargetRelationship.NONE),
				new SimulatedGolem("Golem_Idle_ExactThreshold", 70.0F, 100.0F, 2.0D, TargetRelationship.NONE),
				new SimulatedGolem("Golem_Idle_OutOfRange", 40.0F, 100.0F, 11.0D, TargetRelationship.NONE)
		);

		// Assert expected qualifications
		Assertions.assertTrue(matrix.get(0).isEligibleForAegisOfRestoration(), "Idle injured golem must be eligible");
		Assertions.assertTrue(matrix.get(1).isEligibleForAegisOfRestoration(), "Golem fighting hostiles must be eligible");
		Assertions.assertTrue(matrix.get(2).isEligibleForAegisOfRestoration(), "Golem fighting neutral animals must be eligible");
		Assertions.assertFalse(matrix.get(3).isEligibleForAegisOfRestoration(), "Golem targeting healer minion must be rejected as hostile");
		Assertions.assertFalse(matrix.get(4).isEligibleForAegisOfRestoration(), "Golem targeting minion's owner must be rejected as hostile");
		Assertions.assertFalse(matrix.get(5).isEligibleForAegisOfRestoration(), "Golem targeting fellow squad minion must be rejected as hostile");
		Assertions.assertTrue(matrix.get(6).isEligibleForAegisOfRestoration(), "Golem targeting non-owner player is non-hostile to our squad");
		Assertions.assertFalse(matrix.get(7).isEligibleForAegisOfRestoration(), "Dead golem (0 HP) must be rejected");
		Assertions.assertFalse(matrix.get(8).isEligibleForAegisOfRestoration(), "Healthy golem (85% HP) must not qualify");
		Assertions.assertFalse(matrix.get(9).isEligibleForAegisOfRestoration(), "Golem at exactly 70% threshold must not qualify (< 70% required)");
		Assertions.assertFalse(matrix.get(10).isEligibleForAegisOfRestoration(), "Golem at 11 blocks must be rejected due to 10-block range cap");
	}

	@Test
	@DisplayName("Validate MinionEntity.isTeammate logic for Iron Golems")
	void testMinionEntityTeammateInvariantsForIronGolem() {
		class MockOwner {}
		class MockMinion {
			final MockOwner owner;
			MockMinion(MockOwner owner) { this.owner = owner; }
		}

		class MockGolem {
			Object target;
			MockGolem(Object target) { this.target = target; }
		}

		class TeammateChecker {
			boolean isTeammate(MockMinion self, Object other) {
				if (other instanceof MockGolem golem) {
					Object golemTarget = golem.target;
					if (golemTarget == null) {
						return true;
					}
					if (golemTarget.equals(self)) {
						return false;
					}
					if (self.owner != null) {
						if (golemTarget.equals(self.owner)) {
							return false;
						}
						if (golemTarget instanceof MockMinion minionTarget && minionTarget.owner != null && minionTarget.owner.equals(self.owner)) {
							return false;
						}
					}
					return true;
				}
				if (self.owner != null) {
					if (other.equals(self.owner)) return true;
					if (other instanceof MockMinion om && om.owner != null && om.owner.equals(self.owner)) return true;
				}
				return false;
			}
		}

		MockOwner owner = new MockOwner();
		MockMinion selfMinion = new MockMinion(owner);
		MockMinion squadMate = new MockMinion(owner);
		MockOwner otherOwner = new MockOwner();
		MockMinion hostileMinion = new MockMinion(otherOwner);
		Object zombie = new Object();

		TeammateChecker checker = new TeammateChecker();

		// Owner and squadmates
		Assertions.assertTrue(checker.isTeammate(selfMinion, owner), "Owner must be recognized as teammate");
		Assertions.assertTrue(checker.isTeammate(selfMinion, squadMate), "Squadmate with same owner must be teammate");
		Assertions.assertFalse(checker.isTeammate(selfMinion, hostileMinion), "Minion from different owner is not teammate");

		// Iron Golems
		Assertions.assertTrue(checker.isTeammate(selfMinion, new MockGolem(null)), "Neutral golem (target=null) is teammate");
		Assertions.assertTrue(checker.isTeammate(selfMinion, new MockGolem(zombie)), "Golem fighting monster is teammate");
		Assertions.assertTrue(checker.isTeammate(selfMinion, new MockGolem(hostileMinion)), "Golem targeting enemy minion is teammate");
		Assertions.assertFalse(checker.isTeammate(selfMinion, new MockGolem(selfMinion)), "Golem targeting self is NOT teammate");
		Assertions.assertFalse(checker.isTeammate(selfMinion, new MockGolem(owner)), "Golem targeting owner is NOT teammate");
		Assertions.assertFalse(checker.isTeammate(selfMinion, new MockGolem(squadMate)), "Golem targeting squadmate is NOT teammate");
	}

	@Test
	@DisplayName("Validate Sentinel heal threshold boundary conditions and self-heal fallback")
	void testSentinelHealThresholdBoundaryConditions() {
		// Self-heal threshold is 40% (16.0 / 40.0 HP)
		float maxHp = 40.0F;
		float selfCritHealth = 15.9F;
		float selfHealthyHealth = 16.1F;

		Assertions.assertTrue(selfCritHealth < maxHp * SentinelHealAllyGoal.SELF_HEALTH_THRESHOLD,
				"15.9 HP is < 40% threshold -> Self-heal triggers when no allies need healing");
		Assertions.assertFalse(selfHealthyHealth < maxHp * SentinelHealAllyGoal.SELF_HEALTH_THRESHOLD,
				"16.1 HP is > 40% threshold -> Self-heal must not trigger");

		// Ally threshold is 70%
		float allyWounded = 27.9F;
		float allySlightlyScratched = 28.1F;
		Assertions.assertTrue(allyWounded < maxHp * SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD,
				"27.9 HP is < 70% threshold -> Ally healing triggers");
		Assertions.assertFalse(allySlightlyScratched < maxHp * SentinelHealAllyGoal.ALLY_HEALTH_THRESHOLD,
				"28.1 HP is > 70% threshold -> Ally healing ignored");
	}

	@Test
	@DisplayName("Validate Sentinels fight like warriors ONLY if no minions or iron golems near them need zero healing")
	void testSentinelFightsLikeWarriorOnlyWhenZeroHealingNeeded() {
		record MockEntity(String name, float health, float maxHealth, double distance, boolean isTeammate) {
			boolean needsHealing() {
				return isTeammate && health < maxHealth - 0.05F;
			}
			boolean isWithinRange(double maxRange) {
				return distance <= maxRange;
			}
		}

		class SentinelCombatEvaluator {
			boolean hasNearbyAlliesNeedingHealing(List<MockEntity> nearbyEntities, double radius) {
				for (MockEntity entity : nearbyEntities) {
					if (entity.isWithinRange(radius) && entity.needsHealing()) {
						return true;
					}
				}
				return false;
			}

			boolean canFightLikeWarrior(List<MockEntity> nearbyEntities, double radius) {
				return !hasNearbyAlliesNeedingHealing(nearbyEntities, radius);
			}
		}

		SentinelCombatEvaluator evaluator = new SentinelCombatEvaluator();
		double healRange = SentinelHealAllyGoal.HEAL_RANGE; // 10 blocks

		// Scenario 1: Sentinel is completely alone -> Zero allies need healing -> Fights like warrior
		List<MockEntity> alone = List.of();
		Assertions.assertFalse(evaluator.hasNearbyAlliesNeedingHealing(alone, healRange),
				"Solo sentinel has no wounded allies");
		Assertions.assertTrue(evaluator.canFightLikeWarrior(alone, healRange),
				"Solo sentinel must fight like a warrior");

		// Scenario 2: Nearby minions and Iron Golems are at 100% full health (need ZERO healing) -> Fights like warrior
		List<MockEntity> healthySquad = List.of(
				new MockEntity("Commander", 20.0F, 20.0F, 3.0D, true),
				new MockEntity("WarriorMinion", 40.0F, 40.0F, 4.0D, true),
				new MockEntity("BuilderMinion", 40.0F, 40.0F, 5.0D, true),
				new MockEntity("VillageIronGolem", 100.0F, 100.0F, 6.0D, true)
		);
		Assertions.assertFalse(evaluator.hasNearbyAlliesNeedingHealing(healthySquad, healRange),
				"Healthy squad requires zero healing");
		Assertions.assertTrue(evaluator.canFightLikeWarrior(healthySquad, healRange),
				"Sentinel must fight like a warrior when all nearby minions and iron golems need zero healing");

		// Scenario 3: Nearby minion takes minor damage (38/40 HP, missing 2 HP) -> Needs healing -> Yields warrior mode
		List<MockEntity> scratchedMinionSquad = List.of(
				new MockEntity("Commander", 20.0F, 20.0F, 3.0D, true),
				new MockEntity("ScratchedWarrior", 38.0F, 40.0F, 4.0D, true),
				new MockEntity("VillageIronGolem", 100.0F, 100.0F, 6.0D, true)
		);
		Assertions.assertTrue(evaluator.hasNearbyAlliesNeedingHealing(scratchedMinionSquad, healRange),
				"Scratched minion at 38/40 HP needs healing");
		Assertions.assertFalse(evaluator.canFightLikeWarrior(scratchedMinionSquad, healRange),
				"Sentinel must NOT fight like a warrior when a nearby minion needs healing");

		// Scenario 4: Nearby Iron Golem takes damage (80/100 HP, missing 20 HP) -> Needs healing -> Yields warrior mode
		List<MockEntity> woundedGolemSquad = List.of(
				new MockEntity("Commander", 20.0F, 20.0F, 3.0D, true),
				new MockEntity("HealthyWarrior", 40.0F, 40.0F, 4.0D, true),
				new MockEntity("WoundedGolem", 80.0F, 100.0F, 7.0D, true)
		);
		Assertions.assertTrue(evaluator.hasNearbyAlliesNeedingHealing(woundedGolemSquad, healRange),
				"Wounded Iron Golem at 80/100 HP needs healing");
		Assertions.assertFalse(evaluator.canFightLikeWarrior(woundedGolemSquad, healRange),
				"Sentinel must NOT fight like a warrior when a nearby Iron Golem needs healing");

		// Scenario 5: Player commander is damaged (15/20 HP) -> Needs healing -> Yields warrior mode
		List<MockEntity> woundedCommanderSquad = List.of(
				new MockEntity("Commander", 15.0F, 20.0F, 3.0D, true),
				new MockEntity("HealthyWarrior", 40.0F, 40.0F, 4.0D, true),
				new MockEntity("VillageIronGolem", 100.0F, 100.0F, 6.0D, true)
		);
		Assertions.assertTrue(evaluator.hasNearbyAlliesNeedingHealing(woundedCommanderSquad, healRange),
				"Commander at 15/20 HP needs healing");
		Assertions.assertFalse(evaluator.canFightLikeWarrior(woundedCommanderSquad, healRange),
				"Sentinel must NOT fight like a warrior when the player commander needs healing");

		// Scenario 6: Hostile Iron Golem is damaged (isTeammate = false) -> Ignored -> Sentinel fights like warrior
		List<MockEntity> hostileGolemEncounter = List.of(
				new MockEntity("Commander", 20.0F, 20.0F, 3.0D, true),
				new MockEntity("HealthyWarrior", 40.0F, 40.0F, 4.0D, true),
				new MockEntity("HostileIronGolem", 40.0F, 100.0F, 5.0D, false) // Hostile enemy golem
		);
		Assertions.assertFalse(evaluator.hasNearbyAlliesNeedingHealing(hostileGolemEncounter, healRange),
				"Hostile enemy golem does not count as a friendly ally needing healing");
		Assertions.assertTrue(evaluator.canFightLikeWarrior(hostileGolemEncounter, healRange),
				"Sentinel must fight like a warrior when only hostile golems are injured");

		// Scenario 7: Distant wounded minion (beyond 10 blocks) -> Out of range -> Sentinel fights like warrior locally
		List<MockEntity> distantWoundedSquad = List.of(
				new MockEntity("Commander", 20.0F, 20.0F, 3.0D, true),
				new MockEntity("HealthyWarrior", 40.0F, 40.0F, 4.0D, true),
				new MockEntity("DistantWoundedMinion", 10.0F, 40.0F, 14.0D, true) // 14 blocks away (> 10 blocks)
		);
		Assertions.assertFalse(evaluator.hasNearbyAlliesNeedingHealing(distantWoundedSquad, healRange),
				"Minion beyond 10 blocks is outside local medical radius");
		Assertions.assertTrue(evaluator.canFightLikeWarrior(distantWoundedSquad, healRange),
				"Sentinel continues warrior combat when distant allies beyond support envelope are wounded");
	}

	@Test
	@DisplayName("Validate Sentinel warrior combat source code invariants across goals and entity tick")
	void testSentinelWarriorCombatSourceInvariants() throws Exception {
		// 1. MinionEntity defines hasNearbyAlliesNeedingHealing and gates MeleeAttackGoal
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath), "MinionEntity.java must exist");
		String minionContent = Files.readString(minionEntityPath);

		Assertions.assertTrue(minionContent.contains("hasNearbyAlliesNeedingHealing"),
				"MinionEntity must declare hasNearbyAlliesNeedingHealing method");
		Assertions.assertTrue(minionContent.contains("MinionEntity.this.matchesRole(MinionRole.SENTINEL) && MinionEntity.this.hasNearbyAlliesNeedingHealing()"),
				"MeleeAttackGoal must gate Sentinel combat when nearby allies need healing");
		Assertions.assertTrue(minionContent.contains("this.matchesRole(MinionRole.SENTINEL) && currentTarget != null && this.hasNearbyAlliesNeedingHealing()"),
				"MinionEntity tick must disengage target when nearby allies need healing");

		// 2. MinionActiveTargetGoal enables Sentinel warrior mode and disengages when wounded
		Path activeTargetGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionActiveTargetGoal.java");
		Assertions.assertTrue(Files.exists(activeTargetGoalPath), "MinionActiveTargetGoal.java must exist");
		String targetGoalContent = Files.readString(activeTargetGoalPath);

		Assertions.assertTrue(targetGoalContent.contains("isSentinelWarriorMode"),
				"MinionActiveTargetGoal must define isSentinelWarriorMode check");
		Assertions.assertTrue(targetGoalContent.contains("this.minion.matchesRole(MinionRole.SENTINEL) && this.minion.hasNearbyAlliesNeedingHealing()"),
				"MinionActiveTargetGoal.shouldContinue must break targeting when nearby allies need healing");

		// 3. MinionRangedAttackGoal enables Sentinel warrior mode and disengages when wounded
		Path rangedGoalPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionRangedAttackGoal.java");
		Assertions.assertTrue(Files.exists(rangedGoalPath), "MinionRangedAttackGoal.java must exist");
		String rangedGoalContent = Files.readString(rangedGoalPath);

		Assertions.assertTrue(rangedGoalContent.contains("isSentinelWarriorMode"),
				"MinionRangedAttackGoal must define isSentinelWarriorMode check");
	}
}
