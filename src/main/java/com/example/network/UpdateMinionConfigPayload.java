package com.example.network;

import com.example.ExampleMod;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) networking payload dispatched by minion management screens
 * to update the archetype {@link MinionRole} and tactical {@link SquadGroup} of a specific minion thrall.
 *
 * @param minionId The entity ID of the target minion thrall.
 * @param role     The newly assigned archetype role (Warrior, Sentinel, Builder, Miner, Ranger).
 * @param squad    The newly assigned squad group (Alpha, Bravo, Charlie, Delta).
 */
public record UpdateMinionConfigPayload(
	int minionId,
	MinionRole role,
	SquadGroup squad
) implements CustomPayload {

	public static final CustomPayload.Id<UpdateMinionConfigPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "update_minion_config")
	);

	public static final PacketCodec<RegistryByteBuf, UpdateMinionConfigPayload> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER,
		UpdateMinionConfigPayload::minionId,
		MinionRole.PACKET_CODEC.cast(),
		UpdateMinionConfigPayload::role,
		SquadGroup.PACKET_CODEC.cast(),
		UpdateMinionConfigPayload::squad,
		UpdateMinionConfigPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
