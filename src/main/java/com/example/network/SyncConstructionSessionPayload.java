package com.example.network;

import com.example.ExampleMod;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

/**
 * Server-to-client (S2C) payload broadcasting the start or state of an active in-world construction session
 * so client-side holographic wireframe renderers can persistently project the 3D blueprint outline.
 *
 * @param sessionId    Unique session identifier.
 * @param anchorPos    World anchor coordinate where construction begins.
 * @param blueprintId  Identifier of the blueprint being constructed or dismantled.
 * @param rotation     Quadrant rotation (0..3).
 * @param isDismantle  True if this is a deconstruction session.
 */
public record SyncConstructionSessionPayload(
	UUID sessionId,
	BlockPos anchorPos,
	String blueprintId,
	int rotation,
	boolean isDismantle
) implements CustomPayload {

	public static final CustomPayload.Id<SyncConstructionSessionPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "sync_construction_session")
	);

	public static final PacketCodec<RegistryByteBuf, SyncConstructionSessionPayload> PACKET_CODEC = PacketCodec.tuple(
		Uuids.PACKET_CODEC,
		SyncConstructionSessionPayload::sessionId,
		BlockPos.PACKET_CODEC,
		SyncConstructionSessionPayload::anchorPos,
		PacketCodecs.STRING,
		SyncConstructionSessionPayload::blueprintId,
		PacketCodecs.INTEGER,
		SyncConstructionSessionPayload::rotation,
		PacketCodecs.BOOL,
		SyncConstructionSessionPayload::isDismantle,
		SyncConstructionSessionPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
