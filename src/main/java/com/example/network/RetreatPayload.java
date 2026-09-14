package com.example.network;

import com.example.ExampleMod;
import com.example.component.SquadGroup;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched when the commanding player
 * presses the Tactical Retreat keybind ('R') to recall active minions to formation.
 *
 * @param targetSquad The target SquadGroup channel to recall, or ALL.
 */
public record RetreatPayload(
	SquadGroup targetSquad
) implements CustomPayload {

	public static final CustomPayload.Id<RetreatPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "retreat")
	);

	public static final PacketCodec<RegistryByteBuf, RetreatPayload> PACKET_CODEC = PacketCodec.tuple(
		SquadGroup.PACKET_CODEC.cast(),
		RetreatPayload::targetSquad,
		RetreatPayload::new
	);

	public RetreatPayload() {
		this(SquadGroup.ALL);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
