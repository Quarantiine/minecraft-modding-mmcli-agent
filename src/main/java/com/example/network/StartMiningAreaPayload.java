package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-server (C2S) networking payload dispatched when the commanding player confirms a custom mining area
 * to start a 3D boundary excavation / dismantle session.
 *
 * @param pos1 First corner of the 3D mining volume.
 * @param pos2 Second corner of the 3D mining volume.
 */
public record StartMiningAreaPayload(
	BlockPos pos1,
	BlockPos pos2
) implements CustomPayload {

	public static final CustomPayload.Id<StartMiningAreaPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "start_mining_area")
	);

	public static final PacketCodec<RegistryByteBuf, StartMiningAreaPayload> PACKET_CODEC = PacketCodec.tuple(
		BlockPos.PACKET_CODEC,
		StartMiningAreaPayload::pos1,
		BlockPos.PACKET_CODEC,
		StartMiningAreaPayload::pos2,
		StartMiningAreaPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
