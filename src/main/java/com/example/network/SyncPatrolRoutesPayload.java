package com.example.network;

import com.example.ExampleMod;
import com.example.patrol.PatrolRoute;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client (S2C) payload synchronizing all designated patrol route channels
 * and waypoints to the player client for 3D holographic wireframe and path rendering.
 *
 * @param routes List of all 5 channel routes.
 */
public record SyncPatrolRoutesPayload(
	List<PatrolRoute> routes
) implements CustomPayload {

	public static final CustomPayload.Id<SyncPatrolRoutesPayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "sync_patrol_routes")
	);

	public static final PacketCodec<RegistryByteBuf, SyncPatrolRoutesPayload> PACKET_CODEC = PacketCodec.tuple(
		PatrolRoute.PACKET_CODEC.collect(PacketCodecs.toList()),
		SyncPatrolRoutesPayload::routes,
		SyncPatrolRoutesPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
