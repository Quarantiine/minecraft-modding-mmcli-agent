package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched to remove a custom blueprint.
 *
 * @param blueprintId Unique blueprint identifier to delete.
 */
public record DeleteCustomBlueprintPayload(
	String blueprintId
) implements CustomPayload {

	public static final CustomPayload.Id<DeleteCustomBlueprintPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "delete_custom_blueprint")
	);

	public static final PacketCodec<RegistryByteBuf, DeleteCustomBlueprintPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.STRING, DeleteCustomBlueprintPayload::blueprintId,
		DeleteCustomBlueprintPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
