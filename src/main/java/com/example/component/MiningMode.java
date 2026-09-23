package com.example.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;

/**
 * Operational sub-mode for MINE command mode:
 * <ul>
 * <li><b>DIRECT</b> (id 0, Gold): Point-and-click dismantle targeting structure/blueprint/block at crosshairs.</li>
 * <li><b>AREA</b> (id 1, Yellow): Custom 3D boundary selection (Pos1 -> Pos2 -> Height adjustment) with confirmation modal.</li>
 * </ul>
 * <p>
 * Implements {@link StringIdentifiable} with Mojang {@link Codec} for data component persistence
 * and Netty {@link PacketCodec} for networked serialization.
 */
public enum MiningMode implements StringIdentifiable {
	AREA(0, "area", "Custom Area", "§e", Formatting.YELLOW),
	DIRECT(1, "direct", "Direct / Structure", "§6", Formatting.GOLD);

	public static final Codec<MiningMode> CODEC = StringIdentifiable.createCodec(MiningMode::values);

	private static final IntFunction<MiningMode> BY_ID = ValueLists.createIdToValueFunction(
		MiningMode::getId,
		values(),
		ValueLists.OutOfBoundsHandling.ZERO
	);

	public static final PacketCodec<ByteBuf, MiningMode> PACKET_CODEC = PacketCodecs.indexed(
		BY_ID,
		MiningMode::getId
	);

	private final int id;
	private final String name;
	private final String displayName;
	private final String colorCode;
	private final Formatting formatting;

	MiningMode(int id, String name, String displayName, String colorCode, Formatting formatting) {
		this.id = id;
		this.name = name;
		this.displayName = displayName;
		this.colorCode = colorCode;
		this.formatting = formatting;
	}

	public int getId() {
		return this.id;
	}

	@Override
	public String asString() {
		return this.name;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getColorCode() {
		return this.colorCode;
	}

	public Formatting getFormatting() {
		return this.formatting;
	}

	public String getFormattedName() {
		return this.colorCode + this.displayName;
	}

	public String getTranslationKey() {
		return "mining_mode.modid-mmcli-agent-modding." + this.name;
	}

	public static MiningMode fromId(int id) {
		MiningMode[] values = values();
		if (id < 0 || id >= values.length) {
			return AREA;
		}
		return values[id];
	}

	/**
	 * Cycles to the next mining mode in ordinal sequence.
	 *
	 * @return The next MiningMode enum value.
	 */
	public MiningMode next() {
		MiningMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	/**
	 * Cycles to the previous mining mode in ordinal sequence.
	 *
	 * @return The previous MiningMode enum value.
	 */
	public MiningMode previous() {
		MiningMode[] values = values();
		return values[(this.ordinal() - 1 + values.length) % values.length];
	}
}
