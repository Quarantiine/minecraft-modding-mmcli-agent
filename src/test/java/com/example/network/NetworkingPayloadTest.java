package com.example.network;

import com.example.ExampleMod;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating networking payloads:
 * - {@link UpdateMinionConfigPayload}
 * - {@link UpdateScepterPayload}
 */
public class NetworkingPayloadTest {

	@Test
	@DisplayName("Validate UpdateMinionConfigPayload fields, IDs, and records")
	void testUpdateMinionConfigPayload() {
		UpdateMinionConfigPayload payload = new UpdateMinionConfigPayload(42, MinionRole.SENTINEL, SquadGroup.BRAVO);

		Assertions.assertEquals(42, payload.minionId());
		Assertions.assertEquals(MinionRole.SENTINEL, payload.role());
		Assertions.assertEquals(SquadGroup.BRAVO, payload.squad());
		Assertions.assertEquals(UpdateMinionConfigPayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("update_minion_config", payload.getId().id().getPath());
		Assertions.assertNotNull(UpdateMinionConfigPayload.PACKET_CODEC);

		// Record equality & hashing
		UpdateMinionConfigPayload copy = new UpdateMinionConfigPayload(42, MinionRole.SENTINEL, SquadGroup.BRAVO);
		Assertions.assertEquals(payload, copy);
		Assertions.assertEquals(payload.hashCode(), copy.hashCode());

		UpdateMinionConfigPayload different = new UpdateMinionConfigPayload(42, MinionRole.RANGER, SquadGroup.CHARLIE);
		Assertions.assertNotEquals(payload, different);
	}

	@Test
	@DisplayName("Validate UpdateScepterPayload squad group channel, IDs, and backward-compatible constructor")
	void testUpdateScepterPayload() {
		UpdateScepterPayload payloadWithSquad = new UpdateScepterPayload(
			CommandMode.ATTACK,
			"modid-mmcli-agent-modding:watchtower",
			SquadGroup.DELTA,
			true
		);

		Assertions.assertEquals(CommandMode.ATTACK, payloadWithSquad.mode());
		Assertions.assertEquals("modid-mmcli-agent-modding:watchtower", payloadWithSquad.blueprintId());
		Assertions.assertEquals(SquadGroup.DELTA, payloadWithSquad.targetSquad());
		Assertions.assertTrue(payloadWithSquad.executeDirective());
		Assertions.assertEquals(UpdateScepterPayload.ID, payloadWithSquad.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payloadWithSquad.getId().id().getNamespace());
		Assertions.assertEquals("update_scepter", payloadWithSquad.getId().id().getPath());
		Assertions.assertNotNull(UpdateScepterPayload.PACKET_CODEC);

		// Backward-compatible constructor defaulting to SquadGroup.ALL
		UpdateScepterPayload legacyPayload = new UpdateScepterPayload(
			CommandMode.BUILD,
			"modid-mmcli-agent-modding:obelisk",
			false
		);

		Assertions.assertEquals(CommandMode.BUILD, legacyPayload.mode());
		Assertions.assertEquals("modid-mmcli-agent-modding:obelisk", legacyPayload.blueprintId());
		Assertions.assertEquals(SquadGroup.ALL, legacyPayload.targetSquad());
		Assertions.assertFalse(legacyPayload.executeDirective());

		// Equality
		UpdateScepterPayload explicitAll = new UpdateScepterPayload(
			CommandMode.BUILD,
			"modid-mmcli-agent-modding:obelisk",
			SquadGroup.ALL,
			false
		);
		Assertions.assertEquals(legacyPayload, explicitAll);
	}
}
