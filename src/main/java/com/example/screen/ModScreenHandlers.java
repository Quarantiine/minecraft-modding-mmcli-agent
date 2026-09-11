package com.example.screen;

import com.example.ExampleMod;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * Screen handler type registry for the mod.
 * Registers {@link MinionScreenHandler} with Fabric's {@link ExtendedScreenHandlerType}.
 */
public class ModScreenHandlers {

	public static final ScreenHandlerType<MinionScreenHandler> MINION_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER,
		Identifier.of(ExampleMod.MOD_ID, "minion_screen_handler"),
		new ExtendedScreenHandlerType<>(MinionScreenHandler::new, PacketCodecs.INTEGER.cast())
	);

	/**
	 * Initializes and registers mod screen handler types.
	 * Must be invoked during common mod initialization.
	 */
	public static void registerScreenHandlers() {
		ExampleMod.LOGGER.info("Registering Screen Handlers for {}", ExampleMod.MOD_ID);
	}
}
