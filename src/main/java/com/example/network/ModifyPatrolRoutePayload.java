package com.example.network;

import com.example.ExampleMod;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.ValueLists;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-server (C2S) payload dispatched when the commanding player
 * modifies a patrol route (adding/removing waypoints, clearing a channel)
 * or assigns a minion to a route or escort hierarchy.
 */
public record ModifyPatrolRoutePayload(
	Action action,
	int routeId,
	BlockPos pos,
	int minionId,
	int targetMinionId
) implements CustomPayload {

	public enum Action {
		ADD_WAYPOINT,
		REMOVE_WAYPOINT,
		CLEAR_ROUTE,
		ASSIGN_MINION,
		SET_ESCORT,
		CLEAR_ESCORT,
		TOGGLE_PATROL_MODE;

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

	public static final CustomPayload.Id<ModifyPatrolRoutePayload> ID = new CustomPayload.Id<>(
		Identifier.of(ExampleMod.MOD_ID, "modify_patrol_route")
	);

	public static final PacketCodec<RegistryByteBuf, ModifyPatrolRoutePayload> PACKET_CODEC = PacketCodec.tuple(
		Action.PACKET_CODEC, ModifyPatrolRoutePayload::action,
		PacketCodecs.INTEGER, ModifyPatrolRoutePayload::routeId,
		BlockPos.PACKET_CODEC, ModifyPatrolRoutePayload::pos,
		PacketCodecs.INTEGER, ModifyPatrolRoutePayload::minionId,
		PacketCodecs.INTEGER, ModifyPatrolRoutePayload::targetMinionId,
		ModifyPatrolRoutePayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
