package com.example.client;

import com.example.client.camera.TacticalBuildCameraController;
import com.example.client.gui.BlueprintCaptureModalScreen;
import com.example.client.gui.CommandScepterScreen;
import com.example.client.gui.MinionScreen;
import com.example.client.network.ModClientNetworking;
import com.example.client.renderer.BlueprintHologramRenderer;
import com.example.client.renderer.ClientDesignCaptureTracker;
import com.example.client.renderer.ClientMiningCaptureTracker;
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
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-specific entrypoint for the mod, loaded only on the physical Minecraft client.
 * Registers client-side entity renderers, visual hologram handlers, input hooks, and GUI screens.
 */
public class ExampleModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("modid-client");

	public static KeyBinding commandHubKey;
	public static KeyBinding retreatKey;
	public static KeyBinding tacticalCameraKey;

	public static long lastDesignClickTime = 0L;
	public static long lastDesignClickTick = -1L;
	public static long lastMineClickTime = 0L;
	public static long lastMineClickTick = -1L;
	public static long lastHeightKeyTime = 0L;

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
		com.example.client.renderer.PathwayHologramRenderer.register();

		// Register the Command Hub GUI opener callback for CommandScepterItem sneak-right-click
		CommandScepterItem.SCREEN_OPENER = (player, hand, stack) -> {
			MinecraftClient.getInstance().setScreen(new CommandScepterScreen(hand, stack));
		};

		// Register the Blueprint Capture Modal opener callback for CommandScepterItem right-click in DESIGN mode
		CommandScepterItem.CAPTURE_MODAL_OPENER = (player, hand, stack, p1, p2) -> {
			if (p1 == null) p1 = ClientDesignCaptureTracker.getPos1();
			if (p2 == null) p2 = ClientDesignCaptureTracker.getPos2();
			if (p1 != null && p2 != null) {
				MinecraftClient.getInstance().setScreen(new BlueprintCaptureModalScreen(p1, p2, stack, null));
			} else if (player != null) {
				player.sendMessage(Text.literal("§c✦ Left-click two blocks to set Pos1 and Pos2 before capturing!§r"), true);
			}
		};

		// Register the Mining Area Confirm Modal opener callback for CommandScepterItem right-click in MINE AREA mode
		CommandScepterItem.MINE_MODAL_OPENER = (player, hand, stack, p1, p2) -> {
			if (p1 == null) p1 = ClientMiningCaptureTracker.getPos1();
			if (p2 == null) p2 = ClientMiningCaptureTracker.getPos2();
			if (p1 != null && p2 != null) {
				MinecraftClient.getInstance().setScreen(new com.example.client.gui.MiningConfirmModalScreen(p1, p2, stack, null));
			} else if (player != null) {
				player.sendMessage(Text.literal("§c✦ Left-click two blocks to set Pos1 and Pos2 before confirming mining area!§r"), true);
			}
		};

		// Hook client-side design corner stepper and resetter into CommandScepterItem for DESIGN mode
		CommandScepterItem.DESIGN_CORNER_STEPPER = ClientDesignCaptureTracker::stepCorner;
		CommandScepterItem.DESIGN_CORNER_RESETTER = ClientDesignCaptureTracker::clear;

		// Hook client-side mining corner stepper and resetter into CommandScepterItem for MINE AREA mode
		CommandScepterItem.MINE_CORNER_STEPPER = ClientMiningCaptureTracker::stepCorner;
		CommandScepterItem.MINE_CORNER_RESETTER = ClientMiningCaptureTracker::clear;

		CommandScepterItem.DESIGN_CLICK_CONSUMER = () -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			while (mc.options.attackKey.wasPressed()) {}
			try {
				mc.options.attackKey.setPressed(false);
			} catch (Throwable ignored) {}
			lastDesignClickTime = System.currentTimeMillis();
			lastDesignClickTick = mc.world != null ? mc.world.getTime() : 0L;
			CommandScepterItem.recordDesignClick();
		};
		CommandScepterItem.MINE_CLICK_CONSUMER = () -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			while (mc.options.attackKey.wasPressed()) {}
			try {
				mc.options.attackKey.setPressed(false);
			} catch (Throwable ignored) {}
			lastMineClickTime = System.currentTimeMillis();
			lastMineClickTick = mc.world != null ? mc.world.getTime() : 0L;
			CommandScepterItem.recordMineClick();
		};

		// Hook client-side camera raycast target resolver into CommandScepterItem
		CommandScepterItem.CLIENT_TARGET_RESOLVER = (player) ->
			TacticalBuildCameraController.getCameraTargetedBlock(MinecraftClient.getInstance(), 96.0F);

		// Hook client-side waypoint checker for PATHWAY mode into CommandScepterItem
		CommandScepterItem.CLIENT_WAYPOINT_CHECKER = (routeId, pos) -> {
			return com.example.client.renderer.ClientPatrolRouteTracker.isAnyWaypoint(pos);
		};

		// Intercept right-clicks with the Command Scepter in BUILD, MINE, or DESIGN mode
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient()) {
				return TypedActionResult.pass(player.getStackInHand(hand));
			}

			ItemStack stack = player.getStackInHand(hand);
			if (!stack.isOf(ModItems.COMMAND_SCEPTER)) {
				return TypedActionResult.pass(stack);
			}

			// Sneak-right-click is reserved for Command Hub GUI (checks physical sneak key to work in flight)
			// Bypasses GUI if aiming directly at an existing waypoint in PATHWAY mode to allow deletion
			boolean isSneakDown = player.isSneaking() || MinecraftClient.getInstance().options.sneakKey.isPressed();
			if (isSneakDown) {
				CommandMode mode = CommandScepterItem.getMode(stack);
				if (mode == CommandMode.PATHWAY) {
					HitResult hit = MinecraftClient.getInstance().crosshairTarget;
					if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
						BlockPos clicked = blockHit.getBlockPos();
						BlockPos top = clicked.offset(blockHit.getSide());
						if (CommandScepterItem.isClientWaypoint(stack, clicked) || CommandScepterItem.isClientWaypoint(stack, top) || CommandScepterItem.isClientWaypoint(stack, clicked.up())) {
							return TypedActionResult.pass(stack);
						}
					}
				}
				MinecraftClient.getInstance().setScreen(new CommandScepterScreen(hand, stack));
				return TypedActionResult.success(stack);
			}

			if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
				return TypedActionResult.pass(stack);
			}

			CommandMode mode = CommandScepterItem.getMode(stack);
			if (mode == CommandMode.DESIGN) {
				BlockPos p1 = CommandScepterItem.getDesignPos1(stack);
				if (p1 == null) p1 = ClientDesignCaptureTracker.getPos1();
				BlockPos p2 = CommandScepterItem.getDesignPos2(stack);
				if (p2 == null) p2 = ClientDesignCaptureTracker.getPos2();
				if (p1 != null && p2 != null) {
					if (CommandScepterItem.CAPTURE_MODAL_OPENER != null) {
						CommandScepterItem.CAPTURE_MODAL_OPENER.openCaptureModal(player, hand, stack, p1, p2);
					} else {
						MinecraftClient.getInstance().setScreen(new BlueprintCaptureModalScreen(p1, p2, stack, null));
					}
					player.getItemCooldownManager().set(stack.getItem(), 4);
					player.swingHand(hand);
					return TypedActionResult.success(stack);
				} else {
					player.sendMessage(Text.literal("§c✦ Left-click two blocks to set Pos1 and Pos2 before capturing!§r"), true);
					player.getItemCooldownManager().set(stack.getItem(), 4);
					return TypedActionResult.success(stack);
				}
			}

			if (mode == CommandMode.MINE && CommandScepterItem.getMiningMode(stack) == com.example.component.MiningMode.AREA) {
				BlockPos p1 = CommandScepterItem.getMinePos1(stack);
				if (p1 == null) p1 = ClientMiningCaptureTracker.getPos1();
				BlockPos p2 = CommandScepterItem.getMinePos2(stack);
				if (p2 == null) p2 = ClientMiningCaptureTracker.getPos2();
				if (p1 != null && p2 != null) {
					if (CommandScepterItem.MINE_MODAL_OPENER != null) {
						CommandScepterItem.MINE_MODAL_OPENER.openMineModal(player, hand, stack, p1, p2);
					} else {
						MinecraftClient.getInstance().setScreen(new com.example.client.gui.MiningConfirmModalScreen(p1, p2, stack, null));
					}
					player.getItemCooldownManager().set(stack.getItem(), 4);
					player.swingHand(hand);
					return TypedActionResult.success(stack);
				} else {
					player.sendMessage(Text.literal("§c✦ Left-click two blocks to set Pos1 and Pos2 before confirming mining area!§r"), true);
					player.getItemCooldownManager().set(stack.getItem(), 4);
					return TypedActionResult.success(stack);
				}
			}

			if (mode == CommandMode.BUILD || (mode == CommandMode.MINE && CommandScepterItem.getMiningMode(stack) == com.example.component.MiningMode.DIRECT)) {
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
						false
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
						boolean isEmergency = net.minecraft.client.gui.screen.Screen.hasShiftDown()
							|| client.options.sneakKey.isPressed()
							|| client.player.isSneaking();
						com.example.component.SquadGroup squad = !heldScepter.isEmpty() ? CommandScepterItem.getTargetSquad(heldScepter) : com.example.component.SquadGroup.ALL;
						ModClientNetworking.sendRetreat(squad, isEmergency);
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

			// In DESIGN and MINE AREA modes: Keyboard shortcuts for height adjustment ([ / ] and PageUp / PageDown)
			if (client.player != null && client.currentScreen == null && client.getWindow() != null) {
				ItemStack heldScepter = CommandScepterItem.getHeldScepter(client.player);
				if (!heldScepter.isEmpty()) {
					CommandMode heldMode = CommandScepterItem.getMode(heldScepter);
					if (heldMode == CommandMode.DESIGN) {
						long win = client.getWindow().getHandle();
						long now = System.currentTimeMillis();
						if (now - lastHeightKeyTime >= 150L) {
							boolean upPressed = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_RIGHT_BRACKET)
								|| InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_PAGE_UP);
							boolean downPressed = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_LEFT_BRACKET)
								|| InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_PAGE_DOWN);
							if (upPressed || downPressed) {
								int step = upPressed ? 1 : -1;
								if (net.minecraft.client.gui.screen.Screen.hasShiftDown()) step *= 5;
								int newH = ClientDesignCaptureTracker.adjustHeight(step);
								if (newH > 0 && ClientDesignCaptureTracker.getPos1() != null && ClientDesignCaptureTracker.getPos2() != null) {
									int minY = Math.min(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY());
									int maxY = Math.max(ClientDesignCaptureTracker.getPos1().getY(), ClientDesignCaptureTracker.getPos2().getY());
									client.player.sendMessage(
										Text.literal("§d✦ Design Box Height: §f" + newH + " blocks §8(Y: " + minY + " → " + maxY + ") §8| §7" + ClientDesignCaptureTracker.getDimensionString()),
										true
									);
									client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.7F, 1.2F);
								}
								lastHeightKeyTime = now;
							}
						}
					} else if (heldMode == CommandMode.MINE && CommandScepterItem.getMiningMode(heldScepter) == com.example.component.MiningMode.AREA) {
						long win = client.getWindow().getHandle();
						long now = System.currentTimeMillis();
						if (now - lastHeightKeyTime >= 150L) {
							boolean upPressed = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_RIGHT_BRACKET)
								|| InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_PAGE_UP);
							boolean downPressed = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_LEFT_BRACKET)
								|| InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_PAGE_DOWN);
							if (upPressed || downPressed) {
								int step = upPressed ? 1 : -1;
								if (net.minecraft.client.gui.screen.Screen.hasShiftDown()) step *= 5;
								int newH = ClientMiningCaptureTracker.adjustHeight(step);
								if (newH > 0 && ClientMiningCaptureTracker.getPos1() != null && ClientMiningCaptureTracker.getPos2() != null) {
									int minY = Math.min(ClientMiningCaptureTracker.getPos1().getY(), ClientMiningCaptureTracker.getPos2().getY());
									int maxY = Math.max(ClientMiningCaptureTracker.getPos1().getY(), ClientMiningCaptureTracker.getPos2().getY());
									client.player.sendMessage(
										Text.literal("§6✦ Mining Box Height: §f" + newH + " blocks §8(Y: " + minY + " → " + maxY + ") §8| §e" + ClientMiningCaptureTracker.getDimensionString()),
										true
									);
									client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.7F, 1.4F);
								}
								lastHeightKeyTime = now;
							}
						}
					}
				}
			}

			// Left-click / Attack key handling:
			// In BUILD mode: cycles rotation.
			// In DESIGN mode: sets Pos1 corner coordinate.
			// In other modes: Shift + left-click deselects all minions.
			if (client.player != null && client.options.attackKey.wasPressed()) {
				ItemStack heldScepter = CommandScepterItem.getHeldScepter(client.player);
				if (!heldScepter.isEmpty()) {
					CommandMode heldMode = CommandScepterItem.getMode(heldScepter);
					boolean physicalSneak = client.options.sneakKey.isPressed() || client.player.isSneaking();

					if (heldMode == CommandMode.BUILD) {
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
					} else if (heldMode == CommandMode.DESIGN) {
						long currentTick = client.world != null ? client.world.getTime() : 0L;
						long currentTime = System.currentTimeMillis();
						if (currentTick == lastDesignClickTick || (currentTime - lastDesignClickTime) < 350L || CommandScepterItem.isDesignClickDebounced()) {
							while (client.options.attackKey.wasPressed()) {}
						} else {
							// Tactical camera extended-range raycasting (blocks beyond normal reach)
							boolean tacticalActive = com.example.client.camera.TacticalBuildCameraController.isEnabled();
							if (tacticalActive) {
								BlockHitResult hit = TacticalBuildCameraController.getCameraTargetedBlock(client, 96.0F);
								if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
									BlockPos clicked = hit.getBlockPos();
									BlockPos effectiveClicked = (client.world != null && client.world.getBlockState(clicked).isReplaceable())
										? clicked
										: (hit.getSide() != null ? clicked.offset(hit.getSide()) : clicked.up());
									lastDesignClickTick = currentTick;
									lastDesignClickTime = currentTime;
									while (client.options.attackKey.wasPressed()) {}
									try {
										client.options.attackKey.setPressed(false);
									} catch (Throwable ignored) {}
									CommandScepterItem.handleDesignClick(client.player, client.world, heldScepter, effectiveClicked, physicalSneak);
								}
							} else if (physicalSneak) {
								// Open-air Shift + Left-Click reset (AttackBlockCallback only fires for targeted blocks)
								if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
									lastDesignClickTick = currentTick;
									lastDesignClickTime = currentTime;
									while (client.options.attackKey.wasPressed()) {}
									try {
										client.options.attackKey.setPressed(false);
									} catch (Throwable ignored) {}
									CommandScepterItem.handleDesignReset(client.player, client.world, heldScepter);
								}
							}
						}
					} else if (heldMode == CommandMode.MINE && CommandScepterItem.getMiningMode(heldScepter) == com.example.component.MiningMode.AREA) {
						long currentTick = client.world != null ? client.world.getTime() : 0L;
						long currentTime = System.currentTimeMillis();
						if (currentTick == lastMineClickTick || (currentTime - lastMineClickTime) < 350L || CommandScepterItem.isMineClickDebounced()) {
							while (client.options.attackKey.wasPressed()) {}
						} else {
							// Tactical camera extended-range raycasting (blocks beyond normal reach)
							boolean tacticalActive = com.example.client.camera.TacticalBuildCameraController.isEnabled();
							if (tacticalActive) {
								BlockHitResult hit = TacticalBuildCameraController.getCameraTargetedBlock(client, 96.0F);
								if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
									BlockPos clicked = hit.getBlockPos();
									BlockPos effectiveClicked = (client.world != null && client.world.getBlockState(clicked).isReplaceable())
										? clicked
										: (hit.getSide() != null ? clicked.offset(hit.getSide()) : clicked.up());
									lastMineClickTick = currentTick;
									lastMineClickTime = currentTime;
									while (client.options.attackKey.wasPressed()) {}
									try {
										client.options.attackKey.setPressed(false);
									} catch (Throwable ignored) {}
									CommandScepterItem.handleMineClick(client.player, client.world, heldScepter, effectiveClicked, physicalSneak);
								}
							} else if (physicalSneak) {
								// Open-air Shift + Left-Click reset (AttackBlockCallback only fires for targeted blocks)
								if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
									lastMineClickTick = currentTick;
									lastMineClickTime = currentTime;
									while (client.options.attackKey.wasPressed()) {}
									try {
										client.options.attackKey.setPressed(false);
									} catch (Throwable ignored) {}
									CommandScepterItem.handleMineReset(client.player, client.world, heldScepter);
								}
							}
						}
					} else if (physicalSneak) {
						ModClientNetworking.sendDeselectAllMinions();
					}
				}
			}
		});
	}
}
