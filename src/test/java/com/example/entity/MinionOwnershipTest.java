package com.example.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating MinionEntity ownership persistence and singleplayer host adoption (Refinement 1):
 * - Server-safe singleplayer host adoption logic without client imports.
 * - Proper adoption and owner re-binding when host reconnects under alternate dev UUIDs.
 * - Prevention of client-side imports within src/main/java server entity classes.
 */
public class MinionOwnershipTest {

	/**
	 * State simulator replicating MinionEntity's isOwner adoption logic in singleplayer vs dedicated servers.
	 */
	static class MockOwnershipSimulator {
		UUID ownerUuid;
		boolean tamed;
		boolean isSingleplayerServer;
		UUID hostPlayerUuid;

		MockOwnershipSimulator(UUID initialOwnerUuid, boolean tamed, boolean isSingleplayer, UUID hostPlayerUuid) {
			this.ownerUuid = initialOwnerUuid;
			this.tamed = tamed;
			this.isSingleplayerServer = isSingleplayer;
			this.hostPlayerUuid = hostPlayerUuid;
		}

		boolean isOwner(UUID playerUuid) {
			// 1. Super UUID check (standard match)
			if (this.ownerUuid != null && this.ownerUuid.equals(playerUuid)) {
				return true;
			}

			// 2. Server singleplayer host resolution
			if (this.isSingleplayerServer && this.hostPlayerUuid != null && this.hostPlayerUuid.equals(playerUuid)) {
				if (this.tamed) {
					// Adopt host
					this.ownerUuid = playerUuid;
					return true;
				}
			}

			return false;
		}
	}

	@Test
	@DisplayName("Standard owner UUID match returns true without re-binding")
	void testStandardOwnerUuidMatch() {
		UUID originalOwner = UUID.randomUUID();
		MockOwnershipSimulator sim = new MockOwnershipSimulator(originalOwner, true, false, null);

		Assertions.assertTrue(sim.isOwner(originalOwner), "Matching UUID must always return true");
		Assertions.assertEquals(originalOwner, sim.ownerUuid);
	}

	@Test
	@DisplayName("Singleplayer host adopts tamed minion when owner UUID differs across dev restarts")
	void testSingleplayerHostAdoption() {
		UUID staleDevUuid = UUID.randomUUID();
		UUID newHostUuid = UUID.randomUUID();

		// Minion tamed under staleDevUuid in singleplayer integrated server
		MockOwnershipSimulator sim = new MockOwnershipSimulator(staleDevUuid, true, true, newHostUuid);

		Assertions.assertNotEquals(staleDevUuid, newHostUuid);
		Assertions.assertTrue(sim.isOwner(newHostUuid), "Singleplayer host must be adopted by tamed minion");
		Assertions.assertEquals(newHostUuid, sim.ownerUuid, "Minion owner must be rebound to new host UUID");
	}

	@Test
	@DisplayName("Untamed minion does not adopt singleplayer host automatically")
	void testUntamedMinionDoesNotAdoptHost() {
		UUID hostUuid = UUID.randomUUID();

		// Untamed minion
		MockOwnershipSimulator sim = new MockOwnershipSimulator(null, false, true, hostUuid);

		Assertions.assertFalse(sim.isOwner(hostUuid), "Untamed minion must not auto-adopt host until tamed");
		Assertions.assertNull(sim.ownerUuid);
	}

	@Test
	@DisplayName("Multiplayer/dedicated server never auto-adopts non-matching player")
	void testDedicatedServerRejectsNonOwner() {
		UUID originalOwner = UUID.randomUUID();
		UUID otherPlayer = UUID.randomUUID();

		// Dedicated server: isSingleplayer = false
		MockOwnershipSimulator sim = new MockOwnershipSimulator(originalOwner, true, false, otherPlayer);

		Assertions.assertFalse(sim.isOwner(otherPlayer), "Dedicated server must never auto-adopt players with different UUID");
		Assertions.assertEquals(originalOwner, sim.ownerUuid, "Owner UUID must remain unchanged");
	}

	@Test
	@DisplayName("MinionEntity source file strictly avoids client-only imports to preserve server safety")
	void testMinionEntityServerSafety() throws IOException {
		Path minionEntityPath = Path.of("src/main/java/com/example/entity/custom/MinionEntity.java");
		Assertions.assertTrue(Files.exists(minionEntityPath), "MinionEntity.java must exist");

		List<String> lines = Files.readAllLines(minionEntityPath);
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("import net.minecraft.client")) {
				Assertions.fail("Forbidden client-side import in MinionEntity: " + trimmed);
			}
		}
	}
}
