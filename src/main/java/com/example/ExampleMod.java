package com.example;

import com.example.block.ModBlocks;
import com.example.component.CommandMode;
import com.example.component.ModDataComponents;
import com.example.construction.ConstructionManager;
import com.example.entity.ModEntities;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import com.example.network.ModNetworking;
import com.example.screen.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import com.example.entity.custom.MinionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for the mod, loaded on both client and dedicated server
 * environments. Handles core entity, item, and block registration lifecycle.
 */
public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid-mmcli-agent-modding";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Fabric 1.21 Example Mod: {}", MOD_ID);

		ModDataComponents.registerDataComponents();
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModEntities.registerModEntities();
		ModScreenHandlers.registerScreenHandlers();
		ModNetworking.registerC2SPayloads();
		ModNetworking.registerServerReceivers();

		// Register server world load event to initialize persistent patrol routes and custom blueprints
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server, world) -> {
			if (world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) {
				LOGGER.info("Initializing PatrolRouteManager and CustomBlueprintManager persistent state for overworld: {}", world.getRegistryKey().getValue());
				com.example.patrol.PatrolRouteManager.getInstance().init(world);
				com.example.blueprint.CustomBlueprintManager.getInstance().init(world);
			}
		});

		// Register server started event as a guaranteed overworld init point
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			net.minecraft.server.world.ServerWorld overworld = server.getOverworld();
			if (overworld != null) {
				LOGGER.info("Ensuring PatrolRouteManager and CustomBlueprintManager initialized on SERVER_STARTED for overworld");
				com.example.patrol.PatrolRouteManager.getInstance().init(overworld);
				com.example.blueprint.CustomBlueprintManager.getInstance().init(overworld);
			}
		});

		// Register server stopping event to finalize and persist route and custom blueprint data
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			com.example.patrol.PatrolRouteManager.getInstance().onServerStopping();
			com.example.blueprint.CustomBlueprintManager.getInstance().onServerStopping();
		});

		// Register player connect event to sync saved patrol routes and custom blueprints immediately on world join
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			com.example.patrol.PatrolRouteManager.getInstance().syncToPlayer(handler.getPlayer());
			com.example.blueprint.CustomBlueprintManager.getInstance().syncToPlayer(handler.getPlayer());
		});

		// Register server tick event to update construction sessions and holograms
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			ConstructionManager.getInstance().tick(world);
		});

		// Register attack block callback for the Command Scepter:
		// In BUILD mode: cycles blueprint rotation
		// In DESIGN mode: steps corner coordinates (Pos1 -> Pos2 -> restart)
		// In PATHWAY mode: left-click (punch) an existing waypoint block removes it
		// In other modes: sneak + left-click deselects all minions
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			ItemStack stack = CommandScepterItem.getHeldScepter(player);
			if (!stack.isEmpty()) {
				CommandMode mode = CommandScepterItem.getMode(stack);
				if (mode == CommandMode.BUILD) {
					if (!world.isClient()) {
						CommandScepterItem.cycleRotation(stack, player);
					}
					return ActionResult.SUCCESS;
				} else if (mode == CommandMode.DESIGN) {
					if (world.isClient()) {
						boolean sneaking = player.isSneaking();
						BlockPos effectivePos = world.getBlockState(pos).isReplaceable()
							? pos
							: ((direction != null) ? pos.offset(direction) : pos.up());
						CommandScepterItem.handleDesignClick(player, world, stack, effectivePos, sneaking);
						if (CommandScepterItem.DESIGN_CLICK_CONSUMER != null) {
							CommandScepterItem.DESIGN_CLICK_CONSUMER.run();
						}
					}
					return ActionResult.SUCCESS;
				} else if (mode == CommandMode.MINE && CommandScepterItem.getMiningMode(stack) == com.example.component.MiningMode.AREA) {
					if (world.isClient()) {
						boolean sneaking = player.isSneaking();
						BlockPos effectivePos = world.getBlockState(pos).isReplaceable()
							? pos
							: ((direction != null) ? pos.offset(direction) : pos.up());
						CommandScepterItem.handleMineClick(player, world, stack, effectivePos, sneaking);
						if (CommandScepterItem.MINE_CLICK_CONSUMER != null) {
							CommandScepterItem.MINE_CLICK_CONSUMER.run();
						}
					}
					return ActionResult.SUCCESS;
				} else if (mode == CommandMode.PATHWAY) {
					if (!world.isClient() && world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
						com.example.patrol.PatrolRouteManager.WaypointMatch match = com.example.patrol.PatrolRouteManager.getInstance().findWaypoint(
							player.getUuid(), pos, pos.offset(direction), pos.up()
						);
						if (match != null) {
							int matchedRouteId = match.routeId();
							BlockPos targetWp = match.pos();
							com.example.patrol.PatrolRoute updated = com.example.patrol.PatrolRouteManager.getInstance().removeWaypoint(player.getUuid(), matchedRouteId, targetWp);
							com.example.patrol.PatrolRouteManager.getInstance().syncToPlayer((net.minecraft.server.network.ServerPlayerEntity) player);
							if (updated.waypoints().isEmpty()) {
								java.util.List<com.example.entity.custom.MinionEntity> routeMinions = serverWorld.getEntitiesByClass(
									com.example.entity.custom.MinionEntity.class,
									player.getBoundingBox().expand(256.0D),
									m -> m.isAlive() && m.isOwner(player) && m.getPatrolRouteId() == matchedRouteId
								);
								for (com.example.entity.custom.MinionEntity m : routeMinions) {
									m.setPatrolRouteId(-1);
									m.getNavigation().stop();
								}
							}
							world.playSound(null, targetWp.getX(), targetWp.getY(), targetWp.getZ(), net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.8F);
							serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, targetWp.getX() + 0.5D, targetWp.getY() + 0.5D, targetWp.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.05D);
							player.sendMessage(net.minecraft.text.Text.literal("§c✦ Removed Waypoint from " + updated.getFormattedName()), true);
							return ActionResult.SUCCESS;
						}
					} else if (world.isClient()) {
						if (CommandScepterItem.isClientWaypoint(stack, pos) || CommandScepterItem.isClientWaypoint(stack, pos.offset(direction)) || CommandScepterItem.isClientWaypoint(stack, pos.up())) {
							return ActionResult.SUCCESS;
						}
					}
				} else if (player.isSneaking()) {
					if (!world.isClient()) {
						CommandScepterItem.deselectAllMinions(player, world);
					}
					return ActionResult.SUCCESS;
				}
			}
			return ActionResult.PASS;
		});

		// Register attack entity callback:
		// 1. Prevents damaging owned minions with the Command Scepter, toggling selection instead.
		// 2. In BUILD mode: cycles rotation when left-clicking non-owned entities.
		// 3. In other modes: handles sneak + left-click deselecting.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			ItemStack stack = CommandScepterItem.getHeldScepter(player);
			if (!stack.isEmpty()) {
				// Friendly-fire prevention: left-clicking an owned minion
				if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
					if (player.isSneaking()) {
						if (!world.isClient()) {
							int assigned = CommandScepterItem.assignSelectedMinionsToLeader(player, minion);
							if (assigned == 0) {
								if (minion.hasLeader()) {
									minion.clearLeader();
									minion.setSelected(true);
									player.sendMessage(net.minecraft.text.Text.literal("§e✦ Escort cleared; minion follows master.§r"), true);
									world.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), net.minecraft.sound.SoundCategory.PLAYERS, 1.2F, 0.8F);
								} else {
									player.sendMessage(net.minecraft.text.Text.literal("§e✦ Select minions first, then Shift + Punch a minion to designate them as Squad Leader!§r"), true);
									world.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 0.6F);
								}
							}
						}
						return ActionResult.SUCCESS;
					}

					if (!world.isClient()) {
						CommandScepterItem.toggleMinionSelection(player, minion);
					}
					return ActionResult.SUCCESS;
				}

				CommandMode mode = CommandScepterItem.getMode(stack);
				if (mode == CommandMode.BUILD) {
					if (!world.isClient()) {
						CommandScepterItem.cycleRotation(stack, player);
					}
					return ActionResult.SUCCESS;
				} else if (mode == CommandMode.DESIGN) {
					if (world.isClient()) {
						BlockPos entityPos = entity.getBlockPos();
						boolean sneaking = player.isSneaking();
						CommandScepterItem.handleDesignClick(player, world, stack, entityPos, sneaking);
						if (CommandScepterItem.DESIGN_CLICK_CONSUMER != null) {
							CommandScepterItem.DESIGN_CLICK_CONSUMER.run();
						}
					}
					return ActionResult.SUCCESS;
				} else if (mode == CommandMode.MINE && CommandScepterItem.getMiningMode(stack) == com.example.component.MiningMode.AREA) {
					if (world.isClient()) {
						BlockPos entityPos = entity.getBlockPos();
						boolean sneaking = player.isSneaking();
						CommandScepterItem.handleMineClick(player, world, stack, entityPos, sneaking);
						if (CommandScepterItem.MINE_CLICK_CONSUMER != null) {
							CommandScepterItem.MINE_CLICK_CONSUMER.run();
						}
					}
					return ActionResult.SUCCESS;
				} else if (player.isSneaking()) {
					if (!world.isClient()) {
						CommandScepterItem.deselectAllMinions(player, world);
					}
					return ActionResult.SUCCESS;
				}
			}
			return ActionResult.PASS;
		});
	}
}

