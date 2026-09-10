package com.example.client;

import com.example.client.renderer.TntProjectileRenderer;
import com.example.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-specific entrypoint for the mod, loaded only on the physical Minecraft client.
 * Registers client-side entity renderers and visual handlers.
 */
public class ExampleModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("modid-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Fabric 1.21 Example Mod Client");

		// Register the custom projectile renderer so the projectile renders as a spinning 3D TNT item in flight
		EntityRendererRegistry.register(ModEntities.TNT_PROJECTILE, TntProjectileRenderer::new);
	}
}
