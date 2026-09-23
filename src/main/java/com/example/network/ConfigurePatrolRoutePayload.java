package com.example.network;

import com.example.ExampleMod;
import com.example.patrol.PatrolRoute;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.ValueLists;

/**
 * Client-to-server (C2S) payload for creating, updating, or deleting customizable patrol routes with hex colors.
 */
public record ConfigurePatrolRoutePayload(
	Action action,
	int routeId,
	String name,
	int colorRgb,
	PatrolRoute.PatrolMode patrolMode
) implements CustomPayload {

	public enum Action {
		SAVE,
		DELETE;

		private static final IntFunction<Action> BY_ID = ValueLists.createIdToValueFunction(
			Action::ordinal,
			values(),
			ValueLists.OutOfBoundsHandling.ZERO
		);

		public static final PacketCodec<ByteBuf, Action> PACKET_CODEC = PacketCodecs.indexed(
			BY_ID,
			Action::ordinal
		);
	}

	public static final CustomPayload.Id<ConfigurePatrolRoutePayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "configure_patrol_route")
	);

	public static final PacketCodec<RegistryByteBuf, ConfigurePatrolRoutePayload> PACKET_CODEC = PacketCodec.tuple(
		Action.PACKET_CODEC, ConfigurePatrolRoutePayload::action,
		PacketCodecs.INTEGER, ConfigurePatrolRoutePayload::routeId,
		PacketCodecs.STRING, ConfigurePatrolRoutePayload::name,
		PacketCodecs.INTEGER, ConfigurePatrolRoutePayload::colorRgb,
		PatrolRoute.PatrolMode.PACKET_CODEC, ConfigurePatrolRoutePayload::patrolMode,
		ConfigurePatrolRoutePayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
