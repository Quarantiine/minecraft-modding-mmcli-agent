package com.example.blueprint;

import com.example.ExampleMod;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * Defines available architectural design themes for minion construction.
 * Allows structures to adapt dynamically to local biomes or be forced
 * into specific cultural masonry and carpentry styles.
 */
public enum ArchitectureStyle implements StringIdentifiable {
	BIOME_NATIVE("biome_native", "🌍 Biome Native", Formatting.GREEN, "Adapts organically to local biome materials (Desert, Taiga, Plains, Swamp, Nether, Caves)."),
	FORTRESS_STONE("fortress_stone", "🏰 Fortress Stone", Formatting.AQUA, "Forces heavy stone bricks, cracked & mossy masonry, deepslate, and iron fittings."),
	FRONTIER_TIMBER("frontier_timber", "🌲 Frontier Timber", Formatting.GOLD, "Forces rustic log framing, wood planks, stripped accents, fences, and lanterns."),
	ARCANE_NETHER("arcane_nether", "🔮 Arcane Nether", Formatting.DARK_PURPLE, "Forces polished blackstone, basalt, crimson & warped timbers, and soul fire.");

	public static final Codec<ArchitectureStyle> CODEC = StringIdentifiable.createCodec(ArchitectureStyle::values);
	public static final PacketCodec<ByteBuf, ArchitectureStyle> PACKET_CODEC = PacketCodecs.indexed(
		i -> values()[Math.floorMod(i, values().length)],
		ArchitectureStyle::ordinal
	);

	private final String id;
	private final String displayName;
	private final Formatting color;
	private final String description;

	ArchitectureStyle(String id, String displayName, Formatting color, String description) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.description = description;
	}

	@Override
	public String asString() {
		return this.id;
	}

	public String getId() {
		return this.id;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Formatting getColor() {
		return this.color;
	}

	public String getDescription() {
		return this.description;
	}

	public String getFormattedName() {
		return this.color.toString() + this.displayName;
	}

	public Formatting getFormatting() {
		return this.color;
	}

	public Text getFormattedText() {
		return Text.literal(this.displayName).formatted(this.color);
	}

	public static ArchitectureStyle byId(String id) {
		if (id == null) {
			return BIOME_NATIVE;
		}
		for (ArchitectureStyle style : values()) {
			if (style.id.equalsIgnoreCase(id)) {
				return style;
			}
		}
		return BIOME_NATIVE;
	}
}
