package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched when dismissing minions.
 * Can target a specific minion entity ID, or dismiss all minions owned by the player in proximity.
 *
 * @param minionId   The entity ID of the specific minion to dismiss, or -1 if dismissing all owned minions.
 * @param dismissAll True if all owned minions in radius should be dismissed.
 */
public record DismissMinionPayload(
	int minionId,
	boolean dismissAll
) implements CustomPayload {

	public static final int TARGET_ALL = -1;
	public static final int TARGET_SELECTED = -2;

	public static final CustomPayload.Id<DismissMinionPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "dismiss_minion")
	);

	public static final PacketCodec<RegistryByteBuf, DismissMinionPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER,
		DismissMinionPayload::minionId,
		PacketCodecs.BOOL,
		DismissMinionPayload::dismissAll,
		DismissMinionPayload::new
	);

	/**
	 * Convenience constructor for dismissing a single minion by entity ID.
	 *
	 * @param minionId The entity ID of the minion.
	 */
	public DismissMinionPayload(int minionId) {
		this(minionId, minionId < 0);
	}

	public boolean isTargetSelected() {
		return this.minionId == TARGET_SELECTED;
	}

	public boolean isTargetAll() {
		return this.dismissAll || this.minionId == TARGET_ALL;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
