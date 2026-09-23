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
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Box;

/**
 * Central networking registry for Fabric C2S / S2C payloads and server receivers.
 * Handles server-side synchronization of Command Scepter configurations and directives.
 */
public class ModNetworking {

	/**
	 * Registers custom payload types for client-to-server and server-to-client networking.
	 * Must be invoked during common mod initialization.
	 */
	public static void registerC2SPayloads() {
		ExampleMod.LOGGER.info("Registering networking payloads for {}", ExampleMod.MOD_ID);
		PayloadTypeRegistry.playC2S().register(UpdateScepterPayload.ID, UpdateScepterPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(UpdateMinionConfigPayload.ID, UpdateMinionConfigPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(DismissMinionPayload.ID, DismissMinionPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(TeleportMinionPayload.ID, TeleportMinionPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(DeselectMinionsPayload.ID, DeselectMinionsPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(MassRolePayload.ID, MassRolePayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(RetreatPayload.ID, RetreatPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(AnchorConstructionPayload.ID, AnchorConstructionPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(ModifyPatrolRoutePayload.ID, ModifyPatrolRoutePayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(ConfigurePatrolRoutePayload.ID, ConfigurePatrolRoutePayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(CreateCustomBlueprintPayload.ID, CreateCustomBlueprintPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(CaptureSpatialBlueprintPayload.ID, CaptureSpatialBlueprintPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteCustomBlueprintPayload.ID, DeleteCustomBlueprintPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(StartMiningAreaPayload.ID, StartMiningAreaPayload.PACKET_CODEC);

		// S2C Payloads for active blueprint wireframe and patrol route synchronization
		PayloadTypeRegistry.playS2C().register(SyncConstructionSessionPayload.ID, SyncConstructionSessionPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(EndConstructionSessionPayload.ID, EndConstructionSessionPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPatrolRoutesPayload.ID, SyncPatrolRoutesPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(SyncCustomBlueprintsPayload.ID, SyncCustomBlueprintsPayload.PACKET_CODEC);
	}

	/**
	 * Registers server-side packet receivers for scepter updates and minion directives.
	 */
	public static void registerServerReceivers() {
		ExampleMod.LOGGER.info("Registering server network packet receivers for {}", ExampleMod.MOD_ID);
		ServerPlayNetworking.registerGlobalReceiver(AnchorConstructionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleAnchorConstruction(player, payload));
		});
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
		ServerPlayNetworking.registerGlobalReceiver(MassRolePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleMassRole(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(RetreatPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleRetreat(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(ModifyPatrolRoutePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleModifyPatrolRoute(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(ConfigurePatrolRoutePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleConfigurePatrolRoute(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(CreateCustomBlueprintPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleCreateCustomBlueprint(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(CaptureSpatialBlueprintPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleCaptureSpatialBlueprint(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(DeleteCustomBlueprintPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleDeleteCustomBlueprint(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(StartMiningAreaPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> handleStartMiningArea(player, payload));
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

		// 2. Apply updated CommandMode, active blueprint, target squad, rotation, and active patrol route channel
		CommandMode mode = payload.mode();
		String blueprintId = payload.blueprintId();
		SquadGroup targetSquad = payload.targetSquad();
		int rotation = payload.rotation();
		int activePatrolRoute = payload.activePatrolRoute();

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

		// Apply active patrol route channel if within valid bounds [0, PatrolRoute.CHANNEL_COUNT - 1]
		if (activePatrolRoute >= 0 && activePatrolRoute < com.example.patrol.PatrolRoute.CHANNEL_COUNT) {
			CommandScepterItem.setActivePatrolRoute(scepterStack, activePatrolRoute);
		}

		// Apply target role archetype if provided in payload
		if (payload.targetRole() != null) {
			CommandScepterItem.setTargetRole(scepterStack, payload.targetRole().orElse(null));
		}


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
		MinionRole targetRole = CommandScepterItem.getTargetRole(scepterStack);
		String roleSuffix = targetRole != null ? " §7| " + targetRole.getFormattedName() : "";
		player.sendMessage(
			Text.literal("§6✦ Scepter Updated: " + currentMode.getFormattedName() + " §7| §b" + bpName + " §7(" + currentRotDeg + "°) §7| " + currentSquad.getFormattedName() + roleSuffix),
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

		if (payload.minionId() == DismissMinionPayload.TARGET_SELECTED) {
			// Dismiss only selected owned minions within command radius (64 blocks)
			Box searchBox = player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS);
			List<MinionEntity> selectedMinions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected()
			);

			for (MinionEntity minion : selectedMinions) {
				minion.dismiss();
				dismissedCount++;
			}

			if (player.currentScreenHandler instanceof MinionScreenHandler) {
				player.closeHandledScreen();
			}

			if (dismissedCount > 0) {
				player.sendMessage(
					Text.literal("§c✦ Dismissed " + dismissedCount + " selected minion(s).§r"),
					true
				);
			} else {
				player.sendMessage(
					Text.literal("§e⚠ No selected minions found nearby to dismiss.§r"),
					true
				);
			}
		} else if (payload.dismissAll() || payload.minionId() < 0) {
			// Dismiss all owned minions within command radius (64 blocks)
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
			// Teleport only selected owned minions within command radius (64 blocks)
			Box searchBox = player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS);
			List<MinionEntity> nearbyMinions = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && m.isSelected()
			);

			for (MinionEntity minion : nearbyMinions) {
				if (minion.teleportToPlayer(player)) {
					minion.setSitting(false);
					minion.setGuardAnchorPos(null);
					minion.setSelected(true);
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
					Text.translatable("message.modid-mmcli-agent-modding.no_selected_minions_to_teleport"),
					true
				);
			}
		} else {
			// Teleport single minion by entity ID
			Entity entity = world.getEntityById(payload.minionId());
			if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
				if (minion.teleportToPlayer(player)) {
					minion.setSitting(false);
					minion.setGuardAnchorPos(null);
					minion.setSelected(true);
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

	/**
	 * Handles mass role archetype assignments dispatched from the Command Hub GUI.
	 * Updates the archetype role for all selected minions or all minions in the specified squad.
	 *
	 * @param player  The commanding server player.
	 * @param payload The mass role payload containing the target role and squad channel.
	 */
	private static void handleMassRole(ServerPlayerEntity player, MassRolePayload payload) {
		if (player == null || payload == null || payload.role() == null) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		SquadGroup targetSquad = payload.squad() != null ? payload.squad() : SquadGroup.ALL;
		Box searchBox = player.getBoundingBox().expand(CommandScepterItem.MINION_COMMAND_RADIUS);

		// 1. Look for actively selected minions matching the squad filter
		List<MinionEntity> targets = world.getEntitiesByClass(
			MinionEntity.class,
			searchBox,
			m -> m.isAlive() && m.isOwner(player) && m.isSelected() && targetSquad.matches(m.getSquad())
		);

		// 2. If none selected, fallback to all owned minions matching the squad filter
		if (targets.isEmpty()) {
			targets = world.getEntitiesByClass(
				MinionEntity.class,
				searchBox,
				m -> m.isAlive() && m.isOwner(player) && targetSquad.matches(m.getSquad())
			);
		}

		if (targets.isEmpty()) {
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8F, 1.2F);
			player.sendMessage(Text.literal("§e✦ No minions found matching squad " + targetSquad.getFormattedName() + "!§r"), true);
			return;
		}

		MinionRole newRole = payload.role();
		for (MinionEntity minion : targets) {
			minion.setRole(newRole);
			minion.autoEquipFromInventory();
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, minion.getX(), minion.getY() + 1.0D, minion.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
		}

		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.3F);
		player.sendMessage(
			Text.literal("§6✦ Mass Assignment: Assigned §l" + newRole.getColorCode() + newRole.getDisplayName() + " §6to " + targets.size() + " minion(s)!§r"),
			true
		);
	}

	/**
	 * Handles tactical retreat/regroup dispatched when player presses Keybind 'R'.
	 *
	 * @param player  The commanding server player.
	 * @param payload The retreat payload containing target squad channel.
	 */
	private static void handleRetreat(ServerPlayerEntity player, RetreatPayload payload) {
		if (player == null || payload == null) {
			return;
		}
		CommandScepterItem.executeRetreat(player, player.getServerWorld(), payload.targetSquad(), payload.isEmergencyCitadelCall());
	}

	/**
	 * Handles long-range or tactical build anchor construction and dismantle directives dispatched from the client.
	 *
	 * @param player  The commanding server player.
	 * @param payload The anchor construction payload.
	 */
	private static void handleAnchorConstruction(ServerPlayerEntity player, AnchorConstructionPayload payload) {
		if (player == null || payload == null) {
			return;
		}

		ItemStack scepterStack = CommandScepterItem.getHeldScepter(player);
		if (scepterStack.isEmpty()) {
			return;
		}

		if (player.getItemCooldownManager().isCoolingDown(scepterStack.getItem())) {
			return;
		}

		// Security: verify within reasonable tactical command range (128 blocks)
		net.minecraft.util.math.BlockPos clickedPos = payload.clickedPos();
		if (player.squaredDistanceTo(clickedPos.toCenterPos()) > 128.0D * 128.0D) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		CommandMode mode = CommandScepterItem.getMode(scepterStack);
		if (mode == CommandMode.BUILD) {
			CommandScepterItem.executeBuildPlacement(world, player, scepterStack, clickedPos, payload.side(), payload.isDismantle());
		} else if (mode == CommandMode.MINE) {
			CommandScepterItem.executeMinePlacement(world, player, scepterStack, clickedPos);
		}
	}

	/**
	 * Handles C2S customizable patrol route configuration (saving name/hex color or deleting).
	 */
	private static void handleConfigurePatrolRoute(ServerPlayerEntity player, ConfigurePatrolRoutePayload payload) {
		if (player == null || payload == null) return;
		ServerWorld world = player.getServerWorld();

		if (payload.action() == ConfigurePatrolRoutePayload.Action.DELETE) {
			boolean deleted = com.example.patrol.PatrolRouteManager.getInstance().deleteRoute(
				player.getUuid(), payload.routeId(), world, player
			);
			if (deleted) {
				player.sendMessage(Text.literal("§c✦ Deleted Patrol Route: §f" + payload.name() + "§r"), true);
			}
		} else {
			com.example.patrol.PatrolRoute existing = com.example.patrol.PatrolRouteManager.getInstance().getRoute(player.getUuid(), payload.routeId());
			List<BlockPos> waypoints = existing != null ? existing.waypoints() : new ArrayList<>();
			com.example.patrol.PatrolRoute updated = new com.example.patrol.PatrolRoute(
				payload.routeId(),
				payload.name(),
				payload.colorRgb(),
				waypoints,
				payload.patrolMode() != null ? payload.patrolMode() : (existing != null ? existing.patrolMode() : com.example.patrol.PatrolRoute.PatrolMode.LOOP)
			);
			com.example.patrol.PatrolRouteManager.getInstance().setRoute(player.getUuid(), updated);
			com.example.patrol.PatrolRouteManager.getInstance().syncToPlayer(player);
			player.sendMessage(Text.literal("§6✦ Configured Patrol Route: §f" + updated.name() + " §8[" + com.example.patrol.PatrolRoute.toHexCode(updated.colorRgb()) + "]§r"), true);
		}
	}

	/**
	 * Handles C2S patrol route modifications and minion escort/patrol assignments.
	 */
	private static void handleModifyPatrolRoute(ServerPlayerEntity player, ModifyPatrolRoutePayload payload) {
		if (player == null || payload == null) return;
		ServerWorld world = player.getServerWorld();

		switch (payload.action()) {
			case ADD_WAYPOINT -> {
				com.example.patrol.PatrolRoute updated = com.example.patrol.PatrolRouteManager.getInstance().addWaypoint(
					player.getUuid(), payload.routeId(), payload.pos()
				);
				com.example.patrol.PatrolRouteManager.getInstance().syncToPlayer(player);
				player.sendMessage(Text.literal("§6✦ Added Waypoint §e#" + updated.waypoints().size() + " §6to " + updated.getFormattedName()), true);
			}
			case REMOVE_WAYPOINT -> {
				com.example.patrol.PatrolRoute updated = com.example.patrol.PatrolRouteManager.getInstance().removeWaypoint(
					player.getUuid(), payload.routeId(), payload.pos()
				);
				com.example.patrol.PatrolRouteManager.getInstance().syncToPlayer(player);
				if (updated.waypoints().isEmpty()) {
					List<MinionEntity> routeMinions = world.getEntitiesByClass(
						MinionEntity.class,
						player.getBoundingBox().expand(256.0D),
						m -> m.isAlive() && m.isOwner(player) && m.getPatrolRouteId() == payload.routeId()
					);
					for (MinionEntity m : routeMinions) {
						m.setPatrolRouteId(-1);
						m.getNavigation().stop();
					}
				}
				player.sendMessage(Text.literal("§c✦ Removed Waypoint from " + updated.getFormattedName()), true);
			}
			case CLEAR_ROUTE -> {
				com.example.patrol.PatrolRoute updated = com.example.patrol.PatrolRouteManager.getInstance().clearRoute(
					player.getUuid(), payload.routeId()
				);
				com.example.patrol.PatrolRouteManager.getInstance().syncToPlayer(player);
				List<MinionEntity> routeMinions = world.getEntitiesByClass(
					MinionEntity.class,
					player.getBoundingBox().expand(256.0D),
					m -> m.isAlive() && m.isOwner(player) && m.getPatrolRouteId() == payload.routeId()
				);
				for (MinionEntity m : routeMinions) {
					m.setPatrolRouteId(-1);
					m.getNavigation().stop();
				}
				player.sendMessage(Text.literal("§c✦ Cleared all waypoints for " + updated.getFormattedName()), true);
			}
			case ASSIGN_MINION -> {
				Entity entity = world.getEntityById(payload.minionId());
				if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
					if (minion.getPatrolRouteId() == payload.routeId()) {
						minion.setPatrolRouteId(-1);
						minion.setSelected(false);
						minion.getNavigation().stop();
						player.sendMessage(Text.literal("§e✦ " + minion.getRole().getDisplayName() + " removed from patrol duty."), true);
					} else {
						com.example.patrol.PatrolRoute route = com.example.patrol.PatrolRouteManager.getInstance().getRoute(player.getUuid(), payload.routeId());
						if (route == null || route.waypoints().isEmpty()) {
							player.sendMessage(Text.literal("§c⚠ Cannot assign to " + (route != null ? route.getFormattedName() : "Route") + " — no waypoints set! Place waypoints first."), true);
							return;
						}
						minion.setPatrolRouteId(payload.routeId());
						minion.setCurrentWaypointIndex(0);
						minion.clearLeader();
						minion.setSitting(false);
						minion.setGuardAnchorPos(null);
						minion.setSelected(false);
						minion.getNavigation().stop();
						player.sendMessage(Text.literal("§a✦ Assigned " + minion.getRole().getDisplayName() + " to " + route.getFormattedName() + " (" + route.waypoints().size() + " waypoints)!"), true);
					}
				}
			}
			case SET_ESCORT -> {
				Entity followerEntity = world.getEntityById(payload.minionId());
				Entity leaderEntity = world.getEntityById(payload.targetMinionId());
				if (followerEntity instanceof MinionEntity follower && follower.isOwner(player)
					&& leaderEntity instanceof MinionEntity leader && leader.isOwner(player)) {
					if (follower.equals(leader) || follower.getUuid().equals(leader.getUuid())) {
						return; // Minion cannot escort itself
					}
					// Break reciprocal circular escort loop if leader was escorting follower
					if (leader.getLeaderMinionUuid() != null && leader.getLeaderMinionUuid().equals(follower.getUuid())) {
						leader.clearLeader();
					}
					follower.setLeaderMinionUuid(leader.getUuid());
					follower.setSelected(false);
					follower.setPatrolRouteId(-1); // Follows leader's patrol or movements
					follower.setSitting(false);
					follower.setGuardAnchorPos(null);
					follower.getNavigation().stop();
					world.spawnParticles(
						ParticleTypes.HAPPY_VILLAGER,
						follower.getX(), follower.getY() + 1.2D, follower.getZ(),
						10, 0.3D, 0.3D, 0.3D, 0.1D
					);
					world.playSound(
						null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.8F, 1.4F
					);
					world.playSound(
						null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.8F, 1.2F
					);
					world.playSound(
						null, follower.getX(), follower.getY(), follower.getZ(),
						SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.8F, 1.4F
					);
					player.sendMessage(Text.literal("§a✦ " + follower.getRole().getDisplayName() + " is now escorting " + leader.getRole().getDisplayName() + "! (Deselected from you)§r"), true);
				}
			}
			case CLEAR_ESCORT -> {
				Entity followerEntity = world.getEntityById(payload.minionId());
				if (followerEntity instanceof MinionEntity follower && follower.isOwner(player)) {
					follower.clearLeader();
					follower.setSelected(true);
					player.sendMessage(Text.literal("§e✦ Escort cleared; minion follows master."), true);
				}
			}
			case TOGGLE_PATROL_MODE -> {
				com.example.patrol.PatrolRoute updated = com.example.patrol.PatrolRouteManager.getInstance().togglePatrolMode(
					player.getUuid(), payload.routeId()
				);
				com.example.patrol.PatrolRouteManager.getInstance().syncToPlayer(player);
				player.sendMessage(Text.literal("§6✦ Set " + updated.getFormattedName() + " to §b" + updated.patrolMode().getFormattedLabel()), true);
			}
		}
	}

	/**
	 * Handles C2S creation and saving of custom player-designed voxel blueprints.
	 *
	 * @param player  The commanding server player.
	 * @param payload The blueprint creation payload.
	 */
	private static void handleCreateCustomBlueprint(ServerPlayerEntity player, CreateCustomBlueprintPayload payload) {
		if (player == null || payload == null) return;
		if (payload.id() == null || payload.id().isBlank() || payload.name() == null || payload.name().isBlank()) {
			player.sendMessage(Text.literal("§c⚠ Invalid blueprint metadata: ID and Name are required."), true);
			return;
		}

		com.example.blueprint.StructureBlueprint blueprint = payload.toStructureBlueprint();
		com.example.blueprint.CustomBlueprintManager.getInstance().saveBlueprint(player.getServer(), blueprint);

		worldSoundAndMessage(player, blueprint);
	}

	private static void worldSoundAndMessage(ServerPlayerEntity player, com.example.blueprint.StructureBlueprint blueprint) {
		player.getServerWorld().playSound(
			null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 1.3F
		);
		player.getServerWorld().playSound(
			null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, 1.1F
		);
		if (blueprint.getBlockCount() == 0) {
			player.sendMessage(
				Text.literal("§e⚠ Saved Custom Blueprint: §b" + blueprint.getName() + " §e(0 structure blocks captured - region was all air)§r"),
				true
			);
		} else {
			player.sendMessage(
				Text.literal("§6✦ Saved Custom Blueprint: §b" + blueprint.getName() + " §7(" + blueprint.getBlockCount() + " blocks)§r"),
				true
			);
		}
	}

	/**
	 * Handles C2S spatial area capture requests in DESIGN mode.
	 * Samples blocks within the bounding volume [pos1, pos2], normalizes coordinates
	 * relative to the base origin, compiles a StructureBlueprint, and saves to CustomBlueprintManager.
	 *
	 * @param player  The commanding server player.
	 * @param payload The spatial capture payload containing corner positions and metadata.
	 */
	private static void handleCaptureSpatialBlueprint(ServerPlayerEntity player, CaptureSpatialBlueprintPayload payload) {
		if (player == null || payload == null || payload.pos1() == null || payload.pos2() == null) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		net.minecraft.util.math.BlockPos pos1 = payload.pos1();
		net.minecraft.util.math.BlockPos pos2 = payload.pos2();

		// Security: verify player is reasonably close to the captured bounds (within 128 blocks)
		double distSq1 = player.squaredDistanceTo(pos1.toCenterPos());
		double distSq2 = player.squaredDistanceTo(pos2.toCenterPos());
		if (distSq1 > 128.0D * 128.0D && distSq2 > 128.0D * 128.0D) {
			player.sendMessage(Text.literal("§c⚠ Cannot capture structure: Target region is too far away!§r"), true);
			return;
		}

		try {
			com.example.blueprint.StructureBlueprint blueprint = com.example.blueprint.CustomBlueprintManager.getInstance()
				.captureAndSaveBlueprint(
					player.getServer(),
					world,
					player,
					pos1,
					pos2,
					payload.id(),
					payload.name(),
					payload.description()
				);

			worldSoundAndMessage(player, blueprint);
		} catch (Exception e) {
			player.sendMessage(Text.literal("§c⚠ Spatial capture failed: " + e.getMessage()), true);
		}
	}

	/**
	 * Handles C2S deletion of custom blueprints.
	 *
	 * @param player  The commanding server player.
	 * @param payload The blueprint deletion payload.
	 */
	private static void handleDeleteCustomBlueprint(ServerPlayerEntity player, DeleteCustomBlueprintPayload payload) {
		if (player == null || payload == null || payload.blueprintId() == null) return;

		boolean deleted = com.example.blueprint.CustomBlueprintManager.getInstance().deleteBlueprint(player.getServer(), payload.blueprintId());
		if (deleted) {
			player.getServerWorld().playSound(
				null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0F, 0.8F
			);
			player.sendMessage(
				Text.literal("§c✦ Deleted Custom Blueprint: §f" + payload.blueprintId() + "§r"),
				true
			);
		} else {
			player.sendMessage(
				Text.literal("§e⚠ Custom Blueprint not found: §f" + payload.blueprintId() + "§r"),
				true
			);
		}
	}

	/**
	 * Handles C2S start custom mining area requests.
	 * Validates coordinates, ensures bounding limits are respected, and initiates an area dismantle session.
	 *
	 * @param player  The commanding server player.
	 * @param payload The start mining area payload containing pos1 and pos2.
	 */
	private static void handleStartMiningArea(ServerPlayerEntity player, StartMiningAreaPayload payload) {
		if (player == null || payload == null || payload.pos1() == null || payload.pos2() == null) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		net.minecraft.util.math.BlockPos pos1 = payload.pos1();
		net.minecraft.util.math.BlockPos pos2 = payload.pos2();

		// Security: verify player is reasonably close to the target bounds (within 128 blocks)
		double distSq1 = player.squaredDistanceTo(pos1.toCenterPos());
		double distSq2 = player.squaredDistanceTo(pos2.toCenterPos());
		if (distSq1 > 128.0D * 128.0D && distSq2 > 128.0D * 128.0D) {
			player.sendMessage(Text.literal("§c⚠ Cannot start mining: Target area is too far away!§r"), true);
			return;
		}

		int minX = Math.min(pos1.getX(), pos2.getX());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;

		if (sizeX > com.example.blueprint.StructureBlueprint.MAX_SPATIAL_DIMENSION ||
			sizeY > com.example.blueprint.StructureBlueprint.MAX_SPATIAL_HEIGHT ||
			sizeZ > com.example.blueprint.StructureBlueprint.MAX_SPATIAL_DIMENSION) {
			player.sendMessage(Text.literal("§c⚠ Mining area exceeds maximum dimensions of " +
				com.example.blueprint.StructureBlueprint.MAX_SPATIAL_DIMENSION + "x" +
				com.example.blueprint.StructureBlueprint.MAX_SPATIAL_HEIGHT + "x" +
				com.example.blueprint.StructureBlueprint.MAX_SPATIAL_DIMENSION + "§r"), true);
			return;
		}

		long volume = (long) sizeX * sizeY * sizeZ;
		if (volume > com.example.blueprint.StructureBlueprint.MAX_SPATIAL_VOLUME) {
			player.sendMessage(Text.literal("§c⚠ Mining area volume exceeds maximum limit of " +
				com.example.blueprint.StructureBlueprint.MAX_SPATIAL_VOLUME + " blocks!§r"), true);
			return;
		}

		try {
			com.example.construction.ConstructionSession session = com.example.construction.ConstructionManager.getInstance()
				.startAreaDismantleSession(world, pos1, pos2, player);
			if (session != null && session.isActive()) {
				world.playSound(
					null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1.0F, 1.0F
				);
			}
		} catch (Exception e) {
			player.sendMessage(Text.literal("§c⚠ Failed to start area mining: " + e.getMessage()), true);
		}
	}
}
