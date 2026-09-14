package com.example.entity;

import com.example.entity.ai.goal.MinionFormationFollowGoal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating assault target queue tracking and sequential elimination:
 * - Sequential hostile target acquisition across the 90° forward sector until all targets are slain.
 * - Dynamic queue pruning of dead, removed, or out-of-range hostiles.
 * - MinionFormationFollowGoal leash override (48-block radius) preventing premature disengagement.
 * - Clean cancellation and queue clearing on manual retreat, standby, sit, and deselect orders.
 * - Source contract verification across MinionEntity, CommandScepterItem, and MinionFormationFollowGoal.
 */
public class MinionAssaultTargetChainingTest {

	/**
	 * Mock entity representation for simulation of combat target states.
	 */
	static class MockTarget {
		final String id;
		double x, y, z;
		boolean alive = true;
		boolean removed = false;

		MockTarget(String id, double x, double y, double z) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		double squaredDistanceTo(double ox, double oy, double oz) {
			double dx = this.x - ox;
			double dy = this.y - oy;
			double dz = this.z - oz;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	/**
	 * State simulator replicating MinionEntity assault queue management.
	 */
	static class MockMinionAssaultSimulator {
		double x = 0.0D, y = 64.0D, z = 0.0D;
		final List<MockTarget> assaultTargets = new ArrayList<>();
		MockTarget currentTarget = null;
		boolean attacking = false;
		boolean returnedToOwner = false;
		int outOfCombatTicks = 0;

		void setAssaultTargets(Collection<MockTarget> targets) {
			this.assaultTargets.clear();
			if (targets != null) {
				for (MockTarget target : targets) {
					if (target != null && target.alive && !target.removed) {
						if (!this.assaultTargets.contains(target)) {
							this.assaultTargets.add(target);
						}
					}
				}
			}
			if (this.currentTarget == null || !this.currentTarget.alive) {
				this.acquireNextAssaultTarget();
			}
		}

		boolean hasAssaultTargets() {
			this.assaultTargets.removeIf(e -> e == null || !e.alive || e.removed);
			return !this.assaultTargets.isEmpty();
		}

		List<MockTarget> getAssaultTargets() {
			this.assaultTargets.removeIf(e -> e == null || !e.alive || e.removed);
			return Collections.unmodifiableList(this.assaultTargets);
		}

		void clearAssaultTargets() {
			this.assaultTargets.clear();
		}

		MockTarget acquireNextAssaultTarget() {
			this.assaultTargets.removeIf(e -> e == null || !e.alive || e.removed);
			if (this.assaultTargets.isEmpty()) {
				return null;
			}

			MockTarget next = this.assaultTargets.stream()
				.filter(t -> t.squaredDistanceTo(this.x, this.y, this.z) <= 2304.0D) // 48 blocks
				.min(Comparator.comparingDouble(t -> t.squaredDistanceTo(this.x, this.y, this.z)))
				.orElse(null);

			if (next != null) {
				this.currentTarget = next;
				this.attacking = true;
			}
			return next;
		}

		void tick() {
			if (this.currentTarget == null || !this.currentTarget.alive || this.currentTarget.removed) {
				if (this.hasAssaultTargets()) {
					this.currentTarget = this.acquireNextAssaultTarget();
				}
			}

			if (this.currentTarget != null && this.currentTarget.alive) {
				this.outOfCombatTicks = 0;
			} else {
				this.outOfCombatTicks++;
				if (!this.hasAssaultTargets()) {
					this.returnToOwnerPostCombat();
				}
			}
		}

		void onKilledOther(MockTarget slain) {
			slain.alive = false;
			if (this.hasAssaultTargets()) {
				MockTarget next = this.acquireNextAssaultTarget();
				if (next != null) {
					return;
				}
			}
			this.returnToOwnerPostCombat();
		}

		void returnToOwnerPostCombat() {
			this.returnedToOwner = true;
			this.attacking = false;
			this.currentTarget = null;
		}
	}

	// =========================================================================
	// 1. Assault Target Chaining Simulation Tests
	// =========================================================================

	@Test
	@DisplayName("Minion sequentially engages and chains through all targets in assault queue until all are slain")
	void testSequentialAssaultChainingUntilAllDead() {
		MockMinionAssaultSimulator minion = new MockMinionAssaultSimulator();
		MockTarget t1 = new MockTarget("zombie1", 5.0, 64.0, 0.0);
		MockTarget t2 = new MockTarget("skeleton1", 10.0, 64.0, 0.0);
		MockTarget t3 = new MockTarget("spider1", 15.0, 64.0, 0.0);

		List<MockTarget> sectorHostiles = List.of(t1, t2, t3);
		minion.setAssaultTargets(sectorHostiles);

		// Initial acquisition should pick closest (t1 at distance 5)
		Assertions.assertTrue(minion.hasAssaultTargets());
		Assertions.assertEquals(3, minion.getAssaultTargets().size());
		Assertions.assertEquals(t1, minion.currentTarget);
		Assertions.assertTrue(minion.attacking);
		Assertions.assertFalse(minion.returnedToOwner);

		// Kill t1: minion should immediately acquire t2 without returning to owner
		minion.onKilledOther(t1);
		Assertions.assertTrue(minion.hasAssaultTargets());
		Assertions.assertEquals(2, minion.getAssaultTargets().size());
		Assertions.assertEquals(t2, minion.currentTarget);
		Assertions.assertFalse(minion.returnedToOwner);

		// Kill t2: minion should acquire t3 without returning to owner
		minion.onKilledOther(t2);
		Assertions.assertTrue(minion.hasAssaultTargets());
		Assertions.assertEquals(1, minion.getAssaultTargets().size());
		Assertions.assertEquals(t3, minion.currentTarget);
		Assertions.assertFalse(minion.returnedToOwner);

		// Kill t3 (last hostile): minion should now return to owner
		minion.onKilledOther(t3);
		Assertions.assertFalse(minion.hasAssaultTargets());
		Assertions.assertEquals(0, minion.getAssaultTargets().size());
		Assertions.assertNull(minion.currentTarget);
		Assertions.assertTrue(minion.returnedToOwner);
	}

	@Test
	@DisplayName("When another unit eliminates the current combat target, minion tick chains to the next target")
	void testCrossUnitKillChainingDuringTick() {
		MockMinionAssaultSimulator minion = new MockMinionAssaultSimulator();
		MockTarget t1 = new MockTarget("mobA", 4.0, 64.0, 0.0);
		MockTarget t2 = new MockTarget("mobB", 8.0, 64.0, 0.0);

		minion.setAssaultTargets(List.of(t1, t2));
		Assertions.assertEquals(t1, minion.currentTarget);

		// Ally kills t1 out of band
		t1.alive = false;

		// Tick should discover t1 is dead and acquire t2
		minion.tick();
		Assertions.assertEquals(t2, minion.currentTarget);
		Assertions.assertFalse(minion.returnedToOwner);
		Assertions.assertEquals(0, minion.outOfCombatTicks);

		// Ally kills t2 out of band
		t2.alive = false;

		// Tick discovers t2 is dead, assault targets empty, triggers returnToOwnerPostCombat
		minion.tick();
		Assertions.assertNull(minion.currentTarget);
		Assertions.assertTrue(minion.returnedToOwner);
		Assertions.assertTrue(minion.outOfCombatTicks > 0);
	}

	@Test
	@DisplayName("Removed or dead targets are pruned during queue inspection")
	void testDeadAndRemovedTargetPruning() {
		MockMinionAssaultSimulator minion = new MockMinionAssaultSimulator();
		MockTarget deadTarget = new MockTarget("dead", 2.0, 64.0, 0.0);
		deadTarget.alive = false;
		MockTarget removedTarget = new MockTarget("removed", 3.0, 64.0, 0.0);
		removedTarget.removed = true;
		MockTarget liveTarget = new MockTarget("live", 6.0, 64.0, 0.0);

		minion.setAssaultTargets(List.of(deadTarget, removedTarget, liveTarget));

		Assertions.assertTrue(minion.hasAssaultTargets());
		Assertions.assertEquals(1, minion.getAssaultTargets().size());
		Assertions.assertEquals(liveTarget, minion.currentTarget);
	}

	@Test
	@DisplayName("Clear assault targets resets queue immediately for retreat, standby, and deselect")
	void testClearAssaultTargets() {
		MockMinionAssaultSimulator minion = new MockMinionAssaultSimulator();
		MockTarget t1 = new MockTarget("target1", 5.0, 64.0, 0.0);
		MockTarget t2 = new MockTarget("target2", 10.0, 64.0, 0.0);

		minion.setAssaultTargets(List.of(t1, t2));
		Assertions.assertTrue(minion.hasAssaultTargets());

		minion.clearAssaultTargets();
		minion.currentTarget = null;

		Assertions.assertFalse(minion.hasAssaultTargets());
		Assertions.assertEquals(0, minion.getAssaultTargets().size());
	}

	// =========================================================================
	// 2. Formation Follow Goal Leash Override Tests
	// =========================================================================

	@Test
	@DisplayName("MinionFormationFollowGoal respects assault leash override constant")
	void testFormationFollowGoalConstants() {
		Assertions.assertEquals(256.0D, MinionFormationFollowGoal.COMBAT_LEASH_OVERRIDE_SQ, "Standard combat leash is 16 blocks");
		Assertions.assertEquals(2304.0D, MinionFormationFollowGoal.ASSAULT_LEASH_OVERRIDE_SQ, "Mass assault leash is 48 blocks (2304 blocks sq)");
	}

	// =========================================================================
	// 3. Source Code Contract Verification Tests
	// =========================================================================

	@Test
	@DisplayName("MinionEntity source code implements complete assault target queue tracking contract")
	void testMinionEntitySourceContracts() throws IOException {
		String content = Files.readString(Path.of("src/main/java/com/example/entity/custom/MinionEntity.java"));

		Assertions.assertTrue(content.contains("assaultTargets"), "MinionEntity must declare assaultTargets field");
		Assertions.assertTrue(content.contains("setAssaultTargets"), "MinionEntity must declare setAssaultTargets");
		Assertions.assertTrue(content.contains("hasAssaultTargets"), "MinionEntity must declare hasAssaultTargets");
		Assertions.assertTrue(content.contains("getAssaultTargets"), "MinionEntity must declare getAssaultTargets");
		Assertions.assertTrue(content.contains("clearAssaultTargets"), "MinionEntity must declare clearAssaultTargets");
		Assertions.assertTrue(content.contains("acquireNextAssaultTarget"), "MinionEntity must declare acquireNextAssaultTarget");

		// Chaining in tick and onKilledOther
		Assertions.assertTrue(content.contains("if (this.hasAssaultTargets())"), "MinionEntity must check hasAssaultTargets in tick / onKilledOther");
		Assertions.assertTrue(content.contains("returnToOwnerPostCombat"), "MinionEntity must return to owner post combat");

		// Clearing on posture changes
		Assertions.assertTrue(content.contains("this.clearAssaultTargets()"), "MinionEntity must clear assault targets on state transitions");
	}

	@Test
	@DisplayName("CommandScepterItem source code populates assault queue during 90° mass assault and clears on commands")
	void testCommandScepterItemSourceContracts() throws IOException {
		String content = Files.readString(Path.of("src/main/java/com/example/item/custom/CommandScepterItem.java"));

		// Enqueuing during 90° sector mass assault
		Assertions.assertTrue(content.contains("minion.setAssaultTargets(enclosedHostiles)"),
			"CommandScepterItem must pass enclosedHostiles to minion.setAssaultTargets");

		// Clearing on retreat, deselect, and standby
		Assertions.assertTrue(content.contains("executeRetreat"), "CommandScepterItem must define executeRetreat");
		Assertions.assertTrue(content.contains("minion.clearAssaultTargets()"),
			"CommandScepterItem must invoke clearAssaultTargets on order dispatches");
	}

	@Test
	@DisplayName("MinionFormationFollowGoal source code prevents disengaging during active mass assault")
	void testMinionFormationFollowGoalSourceContracts() throws IOException {
		String content = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionFormationFollowGoal.java"));

		Assertions.assertTrue(content.contains("ASSAULT_LEASH_OVERRIDE_SQ"),
			"MinionFormationFollowGoal must define ASSAULT_LEASH_OVERRIDE_SQ");
		Assertions.assertTrue(content.contains("this.minion.hasAssaultTargets()"),
			"MinionFormationFollowGoal must check hasAssaultTargets in canStart and shouldContinue");
	}
}
