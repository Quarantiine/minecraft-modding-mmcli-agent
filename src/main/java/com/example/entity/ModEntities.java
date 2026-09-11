package com.example.entity;

import com.example.ExampleMod;
import com.example.entity.custom.MinionEntity;
import com.example.entity.custom.TntProjectileEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
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
	 * Minion Entity: Autonomous worker and combat thrall bound to player command.
	 * Registered with player-scale dimensions (0.6F width, 1.95F height) and eye-height (1.74F)
	 * ensuring accurate overhead badge positioning and eye-level line of sight.
	 */
	public static final EntityType<MinionEntity> MINION = Registry.register(
		Registries.ENTITY_TYPE,
		Identifier.of(ExampleMod.MOD_ID, "minion"),
		EntityType.Builder.<MinionEntity>create(MinionEntity::new, SpawnGroup.CREATURE)
			.dimensions(0.6F, 1.95F)
			.eyeHeight(1.74F)
			.maxTrackingRange(8)
			.trackingTickInterval(3)
			.build()
	);

	/**
	 * Static initializer method invoked during mod initialization
	 * to ensure static entity fields are loaded, registered, and attributes bound.
	 */
	public static void registerModEntities() {
		ExampleMod.LOGGER.info("Registering Mod Entities for {}", ExampleMod.MOD_ID);

		// Register default attributes for MinionEntity
		FabricDefaultAttributeRegistry.register(MINION, MinionEntity.createMinionAttributes());
	}
}
