package com.example.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;

/**
 * Squad organization group for minion thralls and tactical command routing.
 * <p>
 * Supports collective targeting or discrete squad maneuvers:
 * <ul>
 * <li><b>ALL</b> (id 0, White): Wildcard channel targeting all owned minions
 * regardless of individual assignment.</li>
 * <li><b>ALPHA</b> (id 1, Red): First combat/task squad.</li>
 * <li><b>BRAVO</b> (id 2, Blue): Second combat/task squad.</li>
 * <li><b>CHARLIE</b> (id 3, Green): Third combat/task squad.</li>
 * <li><b>DELTA</b> (id 4, Gold): Fourth combat/task squad.</li>
 * </ul>
 * <p>
 * Implements {@link StringIdentifiable} with Mojang {@link Codec} for component
 * persistence
 * and Netty {@link PacketCodec} for networked synchronization.
 */
public enum SquadGroup implements StringIdentifiable {
	ALL(0, "all", "All Squads", "§f", Formatting.WHITE),
	ALPHA(1, "alpha", "Squad Alpha", "§c", Formatting.RED),
	BRAVO(2, "bravo", "Squad Bravo", "§9", Formatting.BLUE),
	CHARLIE(3, "charlie", "Squad Charlie", "§a", Formatting.GREEN),
	DELTA(4, "delta", "Squad Delta", "§6", Formatting.GOLD);

	public static final Codec<SquadGroup> CODEC = StringIdentifiable.createCodec(SquadGroup::values);

	private static final IntFunction<SquadGroup> BY_ID = ValueLists.createIdToValueFunction(
			SquadGroup::getId,
			values(),
			ValueLists.OutOfBoundsHandling.ZERO);

	public static final PacketCodec<ByteBuf, SquadGroup> PACKET_CODEC = PacketCodecs.indexed(
			BY_ID,
			SquadGroup::getId);

	/**
	 * Immutable list of concrete squad groups assignable to individual minion units
	 * (excludes the {@link #ALL} broadcast wildcard).
	 */
	private static final List<SquadGroup> SELECTABLE_SQUADS = List.of(ALPHA, BRAVO, CHARLIE, DELTA);

	private final int id;
	private final String name;
	private final String displayName;
	private final String colorCode;
	private final Formatting formatting;

	/**
	 * Constructs a SquadGroup instance.
	 *
	 * @param id          Unique numeric identifier for indexed serialization.
	 * @param name        String identifier for JSON/NBT serialization.
	 * @param displayName Human-readable display label.
	 * @param colorCode   Legacy color code for text and HUD formatting.
	 * @param formatting  Text formatting color.
	 */
	SquadGroup(int id, String name, String displayName, String colorCode, Formatting formatting) {
		this.id = id;
		this.name = name;
		this.displayName = displayName;
		this.colorCode = colorCode;
		this.formatting = formatting;
	}

	/**
	 * @return The integer identifier of this squad.
	 */
	public int getId() {
		return this.id;
	}

	@Override
	public String asString() {
		return this.name;
	}

	/**
	 * @return Human-readable display name.
	 */
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * @return Color code prefix string.
	 */
	public String getColorCode() {
		return this.colorCode;
	}

	/**
	 * @return Formatting enum constant.
	 */
	public Formatting getFormatting() {
		return this.formatting;
	}

	/**
	 * @return Formatted string including color prefix.
	 */
	public String getFormattedName() {
		return this.colorCode + this.displayName;
	}

	/**
	 * @return Translation key for localization files.
	 */
	public String getTranslationKey() {
		return "squad.modid-mmcli-agent-modding." + this.name;
	}

	/**
	 * @return Roman numeral badge designation for this squad.
	 */
	public String getRomanNumeral() {
		return switch (this) {
			case ALPHA -> "[I]";
			case BRAVO -> "[II]";
			case CHARLIE -> "[III]";
			case DELTA -> "[IV]";
			case ALL -> "[*]";
		};
	}

	/**
	 * @return Formatted squad banner string with flag icon, name, and Roman numeral
	 *         designation.
	 */
	public String getSquadBanner() {
		String squadName = this == ALL ? "ALL" : this.name.toUpperCase();
		return "⚑ SQUAD " + squadName + " " + this.getRomanNumeral();
	}

	/**
	 * @return True if this squad represents the wildcard broadcast channel.
	 */
	public boolean isWildcard() {
		return this == ALL;
	}

	/**
	 * Checks whether this squad matches a target squad filter.
	 * If this squad is {@link #ALL}, it matches any target squad.
	 *
	 * @param target The target squad to check against.
	 * @return True if this squad is ALL or equals the target.
	 */
	public boolean matches(SquadGroup target) {
		return this == ALL || this == target;
	}

	/**
	 * Resolves a SquadGroup by its integer ID. Defaults to {@link #ALL} if invalid.
	 *
	 * @param id The numeric identifier.
	 * @return The matching SquadGroup.
	 */
	public static SquadGroup fromId(int id) {
		SquadGroup[] values = values();
		if (id < 0 || id >= values.length) {
			return ALL;
		}
		return values[id];
	}

	/**
	 * @return Unmodifiable list of squads available for assignment to individual
	 *         minion units.
	 */
	public static List<SquadGroup> getSelectableSquads() {
		return SELECTABLE_SQUADS;
	}

	/**
	 * Cycles through all squads in natural order including wildcard {@link #ALL}.
	 *
	 * @return The next SquadGroup.
	 */
	public SquadGroup next() {
		SquadGroup[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	/**
	 * Cycles through all squads backwards including wildcard {@link #ALL}.
	 *
	 * @return The previous SquadGroup.
	 */
	public SquadGroup previous() {
		SquadGroup[] values = values();
		return values[(this.ordinal() - 1 + values.length) % values.length];
	}

	/**
	 * Cycles among selectable squads for unit assignment (skips wildcard
	 * {@link #ALL}).
	 *
	 * @return The next assignable SquadGroup.
	 */
	public SquadGroup nextSelectable() {
		List<SquadGroup> squads = SELECTABLE_SQUADS;
		int index = squads.indexOf(this);
		if (index == -1) {
			return ALPHA;
		}
		return squads.get((index + 1) % squads.size());
	}

	/**
	 * Cycles backwards among selectable squads for unit assignment (skips wildcard
	 * {@link #ALL}).
	 *
	 * @return The previous assignable SquadGroup.
	 */
	public SquadGroup previousSelectable() {
		List<SquadGroup> squads = SELECTABLE_SQUADS;
		int index = squads.indexOf(this);
		if (index == -1) {
			return DELTA;
		}
		return squads.get((index - 1 + squads.size()) % squads.size());
	}

	/**
	 * Resolves the RGB integer outline color for this squad channel,
	 * used for rendering glowing unit silhouettes.
	 *
	 * @return Hex RGB color value.
	 */
	public int getOutlineColor() {
		return switch (this) {
			case ALPHA -> 0xE74C3C;   // Bright Tactical Red
			case BRAVO -> 0x3498DB;   // Vibrant Arcane Blue
			case CHARLIE -> 0x2ECC71; // Vivid Emerald Green
			case DELTA -> 0xF39C12;   // Radiant Royal Gold
			case ALL -> 0xFFFFFF;     // Pure White
		};
	}
}
