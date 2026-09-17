package com.example.client;

import com.example.client.camera.TacticalBuildCameraController;
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
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
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
	public static KeyBinding retreatKey;
	public static KeyBinding tacticalCameraKey;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Fabric 1.21 Example Mod Client");

		// Register the custom projectile renderer so the projectile renders as a spinning 3D TNT item in flight
		EntityRendererRegistry.register(ModEntities.TNT_PROJECTILE, TntProjectileRenderer::new);

		// Register cryogenic frost projectile renderer
		EntityRendererRegistry.register(ModEntities.FROST_PROJECTILE, FlyingItemEntityRenderer::new);

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

		// Hook client-side camera raycast target resolver into CommandScepterItem
		CommandScepterItem.CLIENT_TARGET_RESOLVER = (player) ->
			TacticalBuildCameraController.getCameraTargetedBlock(MinecraftClient.getInstance(), 96.0F);

		// Intercept right-clicks with the Command Scepter in BUILD or MINE mode
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient()) {
				return TypedActionResult.pass(player.getStackInHand(hand));
			}

			ItemStack stack = player.getStackInHand(hand);
			if (!stack.isOf(ModItems.COMMAND_SCEPTER)) {
				return TypedActionResult.pass(stack);
			}

			// Sneak-right-click is reserved for Command Hub GUI (checks physical sneak key to work in flight)
			boolean isSneakDown = player.isSneaking() || MinecraftClient.getInstance().options.sneakKey.isPressed();
			if (isSneakDown) {
				MinecraftClient.getInstance().setScreen(new CommandScepterScreen(hand, stack));
				return TypedActionResult.success(stack);
			}

			if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
				return TypedActionResult.pass(stack);
			}

			CommandMode mode = CommandScepterItem.getMode(stack);
			if (mode == CommandMode.BUILD || mode == CommandMode.MINE) {
				MinecraftClient client = MinecraftClient.getInstance();
				BlockHitResult hit = TacticalBuildCameraController.getCameraTargetedBlock(client, 96.0F);
				if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
					boolean isDismantle = (mode == CommandMode.MINE);
					ModClientNetworking.sendAnchorConstruction(hit.getBlockPos(), hit.getSide(), isDismantle);
					player.getItemCooldownManager().set(stack.getItem(), 10);
					player.swingHand(hand);
					if (client.world != null) {
						client.world.playSound(player, hit.getBlockPos(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
					}
					return TypedActionResult.success(stack);
				} else if (mode == CommandMode.BUILD) {
					// Aiming at sky in BUILD mode: quick-tap cycles blueprint
					CommandScepterItem.cycleBlueprint(stack, player, world);
					player.getItemCooldownManager().set(stack.getItem(), 4);
					ModClientNetworking.sendUpdateScepter(
						mode,
						CommandScepterItem.getBlueprintId(stack),
						CommandScepterItem.getTargetSquad(stack),
						CommandScepterItem.getRotationIndex(stack),
						Optional.empty(),
						false,
						CommandScepterItem.getArchitectureStyle(stack),
						CommandScepterItem.getBuildingSize(stack)
					);
					player.swingHand(hand);
					return TypedActionResult.success(stack);
				}
			}

			return TypedActionResult.pass(stack);
		});

		// Register keybind V to open Command Hub GUI
		commandHubKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.modid-mmcli-agent-modding.command_hub",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_V,
			"category.modid-mmcli-agent-modding.general"
		));

		// Register keybind R for Panic Retreat / Regroup (or Blueprint Rotation in BUILD mode)
		retreatKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.modid-mmcli-agent-modding.retreat",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_R,
			"category.modid-mmcli-agent-modding.general"
		));

		// Register keybind H for Tactical Camera Zoom & Toggle
		tacticalCameraKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.modid-mmcli-agent-modding.tactical_camera",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			"category.modid-mmcli-agent-modding.general"
		));

		// Tick handler for flight and keybinds
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			BuildFlightManager.tick(client);

			while (tacticalCameraKey.wasPressed()) {
				com.example.client.camera.TacticalBuildCameraController.cycleZoomPreset();
				if (client.player != null) {
					boolean on = com.example.client.camera.TacticalBuildCameraController.isEnabled();
					client.player.sendMessage(
						Text.literal("§b📷 Tactical Camera: " + (on ? "§aACTIVE" : "§cDISABLED")),
						true
					);
				}
			}

			while (retreatKey.wasPressed()) {
				if (client.player != null) {
					ItemStack heldScepter = CommandScepterItem.getHeldScepter(client.player);
					if (!heldScepter.isEmpty() && CommandScepterItem.getMode(heldScepter) == CommandMode.BUILD) {
						// In BUILD mode: pressing R rotates the blueprint 90° clockwise!
						CommandScepterItem.cycleRotation(heldScepter, client.player);
						ModClientNetworking.sendUpdateScepter(
							CommandMode.BUILD,
							CommandScepterItem.getBlueprintId(heldScepter),
							CommandScepterItem.getTargetSquad(heldScepter),
							CommandScepterItem.getRotationIndex(heldScepter),
							false
						);
					} else {
						com.example.component.SquadGroup squad = !heldScepter.isEmpty() ? CommandScepterItem.getTargetSquad(heldScepter) : com.example.component.SquadGroup.ALL;
						ModClientNetworking.sendRetreat(squad);
						com.example.client.renderer.ClientConstructionTracker.clear();
					}
				}
			}

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

			// Left-click / Attack key handling:
			// In BUILD mode: any left-click (with or without Shift, looking at air, block, or ground from any distance)
			// cycles rotation, unless directly clicking an owned minion (which toggles minion selection).
			// In other modes: Shift + left-click deselects all minions.
			if (client.player != null && client.options.attackKey.wasPressed()) {
				ItemStack heldScepter = CommandScepterItem.getHeldScepter(client.player);
				if (!heldScepter.isEmpty()) {
					CommandMode heldMode = CommandScepterItem.getMode(heldScepter);
					boolean physicalSneak = client.options.sneakKey.isPressed() || client.player.isSneaking();

					if (heldMode == CommandMode.BUILD) {
						// Don't cycle rotation if targeting an owned minion to toggle selection
						boolean isTargetingOwnedMinion = client.targetedEntity instanceof com.example.entity.custom.MinionEntity minion
							&& minion.isOwner(client.player);
						if (!isTargetingOwnedMinion) {
							CommandScepterItem.cycleRotation(heldScepter, client.player);
							ModClientNetworking.sendUpdateScepter(
								heldMode,
								CommandScepterItem.getBlueprintId(heldScepter),
								CommandScepterItem.getTargetSquad(heldScepter),
								CommandScepterItem.getRotationIndex(heldScepter),
								false
							);
						}
					} else if (physicalSneak) {
						ModClientNetworking.sendDeselectAllMinions();
					}
				}
			}
		});
	}
}
