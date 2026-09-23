package com.example.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;

/**
 * Operating mode for the Loki Command Scepter and minion directives.
 * Supports serialization via Mojang Codec and Minecraft PacketCodec.
 */
public enum CommandMode implements StringIdentifiable {
	FOLLOW("follow", 0.8F, "Follow", "§a"),
	STAY("stay", 1.0F, "Stay", "§e"),
	MINE("mine", 1.4F, "Mine", "§6"),
	BUILD("build", 1.6F, "Build", "§b"),
	DESIGN("design", 1.7F, "Design", "§d"),
	RECRUIT("recruit", 1.8F, "Recruit", "§d"),
	PATHWAY("pathway", 2.0F, "Pathway", "§3");

	public static final Codec<CommandMode> CODEC = StringIdentifiable.createCodec(CommandMode::values);

	private static final IntFunction<CommandMode> BY_ID = ValueLists.createIdToValueFunction(
		CommandMode::ordinal,
		values(),
		ValueLists.OutOfBoundsHandling.ZERO
	);

	public static final PacketCodec<ByteBuf, CommandMode> PACKET_CODEC = PacketCodecs.indexed(
		BY_ID,
		CommandMode::ordinal
	);

	private final String id;
	private final float pitch;
	private final String displayName;
	private final String colorCode;

	CommandMode(String id, float pitch, String displayName, String colorCode) {
		this.id = id;
		this.pitch = pitch;
		this.displayName = displayName;
		this.colorCode = colorCode;
	}

	@Override
	public String asString() {
		return this.id;
	}

	public float getPitch() {
		return this.pitch;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getColorCode() {
		return this.colorCode;
	}

	public String getFormattedName() {
		return this.colorCode + this.displayName;
	}

	/**
	 * Cycles to the next command mode in ordinal sequence.
	 *
	 * @return The next CommandMode enum value.
	 */
	public CommandMode next() {
		CommandMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	/**
	 * Cycles to the previous command mode in ordinal sequence.
	 *
	 * @return The previous CommandMode enum value.
	 */
	public CommandMode previous() {
		CommandMode[] values = values();
		return values[(this.ordinal() - 1 + values.length) % values.length];
	}
}
