package com.example;

import com.example.block.ModBlocks;
import com.example.component.CommandMode;
import com.example.component.ModDataComponents;
import com.example.construction.ConstructionManager;
import com.example.construction.TraversalScaffoldingManager;
import com.example.entity.ModEntities;
import com.example.item.ModItems;
import com.example.item.custom.CommandScepterItem;
import com.example.network.ModNetworking;
import com.example.screen.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
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

		// Register server tick event to update construction sessions, holograms, and traversal scaffolding decay
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			ConstructionManager.getInstance().tick(world);
			TraversalScaffoldingManager.getInstance().tick(world);
		});

		// Register sneak + left-click attack block callback for cycling blueprints in BUILD mode
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (hand != Hand.MAIN_HAND || !player.isSneaking()) {
				return ActionResult.PASS;
			}
			ItemStack stack = player.getStackInHand(hand);
			if (stack.isOf(ModItems.COMMAND_SCEPTER)) {
				CommandMode mode = CommandScepterItem.getMode(stack);
				if (mode == CommandMode.BUILD) {
					if (!world.isClient()) {
						CommandScepterItem.cycleBlueprint(stack, player, world);
					}
					return ActionResult.SUCCESS;
				}
			}
			return ActionResult.PASS;
		});
	}
}

