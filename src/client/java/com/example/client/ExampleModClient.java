package com.example.client;

import com.example.client.gui.CommandScepterScreen;
import com.example.client.gui.MinionScreen;
import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.BlueprintHologramRenderer;
import com.example.client.renderer.MinionEntityRenderer;
import com.example.client.renderer.TntProjectileRenderer;
import com.example.component.CommandMode;
import com.example.entity.ModEntities;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import com.example.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-specific entrypoint for the mod, loaded only on the physical Minecraft client.
 * Registers client-side entity renderers and visual handlers.
 */
public class ExampleModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("modid-client");

	public static KeyBinding commandHubKey;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Fabric 1.21 Example Mod Client");

		// Register the custom projectile renderer so the projectile renders as a spinning 3D TNT item in flight
		EntityRendererRegistry.register(ModEntities.TNT_PROJECTILE, TntProjectileRenderer::new);

		// Register the Minion entity renderer with biped model, armor, and held item support
		EntityRendererRegistry.register(ModEntities.MINION, MinionEntityRenderer::new);

		// Register client-side handled screens
		HandledScreens.register(ModScreenHandlers.MINION_SCREEN_HANDLER, MinionScreen::new);

		// Wire client-side networking
		ModClientNetworking.registerClientNetworking();

		// Register 3D holographic blueprint wireframe renderer for WorldRenderEvents.AFTER_TRANSLUCENT
		BlueprintHologramRenderer.register();

		// Register the Command Hub GUI opener callback for CommandScepterItem sneak-right-click
		CommandScepterItem.SCREEN_OPENER = (player, hand, stack) -> {
			MinecraftClient.getInstance().setScreen(new CommandScepterScreen(hand, stack));
		};

		// Register keybind V to open Command Hub GUI
		commandHubKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.modid-mmcli-agent-modding.command_hub",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_V,
			"category.modid-mmcli-agent-modding.general"
		));

		// Tick handler for the keybind
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (commandHubKey.wasPressed()) {
				if (client.player != null) {
					ItemStack mainStack = client.player.getMainHandStack();
					ItemStack offStack = client.player.getOffHandStack();
					if (mainStack.isOf(ModItems.COMMAND_SCEPTER)) {
						client.setScreen(new CommandScepterScreen(Hand.MAIN_HAND, mainStack));
					} else if (offStack.isOf(ModItems.COMMAND_SCEPTER)) {
						client.setScreen(new CommandScepterScreen(Hand.OFF_HAND, offStack));
					} else {
						ItemStack invStack = null;
						for (int i = 0; i < client.player.getInventory().size(); i++) {
							ItemStack s = client.player.getInventory().getStack(i);
							if (s.isOf(ModItems.COMMAND_SCEPTER)) {
								invStack = s;
								break;
							}
						}
						if (invStack != null) {
							client.setScreen(new CommandScepterScreen(Hand.MAIN_HAND, invStack));
						} else {
							client.player.sendMessage(
								Text.translatable("message.modid-mmcli-agent-modding.scepter_required"),
								true
							);
						}
					}
				}
			}

			// Open-air sneak + left-click shortcut to deselect all minions when no block or entity is targeted
			if (client.player != null && client.options.attackKey.wasPressed()) {
				ItemStack heldScepter = CommandScepterItem.getHeldScepter(client.player);
				if (client.player.isSneaking() && !heldScepter.isEmpty()) {
					if (client.crosshairTarget == null || client.crosshairTarget.getType() == HitResult.Type.MISS) {
						CommandMode mode = CommandScepterItem.getMode(heldScepter);
						if (mode != CommandMode.BUILD) {
							ModClientNetworking.sendDeselectAllMinions();
						}
					}
				}
			}
		});
	}
}
