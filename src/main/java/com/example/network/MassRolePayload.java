package com.example.network;

import com.example.ExampleMod;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionRole;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server (C2S) payload dispatched from the Command Hub GUI to batch-assign
 * an archetype {@link MinionRole} to all selected minions (or all minions in the target {@link SquadGroup}).
 *
 * @param role  The newly assigned archetype role.
 * @param squad The target squad channel filter.
 */
public record MassRolePayload(
	MinionRole role,
	SquadGroup squad
) implements CustomPayload {

	public static final CustomPayload.Id<MassRolePayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "mass_role")
	);

	public static final PacketCodec<RegistryByteBuf, MassRolePayload> PACKET_CODEC = PacketCodec.tuple(
		MinionRole.PACKET_CODEC.cast(),
		MassRolePayload::role,
		SquadGroup.PACKET_CODEC.cast(),
		MassRolePayload::squad,
		MassRolePayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
