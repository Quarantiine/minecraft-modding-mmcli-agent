package com.example.network;

import com.example.ExampleMod;
import com.example.component.CommandMode;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched by the Command Hub GUI
 * when the commanding player selects a new operating mode, active blueprint,
 * or requests direct command execution.
 *
 * @param mode             The updated CommandMode to apply to the held scepter.
 * @param blueprintId      The identifier of the active blueprint selected in the catalog.
 * @param executeDirective If true, immediately triggers tactical minion broadcast or mode action.
 */
public record UpdateScepterPayload(
	CommandMode mode,
	String blueprintId,
	boolean executeDirective
) implements CustomPayload {

	public static final CustomPayload.Id<UpdateScepterPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "update_scepter")
	);

	public static final PacketCodec<RegistryByteBuf, UpdateScepterPayload> PACKET_CODEC = PacketCodec.tuple(
		CommandMode.PACKET_CODEC.cast(),
		UpdateScepterPayload::mode,
		PacketCodecs.STRING,
		UpdateScepterPayload::blueprintId,
		PacketCodecs.BOOL,
		UpdateScepterPayload::executeDirective,
		UpdateScepterPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
