package com.example.network;

import com.example.ExampleMod;
import com.example.blueprint.BlueprintRegistry;
import com.example.component.CommandMode;
import com.example.component.SquadGroup;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.MinionRole;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import com.example.screen.MinionScreenHandler;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/**
 * Central networking registry for Fabric C2S / S2C payloads and server receivers.
 * Handles server-side synchronization of Command Scepter configurations and directives.
 */
public class ModNetworking {

	/**
	 * Registers custom payload types for client-to-server networking.
	 * Must be invoked during common mod initialization.
	 */
	public static void registerC2SPayloads() {
		ExampleMod.LOGGER.info("Registering C2S networking payloads for {}", ExampleMod.MOD_ID);
		PayloadTypeRegistry.playC2S().register(UpdateScepterPayload.ID, UpdateScepterPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(UpdateMinionConfigPayload.ID, UpdateMinionConfigPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(DismissMinionPayload.ID, DismissMinionPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(TeleportMinionPayload.ID, TeleportMinionPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(DeselectMinionsPayload.ID, DeselectMinionsPayload.PACKET_CODEC);
	}

	/**
	 * Registers server-side packet receivers for scepter updates and minion directives.
	 */
	public static void registerServerReceivers() {
		ExampleMod.LOGGER.info("Registering server network packet receivers for {}", ExampleMod.MOD_ID);
		ServerPlayNetworking.registerGlobalReceiver(UpdateScepterPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleUpdateScepter(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(UpdateMinionConfigPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleUpdateMinionConfig(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(DismissMinionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleDismissMinion(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(TeleportMinionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleTeleportMinion(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(DeselectMinionsPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleDeselectMinions(player, payload));
		});
	}

	/**
	 * Processes scepter updates on the main server thread.
	 * Resolves the held or inventory scepter, applies updated modes and blueprint selections,
	 * emits audio and actionbar confirmations, and optionally executes directives.
	 *
	 * @param player  The commanding server player.
	 * @param payload The incoming scepter configuration payload.
	 */
	private static void handleUpdateScepter(ServerPlayerEntity player, UpdateScepterPayload payload) {
		if (player == null || payload == null) {
			return;
		}

		// 1. Resolve scepter ItemStack from main hand, off hand, or inventory
		ItemStack scepterStack = null;
		if (player.getMainHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getMainHandStack();
		} else if (player.getOffHandStack().isOf(ModItems.COMMAND_SCEPTER)) {
			scepterStack = player.getOffHandStack();
		} else {
			for (int i = 0; i < player.getInventory().size(); i++) {
				ItemStack stack = player.getInventory().getStack(i);
				if (stack.isOf(ModItems.COMMAND_SCEPTER)) {
					scepterStack = stack;
					break;
				}
			}
		}

		if (scepterStack == null) {
			return;
		}

		// 2. Apply updated CommandMode, active blueprint, target squad, and rotation
		CommandMode mode = payload.mode();
		String blueprintId = payload.blueprintId();
		SquadGroup targetSquad = payload.targetSquad();
		int rotation = payload.rotation();

		if (mode != null) {
			CommandScepterItem.setMode(scepterStack, mode);
		}
		if (blueprintId != null && !blueprintId.isBlank()) {
			CommandScepterItem.setBlueprintId(scepterStack, blueprintId);
		}
		if (targetSquad != null) {
			CommandScepterItem.setTargetSquad(scepterStack, targetSquad);
		}
		CommandScepterItem.setRotationIndex(scepterStack, rotation);

		// 3. Audio & actionbar feedback
		CommandMode currentMode = CommandScepterItem.getMode(scepterStack);
		player.getServerWorld().playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.BLOCK_NOTE_BLOCK_CHIME,
			SoundCategory.PLAYERS,
			1.0F,
			currentMode.getPitch()
		);

		String bpName = BlueprintRegistry.getOrDefault(CommandScepterItem.getBlueprintId(scepterStack)).getName();
		SquadGroup currentSquad = CommandScepterItem.getTargetSquad(scepterStack);
		int currentRotDeg = CommandScepterItem.getRotationIndex(scepterStack) * 90;
		player.sendMessage(
			Text.literal("§6✦ Scepter Updated: " + currentMode.getFormattedName() + " §7| §b" + bpName + " §7(" + currentRotDeg + "°) §7| " + currentSquad.getFormattedName()),
			true
		);

		// 4. Optionally execute tactical directive
		if (payload.executeDirective()) {
			CommandScepterItem.executeDirective(player, player.getServerWorld(), currentMode, currentSquad);
		}
	}

	/**
	 * Handles minion configuration updates (archetype role and squad channel) dispatched from client management screens.
	 * Verifies commanding player ownership before updating the target minion entity.
	 *
	 * @param player  The commanding server player.
	 * @param payload The update minion configuration payload.
	 */
	private static void handleUpdateMinionConfig(ServerPlayerEntity player, UpdateMinionConfigPayload payload) {
		if (player == null || payload == null) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		Entity entity = world.getEntityById(payload.minionId());
		if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
			if (payload.role() != null) {
				minion.setRole(payload.role());
			}
			if (payload.squad() != null) {
				minion.setSquad(payload.squad());
			}

			world.playSound(
				null,
				minion.getX(),
				minion.getY(),
				minion.getZ(),
				SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
				SoundCategory.NEUTRAL,
				0.6F,
				1.2F
			);

			player.sendMessage(
				Text.literal("§a✔ Minion configuration updated: " + minion.getRole().getFormattedName() + " §7| " + minion.getSquad().getFormattedName()),
				true
			);
		}
	}

	/**
	 * Handles minion dismissal requests from client GUIs.
	 * Can dismiss an individual minion by entity ID, or all owned minions within the command radius.
	 *
	 * @param player  The commanding server player.
	 * @param payload The dismissal payload containing target minion ID or broadcast flag.
	 */
	private static void handleDismissMinion(ServerPlayerEntity player, DismissMinionPayload payload) {
		if (player == null || payload == null) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		int dismissedCount = 0;

		if (payload.dismissAll() || payload.minionId() < 0) {
			// Dismiss all owned minions within command radius (32 blocks)
			Box searchBox = player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS);
			List<MinionEntity> nearbyMinions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player)
			);

			for (MinionEntity minion : nearbyMinions) {
				minion.dismiss();
				dismissedCount++;
			}

			if (player.currentScreenHandler instanceof MinionScreenHandler) {
				player.closeHandledScreen();
			}

			if (dismissedCount > 0) {
				player.sendMessage(
					Text.translatable("message.modid-mmcli-agent-modding.minions_dismissed", dismissedCount),
					true
				);
			} else {
				player.sendMessage(
					Text.translatable("message.modid-mmcli-agent-modding.no_minions_to_dismiss"),
					true
				);
			}
		} else {
			// Dismiss single minion by entity ID
			Entity entity = world.getEntityById(payload.minionId());
			if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
				minion.dismiss();
				if (player.currentScreenHandler instanceof MinionScreenHandler handler && handler.getMinionId() == payload.minionId()) {
					player.closeHandledScreen();
				}
				player.sendMessage(
					Text.translatable("message.modid-mmcli-agent-modding.minion_dismissed"),
					true
				);
			}
		}
	}

	/**
	 * Handles minion teleportation requests from client GUIs.
	 * Can teleport an individual minion by entity ID, or all owned minions within the command radius.
	 * Safe landing checks, particle effects, and audio are executed on the minion thralls.
	 *
	 * @param player  The commanding server player.
	 * @param payload The teleport payload containing target minion ID or broadcast flag.
	 */
	private static void handleTeleportMinion(ServerPlayerEntity player, TeleportMinionPayload payload) {
		if (player == null || payload == null) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		int teleportedCount = 0;

		if (payload.teleportAll() || payload.minionId() < 0) {
			// Teleport all owned minions within command radius (32 blocks)
			Box searchBox = player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS);
			List<MinionEntity> nearbyMinions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player)
			);

			for (MinionEntity minion : nearbyMinions) {
				if (minion.teleportToPlayer(player)) {
					if (minion.isSitting()) {
						minion.setSitting(false);
					}
					teleportedCount++;
				}
			}

			if (teleportedCount > 0) {
				player.sendMessage(
					Text.translatable("message.modid-mmcli-agent-modding.minions_teleported", teleportedCount),
					true
				);
			} else {
				player.sendMessage(
					Text.translatable("message.modid-mmcli-agent-modding.no_minions_to_teleport"),
					true
				);
			}
		} else {
			// Teleport single minion by entity ID
			Entity entity = world.getEntityById(payload.minionId());
			if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
				if (minion.teleportToPlayer(player)) {
					if (minion.isSitting()) {
						minion.setSitting(false);
					}
					player.sendMessage(
						Text.translatable("message.modid-mmcli-agent-modding.minion_teleported"),
						true
					);
				}
			}
		}
	}

	/**
	 * Handles minion deselection requests from client GUIs and shortcuts.
	 * Deselects all owned minions or a specific minion, anchoring them at their posts without sitting.
	 *
	 * @param player  The commanding server player.
	 * @param payload The deselection payload.
	 */
	private static void handleDeselectMinions(ServerPlayerEntity player, DeselectMinionsPayload payload) {
		if (player == null || payload == null) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		if (payload.deselectAll() || payload.minionId() < 0) {
			CommandScepterItem.deselectAllMinions(player, world);
		} else {
			Entity entity = world.getEntityById(payload.minionId());
			if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
				CommandScepterItem.toggleMinionSelection(player, minion);
			}
		}
	}
}
