package com.example.entity;

import com.example.ExampleMod;
import com.example.entity.custom.TntProjectileEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registry class for all custom entities introduced by this mod.
 * Follows the recommended Fabric architecture for centralized entity registration.
 */
public class ModEntities {

	public static final EntityType<TntProjectileEntity> TNT_PROJECTILE = Registry.register(
		Registries.ENTITY_TYPE,
		Identifier.of(ExampleMod.MOD_ID, "tnt_projectile"),
		EntityType.Builder.<TntProjectileEntity>create(TntProjectileEntity::new, SpawnGroup.MISC)
			.dimensions(0.25F, 0.25F)
			.maxTrackingRange(4)
			.trackingTickInterval(10)
			.build()
	);

	/**
	 * Static initializer method invoked during mod initialization
	 * to ensure static entity fields are loaded and registered with Minecraft.
	 */
	public static void registerModEntities() {
		ExampleMod.LOGGER.info("Registering Mod Entities for {}", ExampleMod.MOD_ID);
	}
}
