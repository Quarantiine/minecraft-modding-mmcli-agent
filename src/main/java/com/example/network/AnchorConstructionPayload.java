package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Client-to-server (C2S) payload dispatched when the commanding player right-clicks to anchor a construction
 * or dismantle session, especially from long-range crosshairs or elevated tactical build camera perspectives.
 *
 * @param clickedPos  The targeted block position in the world.
 * @param side        The targeted block face side.
 * @param isDismantle True if this should start a dismantle session rather than a build session.
 */
public record AnchorConstructionPayload(
	BlockPos clickedPos,
	Direction side,
	boolean isDismantle
) implements CustomPayload {

	public static final CustomPayload.Id<AnchorConstructionPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "anchor_construction")
	);

	public static final PacketCodec<RegistryByteBuf, AnchorConstructionPayload> PACKET_CODEC = PacketCodec.tuple(
		BlockPos.PACKET_CODEC,
		AnchorConstructionPayload::clickedPos,
		PacketCodecs.INTEGER.xmap(Direction::byId, Direction::getId),
		AnchorConstructionPayload::side,
		PacketCodecs.BOOL,
		AnchorConstructionPayload::isDismantle,
		AnchorConstructionPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
