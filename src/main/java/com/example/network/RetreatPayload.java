package com.example.network;

import com.example.ExampleMod;
import com.example.component.SquadGroup;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched when the commanding player
 * presses the Tactical Retreat keybind ('R' or 'Shift + R') to recall active minions.
 *
 * @param targetSquad            The target SquadGroup channel to recall, or ALL.
 * @param isEmergencyCitadelCall Whether this is an emergency base-wide call recalling all sentries and patrols.
 */
public record RetreatPayload(
	SquadGroup targetSquad,
	boolean isEmergencyCitadelCall
) implements CustomPayload {

	public static final CustomPayload.Id<RetreatPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "retreat")
	);

	public static final PacketCodec<RegistryByteBuf, RetreatPayload> PACKET_CODEC = PacketCodec.tuple(
		SquadGroup.PACKET_CODEC,
		RetreatPayload::targetSquad,
		PacketCodecs.BOOL,
		RetreatPayload::isEmergencyCitadelCall,
		RetreatPayload::new
	);

	public RetreatPayload(SquadGroup targetSquad) {
		this(targetSquad, false);
	}

	public RetreatPayload() {
		this(SquadGroup.ALL, false);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
