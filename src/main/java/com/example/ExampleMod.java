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

		// Register server tick event to update construction sessions and holograms
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			ConstructionManager.getInstance().tick(world);
		});

		// Register sneak + left-click attack block callback:
		// In BUILD mode: cycles rotation
		// In other modes: deselects all minions
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!player.isSneaking()) {
				return ActionResult.PASS;
			}
			ItemStack stack = CommandScepterItem.getHeldScepter(player);
			if (!stack.isEmpty()) {
				CommandMode mode = CommandScepterItem.getMode(stack);
				if (mode == CommandMode.BUILD) {
					if (!world.isClient()) {
						CommandScepterItem.cycleRotation(stack, player);
					}
					return ActionResult.SUCCESS;
				} else {
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
		// 2. Handles sneak + left-click deselecting / rotation cycling when clicking entities.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			ItemStack stack = CommandScepterItem.getHeldScepter(player);
			if (!stack.isEmpty()) {
				if (player.isSneaking()) {
					CommandMode mode = CommandScepterItem.getMode(stack);
					if (mode == CommandMode.BUILD) {
						if (!world.isClient()) {
							CommandScepterItem.cycleRotation(stack, player);
						}
						return ActionResult.SUCCESS;
					} else {
						if (!world.isClient()) {
							CommandScepterItem.deselectAllMinions(player, world);
						}
						return ActionResult.SUCCESS;
					}
				}

				// Friendly-fire prevention: left-clicking an owned minion toggles its selection
				if (entity instanceof MinionEntity minion && minion.isOwner(player)) {
					if (!world.isClient()) {
						CommandScepterItem.toggleMinionSelection(player, minion);
					}
					return ActionResult.SUCCESS;
				}
			}
			return ActionResult.PASS;
		});
	}
}

