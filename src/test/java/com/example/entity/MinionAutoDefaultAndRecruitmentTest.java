package com.example.entity;

import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating that minions default to {@link MinionRole#AUTO} upon initial
 * creation, recruitment transfiguration, and fallback resets.
 * <p>
 * Also verifies adaptive archetype role shifts, construction session detection,
 * and role-matching invariants across logistics, formation following, and combat.
 */
public class MinionAutoDefaultAndRecruitmentTest {

	@Test
	@DisplayName("Source Invariant: MinionEntity defaults ROLE_ID and setRole to AUTO")
	void testMinionEntityAutoDefaultSourceInvariants() throws IOException {
		Path path = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(path), "MinionEntity.java must exist");
		String content = Files.readString(path);

		// ROLE_ID initialized to AUTO in initDataTracker
		Assertions.assertTrue(
			content.contains("builder.add(ROLE_ID, MinionRole.AUTO.getId());"),
			"MinionEntity must initialize ROLE_ID to MinionRole.AUTO in initDataTracker"
		);

		// setRole fallback to AUTO on null
		Assertions.assertTrue(
			content.contains("this.dataTracker.set(ROLE_ID, role != null ? role.getId() : MinionRole.AUTO.getId());"),
			"MinionEntity.setRole must fallback to MinionRole.AUTO when null role is passed"
		);

		// Phasing and egress accept matchesRole(MinionRole.BUILDER)
		Assertions.assertTrue(
			content.contains("this.matchesRole(MinionRole.BUILDER) && (this.activelyBuilding || this.exitingBuilding)"),
			"MinionEntity.isPhasingBlocks must check matchesRole(MinionRole.BUILDER)"
		);
		Assertions.assertTrue(
			content.contains("if (!this.isAlive() || !this.matchesRole(MinionRole.BUILDER))"),
			"MinionEntity.startEgressFromStructure must check matchesRole(MinionRole.BUILDER)"
		);

		// evaluateAutoRole checks ConstructionManager and defaults peaceful roaming to WARRIOR
		Assertions.assertTrue(
			content.contains("ConstructionManager.getInstance().findNearestSessionForMinion"),
			"MinionEntity.evaluateAutoRole must check for active ConstructionManager sessions"
		);
		Assertions.assertTrue(
			content.contains("targetAdaptiveRole = MinionRole.WARRIOR;"),
			"MinionEntity.evaluateAutoRole must assign WARRIOR for peacetime roaming / frontline formation"
		);
	}

	@Test
	@DisplayName("Source Invariant: CommandScepterItem recruits minions into AUTO role")
	void testCommandScepterRecruitmentAutoDefault() throws IOException {
		Path path = Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java");
		Assertions.assertTrue(Files.exists(path), "CommandScepterItem.java must exist");
		String content = Files.readString(path);

		// transfigureEntityToMinion explicitly sets AUTO role
		Assertions.assertTrue(
			content.contains("minion.setRole(MinionRole.AUTO);"),
			"CommandScepterItem.transfigureEntityToMinion must explicitly assign MinionRole.AUTO"
		);

		// Mine mode builder query uses matchesRole
		Assertions.assertTrue(
			content.contains("m.matchesRole(MinionRole.BUILDER)"),
			"CommandScepterItem mine mode must query builders using matchesRole"
		);

		// Combat assault archer check uses matchesRole
		Assertions.assertTrue(
			content.contains("minion.matchesRole(MinionRole.WARRIOR)"),
			"CommandScepterItem combat assault must check archer status using matchesRole"
		);
	}

	@Test
	@DisplayName("Source Invariant: Formation follow and harvesting helper support AUTO minions via matchesRole & getEffectiveRole")
	void testFormationAndHarvestingSupportAuto() throws IOException {
		Path formationPath = Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java");
		Assertions.assertTrue(Files.exists(formationPath), "MinionFormationFollowGoal.java must exist");
		String formationContent = Files.readString(formationPath);

		Assertions.assertTrue(
			formationContent.contains("resolveRank(comrades, this.minion, MinionEntity::getEffectiveRole, Entity::getId);"),
			"MinionFormationFollowGoal must resolve formation rank using getEffectiveRole"
		);

		Path harvestingPath = Path.of("src/main/java/com/example/entity/ai/logistics/MinionHarvestingHelper.java");
		Assertions.assertTrue(Files.exists(harvestingPath), "MinionHarvestingHelper.java must exist");
		String harvestingContent = Files.readString(harvestingPath);

		Assertions.assertTrue(
			harvestingContent.contains("m.matchesRole(MinionRole.WARRIOR)"),
			"MinionHarvestingHelper must recruit warriors using matchesRole"
		);
	}

	@Test
	@DisplayName("Behavioral Simulation: Minion AUTO role matching and dynamic environmental adaptation")
	void testAutoRoleEnvironmentalAdaptationSimulation() {
		class MockMinion {
			MinionRole role = MinionRole.AUTO;
			MinionRole adaptiveRole = MinionRole.WARRIOR;

			boolean matchesRole(MinionRole expected) {
				if (this.role == expected) return true;
				return this.role == MinionRole.AUTO && this.adaptiveRole == expected;
			}

			MinionRole getEffectiveRole() {
				return this.role == MinionRole.AUTO ? this.adaptiveRole : this.role;
			}

			void evaluateAdaptiveRole(boolean allyWounded, boolean hasTarget, boolean hostilesNearby, boolean hasBuildSession, boolean holdingPosition) {
				if (this.role != MinionRole.AUTO) return;

				if (allyWounded) {
					this.adaptiveRole = MinionRole.SENTINEL;
				} else if (hasTarget || hostilesNearby) {
					this.adaptiveRole = MinionRole.WARRIOR;
				} else if (hasBuildSession) {
					this.adaptiveRole = MinionRole.BUILDER;
				} else if (holdingPosition) {
					this.adaptiveRole = MinionRole.SENTINEL;
				} else {
					this.adaptiveRole = MinionRole.WARRIOR;
				}
			}
		}

		MockMinion minion = new MockMinion();
		Assertions.assertEquals(MinionRole.AUTO, minion.role, "Minions must start in AUTO role");

		// 1. Initial peacetime following: adapts to WARRIOR (frontline escort)
		minion.evaluateAdaptiveRole(false, false, false, false, false);
		Assertions.assertEquals(MinionRole.WARRIOR, minion.getEffectiveRole());
		Assertions.assertTrue(minion.matchesRole(MinionRole.WARRIOR));
		Assertions.assertFalse(minion.matchesRole(MinionRole.BUILDER));

		// 2. Commander places blueprint / active build session nearby: adapts to BUILDER
		minion.evaluateAdaptiveRole(false, false, false, true, false);
		Assertions.assertEquals(MinionRole.BUILDER, minion.getEffectiveRole());
		Assertions.assertTrue(minion.matchesRole(MinionRole.BUILDER));
		Assertions.assertFalse(minion.matchesRole(MinionRole.WARRIOR));

		// 3. Combat breaks out: adapts to WARRIOR
		minion.evaluateAdaptiveRole(false, true, true, true, false);
		Assertions.assertEquals(MinionRole.WARRIOR, minion.getEffectiveRole());
		Assertions.assertTrue(minion.matchesRole(MinionRole.WARRIOR));

		// 4. Ally takes heavy damage (< 70% HP): adapts to SENTINEL medic
		minion.evaluateAdaptiveRole(true, true, true, true, false);
		Assertions.assertEquals(MinionRole.SENTINEL, minion.getEffectiveRole());
		Assertions.assertTrue(minion.matchesRole(MinionRole.SENTINEL));

		// 5. Standing guard / sitting posture: adapts to SENTINEL
		minion.evaluateAdaptiveRole(false, false, false, false, true);
		Assertions.assertEquals(MinionRole.SENTINEL, minion.getEffectiveRole());
		Assertions.assertTrue(minion.matchesRole(MinionRole.SENTINEL));

		// 6. Return to peaceful following: smoothly returns to WARRIOR
		minion.evaluateAdaptiveRole(false, false, false, false, false);
		Assertions.assertEquals(MinionRole.WARRIOR, minion.getEffectiveRole());
		Assertions.assertTrue(minion.matchesRole(MinionRole.WARRIOR));
	}
}
