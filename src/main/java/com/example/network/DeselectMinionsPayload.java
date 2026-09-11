package com.example.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched when deselecting minions.
 * Can target a specific minion entity ID, or deselect all minions owned by the player.
 *
 * @param minionId    The entity ID of the specific minion to deselect, or -1 if deselecting all owned minions.
 * @param deselectAll True if all owned minions in radius should be deselected.
 */
public record DeselectMinionsPayload(
	int minionId,
	boolean deselectAll
) implements CustomPayload {

	public static final CustomPayload.Id<DeselectMinionsPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "deselect_minions")
	);

	public static final PacketCodec<RegistryByteBuf, DeselectMinionsPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER,
		DeselectMinionsPayload::minionId,
		PacketCodecs.BOOL,
		DeselectMinionsPayload::deselectAll,
		DeselectMinionsPayload::new
	);

	/**
	 * Convenience constructor for requesting deselection of all owned minions in radius.
	 */
	public DeselectMinionsPayload() {
		this(-1, true);
	}

	/**
	 * Convenience constructor for deselecting a single minion by entity ID.
	 *
	 * @param minionId The entity ID of the minion.
	 */
	public DeselectMinionsPayload(int minionId) {
		this(minionId, minionId < 0);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
