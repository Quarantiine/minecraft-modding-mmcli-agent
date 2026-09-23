package com.example.network;

import com.example.ExampleMod;
import com.example.blueprint.BlueprintBlock;
import com.example.blueprint.StructureBlueprint;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating network payload records, custom identifiers, packet codecs,
 * backwards-compatible constructor overloads, and serialization integrity.
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

		// Payload with explicit target role and active patrol route channel
		UpdateScepterPayload payloadWithRole = new UpdateScepterPayload(
			CommandMode.FOLLOW,
			"modid-mmcli-agent-modding:watchtower",
			SquadGroup.ALPHA,
			1,
			Optional.of(MinionRole.WARRIOR),
			2,
			false
		);
		Assertions.assertEquals(Optional.of(MinionRole.WARRIOR), payloadWithRole.targetRole());
		Assertions.assertEquals(2, payloadWithRole.activePatrolRoute());

		// Payload clearing target role
		UpdateScepterPayload payloadClearRole = new UpdateScepterPayload(
			CommandMode.FOLLOW,
			"modid-mmcli-agent-modding:watchtower",
			SquadGroup.ALPHA,
			1,
			Optional.empty(),
			3,
			false
		);
		Assertions.assertEquals(Optional.empty(), payloadClearRole.targetRole());
		Assertions.assertEquals(3, payloadClearRole.activePatrolRoute());
	}

	@Test
	@DisplayName("Validate UpdateScepterPayload PacketCodec encode and decode roundtrip with target role and active patrol route channel")
	void testUpdateScepterPayloadPacketCodecRoundtrip() {
		// Roundtrip for all 4 MinionRole archetypes wrapped in Optional.of with varying route IDs
		int routeIndex = 0;
		for (MinionRole role : MinionRole.values()) {
			UpdateScepterPayload original = new UpdateScepterPayload(
				CommandMode.FOLLOW,
				"modid-mmcli-agent-modding:watchtower",
				SquadGroup.BRAVO,
				2,
				Optional.of(role),
				routeIndex,
				true
			);

			RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
			try {
				UpdateScepterPayload.PACKET_CODEC.encode(buf, original);
				Assertions.assertTrue(buf.readableBytes() > 0, "Buffer must contain encoded bytes for role " + role);

				UpdateScepterPayload decoded = UpdateScepterPayload.PACKET_CODEC.decode(buf);
				Assertions.assertEquals(original, decoded, "Decoded payload must match original for role " + role);
				Assertions.assertEquals(Optional.of(role), decoded.targetRole());
				Assertions.assertEquals(routeIndex, decoded.activePatrolRoute());
				Assertions.assertEquals(CommandMode.FOLLOW, decoded.mode());
				Assertions.assertEquals("modid-mmcli-agent-modding:watchtower", decoded.blueprintId());
				Assertions.assertEquals(SquadGroup.BRAVO, decoded.targetSquad());
				Assertions.assertEquals(2, decoded.rotation());
				Assertions.assertTrue(decoded.executeDirective());
				Assertions.assertEquals(0, buf.readableBytes(), "All bytes must be consumed from buffer for role " + role);
			} finally {
				buf.release();
			}
			routeIndex++;
		}

		// Roundtrip for Optional.empty() (cleared target archetype) with route channel 4
		UpdateScepterPayload emptyRolePayload = new UpdateScepterPayload(
			CommandMode.BUILD,
			"modid-mmcli-agent-modding:obelisk",
			SquadGroup.ALL,
			0,
			Optional.empty(),
			4,
			false
		);
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			UpdateScepterPayload.PACKET_CODEC.encode(buf, emptyRolePayload);
			Assertions.assertTrue(buf.readableBytes() > 0, "Buffer must contain encoded bytes for empty role");

			UpdateScepterPayload decoded = UpdateScepterPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(emptyRolePayload, decoded, "Decoded payload must match empty role payload");
			Assertions.assertTrue(decoded.targetRole().isEmpty(), "Target role must be empty");
			Assertions.assertEquals(4, decoded.activePatrolRoute());
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

	@Test
	@DisplayName("Validate CreateCustomBlueprintPayload fields, structure compilation, IDs, and packet codec")
	void testCreateCustomBlueprintPayload() {
		List<BlueprintBlock> blocks = List.of();
		CreateCustomBlueprintPayload payload = new CreateCustomBlueprintPayload("custom_tower", "Custom Tower", "A simple tower", blocks);

		Assertions.assertEquals("custom_tower", payload.id());
		Assertions.assertEquals("Custom Tower", payload.name());
		Assertions.assertEquals("A simple tower", payload.description());
		Assertions.assertEquals(0, payload.blocks().size());
		Assertions.assertEquals(CreateCustomBlueprintPayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("create_custom_blueprint", payload.getId().id().getPath());
		Assertions.assertNotNull(CreateCustomBlueprintPayload.PACKET_CODEC);

		// Compilation into StructureBlueprint
		StructureBlueprint bp = payload.toStructureBlueprint();
		Assertions.assertNotNull(bp);
		Assertions.assertEquals("custom_tower", bp.getId());
		Assertions.assertEquals("Custom Tower", bp.getName());
		Assertions.assertEquals("A simple tower", bp.getDescription());
		Assertions.assertEquals(0, bp.getBlockCount());

		// StructureBlueprint constructor overload
		CreateCustomBlueprintPayload fromBp = new CreateCustomBlueprintPayload(bp);
		Assertions.assertEquals(payload.id(), fromBp.id());
		Assertions.assertEquals(payload.name(), fromBp.name());
		Assertions.assertEquals(payload.blocks().size(), fromBp.blocks().size());
	}

	@Test
	@DisplayName("Validate DeleteCustomBlueprintPayload fields, IDs, and packet codec")
	void testDeleteCustomBlueprintPayload() {
		DeleteCustomBlueprintPayload payload = new DeleteCustomBlueprintPayload("custom_watchtower");

		Assertions.assertEquals("custom_watchtower", payload.blueprintId());
		Assertions.assertEquals(DeleteCustomBlueprintPayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("delete_custom_blueprint", payload.getId().id().getPath());
		Assertions.assertNotNull(DeleteCustomBlueprintPayload.PACKET_CODEC);

		// Equality & hashing
		DeleteCustomBlueprintPayload copy = new DeleteCustomBlueprintPayload("custom_watchtower");
		Assertions.assertEquals(payload, copy);
		Assertions.assertEquals(payload.hashCode(), copy.hashCode());

		DeleteCustomBlueprintPayload different = new DeleteCustomBlueprintPayload("other_blueprint");
		Assertions.assertNotEquals(payload, different);
	}

	@Test
	@DisplayName("Validate SyncCustomBlueprintsPayload fields, IDs, and packet codec")
	void testSyncCustomBlueprintsPayload() {
		StructureBlueprint bp1 = StructureBlueprint.builder("custom_1", "One")
			.description("Desc 1")
			.build();
		StructureBlueprint bp2 = StructureBlueprint.builder("custom_2", "Two")
			.description("Desc 2")
			.build();

		SyncCustomBlueprintsPayload payload = new SyncCustomBlueprintsPayload(List.of(bp1, bp2));

		Assertions.assertEquals(2, payload.blueprints().size());
		Assertions.assertEquals(SyncCustomBlueprintsPayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("sync_custom_blueprints", payload.getId().id().getPath());
		Assertions.assertNotNull(SyncCustomBlueprintsPayload.PACKET_CODEC);
	}

	@Test
	@DisplayName("Validate CaptureSpatialBlueprintPayload fields, IDs, constructors, and packet codec roundtrip")
	void testCaptureSpatialBlueprintPayload() {
		BlockPos pos1 = new BlockPos(10, 64, -20);
		BlockPos pos2 = new BlockPos(15, 70, -15);
		CaptureSpatialBlueprintPayload payload = new CaptureSpatialBlueprintPayload(
			"custom_fortress",
			"Mini Fortress",
			"Spatial capture of mini fortress",
			pos1,
			pos2
		);

		Assertions.assertEquals("custom_fortress", payload.id());
		Assertions.assertEquals("Mini Fortress", payload.name());
		Assertions.assertEquals("Spatial capture of mini fortress", payload.description());
		Assertions.assertEquals(pos1, payload.pos1());
		Assertions.assertEquals(pos2, payload.pos2());
		Assertions.assertEquals(CaptureSpatialBlueprintPayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("capture_spatial_blueprint", payload.getId().id().getPath());
		Assertions.assertNotNull(CaptureSpatialBlueprintPayload.PACKET_CODEC);

		// Convenience constructor
		CaptureSpatialBlueprintPayload conv = new CaptureSpatialBlueprintPayload("custom_short", "Short", pos1, pos2);
		Assertions.assertEquals("", conv.description());

		// Equality and hashing
		CaptureSpatialBlueprintPayload copy = new CaptureSpatialBlueprintPayload(
			"custom_fortress",
			"Mini Fortress",
			"Spatial capture of mini fortress",
			pos1,
			pos2
		);
		Assertions.assertEquals(payload, copy);
		Assertions.assertEquals(payload.hashCode(), copy.hashCode());

		// Packet codec roundtrip
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			CaptureSpatialBlueprintPayload.PACKET_CODEC.encode(buf, payload);
			Assertions.assertTrue(buf.readableBytes() > 0);

			CaptureSpatialBlueprintPayload decoded = CaptureSpatialBlueprintPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(payload, decoded);
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}

	@Test
	@DisplayName("Validate CommandMode DESIGN enum properties, cycling, and packet codec")
	void testCommandModeDesign() {
		CommandMode design = CommandMode.DESIGN;
		Assertions.assertEquals("design", design.asString());
		Assertions.assertEquals("Design", design.getDisplayName());
		Assertions.assertEquals("§d", design.getColorCode());
		Assertions.assertEquals("§dDesign", design.getFormattedName());
		Assertions.assertEquals(1.7F, design.getPitch());

		// Next and previous cycling
		Assertions.assertEquals(CommandMode.DESIGN, CommandMode.BUILD.next());
		Assertions.assertEquals(CommandMode.RECRUIT, CommandMode.DESIGN.next());
		Assertions.assertEquals(CommandMode.BUILD, CommandMode.DESIGN.previous());

		// ByteBuf roundtrip via PACKET_CODEC
		io.netty.buffer.ByteBuf buf = Unpooled.buffer();
		try {
			CommandMode.PACKET_CODEC.encode(buf, CommandMode.DESIGN);
			Assertions.assertTrue(buf.readableBytes() > 0);
			CommandMode decoded = CommandMode.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(CommandMode.DESIGN, decoded);
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}

	@Test
	@DisplayName("Validate StartMiningAreaPayload fields, IDs, constructors, and packet codec roundtrip")
	void testStartMiningAreaPayload() {
		BlockPos pos1 = new BlockPos(100, 60, -200);
		BlockPos pos2 = new BlockPos(115, 75, -185);
		StartMiningAreaPayload payload = new StartMiningAreaPayload(pos1, pos2);

		Assertions.assertEquals(pos1, payload.pos1());
		Assertions.assertEquals(pos2, payload.pos2());
		Assertions.assertEquals(StartMiningAreaPayload.ID, payload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payload.getId().id().getNamespace());
		Assertions.assertEquals("start_mining_area", payload.getId().id().getPath());
		Assertions.assertNotNull(StartMiningAreaPayload.PACKET_CODEC);

		// Record equality & hashing
		StartMiningAreaPayload copy = new StartMiningAreaPayload(pos1, pos2);
		Assertions.assertEquals(payload, copy);
		Assertions.assertEquals(payload.hashCode(), copy.hashCode());

		StartMiningAreaPayload different = new StartMiningAreaPayload(pos1, new BlockPos(120, 80, -180));
		Assertions.assertNotEquals(payload, different);

		// Packet codec roundtrip
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			StartMiningAreaPayload.PACKET_CODEC.encode(buf, payload);
			Assertions.assertTrue(buf.readableBytes() > 0);

			StartMiningAreaPayload decoded = StartMiningAreaPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(payload, decoded);
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}

		// Extreme/negative coordinates roundtrip (e.g. subterranean Y = -64 to Y = 320)
		BlockPos deepPos1 = new BlockPos(-1500, -64, -3000);
		BlockPos skyPos2 = new BlockPos(2000, 319, 4500);
		StartMiningAreaPayload deepPayload = new StartMiningAreaPayload(deepPos1, skyPos2);

		RegistryByteBuf deepBuf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			StartMiningAreaPayload.PACKET_CODEC.encode(deepBuf, deepPayload);
			Assertions.assertTrue(deepBuf.readableBytes() > 0);

			StartMiningAreaPayload decodedDeep = StartMiningAreaPayload.PACKET_CODEC.decode(deepBuf);
			Assertions.assertEquals(deepPayload, decodedDeep);
			Assertions.assertEquals(deepPos1, decodedDeep.pos1());
			Assertions.assertEquals(skyPos2, decodedDeep.pos2());
			Assertions.assertEquals(0, deepBuf.readableBytes());
		} finally {
			deepBuf.release();
		}
	}

	@Test
	@DisplayName("Validate MiningMode enum properties, cycling, and packet codec")
	void testMiningModeEnum() {
		com.example.component.MiningMode direct = com.example.component.MiningMode.DIRECT;
		com.example.component.MiningMode area = com.example.component.MiningMode.AREA;

		Assertions.assertEquals("direct", direct.asString());
		Assertions.assertEquals("Direct / Structure", direct.getDisplayName());
		Assertions.assertEquals("§6", direct.getColorCode());
		Assertions.assertEquals("§6Direct / Structure", direct.getFormattedName());
		Assertions.assertEquals(1, direct.getId());
		Assertions.assertEquals("mining_mode.modid-mmcli-agent-modding.direct", direct.getTranslationKey());

		Assertions.assertEquals("area", area.asString());
		Assertions.assertEquals("Custom Area", area.getDisplayName());
		Assertions.assertEquals("§e", area.getColorCode());
		Assertions.assertEquals("§eCustom Area", area.getFormattedName());
		Assertions.assertEquals(0, area.getId());
		Assertions.assertEquals("mining_mode.modid-mmcli-agent-modding.area", area.getTranslationKey());

		// Cycling
		Assertions.assertEquals(area, direct.next());
		Assertions.assertEquals(direct, area.next());
		Assertions.assertEquals(area, direct.previous());
		Assertions.assertEquals(direct, area.previous());

		// fromId (AREA is 0 and default fallback, DIRECT is 1)
		Assertions.assertEquals(area, com.example.component.MiningMode.fromId(0));
		Assertions.assertEquals(direct, com.example.component.MiningMode.fromId(1));
		Assertions.assertEquals(area, com.example.component.MiningMode.fromId(-1));
		Assertions.assertEquals(area, com.example.component.MiningMode.fromId(99));

		// ByteBuf roundtrip via PACKET_CODEC
		io.netty.buffer.ByteBuf buf = Unpooled.buffer();
		try {
			com.example.component.MiningMode.PACKET_CODEC.encode(buf, area);
			Assertions.assertTrue(buf.readableBytes() > 0);
			com.example.component.MiningMode decoded = com.example.component.MiningMode.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(area, decoded);
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}

	@Test
	@DisplayName("Validate SyncConstructionSessionPayload dimensions, backward compatibility, IDs, and packet codec roundtrip")
	void testSyncConstructionSessionPayload() {
		java.util.UUID sessionId = java.util.UUID.randomUUID();
		BlockPos anchorPos = new BlockPos(250, 64, -180);
		SyncConstructionSessionPayload payloadWithDims = new SyncConstructionSessionPayload(
			sessionId,
			anchorPos,
			"mining_area_12345",
			0,
			true,
			16,
			8,
			16
		);

		Assertions.assertEquals(sessionId, payloadWithDims.sessionId());
		Assertions.assertEquals(anchorPos, payloadWithDims.anchorPos());
		Assertions.assertEquals("mining_area_12345", payloadWithDims.blueprintId());
		Assertions.assertEquals(0, payloadWithDims.rotation());
		Assertions.assertTrue(payloadWithDims.isDismantle());
		Assertions.assertEquals(16, payloadWithDims.sizeX());
		Assertions.assertEquals(8, payloadWithDims.sizeY());
		Assertions.assertEquals(16, payloadWithDims.sizeZ());
		Assertions.assertEquals(SyncConstructionSessionPayload.ID, payloadWithDims.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, payloadWithDims.getId().id().getNamespace());
		Assertions.assertEquals("sync_construction_session", payloadWithDims.getId().id().getPath());
		Assertions.assertNotNull(SyncConstructionSessionPayload.PACKET_CODEC);

		// Backward-compatible constructor defaulting sizes to 0
		SyncConstructionSessionPayload legacyPayload = new SyncConstructionSessionPayload(
			sessionId,
			anchorPos,
			"modid-mmcli-agent-modding:watchtower",
			2,
			false
		);
		Assertions.assertEquals(0, legacyPayload.sizeX());
		Assertions.assertEquals(0, legacyPayload.sizeY());
		Assertions.assertEquals(0, legacyPayload.sizeZ());
		Assertions.assertFalse(legacyPayload.isDismantle());
		Assertions.assertEquals(2, legacyPayload.rotation());

		// Equality and hashing
		SyncConstructionSessionPayload copy = new SyncConstructionSessionPayload(
			sessionId,
			anchorPos,
			"mining_area_12345",
			0,
			true,
			16,
			8,
			16
		);
		Assertions.assertEquals(payloadWithDims, copy);
		Assertions.assertEquals(payloadWithDims.hashCode(), copy.hashCode());
		Assertions.assertNotEquals(payloadWithDims, legacyPayload);

		// Packet codec roundtrip
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			SyncConstructionSessionPayload.PACKET_CODEC.encode(buf, payloadWithDims);
			Assertions.assertTrue(buf.readableBytes() > 0);

			SyncConstructionSessionPayload decoded = SyncConstructionSessionPayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(payloadWithDims, decoded);
			Assertions.assertEquals(sessionId, decoded.sessionId());
			Assertions.assertEquals(anchorPos, decoded.anchorPos());
			Assertions.assertEquals("mining_area_12345", decoded.blueprintId());
			Assertions.assertEquals(0, decoded.rotation());
			Assertions.assertTrue(decoded.isDismantle());
			Assertions.assertEquals(16, decoded.sizeX());
			Assertions.assertEquals(8, decoded.sizeY());
			Assertions.assertEquals(16, decoded.sizeZ());
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}

	@Test
	@DisplayName("Validate ConfigurePatrolRoutePayload fields, IDs, constructors, and packet codec roundtrip")
	void testConfigurePatrolRoutePayload() {
		ConfigurePatrolRoutePayload savePayload = new ConfigurePatrolRoutePayload(
			ConfigurePatrolRoutePayload.Action.SAVE,
			1,
			"Citadel Perimeter",
			0x00E5FF,
			com.example.patrol.PatrolRoute.PatrolMode.LOOP
		);

		Assertions.assertEquals(ConfigurePatrolRoutePayload.Action.SAVE, savePayload.action());
		Assertions.assertEquals(1, savePayload.routeId());
		Assertions.assertEquals("Citadel Perimeter", savePayload.name());
		Assertions.assertEquals(0x00E5FF, savePayload.colorRgb());
		Assertions.assertEquals(com.example.patrol.PatrolRoute.PatrolMode.LOOP, savePayload.patrolMode());
		Assertions.assertEquals(ConfigurePatrolRoutePayload.ID, savePayload.getId());
		Assertions.assertEquals(ExampleMod.MOD_ID, savePayload.getId().id().getNamespace());
		Assertions.assertEquals("configure_patrol_route", savePayload.getId().id().getPath());
		Assertions.assertNotNull(ConfigurePatrolRoutePayload.PACKET_CODEC);

		// Packet codec roundtrip
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), null);
		try {
			ConfigurePatrolRoutePayload.PACKET_CODEC.encode(buf, savePayload);
			Assertions.assertTrue(buf.readableBytes() > 0);

			ConfigurePatrolRoutePayload decoded = ConfigurePatrolRoutePayload.PACKET_CODEC.decode(buf);
			Assertions.assertEquals(savePayload, decoded);
			Assertions.assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}

		// Delete payload
		ConfigurePatrolRoutePayload deletePayload = new ConfigurePatrolRoutePayload(
			ConfigurePatrolRoutePayload.Action.DELETE,
			1,
			"Citadel Perimeter",
			0,
			com.example.patrol.PatrolRoute.PatrolMode.LOOP
		);
		Assertions.assertEquals(ConfigurePatrolRoutePayload.Action.DELETE, deletePayload.action());
	}
}
