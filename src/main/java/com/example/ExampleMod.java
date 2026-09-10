package com.example;

import com.example.block.ModBlocks;
import com.example.entity.ModEntities;
import com.example.item.ModItems;
import net.fabricmc.api.ModInitializer;
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

		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModEntities.registerModEntities();
	}
}

