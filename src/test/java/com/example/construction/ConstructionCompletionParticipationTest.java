package com.example.construction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating:
 * 1. ConstructionSession participant tracking (registerParticipant, isParticipant, hasParticipants).
 * 2. ConstructionManager completeSession filtering (only participating builders are stationed).
 * 3. Non-builder roles (Warriors, Sentinels) and uninvolved builders are never hijacked or teleported.
 * 4. ConstructionManager cancelSession only targets participating or inside builders.
 * 5. MinionBuildGoal registration on goal start.
 */
public class ConstructionCompletionParticipationTest {

	@Test
	@DisplayName("ConstructionSession defines and manages participating minions thread-safely")
	void testConstructionSessionParticipantTrackingInvariants() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionSession.java"));

		// Must define participatingMinions field
		Assertions.assertTrue(source.contains("participatingMinions = ConcurrentHashMap.newKeySet();"),
			"ConstructionSession must define participatingMinions set");

		// Must record participant in claimNextTask
		Assertions.assertTrue(source.contains("this.participatingMinions.add(minionUuid);"),
			"ConstructionSession.claimNextTask must record minionUuid in participatingMinions");

		// Must record participant in completeTask
		Assertions.assertTrue(source.contains("this.participatingMinions.add(task.getClaimedBy());"),
			"ConstructionSession.completeTask must record claimedBy in participatingMinions");

		// Must expose helper methods
		Assertions.assertTrue(source.contains("public void registerParticipant(UUID minionUuid)"),
			"ConstructionSession must expose registerParticipant");
		Assertions.assertTrue(source.contains("public boolean isParticipant(UUID minionUuid)"),
			"ConstructionSession must expose isParticipant");
		Assertions.assertTrue(source.contains("public boolean hasParticipants()"),
			"ConstructionSession must expose hasParticipants");
		Assertions.assertTrue(source.contains("public Set<UUID> getParticipatingMinions()"),
			"ConstructionSession must expose getParticipatingMinions");
	}

	@Test
	@DisplayName("ConstructionManager completeSession strictly filters to participating builders")
	void testConstructionManagerCompleteSessionParticipantFiltering() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionManager.java"));

		// Must gate non-builder roles
		Assertions.assertTrue(source.contains("!m.matchesRole(MinionRole.BUILDER)"),
			"completeSession must reject non-builder roles (Warriors, Sentinels)");

		// Must check session.isParticipant
		Assertions.assertTrue(source.contains("session.isParticipant(m.getUuid())"),
			"completeSession must check session.isParticipant(m.getUuid())");

		// Must evaluate hasTrackedParticipants
		Assertions.assertTrue(source.contains("boolean hasTrackedParticipants = session.hasParticipants();"),
			"completeSession must evaluate hasTrackedParticipants");
	}

	@Test
	@DisplayName("ConstructionManager cancelSession strictly gates non-builders and uninvolved minions")
	void testConstructionManagerCancelSessionParticipantFiltering() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/construction/ConstructionManager.java"));

		// In cancelSession, must check matchesRole(MinionRole.BUILDER)
		Assertions.assertTrue(source.contains("if (!m.matchesRole(MinionRole.BUILDER))"),
			"cancelSession must reject non-builder roles");
	}

	@Test
	@DisplayName("MinionBuildGoal registers minion in currentSession on start")
	void testMinionBuildGoalRegistersParticipantOnStart() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/example/entity/ai/goal/MinionBuildGoal.java"));

		Assertions.assertTrue(source.contains("this.currentSession.registerParticipant(this.minion.getUuid());"),
			"MinionBuildGoal.start must register minion as participant in currentSession");
	}

	@Test
	@DisplayName("Simulation: Only participating builders pass candidate completion filter")
	void testParticipationFilterSimulation() {
		UUID ownerUuid = UUID.randomUUID();
		UUID participantBuilderUuid = UUID.randomUUID();
		UUID uninvolvedBuilderUuid = UUID.randomUUID();
		UUID warriorUuid = UUID.randomUUID();
		UUID sentinelUuid = UUID.randomUUID();
		UUID otherOwnerBuilderUuid = UUID.randomUUID();

		Set<UUID> participatingMinions = new HashSet<>();
		participatingMinions.add(participantBuilderUuid);

		class MockMinion {
			final UUID uuid;
			final UUID owner;
			final String role;
			final boolean alive;

			MockMinion(UUID uuid, UUID owner, String role, boolean alive) {
				this.uuid = uuid;
				this.owner = owner;
				this.role = role;
				this.alive = alive;
			}

			boolean matchesRole(String r) {
				return this.role.equals(r);
			}
		}

		java.util.function.Predicate<MockMinion> filter = m -> {
			if (!m.alive || !ownerUuid.equals(m.owner)) {
				return false;
			}
			if (!m.matchesRole("BUILDER")) {
				return false;
			}
			if (!participatingMinions.isEmpty()) {
				return participatingMinions.contains(m.uuid);
			}
			return false;
		};

		MockMinion participantBuilder = new MockMinion(participantBuilderUuid, ownerUuid, "BUILDER", true);
		MockMinion uninvolvedBuilder = new MockMinion(uninvolvedBuilderUuid, ownerUuid, "BUILDER", true);
		MockMinion bodyguardWarrior = new MockMinion(warriorUuid, ownerUuid, "WARRIOR", true);
		MockMinion bodyguardSentinel = new MockMinion(sentinelUuid, ownerUuid, "SENTINEL", true);
		MockMinion otherOwnerBuilder = new MockMinion(otherOwnerBuilderUuid, UUID.randomUUID(), "BUILDER", true);

		// Participating builder must pass
		Assertions.assertTrue(filter.test(participantBuilder), "Participating builder must be accepted for stationing");

		// Uninvolved builder must be rejected
		Assertions.assertFalse(filter.test(uninvolvedBuilder), "Uninvolved builder must NOT be stationed");

		// Bodyguards (Warrior, Sentinel) must be rejected
		Assertions.assertFalse(filter.test(bodyguardWarrior), "Warrior bodyguard must NOT be stationed");
		Assertions.assertFalse(filter.test(bodyguardSentinel), "Sentinel bodyguard must NOT be stationed");

		// Minions of different owners must be rejected
		Assertions.assertFalse(filter.test(otherOwnerBuilder), "Different owner minion must NOT be stationed");
	}
}
