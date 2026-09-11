package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched when requesting minion teleportation.
 * Can target a specific minion entity ID, or recall all minions owned by the player in proximity.
 *
 * @param minionId    The entity ID of the specific minion to teleport, or -1 if recalling all owned minions.
 * @param teleportAll True if all owned minions in radius should be teleported to the player.
 */
public record TeleportMinionPayload(
	int minionId,
	boolean teleportAll
) implements CustomPayload {

	public static final CustomPayload.Id<TeleportMinionPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "teleport_minion")
	);

	public static final PacketCodec<RegistryByteBuf, TeleportMinionPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER,
		TeleportMinionPayload::minionId,
		PacketCodecs.BOOL,
		TeleportMinionPayload::teleportAll,
		TeleportMinionPayload::new
	);

	/**
	 * Convenience constructor for teleporting a single minion by entity ID.
	 *
	 * @param minionId The entity ID of the minion to teleport.
	 */
	public TeleportMinionPayload(int minionId) {
		this(minionId, minionId < 0);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
