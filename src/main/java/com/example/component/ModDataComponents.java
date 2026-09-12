package com.example.component;

import com.example.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registry for custom item data components introduced in Minecraft 1.21.
 * Defines persistent data attached to items such as the Command Scepter.
 */
public class ModDataComponents {

	/**
	 * Data component storing the active {@link CommandMode} on an item.
	 */
	public static final ComponentType<CommandMode> COMMAND_MODE = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(ExampleMod.MOD_ID, "command_mode"),
		ComponentType.<CommandMode>builder()
			.codec(CommandMode.CODEC)
			.packetCodec(CommandMode.PACKET_CODEC)
			.build()
	);

	/**
	 * Data component storing the active structure blueprint identifier on an item.
	 */
	public static final ComponentType<String> ACTIVE_BLUEPRINT = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(ExampleMod.MOD_ID, "active_blueprint"),
		ComponentType.<String>builder()
			.codec(Codec.STRING)
			.packetCodec(PacketCodecs.STRING)
			.build()
	);

	/**
	 * Data component storing the active target {@link SquadGroup} filter on an item (e.g. Command Scepter).
	 */
	public static final ComponentType<SquadGroup> TARGET_SQUAD = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(ExampleMod.MOD_ID, "target_squad"),
		ComponentType.<SquadGroup>builder()
			.codec(SquadGroup.CODEC)
			.packetCodec(SquadGroup.PACKET_CODEC)
			.build()
	);

	/**
	 * Data component storing the active architectural structure rotation index on an item (e.g. Command Scepter).
	 * Stored as an integer index: 0 -> 0°, 1 -> 90°, 2 -> 180°, 3 -> 270°.
	 */
	public static final ComponentType<Integer> STRUCTURE_ROTATION = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(ExampleMod.MOD_ID, "structure_rotation"),
		ComponentType.<Integer>builder()
			.codec(Codec.INT)
			.packetCodec(PacketCodecs.INTEGER)
			.build()
	);

	/**
	 * Static initializer method invoked during mod initialization
	 * to ensure all static component type registrations are registered.
	 */
	public static void registerDataComponents() {
		ExampleMod.LOGGER.info("Registering Mod Data Components for {}", ExampleMod.MOD_ID);
	}
}
