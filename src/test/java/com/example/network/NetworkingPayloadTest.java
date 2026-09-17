package com.example.network;

import com.example.ExampleMod;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.network.RegistryByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating networking payloads:
 * - {@link UpdateMinionConfigPayload}
 * - {@link UpdateScepterPayload}
 * - {@link TeleportMinionPayload}
 * - {@link DismissMinionPayload}
 * - {@link DeselectMinionsPayload}
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

		UpdateMinionConfigPayload different = new UpdateMinionConfigPayload(42, MinionRole.WARRIOR, SquadGroup.CHARLIE);
		Assertions.assertNotEquals(payload, different);
	}

	@Test
	@DisplayName("Validate UpdateScepterPayload squad group channel, rotation parameter, IDs, and backward-compatible constructors")
	void testUpdateScepterPayload() {
		UpdateScepterPayload payloadWithRotation = new UpdateScepterPayload(
			CommandMode.FOLLOW,
			"modid-mmcli-agent-modding:watchtower",
			SquadGroup.DELTA,
			2,
			true
		);

		Assertions.assertEquals(CommandMode.FOLLOW, payloadWithRotation.mode());
		Assertions.assertEquals("modid-mmcli-agent-modding:watchtower", payloadWithRotation.blueprintId());
		Assertions.assertEquals(SquadGroup.DELTA, payloadWithRotation.targetSquad());
		Assertions.assertEquals(2, payloadWithRotation.rotation());
		Assertions.assertTrue(payloadWithRotation.executeDirective());
		Assertions.assertEquals(UpdateScepterPayload.ID, payloadWithRotation.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payloadWithRotation.getId().id().getNamespace());
		Assertions.assertEquals("update_scepter", payloadWithRotation.getId().id().getPath());
		Assertions.assertNotNull(UpdateScepterPayload.PACKET_CODEC);

		// Backward-compatible constructor defaulting to rotation = 0
		UpdateScepterPayload payloadWithSquad = new UpdateScepterPayload(
			CommandMode.FOLLOW,
			"modid-mmcli-agent-modding:watchtower",
			SquadGroup.DELTA,
			true
		);
		Assertions.assertEquals(0, payloadWithSquad.rotation());
		Assertions.assertEquals(SquadGroup.DELTA, payloadWithSquad.targetSquad());

		// Backward-compatible constructor defaulting to SquadGroup.ALL and rotation = 0
		UpdateScepterPayload legacyPayload = new UpdateScepterPayload(
			CommandMode.BUILD,
			"modid-mmcli-agent-modding:obelisk",
			false
		);

		Assertions.assertEquals(CommandMode.BUILD, legacyPayload.mode());
		Assertions.assertEquals("modid-mmcli-agent-modding:obelisk", legacyPayload.blueprintId());
		Assertions.assertEquals(SquadGroup.ALL, legacyPayload.targetSquad());
		Assertions.assertEquals(0, legacyPayload.rotation());
		Assertions.assertFalse(legacyPayload.executeDirective());

		// Constructor with mode, blueprintId, rotation, and executeDirective
		UpdateScepterPayload rotOnlyPayload = new UpdateScepterPayload(
			CommandMode.BUILD,
			"modid-mmcli-agent-modding:obelisk",
			3,
			false
		);
		Assertions.assertEquals(SquadGroup.ALL, rotOnlyPayload.targetSquad());
		Assertions.assertEquals(3, rotOnlyPayload.rotation());

		// Equality
		UpdateScepterPayload explicitAll = new UpdateScepterPayload(
			CommandMode.BUILD,
			"modid-mmcli-agent-modding:obelisk",
			SquadGroup.ALL,
			0,
			false
		);
		Assertions.assertEquals(legacyPayload, explicitAll);

		// Payload with explicit target role
		UpdateScepterPayload payloadWithRole = new UpdateScepterPayload(
			CommandMode.FOLLOW,
			"modid-mmcli-agent-modding:watchtower",
			SquadGroup.ALPHA,
			1,
			Optional.of(MinionRole.WARRIOR),
			false
		);
		Assertions.assertEquals(Optional.of(MinionRole.WARRIOR), payloadWithRole.targetRole());

		// Payload clearing target role
		UpdateScepterPayload payloadClearRole = new UpdateScepterPayload(
			CommandMode.FOLLOW,
			"modid-mmcli-agent-modding:watchtower",
			SquadGroup.ALPHA,
			1,
			Optional.empty(),
			false
		);
		Assertions.assertEquals(Optional.empty(), payloadClearRole.targetRole());
	}

	@Test
	@DisplayName("Validate UpdateScepterPayload PacketCodec encode and decode roundtrip with target role and optional presence")
	void testUpdateScepterPayloadPacketCodecRoundtrip() {
		// Roundtrip for all 4 MinionRole archetypes wrapped in Optional.of
		for (MinionRole role : MinionRole.values()) {
			UpdateScepterPayload original = new UpdateScepterPayload(
				CommandMode.FOLLOW,
				"modid-mmcli-agent-modding:watchtower",
				SquadGroup.BRAVO,
				2,
				Optional.of(role),
				true
			);

			RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
			try {
				UpdateScepterPayload.PACKET_CODEC.encode(buf, original);
				Assertions.assertTrue(buf.readableBytes() > 0, "Buffer must contain encoded bytes for role " + role);

				UpdateScepterPayload decoded = UpdateScepterPayload.PACKET_CODEC.decode(buf);
				Assertions.assertEquals(original, decoded, "Decoded payload must match original for role " + role);
				Assertions.assertEquals(Optional.of(role), decoded.targetRole());
				Assertions.assertEquals(CommandMode.FOLLOW, decoded.mode());
				Assertions.assertEquals("modid-mmcli-agent-modding:watchtower", decoded.blueprintId());
				Assertions.assertEquals(SquadGroup.BRAVO, decoded.targetSquad());
				Assertions.assertEquals(2, decoded.rotation());
				Assertions.assertTrue(decoded.executeDirective());
				Assertions.assertEquals(0, buf.readableBytes(), "All bytes must be consumed from buffer for role " + role);
			} finally {
				buf.release();
			}
		}

		// Roundtrip for Optional.empty() (cleared target archetype)
		UpdateScepterPayload emptyRolePayload = new UpdateScepterPayload(
			CommandMode.BUILD,
			"modid-mmcli-agent-modding:obelisk",
			SquadGroup.ALL,
			0,
			Optional.empty(),
			false
		);
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			UpdateScepterPayload.PACKET_CODEC.encode(buf, emptyRolePayload);
			Assertions.assertTrue(buf.readableBytes() > 0, "Buffer must contain encoded bytes for empty role");

			UpdateScepterPayload decoded = UpdateScepterPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(emptyRolePayload, decoded, "Decoded payload must match empty role payload");
			Assertions.assertTrue(decoded.targetRole().isEmpty(), "Target role must be empty");
			Assertions.assertEquals(CommandMode.BUILD, decoded.mode());
			Assertions.assertEquals("modid-mmcli-agent-modding:obelisk", decoded.blueprintId());
			Assertions.assertEquals(SquadGroup.ALL, decoded.targetSquad());
			Assertions.assertEquals(0, decoded.rotation());
			Assertions.assertFalse(decoded.executeDirective());
			Assertions.assertEquals(0, buf.readableBytes(), "All bytes must be consumed from buffer for empty role");
		} finally {
			buf.release();
		}
	}

	@Test
	@DisplayName("Validate TeleportMinionPayload single minion and recall-all records and IDs")
	void testTeleportMinionPayload() {
		// Single minion teleport
		TeleportMinionPayload single = new TeleportMinionPayload(105, false);
		Assertions.assertEquals(105, single.minionId());
		Assertions.assertFalse(single.teleportAll());
		Assertions.assertEquals(TeleportMinionPayload.ID, single.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, single.getId().id().getNamespace());
		Assertions.assertEquals("teleport_minion", single.getId().id().getPath());
		Assertions.assertNotNull(TeleportMinionPayload.PACKET_CODEC);

		// Recall all minions
		TeleportMinionPayload all = new TeleportMinionPayload(-1, true);
		Assertions.assertEquals(-1, all.minionId());
		Assertions.assertTrue(all.teleportAll());

		// Single minion convenience constructor
		TeleportMinionPayload conv = new TeleportMinionPayload(200);
		Assertions.assertEquals(200, conv.minionId());
		Assertions.assertFalse(conv.teleportAll());

		// Negative id convenience constructor defaults to teleportAll = true
		TeleportMinionPayload convAll = new TeleportMinionPayload(-1);
		Assertions.assertEquals(-1, convAll.minionId());
		Assertions.assertTrue(convAll.teleportAll());

		// Equality and hashing
		TeleportMinionPayload copy = new TeleportMinionPayload(105, false);
		Assertions.assertEquals(single, copy);
		Assertions.assertEquals(single.hashCode(), copy.hashCode());
		Assertions.assertNotEquals(single, all);
	}

	@Test
	@DisplayName("Validate DismissMinionPayload single minion and dismiss-all records and IDs")
	void testDismissMinionPayload() {
		// Single minion dismiss
		DismissMinionPayload single = new DismissMinionPayload(88, false);
		Assertions.assertEquals(88, single.minionId());
		Assertions.assertFalse(single.dismissAll());
		Assertions.assertEquals(DismissMinionPayload.ID, single.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, single.getId().id().getNamespace());
		Assertions.assertEquals("dismiss_minion", single.getId().id().getPath());
		Assertions.assertNotNull(DismissMinionPayload.PACKET_CODEC);

		// Dismiss all minions
		DismissMinionPayload all = new DismissMinionPayload(-1, true);
		Assertions.assertEquals(-1, all.minionId());
		Assertions.assertTrue(all.dismissAll());

		// Convenience constructors
		DismissMinionPayload convSingle = new DismissMinionPayload(77);
		Assertions.assertEquals(77, convSingle.minionId());
		Assertions.assertFalse(convSingle.dismissAll());

		DismissMinionPayload convAll = new DismissMinionPayload(-1);
		Assertions.assertEquals(-1, convAll.minionId());
		Assertions.assertTrue(convAll.dismissAll());

		// Equality & hashing
		DismissMinionPayload copy = new DismissMinionPayload(88, false);
		Assertions.assertEquals(single, copy);
		Assertions.assertEquals(single.hashCode(), copy.hashCode());
		Assertions.assertNotEquals(single, all);
	}

	@Test
	@DisplayName("Validate DeselectMinionsPayload single minion and deselect-all records and IDs")
	void testDeselectMinionsPayload() {
		// Single minion deselect
		DeselectMinionsPayload single = new DeselectMinionsPayload(55, false);
		Assertions.assertEquals(55, single.minionId());
		Assertions.assertFalse(single.deselectAll());
		Assertions.assertEquals(DeselectMinionsPayload.ID, single.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, single.getId().id().getNamespace());
		Assertions.assertEquals("deselect_minions", single.getId().id().getPath());
		Assertions.assertNotNull(DeselectMinionsPayload.PACKET_CODEC);

		// Deselect all
		DeselectMinionsPayload all = new DeselectMinionsPayload();
		Assertions.assertEquals(-1, all.minionId());
		Assertions.assertTrue(all.deselectAll());

		// Convenience constructor
		DeselectMinionsPayload convSingle = new DeselectMinionsPayload(55);
		Assertions.assertEquals(55, convSingle.minionId());
		Assertions.assertFalse(convSingle.deselectAll());

		DeselectMinionsPayload convAll = new DeselectMinionsPayload(-5);
		Assertions.assertEquals(-5, convAll.minionId());
		Assertions.assertTrue(convAll.deselectAll());

		// Equality & hashing
		DeselectMinionsPayload copy = new DeselectMinionsPayload(55, false);
		Assertions.assertEquals(single, copy);
		Assertions.assertEquals(single.hashCode(), copy.hashCode());
		Assertions.assertNotEquals(single, all);
	}

	@Test
	@DisplayName("Validate RetreatPayload squad group channel, IDs, constructors, and packet codec")
	void testRetreatPayload() {
		RetreatPayload payload = new RetreatPayload(SquadGroup.BRAVO);
		Assertions.assertEquals(SquadGroup.BRAVO, payload.targetSquad());
		Assertions.assertEquals(RetreatPayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("retreat", payload.getId().id().getPath());
		Assertions.assertNotNull(RetreatPayload.PACKET_CODEC);

		// Default constructor
		RetreatPayload defaultPayload = new RetreatPayload();
		Assertions.assertEquals(SquadGroup.ALL, defaultPayload.targetSquad());

		// Equality
		RetreatPayload copy = new RetreatPayload(SquadGroup.BRAVO);
		Assertions.assertEquals(payload, copy);
		Assertions.assertEquals(payload.hashCode(), copy.hashCode());
		Assertions.assertNotEquals(payload, defaultPayload);
	}

	@Test
	@DisplayName("Validate AnchorConstructionPayload fields, IDs, side direction, and packet codec")
	void testAnchorConstructionPayload() {
		net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(120, 64, -250);
		AnchorConstructionPayload buildPayload = new AnchorConstructionPayload(pos, net.minecraft.util.math.Direction.UP, false);

		Assertions.assertEquals(pos, buildPayload.clickedPos());
		Assertions.assertEquals(net.minecraft.util.math.Direction.UP, buildPayload.side());
		Assertions.assertFalse(buildPayload.isDismantle());
		Assertions.assertEquals(AnchorConstructionPayload.ID, buildPayload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, buildPayload.getId().id().getNamespace());
		Assertions.assertEquals("anchor_construction", buildPayload.getId().id().getPath());
		Assertions.assertNotNull(AnchorConstructionPayload.PACKET_CODEC);

		// Dismantle variant
		AnchorConstructionPayload dismantlePayload = new AnchorConstructionPayload(pos, net.minecraft.util.math.Direction.NORTH, true);
		Assertions.assertEquals(pos, dismantlePayload.clickedPos());
		Assertions.assertEquals(net.minecraft.util.math.Direction.NORTH, dismantlePayload.side());
		Assertions.assertTrue(dismantlePayload.isDismantle());

		// Equality
		AnchorConstructionPayload copy = new AnchorConstructionPayload(pos, net.minecraft.util.math.Direction.UP, false);
		Assertions.assertEquals(buildPayload, copy);
		Assertions.assertEquals(buildPayload.hashCode(), copy.hashCode());
		Assertions.assertNotEquals(buildPayload, dismantlePayload);
	}
}
