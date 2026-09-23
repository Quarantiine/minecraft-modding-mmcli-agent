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
	 * Parses a hex color string into a 24-bit RGB integer (0xRRGGBB).
	 * Accepts formats such as "#FFD700", "0xFFD700", or "FFD700".
	 *
	 * @param hex The hex color string.
	 * @return Parsed RGB integer, or default 0xFFD700 if invalid or empty.
	 */
	public static int parseHexColor(String hex) {
		if (hex == null || hex.trim().isEmpty()) {
			return 0xFFD700;
		}
		String clean = hex.trim();
		if (clean.startsWith("#")) {
			clean = clean.substring(1);
		} else if (clean.startsWith("0x") || clean.startsWith("0X")) {
			clean = clean.substring(2);
		}
		try {
			long parsed = Long.parseLong(clean, 16);
			return (int) (parsed & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return 0xFFD700;
		}
	}

	/**
	 * Formats a 24-bit RGB integer as a 6-digit uppercase hex code with leading '#'.
	 *
	 * @param colorRgb The 24-bit RGB color.
	 * @return Hex color string (e.g. "#FFD700").
	 */
	public static String toHexCode(int colorRgb) {
		return String.format("#%06X", (0xFFFFFF & colorRgb));
	}

	/**
	 * Returns the hex code string for this route's color.
	 */
	public String toHexCode() {
		return toHexCode(this.colorRgb);
	}

	/**
	 * Creates a default empty patrol route for the specified channel ID.
	 *
	 * @param routeId Channel index (0 to 4).
	 * @return A new empty PatrolRoute.
	 */
	public static PatrolRoute createDefault(int routeId) {
		if (routeId >= 0 && routeId < CHANNEL_COUNT) {
			return new PatrolRoute(routeId, CHANNEL_NAMES[routeId], CHANNEL_COLORS[routeId], new ArrayList<>(), PatrolMode.LOOP);
		}
		int color = CHANNEL_COLORS[Math.floorMod(routeId, CHANNEL_COLORS.length)];
		return new PatrolRoute(routeId, "Route " + (routeId + 1), color, new ArrayList<>(), PatrolMode.LOOP);
	}

	/**
	 * Creates a custom patrol route with the specified ID, custom name, and hex color.
	 *
	 * @param routeId  The unique route channel ID.
	 * @param name     Custom display name.
	 * @param colorRgb 24-bit RGB color integer.
	 * @return A new custom PatrolRoute.
	 */
	public static PatrolRoute createCustom(int routeId, String name, int colorRgb) {
		String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : ("Route " + (routeId + 1));
		return new PatrolRoute(routeId, cleanName, colorRgb & 0xFFFFFF, new ArrayList<>(), PatrolMode.LOOP);
	}

	/**
	 * Creates a custom patrol route with the specified ID, custom name, and hex color string.
	 *
	 * @param routeId  The unique route channel ID.
	 * @param name     Custom display name.
	 * @param hexColor Hex color string (e.g. "#FFD700").
	 * @return A new custom PatrolRoute.
	 */
	public static PatrolRoute createCustom(int routeId, String name, String hexColor) {
		return createCustom(routeId, name, parseHexColor(hexColor));
	}

	/**
	 * Returns the formatted display name of this route with color codes.
	 */
	public String getFormattedName() {
		if (routeId >= 0 && routeId < CHANNEL_FORMATTED_NAMES.length && name != null && name.equals(CHANNEL_NAMES[routeId])) {
			return CHANNEL_FORMATTED_NAMES[routeId];
		}
		return name != null ? name : "Route " + (routeId + 1);
	}

	/**
	 * Returns a stylized Text component using the route's dynamic RGB color.
	 */
	public net.minecraft.text.Text getFormattedText() {
		String displayName = (name != null && !name.isEmpty()) ? name : ("Route " + (routeId + 1));
		return net.minecraft.text.Text.literal(displayName).styled(style ->
			style.withColor(net.minecraft.text.TextColor.fromRgb(this.colorRgb))
		);
	}

	/**
	 * Creates a copy of this route with a new name.
	 */
	public PatrolRoute withName(String newName) {
		String cleanName = (newName != null && !newName.trim().isEmpty()) ? newName.trim() : ("Route " + (this.routeId + 1));
		return new PatrolRoute(this.routeId, cleanName, this.colorRgb, new ArrayList<>(this.waypoints), this.patrolMode);
	}

	/**
	 * Creates a copy of this route with a new 24-bit RGB color.
	 */
	public PatrolRoute withColor(int newColorRgb) {
		return new PatrolRoute(this.routeId, this.name, newColorRgb & 0xFFFFFF, new ArrayList<>(this.waypoints), this.patrolMode);
	}

	/**
	 * Creates a copy of this route with a new 24-bit RGB color (alias for withColor).
	 */
	public PatrolRoute withColorRgb(int newColorRgb) {
		return withColor(newColorRgb);
	}

	/**
	 * Creates a copy of this route with a new color from hex string.
	 */
	public PatrolRoute withColor(String newHexColor) {
		return withColor(parseHexColor(newHexColor));
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
