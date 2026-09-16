package com.example.entity.custom;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;

/**
 * Tactical archetype role assigned to an autonomous {@link MinionEntity}.
 * <p>
 * Roles partition AI goals, tactical positioning, combat engagement distance,
 * and behavior routines:
 * <ul>
 *   <li><b>WARRIOR</b>: Versatile combatant engaging hostile mobs at close quarters with melee weapons or at range with bows/crossbows (Red).</li>
 *   <li><b>SENTINEL</b>: Defensive perimeter guard tethered to an anchor position with shield bulwark (Green).</li>
 *   <li><b>BUILDER</b>: Architectural construction unit participating in structure erection with Arcane Levitation flight (Blue).</li>
 *   <li><b>MINER</b>: Resource extraction, area excavation, and structure deconstruction specialist (Gold).</li>
 * </ul>
 * <p>
 * Supports serialization across Mojang Codecs (for entity NBT and recipes) and Netty PacketCodecs
 * for client-server network synchronization.
 */
public enum MinionRole implements StringIdentifiable {
	WARRIOR(0, "warrior", "Warrior", "§c", Formatting.RED),
	SENTINEL(1, "sentinel", "Sentinel", "§a", Formatting.GREEN),
	BUILDER(2, "builder", "Builder", "§9", Formatting.BLUE);

	public static final Codec<MinionRole> CODEC = StringIdentifiable.createCodec(MinionRole::values);

	private static final IntFunction<MinionRole> BY_ID = ValueLists.createIdToValueFunction(
		MinionRole::getId,
		values(),
		ValueLists.OutOfBoundsHandling.ZERO
	);

	public static final PacketCodec<ByteBuf, MinionRole> PACKET_CODEC = PacketCodecs.indexed(
		BY_ID,
		MinionRole::getId
	);

	private final int id;
	private final String name;
	private final String displayName;
	private final String colorCode;
	private final Formatting formatting;

	/**
	 * Constructs a MinionRole with network ID, identification key, display name, color code, and formatting.
	 *
	 * @param id          Unique integer ID used for indexed packet encoding and DataTracker storage.
	 * @param name        String identifier used for JSON/NBT serialization.
	 * @param displayName Human-readable display label.
	 * @param colorCode   Legacy formatting color code for chat and floating labels.
	 * @param formatting  Text formatting color.
	 */
	MinionRole(int id, String name, String displayName, String colorCode, Formatting formatting) {
		this.id = id;
		this.name = name;
		this.displayName = displayName;
		this.colorCode = colorCode;
		this.formatting = formatting;
	}

	/**
	 * @return The integer identifier of this role.
	 */
	public int getId() {
		return this.id;
	}

	@Override
	public String asString() {
		return this.name;
	}

	/**
	 * @return The human-readable display name of this role.
	 */
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * @return The color formatting code (e.g. "§c").
	 */
	public String getColorCode() {
		return this.colorCode;
	}

	/**
	 * @return The text formatting representation.
	 */
	public Formatting getFormatting() {
		return this.formatting;
	}

	/**
	 * @return Formatted role string with embedded color code.
	 */
	public String getFormattedName() {
		return this.colorCode + this.displayName;
	}

	/**
	 * @return The translation key for localization in language files.
	 */
	public String getTranslationKey() {
		return "role.modid-mmcli-agent-modding." + this.name;
	}

	/**
	 * @return Tactical UTF-8 icon symbol representing this archetype.
	 */
	public String getIcon() {
		return switch (this) {
			case WARRIOR -> "⚔";
			case SENTINEL -> "🛡";
			case BUILDER -> "🔨";
		};
	}

	/**
	 * @return Compact role crest label combining the tactical icon and uppercase role name.
	 */
	public String getBadgeLabel() {
		return this.getIcon() + " " + this.name();
	}

	/**
	 * Resolves a role by its numeric identifier with fallback to {@link #WARRIOR},
	 * gracefully mapping legacy MINER (ID 3) to {@link #BUILDER}.
	 *
	 * @param id The numeric identifier.
	 * @return The matching MinionRole, or WARRIOR if out of bounds.
	 */
	public static MinionRole fromId(int id) {
		if (id == 3) {
			return BUILDER; // Gracefully map legacy MINER (ID 3) to BUILDER
		}
		MinionRole[] values = values();
		if (id < 0 || id >= values.length) {
			return WARRIOR;
		}
		return values[id];
	}

	/**
	 * Cycles to the next role in sequence.
	 *
	 * @return The subsequent MinionRole.
	 */
	public MinionRole next() {
		MinionRole[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	/**
	 * Cycles to the previous role in sequence.
	 *
	 * @return The antecedent MinionRole.
	 */
	public MinionRole previous() {
		MinionRole[] values = values();
		return values[(this.ordinal() - 1 + values.length) % values.length];
	}
}
