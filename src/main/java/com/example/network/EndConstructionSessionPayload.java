package com.example.network;

import com.example.ExampleMod;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * Server-to-client (S2C) payload informing clients that a construction session has ended
 * (completed or cancelled), prompting holographic renderers to remove the 3D outline.
 *
 * @param sessionId Unique session identifier that has terminated.
 */
public record EndConstructionSessionPayload(
	UUID sessionId
) implements CustomPayload {

	public static final CustomPayload.Id<EndConstructionSessionPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "end_construction_session")
	);

	public static final PacketCodec<RegistryByteBuf, EndConstructionSessionPayload> PACKET_CODEC = PacketCodec.tuple(
		Uuids.PACKET_CODEC,
		EndConstructionSessionPayload::sessionId,
		EndConstructionSessionPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
