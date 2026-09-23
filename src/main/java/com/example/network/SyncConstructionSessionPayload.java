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
 * @param sizeX        Bounding box size along X axis.
 * @param sizeY        Bounding box size along Y axis.
 * @param sizeZ        Bounding box size along Z axis.
 */
public record SyncConstructionSessionPayload(
	UUID sessionId,
	BlockPos anchorPos,
	String blueprintId,
	int rotation,
	boolean isDismantle,
	int sizeX,
	int sizeY,
	int sizeZ
) implements CustomPayload {

	public SyncConstructionSessionPayload(
		UUID sessionId,
		BlockPos anchorPos,
		String blueprintId,
		int rotation,
		boolean isDismantle
	) {
		this(sessionId, anchorPos, blueprintId, rotation, isDismantle, 0, 0, 0);
	}

	public static final CustomPayload.Id<SyncConstructionSessionPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "sync_construction_session")
	);

	public static final PacketCodec<RegistryByteBuf, SyncConstructionSessionPayload> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, SyncConstructionSessionPayload payload) {
			Uuids.PACKET_CODEC.encode(buf, payload.sessionId());
			BlockPos.PACKET_CODEC.encode(buf, payload.anchorPos());
			PacketCodecs.STRING.encode(buf, payload.blueprintId());
			PacketCodecs.INTEGER.encode(buf, payload.rotation());
			PacketCodecs.BOOL.encode(buf, payload.isDismantle());
			PacketCodecs.INTEGER.encode(buf, payload.sizeX());
			PacketCodecs.INTEGER.encode(buf, payload.sizeY());
			PacketCodecs.INTEGER.encode(buf, payload.sizeZ());
		}

		@Override
		public SyncConstructionSessionPayload decode(RegistryByteBuf buf) {
			return new SyncConstructionSessionPayload(
				Uuids.PACKET_CODEC.decode(buf),
				BlockPos.PACKET_CODEC.decode(buf),
				PacketCodecs.STRING.decode(buf),
				PacketCodecs.INTEGER.decode(buf),
				PacketCodecs.BOOL.decode(buf),
				PacketCodecs.INTEGER.decode(buf),
				PacketCodecs.INTEGER.decode(buf),
				PacketCodecs.INTEGER.decode(buf)
			);
		}
	};

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
