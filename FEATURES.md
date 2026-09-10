# Fabric 1.21 Feature Showcase & Technical Specifications

This document provides a comprehensive breakdown of all features, items, entities, block registration architecture, client rendering, mixins, and resource assets implemented in the mod (`modid-mmcli-agent-modding`).

---

## Table of Contents
1. [Overview & Mod Metadata](#1-overview--mod-metadata)
2. [Custom Items: TNT Stick](#2-custom-items-tnt-stick)
3. [Custom Entities: TNT Projectile](#3-custom-entities-tnt-projectile)
4. [Client Rendering: TntProjectileRenderer](#4-client-rendering-tntprojectilerenderer)
5. [Block Registration Architecture: ModBlocks](#5-block-registration-architecture-modblocks)
6. [Bytecode Injections & Mixins: ExampleMixin](#6-bytecode-injections--mixins-examplemixin)
7. [Assets & Resource Pipeline](#7-assets--resource-pipeline)
8. [Extensibility & Developer Roadmap](#8-extensibility--developer-roadmap)

---

## 1. Overview & Mod Metadata

The mod is engineered using **The Recommended Fabric Architecture**, enforcing strict domain separation between items, entities, blocks, rendering, and lifecycle mixins across a split client/server environment.

| Property | Value | Notes |
| :--- | :--- | :--- |
| **Mod ID / Namespace** | `modid-mmcli-agent-modding` | Registered identifier across registries & assets |
| **Target Minecraft Version** | `1.21` | Compatible with vanilla 1.21 clients and servers |
| **Target Java Version** | `Java 21` (Bytecode Class Version 65.0) | Standard JVM for 1.21+ |
| **Yarn Mappings** | `1.21+build.9` | Deobfuscation symbol map |
| **Fabric Loader** | `>=0.16.0` (Configured: `0.16.10`) | Runtime classloader and mixin engine |
| **Fabric API** | `0.100.4+1.21` | Lifecycle events, registries, rendering hooks |

### Architectural Entrypoints
- **Common Entrypoint (`ExampleMod.java`)**: Implements `net.fabricmc.api.ModInitializer`. Initializes static registries:
  - `ModItems.registerModItems()`
  - `ModBlocks.registerModBlocks()`
  - `ModEntities.registerModEntities()`
- **Client Entrypoint (`ExampleModClient.java`)**: Implements `net.fabricmc.api.ClientModInitializer`. Loaded exclusively on the physical client to register renderers and client visual hooks.

---

## 2. Custom Items: TNT Stick

The **TNT Stick** is a handheld weapon that enables players to launch explosive TNT projectiles on right-click with built-in auditory feedback, cooldown controls, and gameplay statistics tracking.

```
                    [Right-Click Action]
                              │
               ┌──────────────┴──────────────┐
               ▼                             ▼
       [Client & Server]                 [Server Only]
   • Play TNT Priming Sound          • Instantiate TntProjectileEntity
   • Increment Stats.USED            • Set Velocity (Pitch, Yaw, Speed 1.5)
   • Apply 5-tick (0.25s) Cooldown   • Spawn Entity in World
```

### Technical Specifications
- **Identifier**: `modid-mmcli-agent-modding:tnt_stick`
- **Class**: `com.example.item.custom.TntStickItem`
- **Registry Holder**: `ModItems.TNT_STICK`
- **Base Class**: `net.minecraft.item.Item`
- **Creative Tab**: `ItemGroups.COMBAT` (registered via Fabric `ItemGroupEvents.modifyEntriesEvent`)
- **Rarity**: `Rarity.EPIC` (purple item name tooltip)
- **Max Stack Size**: `1` (single handheld unit)

### Gameplay Mechanics & Implementation Details
1. **Auditory Cue**: On right-click (`use`), plays `SoundEvents.ENTITY_TNT_PRIMED` in `SoundCategory.PLAYERS` at volume `1.0F` and pitch `1.0F`, alerting nearby players.
2. **Server-Authoritative Spawning**: Spawning occurs strictly on the logical server (`!world.isClient()`) to prevent ghost entities or desynchronization.
3. **Launch Vector & Velocity**:
   - Direction calculated from the player's current view pitch and yaw.
   - **Launch Speed**: `1.5F` blocks/tick.
   - **Divergence (Spread)**: `1.0F` (simulates slight projectile wobble/realism).
4. **Anti-Spam Rate Limiting**: Applies a 5-tick (`0.25` second) cooldown via `user.getItemCooldownManager().set(this, 5)` to eliminate runaway spam and server lag.
5. **Statistics Tracking**: Integrates with the vanilla statistics tracker via `user.incrementStat(Stats.USED.getOrCreateStat(this))`.

---

## 3. Custom Entities: TNT Projectile

The **TNT Projectile** is a high-velocity throwable projectile that exhibits in-flight visual effects and triggers a devastating 4.0F explosion immediately upon contact with blocks or living entities.

```
                  [Flight Phase]                             [Impact Phase]
  ┌──────────────────────────────────────────┐      ┌───────────────────────────────┐
  │ • Smoke trail (100% tick rate)           │ ───► │ • Create 4.0F TNT Explosion   │
  │ • Flame sparks (60% random probability)  │      │ • Destroy Blocks (Source=TNT) │
  │ • Spinning 3D TNT block item model       │      │ • Discard entity immediately  │
  └──────────────────────────────────────────┘      └───────────────────────────────┘
```

### Technical Specifications
- **Identifier**: `modid-mmcli-agent-modding:tnt_projectile`
- **Class**: `com.example.entity.custom.TntProjectileEntity`
- **Registry Holder**: `ModEntities.TNT_PROJECTILE`
- **Base Class**: `net.minecraft.entity.projectile.thrown.ThrownItemEntity`
- **Hitbox Dimensions**: `0.25` width x `0.25` height
- **Spawn Group**: `SpawnGroup.MISC`
- **Tracking Parameters**:
  - `maxTrackingRange`: `4` chunks
  - `trackingTickInterval`: `10` ticks
- **Default Render Item**: `Items.TNT`

### Particle & In-Flight Dynamics
During each tick (`tick()`) on the client side (`this.getWorld().isClient()`):
- **Smoke Trail**: Spawns `ParticleTypes.SMOKE` at `(x, y + 0.1, z)` at zero velocity every single tick.
- **Flame Sparks**: Spawns `ParticleTypes.FLAME` at `(x, y + 0.1, z)` with a 60% probability (`random.nextFloat() < 0.6f`).

### Collision & Detonation Mechanics
In `onCollision(HitResult hitResult)`:
1. Validates execution on the server (`!this.getWorld().isClient()`).
2. Creates an explosion via `world.createExplosion`:
   - **Explosive Power**: `4.0F` (standard vanilla TNT strength).
   - **Explosion Source Type**: `World.ExplosionSourceType.TNT` (respects `mobGriefing`, water dampening, and blast resistance).
3. Immediately calls `this.discard()` to remove the projectile from the world and prevent multi-collision loops.

---

## 4. Client Rendering: TntProjectileRenderer

The **TntProjectileRenderer** handles the visual appearance of the projectile in client space, ensuring seamless interpolation and 3D item rendering.

### Technical Specifications
- **Class**: `com.example.client.renderer.TntProjectileRenderer`
- **Base Class**: `net.minecraft.client.render.entity.FlyingItemEntityRenderer<TntProjectileEntity>`
- **Registration**: Registered in `ExampleModClient` via Fabric API's `EntityRendererRegistry.register(ModEntities.TNT_PROJECTILE, TntProjectileRenderer::new)`.

### Features & Capabilities
- Inherits spinning physics and pitch/yaw rotation matrices from vanilla's `FlyingItemEntityRenderer`.
- Renders the item stack assigned to the projectile (defaults to `Items.TNT`).
- Provides overloaded constructors allowing custom scale factors (`float scale`) and full-bright rendering (`boolean lit`) for future projectile variants.

---

## 5. Block Registration Architecture: ModBlocks

The **ModBlocks** module provides a centralized, reusable architecture for registering custom blocks and their corresponding `BlockItem` instances into the vanilla registry.

### Technical Specifications
- **Class**: `com.example.block.ModBlocks`
- **Registry Reference**: `Registries.BLOCK` and `Registries.ITEM`

### Architectural Pattern
```java
// Central helper pattern implemented in ModBlocks.java
private static Block registerBlock(String name, Block block) {
    registerBlockItem(name, block);
    return Registry.register(Registries.BLOCK, Identifier.of(ExampleMod.MOD_ID, name), block);
}

private static Item registerBlockItem(String name, Block block) {
    return Registry.register(
        Registries.ITEM,
        Identifier.of(ExampleMod.MOD_ID, name),
        new BlockItem(block, new Item.Settings())
    );
}
```

### Benefits
- **Automated BlockItem Binding**: Prevents developer oversight where blocks are registered without inventory items.
- **Namespace Safety**: Automatically applies `ExampleMod.MOD_ID` namespace to all paths.
- **Lifecycle Integration**: Called directly during `ExampleMod.onInitialize()` prior to world loading.

---

## 6. Bytecode Injections & Mixins: ExampleMixin

The mod includes SpongePowered Mixin integration for runtime bytecode manipulation.

### Technical Specifications
- **Mixin Config**: `src/main/resources/modid.mixins.json`
- **Class**: `com.example.mixin.ExampleMixin`
- **Target Class**: `net.minecraft.server.MinecraftServer`
- **Injected Method**: `loadWorld`
- **Injection Point**: `@At("HEAD")`
- **Compatibility Level**: `JAVA_21`
- **Injector Constraint**: `"defaultRequire": 1`

### Architectural Purpose
Provides a lifecycle injection hook into Minecraft's dedicated and integrated server startup routine, suitable for custom world-data initialization, server configuration validation, and server-side mod telemetry.

---

## 7. Assets & Resource Pipeline

All client-facing assets adhere to the vanilla 1.21 resource pack specifications under the mod namespace `modid-mmcli-agent-modding`.

```
src/main/resources/assets/modid-mmcli-agent-modding/
├── lang/
│   └── en_us.json                # English (US) localization keys
├── models/
│   └── item/
│       └── tnt_stick.json        # Handheld item model definition
└── textures/
    └── item/                     # Texture storage directory
```

### Localization (`en_us.json`)
```json
{
  "item.modid-mmcli-agent-modding.tnt_stick": "TNT Stick",
  "entity.modid-mmcli-agent-modding.tnt_projectile": "TNT Projectile"
}
```

### Item Model (`models/item/tnt_stick.json`)
Configured with parent `"minecraft:item/handheld"` to ensure the TNT Stick renders upright in the player's hand with standard first-person and third-person transformation matrices.

---

## 8. Extensibility & Developer Roadmap

The current architecture provides a clean template for extending gameplay features:

| Extension Target | Implementation Strategy |
| :--- | :--- |
| **New Explosive Items** | Subclass `Item`, register in `ModItems.java`, and set custom projectile velocities or cooldowns. |
| **Custom Blocks** | Add static fields to `ModBlocks.java` using `registerBlock("custom_block", new Block(...))`. |
| **Cluster / Napalm Projectiles** | Subclass `ThrownItemEntity`, configure particle trails in `tick()`, and spawn sub-projectiles in `onCollision()`. |
| **Custom Sound Effects** | Define custom `SoundEvent` in a `ModSounds` registry and trigger via `world.playSound()`. |
| **Client-Server Packets** | Implement Fabric Networking API (`ServerPlayNetworking`, `ClientPlayNetworking`) for custom sync payloads. |
