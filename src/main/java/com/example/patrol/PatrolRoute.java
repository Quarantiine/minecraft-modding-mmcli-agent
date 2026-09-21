package com.example.patrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

/**
 * Immutable data record representing a designated minion patrol pathway.
 * Includes unique channel color identity, waypoints, serialization codecs, and constants.
 */
public record PatrolRoute(
	int routeId,
	String name,
	int colorRgb,
	List<BlockPos> waypoints,
	PatrolMode patrolMode
) {
	public enum PatrolMode {
		LOOP("Loop", "🔁"),
		PING_PONG("Ping-Pong", "🏓");

		private final String displayName;
		private final String icon;

		PatrolMode(String displayName, String icon) {
			this.displayName = displayName;
			this.icon = icon;
		}

		public String getDisplayName() {
			return this.displayName;
		}

		public String getIcon() {
			return this.icon;
		}

		public String getFormattedLabel() {
			return this.icon + " " + this.displayName;
		}

		public PatrolMode toggle() {
			return this == LOOP ? PING_PONG : LOOP;
		}

		public static final Codec<PatrolMode> CODEC = Codec.STRING.xmap(PatrolMode::valueOf, PatrolMode::name);
		public static final PacketCodec<ByteBuf, PatrolMode> PACKET_CODEC = PacketCodecs.indexed(
			i -> values()[Math.floorMod(i, values().length)],
			PatrolMode::ordinal
		);
	}

	public static final int CHANNEL_COUNT = 5;

	public static final int[] CHANNEL_COLORS = {
		0xFFD700, // Route 1: 🟡 Gold / Amber
		0x00E5FF, // Route 2: 🔵 Azure / Cyan
		0x00FF66, // Route 3: 🟢 Emerald Green
		0xB300FF, // Route 4: 🟣 Arcane Purple
		0xFF2244  // Route 5: 🔴 Crimson Red
	};

	public static final String[] CHANNEL_NAMES = {
		"Route 1 (Gold)",
		"Route 2 (Cyan)",
		"Route 3 (Emerald)",
		"Route 4 (Purple)",
		"Route 5 (Crimson)"
	};

	public static final String[] CHANNEL_FORMATTED_NAMES = {
		"§6Route 1 (Gold)§r",
		"§bRoute 2 (Cyan)§r",
		"§aRoute 3 (Emerald)§r",
		"§dRoute 4 (Purple)§r",
		"§cRoute 5 (Crimson)§r"
	};

	public PatrolRoute(int routeId, String name, int colorRgb, List<BlockPos> waypoints) {
		this(routeId, name, colorRgb, waypoints, PatrolMode.LOOP);
	}

	public static final Codec<PatrolRoute> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			Codec.INT.fieldOf("routeId").forGetter(PatrolRoute::routeId),
			Codec.STRING.fieldOf("name").forGetter(PatrolRoute::name),
			Codec.INT.fieldOf("colorRgb").forGetter(PatrolRoute::colorRgb),
			BlockPos.CODEC.listOf().fieldOf("waypoints").forGetter(PatrolRoute::waypoints),
			PatrolMode.CODEC.optionalFieldOf("patrolMode", PatrolMode.LOOP).forGetter(PatrolRoute::patrolMode)
		).apply(instance, PatrolRoute::new)
	);

	public static final PacketCodec<ByteBuf, PatrolRoute> PACKET_CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER, PatrolRoute::routeId,
		PacketCodecs.STRING, PatrolRoute::name,
		PacketCodecs.INTEGER, PatrolRoute::colorRgb,
		BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()), PatrolRoute::waypoints,
		PatrolMode.PACKET_CODEC, PatrolRoute::patrolMode,
		PatrolRoute::new
	);

	/**
	 * Creates a default empty patrol route for the specified channel ID.
	 *
	 * @param routeId Channel index (0 to 4).
	 * @return A new empty PatrolRoute.
	 */
	public static PatrolRoute createDefault(int routeId) {
		int id = Math.max(0, Math.min(CHANNEL_COUNT - 1, routeId));
		return new PatrolRoute(id, CHANNEL_NAMES[id], CHANNEL_COLORS[id], new ArrayList<>(), PatrolMode.LOOP);
	}

	/**
	 * Returns the formatted display name of this route with color codes.
	 */
	public String getFormattedName() {
		if (routeId >= 0 && routeId < CHANNEL_FORMATTED_NAMES.length) {
			return CHANNEL_FORMATTED_NAMES[routeId];
		}
		return name;
	}

	/**
	 * Creates a copy of this route with an added waypoint.
	 */
	public PatrolRoute withAddedWaypoint(BlockPos pos) {
		List<BlockPos> newWaypoints = new ArrayList<>(this.waypoints);
		newWaypoints.add(pos);
		return new PatrolRoute(this.routeId, this.name, this.colorRgb, newWaypoints, this.patrolMode);
	}

	/**
	 * Creates a copy of this route with a waypoint removed.
	 */
	public PatrolRoute withRemovedWaypoint(BlockPos pos) {
		List<BlockPos> newWaypoints = new ArrayList<>(this.waypoints);
		newWaypoints.remove(pos);
		return new PatrolRoute(this.routeId, this.name, this.colorRgb, newWaypoints, this.patrolMode);
	}

	/**
	 * Creates a copy of this route cleared of all waypoints.
	 */
	public PatrolRoute withClearedWaypoints() {
		return new PatrolRoute(this.routeId, this.name, this.colorRgb, new ArrayList<>(), this.patrolMode);
	}

	/**
	 * Creates a copy of this route with a different patrol mode.
	 */
	public PatrolRoute withPatrolMode(PatrolMode mode) {
		return new PatrolRoute(this.routeId, this.name, this.colorRgb, new ArrayList<>(this.waypoints), mode);
	}
}
