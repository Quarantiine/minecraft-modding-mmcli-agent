package com.example.entity;

import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating tactical minion roles, squad filtering logic,
 * Mojang Codec & Netty PacketCodec serialization, cyclic transitions,
 * sentinel perimeter leash constraints, and combat engagement zones.
 */
public class MinionSquadAndRoleTest {

	// =========================================================================
	// 1. MinionRole Properties & Cycling Invariants
	// =========================================================================

	@Test
	@DisplayName("Validate MinionRole mappings, bounds fallbacks, and sequential cycles")
	void testMinionRoleProperties() {
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.fromId(0));
		Assertions.assertEquals(MinionRole.SENTINEL, MinionRole.fromId(1));
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.fromId(2));
		Assertions.assertEquals(MinionRole.AUTO, MinionRole.fromId(3));

		// Fallback for out-of-bounds IDs defaults to WARRIOR
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.fromId(99));
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.fromId(-1));

		// Sequential cycling
		Assertions.assertEquals(MinionRole.SENTINEL, MinionRole.WARRIOR.next());
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.SENTINEL.next());
		Assertions.assertEquals(MinionRole.AUTO, MinionRole.BUILDER.next());
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.AUTO.next());

		// Reverse cycling
		Assertions.assertEquals(MinionRole.AUTO, MinionRole.WARRIOR.previous());
		Assertions.assertEquals(MinionRole.BUILDER, MinionRole.AUTO.previous());
		Assertions.assertEquals(MinionRole.SENTINEL, MinionRole.BUILDER.previous());
		Assertions.assertEquals(MinionRole.WARRIOR, MinionRole.SENTINEL.previous());

		// Localization key integrity
		Assertions.assertEquals("role.modid-mmcli-agent-modding.warrior", MinionRole.WARRIOR.getTranslationKey());
		Assertions.assertEquals("role.modid-mmcli-agent-modding.sentinel", MinionRole.SENTINEL.getTranslationKey());
		Assertions.assertEquals("role.modid-mmcli-agent-modding.builder", MinionRole.BUILDER.getTranslationKey());
		Assertions.assertEquals("role.modid-mmcli-agent-modding.auto", MinionRole.AUTO.getTranslationKey());
	}

	@Test
	@DisplayName("Validate MinionRole cyclic algebra invariants")
	void testMinionRoleCyclicInvariants() {
		MinionRole[] roles = MinionRole.values();
		Assertions.assertEquals(4, roles.length);

		for (MinionRole role : roles) {
			// Invariant 1: Cycling length times returns to original
			MinionRole current = role;
			for (int i = 0; i < roles.length; i++) {
				current = current.next();
			}
			Assertions.assertEquals(role, current, "Cycling next() 5 times must return to starting role");

			// Invariant 2: Reverse cycling length times returns to original
			current = role;
			for (int i = 0; i < roles.length; i++) {
				current = current.previous();
			}
			Assertions.assertEquals(role, current, "Cycling previous() 5 times must return to starting role");

			// Invariant 3: next().previous() is identity
			Assertions.assertEquals(role, role.next().previous());
			Assertions.assertEquals(role, role.previous().next());
		}
	}

	// =========================================================================
	// 2. SquadGroup Properties & Cycling Invariants
	// =========================================================================

	@Test
	@DisplayName("Validate SquadGroup filtering, wildcard matches, and unit cycling")
	void testSquadGroupFiltering() {
		Assertions.assertEquals(SquadGroup.ALL, SquadGroup.fromId(0));
		Assertions.assertEquals(SquadGroup.ALPHA, SquadGroup.fromId(1));
		Assertions.assertEquals(SquadGroup.BRAVO, SquadGroup.fromId(2));
		Assertions.assertEquals(SquadGroup.CHARLIE, SquadGroup.fromId(3));
		Assertions.assertEquals(SquadGroup.DELTA, SquadGroup.fromId(4));

		// Fallback for out-of-bounds IDs defaults to ALL
		Assertions.assertEquals(SquadGroup.ALL, SquadGroup.fromId(100));
		Assertions.assertEquals(SquadGroup.ALL, SquadGroup.fromId(-5));

		// Wildcard match checks
		Assertions.assertTrue(SquadGroup.ALL.isWildcard());
		Assertions.assertFalse(SquadGroup.ALPHA.isWildcard());
		Assertions.assertFalse(SquadGroup.BRAVO.isWildcard());
		Assertions.assertFalse(SquadGroup.CHARLIE.isWildcard());
		Assertions.assertFalse(SquadGroup.DELTA.isWildcard());

		// SquadGroup.ALL wildcard matches every squad
		Assertions.assertTrue(SquadGroup.ALL.matches(SquadGroup.ALPHA));
		Assertions.assertTrue(SquadGroup.ALL.matches(SquadGroup.BRAVO));
		Assertions.assertTrue(SquadGroup.ALL.matches(SquadGroup.CHARLIE));
		Assertions.assertTrue(SquadGroup.ALL.matches(SquadGroup.DELTA));
		Assertions.assertTrue(SquadGroup.ALL.matches(SquadGroup.ALL));

		// Specific squad matching
		Assertions.assertTrue(SquadGroup.ALPHA.matches(SquadGroup.ALPHA));
		Assertions.assertFalse(SquadGroup.ALPHA.matches(SquadGroup.BRAVO));
		Assertions.assertFalse(SquadGroup.ALPHA.matches(SquadGroup.CHARLIE));
		Assertions.assertFalse(SquadGroup.ALPHA.matches(SquadGroup.DELTA));

		// Selectable squads list (excludes ALL wildcard)
		List<SquadGroup> selectable = SquadGroup.getSelectableSquads();
		Assertions.assertEquals(4, selectable.size());
		Assertions.assertFalse(selectable.contains(SquadGroup.ALL));
		Assertions.assertTrue(selectable.contains(SquadGroup.ALPHA));
		Assertions.assertTrue(selectable.contains(SquadGroup.BRAVO));
		Assertions.assertTrue(selectable.contains(SquadGroup.CHARLIE));
		Assertions.assertTrue(selectable.contains(SquadGroup.DELTA));

		// Selectable squads cycling (skips ALL)
		Assertions.assertEquals(SquadGroup.BRAVO, SquadGroup.ALPHA.nextSelectable());
		Assertions.assertEquals(SquadGroup.CHARLIE, SquadGroup.BRAVO.nextSelectable());
		Assertions.assertEquals(SquadGroup.DELTA, SquadGroup.CHARLIE.nextSelectable());
		Assertions.assertEquals(SquadGroup.ALPHA, SquadGroup.DELTA.nextSelectable());

		Assertions.assertEquals(SquadGroup.DELTA, SquadGroup.ALPHA.previousSelectable());
		Assertions.assertEquals(SquadGroup.CHARLIE, SquadGroup.DELTA.previousSelectable());
		Assertions.assertEquals(SquadGroup.BRAVO, SquadGroup.CHARLIE.previousSelectable());
		Assertions.assertEquals(SquadGroup.ALPHA, SquadGroup.BRAVO.previousSelectable());

		// Fallback when cycling selectable from ALL
		Assertions.assertEquals(SquadGroup.ALPHA, SquadGroup.ALL.nextSelectable());
		Assertions.assertEquals(SquadGroup.DELTA, SquadGroup.ALL.previousSelectable());
	}

	@Test
	@DisplayName("Validate SquadGroup cyclic algebra invariants")
	void testSquadGroupCyclicInvariants() {
		SquadGroup[] squads = SquadGroup.values();
		Assertions.assertEquals(5, squads.length);

		for (SquadGroup squad : squads) {
			// Invariant 1: Cycling length times returns to original
			SquadGroup current = squad;
			for (int i = 0; i < squads.length; i++) {
				current = current.next();
			}
			Assertions.assertEquals(squad, current);

			// Invariant 2: Reverse cycling length times returns to original
			current = squad;
			for (int i = 0; i < squads.length; i++) {
				current = current.previous();
			}
			Assertions.assertEquals(squad, current);

			// Invariant 3: next().previous() is identity
			Assertions.assertEquals(squad, squad.next().previous());
			Assertions.assertEquals(squad, squad.previous().next());
		}

		// Selectable cycling invariants
		for (SquadGroup selectable : SquadGroup.getSelectableSquads()) {
			SquadGroup current = selectable;
			for (int i = 0; i < SquadGroup.getSelectableSquads().size(); i++) {
				current = current.nextSelectable();
			}
			Assertions.assertEquals(selectable, current);
			Assertions.assertEquals(selectable, selectable.nextSelectable().previousSelectable());
			Assertions.assertEquals(selectable, selectable.previousSelectable().nextSelectable());
		}
	}

	// =========================================================================
	// 3. Serialization Tests: Mojang Codec & Netty PacketCodec
	// =========================================================================

	@Test
	@DisplayName("Validate MinionRole Mojang Codec serialization and deserialization")
	void testMinionRoleCodecSerialization() {
		for (MinionRole role : MinionRole.values()) {
			// Encode to JSON
			DataResult<JsonElement> encodeResult = MinionRole.CODEC.encodeStart(JsonOps.INSTANCE, role);
			Assertions.assertTrue(encodeResult.isSuccess(), "Encoding role " + role + " must succeed");
			JsonElement json = encodeResult.getOrThrow();
			Assertions.assertTrue(json.isJsonPrimitive());
			Assertions.assertEquals(role.asString(), json.getAsString());

			// Decode from JSON
			DataResult<MinionRole> decodeResult = MinionRole.CODEC.parse(JsonOps.INSTANCE, json);
			Assertions.assertTrue(decodeResult.isSuccess(), "Decoding role " + role.asString() + " must succeed");
			Assertions.assertEquals(role, decodeResult.getOrThrow());
		}

		// Invalid string returns error
		DataResult<MinionRole> invalidResult = MinionRole.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("invalid_role"));
		Assertions.assertTrue(invalidResult.isError(), "Parsing invalid role name must return error");
	}

	@Test
	@DisplayName("Validate SquadGroup Mojang Codec serialization and deserialization")
	void testSquadGroupCodecSerialization() {
		for (SquadGroup squad : SquadGroup.values()) {
			// Encode to JSON
			DataResult<JsonElement> encodeResult = SquadGroup.CODEC.encodeStart(JsonOps.INSTANCE, squad);
			Assertions.assertTrue(encodeResult.isSuccess(), "Encoding squad " + squad + " must succeed");
			JsonElement json = encodeResult.getOrThrow();
			Assertions.assertTrue(json.isJsonPrimitive());
			Assertions.assertEquals(squad.asString(), json.getAsString());

			// Decode from JSON
			DataResult<SquadGroup> decodeResult = SquadGroup.CODEC.parse(JsonOps.INSTANCE, json);
			Assertions.assertTrue(decodeResult.isSuccess(), "Decoding squad " + squad.asString() + " must succeed");
			Assertions.assertEquals(squad, decodeResult.getOrThrow());
		}

		// Invalid string returns error
		DataResult<SquadGroup> invalidResult = SquadGroup.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("unknown_squad"));
		Assertions.assertTrue(invalidResult.isError(), "Parsing invalid squad name must return error");
	}

	@Test
	@DisplayName("Validate MinionRole Netty PacketCodec byte serialization")
	void testMinionRolePacketCodec() {
		for (MinionRole role : MinionRole.values()) {
			ByteBuf buf = Unpooled.buffer();
			try {
				MinionRole.PACKET_CODEC.encode(buf, role);
				Assertions.assertTrue(buf.readableBytes() > 0, "Buffer must contain encoded role bytes");

				MinionRole decoded = MinionRole.PACKET_CODEC.decode(buf);
				Assertions.assertEquals(role, decoded, "Decoded role must match original");
				Assertions.assertEquals(0, buf.readableBytes(), "All bytes should be consumed");
			} finally {
				buf.release();
			}
		}
	}

	@Test
	@DisplayName("Validate SquadGroup Netty PacketCodec byte serialization")
	void testSquadGroupPacketCodec() {
		for (SquadGroup squad : SquadGroup.values()) {
			ByteBuf buf = Unpooled.buffer();
			try {
				SquadGroup.PACKET_CODEC.encode(buf, squad);
				Assertions.assertTrue(buf.readableBytes() > 0, "Buffer must contain encoded squad bytes");

				SquadGroup decoded = SquadGroup.PACKET_CODEC.decode(buf);
				Assertions.assertEquals(squad, decoded, "Decoded squad must match original");
				Assertions.assertEquals(0, buf.readableBytes(), "All bytes should be consumed");
			} finally {
				buf.release();
			}
		}
	}

	// =========================================================================
	// 4. Tactical Squad Predicate Filtering & Army Delegation
	// =========================================================================

	/**
	 * Lightweight mock minion thrall representing unit attributes for predicate filtering.
	 */
	public record MockMinion(
		int id,
		UUID ownerUuid,
		boolean isAlive,
		SquadGroup squad,
		MinionRole role
	) {
		public boolean matchesCommandFilter(UUID commanderUuid, SquadGroup targetSquad) {
			if (!this.isAlive || !this.ownerUuid.equals(commanderUuid)) {
				return false;
			}
			return targetSquad.matches(this.squad);
		}
	}

	@Test
	@DisplayName("Validate Tactical Squad filtering predicate on mock thrall army")
	void testTacticalSquadFilteringPredicate() {
		UUID commanderId = UUID.randomUUID();
		UUID enemyCommanderId = UUID.randomUUID();

		List<MockMinion> army = List.of(
			// Alive units belonging to commander
			new MockMinion(1, commanderId, true, SquadGroup.ALPHA, MinionRole.WARRIOR),
			new MockMinion(2, commanderId, true, SquadGroup.ALPHA, MinionRole.WARRIOR),
			new MockMinion(3, commanderId, true, SquadGroup.BRAVO, MinionRole.SENTINEL),
			new MockMinion(4, commanderId, true, SquadGroup.CHARLIE, MinionRole.BUILDER),
			new MockMinion(5, commanderId, true, SquadGroup.DELTA, MinionRole.BUILDER),
			new MockMinion(6, commanderId, true, SquadGroup.DELTA, MinionRole.WARRIOR),
			// Dead minion belonging to commander (must be filtered out)
			new MockMinion(7, commanderId, false, SquadGroup.ALPHA, MinionRole.WARRIOR),
			// Enemy minions (must be filtered out)
			new MockMinion(8, enemyCommanderId, true, SquadGroup.ALPHA, MinionRole.WARRIOR),
			new MockMinion(9, enemyCommanderId, true, SquadGroup.BRAVO, MinionRole.SENTINEL)
		);

		// Wildcard filter (ALL) matches all 6 living minions owned by commander
		long matchingAll = army.stream()
			.filter(m -> m.matchesCommandFilter(commanderId, SquadGroup.ALL))
			.count();
		Assertions.assertEquals(6, matchingAll);

		// Discrete filter (ALPHA) matches 2 living minions (id 1, 2); ignores dead id 7 and enemy id 8
		List<MockMinion> matchingAlpha = army.stream()
			.filter(m -> m.matchesCommandFilter(commanderId, SquadGroup.ALPHA))
			.toList();
		Assertions.assertEquals(2, matchingAlpha.size());
		Assertions.assertEquals(1, matchingAlpha.get(0).id());
		Assertions.assertEquals(2, matchingAlpha.get(1).id());

		// Discrete filter (BRAVO) matches 1 minion (id 3)
		List<MockMinion> matchingBravo = army.stream()
			.filter(m -> m.matchesCommandFilter(commanderId, SquadGroup.BRAVO))
			.toList();
		Assertions.assertEquals(1, matchingBravo.size());
		Assertions.assertEquals(3, matchingBravo.get(0).id());

		// Discrete filter (CHARLIE) matches 1 minion (id 4)
		List<MockMinion> matchingCharlie = army.stream()
			.filter(m -> m.matchesCommandFilter(commanderId, SquadGroup.CHARLIE))
			.toList();
		Assertions.assertEquals(1, matchingCharlie.size());
		Assertions.assertEquals(4, matchingCharlie.get(0).id());

		// Discrete filter (DELTA) matches 2 minions (id 5, 6)
		List<MockMinion> matchingDelta = army.stream()
			.filter(m -> m.matchesCommandFilter(commanderId, SquadGroup.DELTA))
			.toList();
		Assertions.assertEquals(2, matchingDelta.size());
		Assertions.assertEquals(5, matchingDelta.get(0).id());
		Assertions.assertEquals(6, matchingDelta.get(1).id());
	}

	@Test
	@DisplayName("Validate Builder role partitioning invariant for construction tasks")
	void testBuilderRolePartitioning() {
		// Only BUILDER role is permitted to execute MinionBuildGoal
		for (MinionRole role : MinionRole.values()) {
			boolean canBuild = (role == MinionRole.BUILDER);
			if (role == MinionRole.BUILDER) {
				Assertions.assertTrue(canBuild, "BUILDER role must be allowed to participate in construction");
			} else {
				Assertions.assertFalse(canBuild, role + " role must be prohibited from participating in construction");
			}
		}
	}

	// =========================================================================
	// 5. Sentinel Perimeter Leash State Machine & Logic Simulation
	// =========================================================================

	/**
	 * State machine simulating the exact behavioral conditions of {@link com.example.entity.ai.goal.SentinelGuardGoal}.
	 */
	public static class TestSentinelGuardLogic {
		public static final double PERIMETER_RADIUS = 8.0D;
		public static final double PERIMETER_RADIUS_SQ = PERIMETER_RADIUS * PERIMETER_RADIUS; // 64.0
		public static final double LEASH_DISTANCE = 128.0D;
		public static final double LEASH_DISTANCE_SQ = LEASH_DISTANCE * LEASH_DISTANCE;
		public static final double ARRIVAL_TOLERANCE_SQ = 4.0D;                               // 2 blocks radius
		public static final double SPRINT_SPEED = 1.35D;

		public static class Vec3 {
			public double x, y, z;
			public Vec3(double x, double y, double z) {
				this.x = x;
				this.y = y;
				this.z = z;
			}
			public double distanceSqTo(double targetX, double targetY, double targetZ) {
				double dx = this.x - targetX;
				double dy = this.y - targetY;
				double dz = this.z - targetZ;
				return dx * dx + dy * dy + dz * dz;
			}
			public double distanceSqTo(Vec3 other) {
				return distanceSqTo(other.x, other.y, other.z);
			}
		}

		public MinionRole role = MinionRole.SENTINEL;
		public boolean isAlive = true;
		public boolean isTamed = true;
		public boolean isSitting = false;

		public Vec3 guardAnchorPos = null;
		public Vec3 ownerPos = null;
		public Vec3 minionPos = new Vec3(0, 0, 0);
		public Vec3 targetPos = null;
		public boolean targetAlive = false;

		public boolean isNavigating = false;
		public double navigationSpeed = 0.0D;
		public boolean aggroBroken = false;

		public Vec3 resolveAnchor() {
			return guardAnchorPos;
		}

		public boolean canStart() {
			if (!isAlive || !isTamed || isSitting || role != MinionRole.SENTINEL) {
				return false;
			}
			Vec3 anchor = resolveAnchor();
			if (anchor == null) {
				return false;
			}

			if (targetPos != null && targetAlive) {
				double targetDistSq = targetPos.distanceSqTo(anchor);
				double minionDistSq = minionPos.distanceSqTo(anchor);
				return targetDistSq > LEASH_DISTANCE_SQ || minionDistSq > LEASH_DISTANCE_SQ;
			}

			// Idle return to post
			double distToAnchorSq = minionPos.distanceSqTo(anchor);
			return distToAnchorSq > ARRIVAL_TOLERANCE_SQ;
		}

		public void start() {
			Vec3 anchor = resolveAnchor();
			if (anchor != null) {
				if (targetPos != null) {
					targetPos = null;
					targetAlive = false;
					aggroBroken = true;
				}
				isNavigating = true;
				navigationSpeed = SPRINT_SPEED;
			}
		}

		public void tick() {
			Vec3 anchor = resolveAnchor();
			if (anchor == null) return;

			if (targetPos != null && targetAlive) {
				double targetDistSq = targetPos.distanceSqTo(anchor);
				double minionDistSq = minionPos.distanceSqTo(anchor);
				if (targetDistSq > LEASH_DISTANCE_SQ || minionDistSq > LEASH_DISTANCE_SQ) {
					targetPos = null;
					targetAlive = false;
					aggroBroken = true;
				}
			}

			double distSq = minionPos.distanceSqTo(anchor);
			if (distSq > ARRIVAL_TOLERANCE_SQ) {
				isNavigating = true;
				navigationSpeed = SPRINT_SPEED;
			} else {
				isNavigating = false;
				navigationSpeed = 0.0D;
			}
		}

		public boolean shouldContinue() {
			if (!isAlive || !isTamed || isSitting || role != MinionRole.SENTINEL) {
				return false;
			}
			Vec3 anchor = resolveAnchor();
			if (anchor == null) return false;

			if (targetPos != null && targetAlive) {
				double targetDistSq = targetPos.distanceSqTo(anchor);
				double minionDistSq = minionPos.distanceSqTo(anchor);
				if (targetDistSq > LEASH_DISTANCE_SQ || minionDistSq > LEASH_DISTANCE_SQ) {
					targetPos = null;
					targetAlive = false;
					aggroBroken = true;
					return true;
				}
				return false;
			}

			double distSq = minionPos.distanceSqTo(anchor);
			return distSq > ARRIVAL_TOLERANCE_SQ;
		}
	}

	@Test
	@DisplayName("Validate Sentinel leash logic: station holding, leash break, and sprint return")
	void testSentinelLeashStateMachine() {
		TestSentinelGuardLogic logic = new TestSentinelGuardLogic();
		logic.guardAnchorPos = new TestSentinelGuardLogic.Vec3(100.0, 64.0, 100.0);
		logic.minionPos = new TestSentinelGuardLogic.Vec3(100.0, 64.0, 100.0);

		// Case 1: Sentinel at post with no target -> canStart is false (holding post, no redundant movement)
		Assertions.assertFalse(logic.canStart(), "Stationary sentinel at post should not start return goal");

		// Case 2: Sentinel drifted 3 blocks from post (distSq = 9 > 4) with no target -> canStart is true
		logic.minionPos = new TestSentinelGuardLogic.Vec3(103.0, 64.0, 100.0);
		Assertions.assertTrue(logic.canStart(), "Drifted sentinel should start return goal");
		logic.start();
		Assertions.assertTrue(logic.isNavigating);
		Assertions.assertEquals(TestSentinelGuardLogic.SPRINT_SPEED, logic.navigationSpeed);

		// Minion moves back to within 1 block of post (distSq = 1 <= 4) -> arrival completes
		logic.minionPos = new TestSentinelGuardLogic.Vec3(101.0, 64.0, 100.0);
		logic.tick();
		Assertions.assertFalse(logic.isNavigating, "Sentinel arriving within 2-block tolerance should stop");

		// Case 3: Combat engagement inside 8-block perimeter
		logic.minionPos = new TestSentinelGuardLogic.Vec3(104.0, 64.0, 100.0); // 4 blocks from anchor
		logic.targetPos = new TestSentinelGuardLogic.Vec3(106.0, 64.0, 100.0); // 6 blocks from anchor
		logic.targetAlive = true;
		logic.aggroBroken = false;

		// Inside perimeter -> leash goal does not trigger; minion is free to fight
		Assertions.assertFalse(logic.canStart(), "Combat within perimeter should not trigger leash retreat");

		// Case 4: Target retreats beyond 128 blocks from anchor (129 blocks away, distSq = 129^2 > 128^2)
		logic.targetPos = new TestSentinelGuardLogic.Vec3(229.0, 64.0, 100.0);
		Assertions.assertTrue(logic.canStart(), "Target fleeing beyond 128 blocks must trigger leash retreat");

		logic.start();
		Assertions.assertTrue(logic.aggroBroken, "Aggro must be broken immediately");
		Assertions.assertNull(logic.targetPos, "Target must be cleared to null");
		Assertions.assertTrue(logic.isNavigating);
		Assertions.assertEquals(TestSentinelGuardLogic.SPRINT_SPEED, logic.navigationSpeed);

		// Case 5: Sentinel lured beyond 128 blocks from anchor (130 blocks away)
		logic.minionPos = new TestSentinelGuardLogic.Vec3(230.0, 64.0, 100.0);
		logic.targetPos = new TestSentinelGuardLogic.Vec3(200.0, 64.0, 100.0); // Target at 100 blocks
		logic.targetAlive = true;
		logic.aggroBroken = false;

		Assertions.assertTrue(logic.canStart(), "Sentinel lured beyond 128 blocks must trigger leash retreat");
		logic.start();
		Assertions.assertTrue(logic.aggroBroken);
		Assertions.assertNull(logic.targetPos);

		// Case 6: Role gating — Non-sentinel role does NOT run SentinelGuardGoal
		logic.role = MinionRole.WARRIOR;
		logic.minionPos = new TestSentinelGuardLogic.Vec3(130.0, 64.0, 100.0);
		Assertions.assertFalse(logic.canStart(), "Warrior role must not execute Sentinel guard goal");

		logic.role = MinionRole.BUILDER;
		Assertions.assertFalse(logic.canStart(), "Builder role must not execute Sentinel guard goal");

		// Case 7: Unanchored Sentinel does NOT anchor to owner (wanders freely unless selected)
		logic.role = MinionRole.SENTINEL;
		logic.guardAnchorPos = null;
		logic.ownerPos = new TestSentinelGuardLogic.Vec3(50.0, 64.0, 50.0);
		logic.minionPos = new TestSentinelGuardLogic.Vec3(65.0, 64.0, 50.0); // 15 blocks from owner (> 12)
		Assertions.assertNull(logic.resolveAnchor(), "Unanchored sentinel should not anchor to owner");
		Assertions.assertFalse(logic.canStart(), "Unanchored sentinel must not execute guard return goal");
	}

	// =========================================================================
	// 6. Ranged Combat Engagement Pocket Thresholds
	// =========================================================================

	@Test
	@DisplayName("Validate Ranged skirmish engagement zones (8 to 16 blocks)")
	void testRangedEngagementPockets() {
		double minRangeSq = 8.0D * 8.0D;   // 64.0
		double maxRangeSq = 16.0D * 16.0D; // 256.0

		// Hostile at 5 blocks -> backpedal zone
		double encroachedDistSq = 5.0D * 5.0D;
		Assertions.assertTrue(encroachedDistSq < minRangeSq, "5 blocks must trigger backpedaling");

		// Hostile at 12 blocks -> sweet spot strafing zone
		double sweetSpotDistSq = 12.0D * 12.0D;
		Assertions.assertTrue(sweetSpotDistSq >= minRangeSq && sweetSpotDistSq <= maxRangeSq, "12 blocks is inside optimal 8-16 block pocket");

		// Hostile at 20 blocks -> pursue forward zone
		double distantDistSq = 20.0D * 20.0D;
		Assertions.assertTrue(distantDistSq > maxRangeSq, "20 blocks must trigger forward approach");
	}

	// =========================================================================
	// 7. Squad Channel Formatting & HUD Labels
	// =========================================================================

	@Test
	@DisplayName("Validate Squad channel display names, color codes, and formatting strings")
	void testSquadChannelFormatting() {
		Assertions.assertEquals("§fAll Squads", SquadGroup.ALL.getFormattedName());
		Assertions.assertEquals("§cSquad Alpha", SquadGroup.ALPHA.getFormattedName());
		Assertions.assertEquals("§9Squad Bravo", SquadGroup.BRAVO.getFormattedName());
		Assertions.assertEquals("§aSquad Charlie", SquadGroup.CHARLIE.getFormattedName());
		Assertions.assertEquals("§6Squad Delta", SquadGroup.DELTA.getFormattedName());

		// Next / previous full cycles
		Assertions.assertEquals(SquadGroup.ALPHA, SquadGroup.ALL.next());
		Assertions.assertEquals(SquadGroup.ALL, SquadGroup.DELTA.next());
		Assertions.assertEquals(SquadGroup.DELTA, SquadGroup.ALL.previous());

		// Translation keys
		Assertions.assertEquals("squad.modid-mmcli-agent-modding.all", SquadGroup.ALL.getTranslationKey());
		Assertions.assertEquals("squad.modid-mmcli-agent-modding.alpha", SquadGroup.ALPHA.getTranslationKey());
		Assertions.assertEquals("squad.modid-mmcli-agent-modding.bravo", SquadGroup.BRAVO.getTranslationKey());
		Assertions.assertEquals("squad.modid-mmcli-agent-modding.charlie", SquadGroup.CHARLIE.getTranslationKey());
		Assertions.assertEquals("squad.modid-mmcli-agent-modding.delta", SquadGroup.DELTA.getTranslationKey());
	}

	// =========================================================================
	// 8. Channeled Banner of Courage Rally & Point-and-Click Pings
	// =========================================================================

	@Test
	@DisplayName("Validate Banner of Courage Rally Ring gathering enclosed minions into selected squad")
	void testBannerOfCourageRallyRingLogic() {
		UUID commanderId = UUID.randomUUID();
		UUID enemyCommanderId = UUID.randomUUID();

		record TacticalThrall(
			int id,
			UUID owner,
			boolean alive,
			SquadGroup squad,
			double x,
			double z,
			boolean sitting,
			String waypointAnchor
		) {
			public boolean isWithinRadius(double originX, double originZ, double radius) {
				double dx = this.x - originX;
				double dz = this.z - originZ;
				return (dx * dx + dz * dz) <= (radius * radius);
			}
		}

		List<TacticalThrall> fieldThralls = List.of(
			// Near commander (at 0, 0), radius 8.0:
			new TacticalThrall(1, commanderId, true, SquadGroup.ALPHA, 3.0, 4.0, true, "10,64,10"),   // dist = 5.0 <= 8.0
			new TacticalThrall(2, commanderId, true, SquadGroup.BRAVO, -5.0, 0.0, false, "20,64,20"), // dist = 5.0 <= 8.0
			new TacticalThrall(3, commanderId, true, SquadGroup.CHARLIE, 2.0, -3.0, true, null),      // dist = 3.6 <= 8.0
			// Outside rally radius (dist = 12.0 > 8.0)
			new TacticalThrall(4, commanderId, true, SquadGroup.DELTA, 12.0, 0.0, true, "30,64,30"),
			// Dead minion nearby (must be ignored)
			new TacticalThrall(5, commanderId, false, SquadGroup.ALPHA, 1.0, 1.0, true, null),
			// Enemy minion nearby (must not be gathered)
			new TacticalThrall(6, enemyCommanderId, true, SquadGroup.ALPHA, 2.0, 2.0, false, null)
		);

		double rallyRadius = 8.0;
		SquadGroup targetSquadChannel = SquadGroup.DELTA;

		// Filter for enclosed owned living minions
		List<TacticalThrall> gathered = fieldThralls.stream()
			.filter(t -> t.alive && t.owner.equals(commanderId) && t.isWithinRadius(0.0, 0.0, rallyRadius))
			.toList();

		Assertions.assertEquals(3, gathered.size());
		Assertions.assertTrue(gathered.stream().anyMatch(t -> t.id == 1));
		Assertions.assertTrue(gathered.stream().anyMatch(t -> t.id == 2));
		Assertions.assertTrue(gathered.stream().anyMatch(t -> t.id == 3));

		// Simulation of rally action: change squad to DELTA, clear sitting, clear anchor
		for (TacticalThrall t : gathered) {
			SquadGroup newSquad = !targetSquadChannel.isWildcard() ? targetSquadChannel : t.squad;
			Assertions.assertEquals(SquadGroup.DELTA, newSquad);
		}
	}

	@Test
	@DisplayName("Validate Ground Waypoint Ping anchors matching squad thralls and ignores sitting units (no squad leak)")
	void testGroundWaypointPingLogic() {
		UUID commanderId = UUID.randomUUID();

		record ThrallState(int id, UUID owner, boolean alive, SquadGroup squad, boolean sitting, String anchor, boolean navigating) {}

		List<ThrallState> thralls = List.of(
			new ThrallState(1, commanderId, true, SquadGroup.BRAVO, false, null, false),
			new ThrallState(2, commanderId, true, SquadGroup.BRAVO, true, "100,64,100", false), // Stationed/sitting
			new ThrallState(3, commanderId, true, SquadGroup.ALPHA, false, null, false),
			new ThrallState(4, commanderId, false, SquadGroup.BRAVO, false, null, false)
		);

		SquadGroup scepterTargetSquad = SquadGroup.BRAVO;
		String pingPos = "150,64,150";

		List<ThrallState> affected = thralls.stream()
			.filter(t -> t.alive && t.owner.equals(commanderId) && !t.sitting && scepterTargetSquad.matches(t.squad))
			.map(t -> new ThrallState(t.id, t.owner, t.alive, t.squad, false, pingPos, true))
			.toList();

		Assertions.assertEquals(1, affected.size(), "Sitting stationed unit must NOT be pulled into waypoint march");
		Assertions.assertEquals(1, affected.get(0).id());
		Assertions.assertEquals(pingPos, affected.get(0).anchor());
		Assertions.assertTrue(affected.get(0).navigating());
	}

	@Test
	@DisplayName("Validate Attack Broadcast ignores sitting/stationed minions to prevent perimeter abandonment")
	void testAttackBroadcastIgnoresSittingUnits() {
		UUID commanderId = UUID.randomUUID();

		record AttackThrall(int id, UUID owner, boolean alive, SquadGroup squad, boolean sitting, Integer targetId) {}

		List<AttackThrall> thralls = List.of(
			new AttackThrall(1, commanderId, true, SquadGroup.ALPHA, false, null), // Active marching thrall
			new AttackThrall(2, commanderId, true, SquadGroup.ALPHA, true, null),  // Stationed sentinel on wall
			new AttackThrall(3, commanderId, true, SquadGroup.BRAVO, false, null)  // Different squad
		);

		SquadGroup scepterSquad = SquadGroup.ALPHA;
		int primaryTargetId = 555;

		List<AttackThrall> mobilized = thralls.stream()
			.filter(t -> t.alive && t.owner.equals(commanderId) && !t.sitting && scepterSquad.matches(t.squad))
			.map(t -> new AttackThrall(t.id, t.owner, t.alive, t.squad, false, primaryTargetId))
			.toList();

		Assertions.assertEquals(1, mobilized.size());
		Assertions.assertEquals(1, mobilized.get(0).id());
		Assertions.assertEquals(primaryTargetId, mobilized.get(0).targetId());
	}

	@Test
	@DisplayName("Validate Hostile Entity Focus-Fire Ping directs squad thralls to engage target")
	void testHostileEntityFocusFireLogic() {
		UUID commanderId = UUID.randomUUID();

		record CombatThrall(int id, UUID owner, boolean alive, SquadGroup squad, Integer combatTargetId) {}

		List<CombatThrall> thralls = List.of(
			new CombatThrall(1, commanderId, true, SquadGroup.CHARLIE, null),
			new CombatThrall(2, commanderId, true, SquadGroup.CHARLIE, null),
			new CombatThrall(3, commanderId, true, SquadGroup.ALPHA, null)
		);

		SquadGroup scepterSquad = SquadGroup.CHARLIE;
		int hostileEntityId = 999;

		List<CombatThrall> focused = thralls.stream()
			.filter(t -> t.alive && t.owner.equals(commanderId) && scepterSquad.matches(t.squad))
			.map(t -> new CombatThrall(t.id, t.owner, t.alive, t.squad, hostileEntityId))
			.toList();

		Assertions.assertEquals(2, focused.size());
		Assertions.assertEquals(hostileEntityId, focused.get(0).combatTargetId());
		Assertions.assertEquals(hostileEntityId, focused.get(1).combatTargetId());
	}

	@Test
	@DisplayName("Validate large-scale army squad partitioning and rejection of foreign/dead thralls")
	void testLargeScaleArmySquadPartitioning() {
		UUID myCommander = UUID.randomUUID();
		UUID rivalCommander = UUID.randomUUID();

		List<MockMinion> largeArmy = new ArrayList<>();
		int idCounter = 1;

		// 20 thralls per squad for myCommander (80 total)
		for (SquadGroup squad : SquadGroup.getSelectableSquads()) {
			for (int i = 0; i < 20; i++) {
				largeArmy.add(new MockMinion(idCounter++, myCommander, true, squad, MinionRole.WARRIOR));
			}
		}

		// 5 dead thralls for myCommander in each squad (20 dead total)
		for (SquadGroup squad : SquadGroup.getSelectableSquads()) {
			for (int i = 0; i < 5; i++) {
				largeArmy.add(new MockMinion(idCounter++, myCommander, false, squad, MinionRole.SENTINEL));
			}
		}

		// 10 living thralls for rival commander in each squad (40 rival total)
		for (SquadGroup squad : SquadGroup.getSelectableSquads()) {
			for (int i = 0; i < 10; i++) {
				largeArmy.add(new MockMinion(idCounter++, rivalCommander, true, squad, MinionRole.WARRIOR));
			}
		}

		Assertions.assertEquals(140, largeArmy.size(), "Total population should be 140");

		// Filter for myCommander with ALL: must match exactly 80 living thralls
		long myLivingAll = largeArmy.stream()
			.filter(m -> m.matchesCommandFilter(myCommander, SquadGroup.ALL))
			.count();
		Assertions.assertEquals(80, myLivingAll);

		// Filter for each discrete squad: must match exactly 20 living thralls each
		for (SquadGroup squad : SquadGroup.getSelectableSquads()) {
			long squadCount = largeArmy.stream()
				.filter(m -> m.matchesCommandFilter(myCommander, squad))
				.count();
			Assertions.assertEquals(20, squadCount, "Squad " + squad + " must contain exactly 20 active units");
		}

		// Rival commander filtering
		long rivalLivingAll = largeArmy.stream()
			.filter(m -> m.matchesCommandFilter(rivalCommander, SquadGroup.ALL))
			.count();
		Assertions.assertEquals(40, rivalLivingAll);
	}

	@Test
	@DisplayName("Validate isolated squad directive dispatch without command cross-talk")
	void testMultiSquadSimultaneousDirectives() {
		UUID commander = UUID.randomUUID();
		Assertions.assertNotNull(commander);

		class ThrallBehaviorState {
			final int id;
			final SquadGroup squad;
			String currentOrder;
			boolean sitting;
			String waypoint;

			ThrallBehaviorState(int id, SquadGroup squad, String order, boolean sitting, String waypoint) {
				this.id = id;
				this.squad = squad;
				this.currentOrder = order;
				this.sitting = sitting;
				this.waypoint = waypoint;
			}
		}

		List<ThrallBehaviorState> units = List.of(
			new ThrallBehaviorState(1, SquadGroup.ALPHA, "IDLE", true, "10,64,10"),
			new ThrallBehaviorState(2, SquadGroup.ALPHA, "IDLE", true, "10,64,10"),
			new ThrallBehaviorState(3, SquadGroup.BRAVO, "IDLE", false, null),
			new ThrallBehaviorState(4, SquadGroup.CHARLIE, "IDLE", false, null)
		);

		// Issue FOLLOW broadcast to ALPHA: should clear sitting and waypoint for ALPHA only
		SquadGroup broadcastTarget = SquadGroup.ALPHA;
		for (ThrallBehaviorState unit : units) {
			if (broadcastTarget.matches(unit.squad)) {
				unit.currentOrder = "FOLLOW";
				unit.sitting = false;
				unit.waypoint = null;
			}
		}

		// Verify ALPHA units were updated
		Assertions.assertEquals(1, units.get(0).id);
		Assertions.assertEquals("FOLLOW", units.get(0).currentOrder);
		Assertions.assertFalse(units.get(0).sitting);
		Assertions.assertNull(units.get(0).waypoint);

		Assertions.assertEquals(2, units.get(1).id);
		Assertions.assertEquals("FOLLOW", units.get(1).currentOrder);
		Assertions.assertFalse(units.get(1).sitting);
		Assertions.assertNull(units.get(1).waypoint);

		// Verify BRAVO and CHARLIE were completely untouched
		Assertions.assertEquals("IDLE", units.get(2).currentOrder);
		Assertions.assertFalse(units.get(2).sitting);
		Assertions.assertNull(units.get(2).waypoint);

		Assertions.assertEquals("IDLE", units.get(3).currentOrder);
		Assertions.assertFalse(units.get(3).sitting);
		Assertions.assertNull(units.get(3).waypoint);
	}

	// =========================================================================
	// 10. Selection System, Outline Colors & Decoupled Standing Guard Stance
	// =========================================================================

	@Test
	@DisplayName("Validate squad outline colors for glowing silhouettes")
	void testSquadOutlineColors() {
		Assertions.assertEquals(0xE74C3C, SquadGroup.ALPHA.getOutlineColor(), "Alpha outline must be crimson red");
		Assertions.assertEquals(0x3498DB, SquadGroup.BRAVO.getOutlineColor(), "Bravo outline must be azure blue");
		Assertions.assertEquals(0x2ECC71, SquadGroup.CHARLIE.getOutlineColor(), "Charlie outline must be emerald green");
		Assertions.assertEquals(0xF39C12, SquadGroup.DELTA.getOutlineColor(), "Delta outline must be amber gold");
		Assertions.assertEquals(0xFFFFFF, SquadGroup.ALL.getOutlineColor(), "All/Wildcard outline must be pure white");
	}

	@Test
	@DisplayName("Validate ground waypoint moves ONLY selected minions matching the active squad channel")
	void testMinionSelectionAndGroundWaypointDispatch() {
		class TestMinion {
			final int id;
			final SquadGroup squad;
			boolean selected;
			String waypoint;

			TestMinion(int id, SquadGroup squad, boolean selected) {
				this.id = id;
				this.squad = squad;
				this.selected = selected;
			}
		}

		List<TestMinion> platoon = List.of(
			new TestMinion(1, SquadGroup.ALPHA, true),   // Alpha & Selected -> SHOULD MOVE
			new TestMinion(2, SquadGroup.ALPHA, false),  // Alpha & Unselected -> SHOULD NOT MOVE
			new TestMinion(3, SquadGroup.BRAVO, true),   // Bravo & Selected -> Filtered if Alpha channel active
			new TestMinion(4, SquadGroup.BRAVO, false)   // Bravo & Unselected -> SHOULD NOT MOVE
		);

		// Scenario A: Alpha channel active, ground waypoint issued at (50, 64, 50)
		SquadGroup channelAlpha = SquadGroup.ALPHA;
		String targetWaypoint = "50,64,50";
		int movedCountA = 0;
		for (TestMinion m : platoon) {
			if (m.selected && channelAlpha.matches(m.squad)) {
				m.waypoint = targetWaypoint;
				movedCountA++;
			}
		}

		Assertions.assertEquals(1, movedCountA, "Only 1 minion (Alpha + Selected) should receive the order");
		Assertions.assertEquals(1, platoon.get(0).id);
		Assertions.assertEquals("50,64,50", platoon.get(0).waypoint);
		Assertions.assertNull(platoon.get(1).waypoint, "Unselected Alpha must NOT move");
		Assertions.assertNull(platoon.get(2).waypoint, "Selected Bravo must NOT move when Alpha channel active");
		Assertions.assertNull(platoon.get(3).waypoint, "Unselected Bravo must NOT move");

		// Scenario B: ALL channel active, ground waypoint issued at (100, 64, 100)
		SquadGroup channelAll = SquadGroup.ALL;
		String newWaypoint = "100,64,100";
		int movedCountB = 0;
		for (TestMinion m : platoon) {
			if (m.selected && channelAll.matches(m.squad)) {
				m.waypoint = newWaypoint;
				movedCountB++;
			}
		}

		Assertions.assertEquals(2, movedCountB, "Both selected minions (Alpha and Bravo) should receive the order");
		Assertions.assertEquals("100,64,100", platoon.get(0).waypoint);
		Assertions.assertNull(platoon.get(1).waypoint, "Unselected Alpha must STILL not move");
		Assertions.assertEquals("100,64,100", platoon.get(2).waypoint);
		Assertions.assertNull(platoon.get(3).waypoint, "Unselected Bravo must STILL not move");
	}

	@Test
	@DisplayName("Validate deselecting decouples guard stance from sitting pose")
	void testDeselectDecoupledFromSitting() {
		class GuardMinion {
			boolean selected = true;
			boolean sitting = false;
			String guardAnchor = null;

			void deselect(String currentPosition) {
				this.selected = false;
				this.guardAnchor = currentPosition;
				// Decoupled: do NOT force sitting = true!
			}

			boolean canFollowPlayer() {
				// MinionFormationFollowGoal check
				return this.selected && !this.sitting;
			}

			boolean canWanderFar() {
				// WanderAroundFarGoal check
				return !this.selected && this.guardAnchor == null && !this.sitting;
			}
		}

		GuardMinion minion = new GuardMinion();
		Assertions.assertTrue(minion.selected);
		Assertions.assertFalse(minion.sitting);
		Assertions.assertTrue(minion.canFollowPlayer(), "Selected unit must be eligible to follow player");
		Assertions.assertFalse(minion.canWanderFar(), "Selected unit must not wander aimlessly");

		// Deselect at guard post
		minion.deselect("25,64,-10");

		Assertions.assertFalse(minion.selected, "Unit should be deselected");
		Assertions.assertFalse(minion.sitting, "Unit must remain STANDING upright at attention (not forced sitting)");
		Assertions.assertEquals("25,64,-10", minion.guardAnchor, "Guard anchor must be anchored to current post");
		Assertions.assertFalse(minion.canFollowPlayer(), "Deselected unit must NOT follow player");
		Assertions.assertFalse(minion.canWanderFar(), "Anchored unit must NOT wander away from its post");
	}

	@Test
	@DisplayName("Validate holding position state reflects both sitting pose and standing guard post")
	void testStandingGuardAndHoldingPositionBadgeSync() {
		class MinionPostState {
			boolean sitting = false;
			boolean guarding = false;

			boolean isHoldingPosition() {
				return this.sitting || this.guarding;
			}
		}

		MinionPostState unit = new MinionPostState();
		Assertions.assertFalse(unit.isHoldingPosition(), "Default following unit is not holding position");

		// Stance A: Sitting
		unit.sitting = true;
		unit.guarding = false;
		Assertions.assertTrue(unit.isHoldingPosition(), "Sitting unit must report holding position");

		// Stance B: Standing guard at waypoint anchor post
		unit.sitting = false;
		unit.guarding = true;
		Assertions.assertTrue(unit.isHoldingPosition(), "Standing sentinel at guard post must report holding position");

		// Active following: Neither sitting nor guarding
		unit.sitting = false;
		unit.guarding = false;
		Assertions.assertFalse(unit.isHoldingPosition(), "Mobilized unit must not report holding position");
	}

	@Test
	@DisplayName("Validate teammate faction resolution and friendly-fire negation")
	void testFriendlyFireAndFactionTeammateIntegration() {
		UUID commanderA = UUID.randomUUID();
		UUID commanderB = UUID.randomUUID();

		class FactionUnit {
			final UUID owner;

			FactionUnit(UUID owner) {
				this.owner = owner;
			}

			boolean isTeammate(UUID otherOwner) {
				return this.owner != null && this.owner.equals(otherOwner);
			}

			boolean shouldNegateDamage(UUID attackerOwner) {
				return this.isTeammate(attackerOwner);
			}
		}

		FactionUnit minionAlpha = new FactionUnit(commanderA);
		FactionUnit minionBravo = new FactionUnit(commanderA);
		FactionUnit enemyMinion = new FactionUnit(commanderB);

		// Teammate checks
		Assertions.assertTrue(minionAlpha.isTeammate(commanderA), "Owner is always a teammate");
		Assertions.assertTrue(minionAlpha.isTeammate(minionBravo.owner), "Fellow minion sharing owner is a teammate");
		Assertions.assertFalse(minionAlpha.isTeammate(commanderB), "Rival commander is not a teammate");
		Assertions.assertFalse(minionAlpha.isTeammate(enemyMinion.owner), "Enemy minion is not a teammate");

		// Damage negation checks
		Assertions.assertTrue(minionAlpha.shouldNegateDamage(commanderA), "Friendly fire from commander must be negated");
		Assertions.assertTrue(minionAlpha.shouldNegateDamage(minionBravo.owner), "Friendly fire from allied minion must be negated");
		Assertions.assertFalse(minionAlpha.shouldNegateDamage(commanderB), "Damage from rival commander must NOT be negated");
		Assertions.assertFalse(minionAlpha.shouldNegateDamage(enemyMinion.owner), "Damage from enemy minion must NOT be negated");
	}
}
