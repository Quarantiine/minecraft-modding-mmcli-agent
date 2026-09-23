package com.example.blueprint;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;

/**
 * Defines functional building classifications for custom blueprints.
 * Supports villager-ready residences, observation watchtowers,
 * fortifications, supply depots, workshops, and arcane monuments.
 */
public enum BuildingCategory implements StringIdentifiable {
	HOME("home", "🏡 Home", Formatting.GREEN, "Living quarters for villagers with beds, doors, and crafting workstations."),
	WATCHTOWER("watchtower", "🗼 Watchtower", Formatting.AQUA, "Elevated tactical observation post with arrow slits and parapets."),
	BARRICADE("barricade", "🛡 Barricade", Formatting.GOLD, "Frontline barrier with firing steps and defensive spikes."),
	WORKSHOP("workshop", "⚒ Workshop", Formatting.YELLOW, "Blacksmith forge with furnaces, anvils, and tool crafting stations."),
	SUPPLY_DEPOT("supply_depot", "📦 Supply Depot", Formatting.LIGHT_PURPLE, "Storage warehouse with double chests and logistics barrels."),
	OBELISK("obelisk", "🔮 Obelisk", Formatting.DARK_PURPLE, "Arcane monument focusing mystical energy and restorative aura.");

	public static final Codec<BuildingCategory> CODEC = StringIdentifiable.createCodec(BuildingCategory::values);
	public static final PacketCodec<ByteBuf, BuildingCategory> PACKET_CODEC = PacketCodecs.indexed(
		i -> values()[Math.floorMod(i, values().length)],
		BuildingCategory::ordinal
	);

	private final String id;
	private final String displayName;
	private final Formatting color;
	private final String description;

	BuildingCategory(String id, String displayName, Formatting color, String description) {
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

	public Text getFormattedText() {
		return Text.literal(this.displayName).formatted(this.color);
	}

	public static BuildingCategory byId(String id) {
		if (id == null) {
			return HOME;
		}
		for (BuildingCategory cat : values()) {
			if (cat.id.equalsIgnoreCase(id)) {
				return cat;
			}
		}
		return HOME;
	}
}
