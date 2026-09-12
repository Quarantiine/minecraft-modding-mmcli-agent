# Fabric 1.21 Feature Showcase & Technical Specifications

This document provides a comprehensive breakdown of all features, items, entities, data components, construction systems, AI behaviors, client rendering pipelines, networking protocols, GUI screens, and resource assets implemented in the mod (`modid-mmcli-agent-modding`).

---

## Table of Contents

1. [Overview & Mod Metadata](#1-overview--mod-metadata)
2. [Loki Command Scepter (`CommandScepterItem`)](#2-loki-command-scepter-commandscepteritem)
3. [Data Components Architecture (`ModDataComponents` & `CommandMode`)](#3-data-components-architecture-moddatacomponents--commandmode)
4. [Autonomous Minion Thrall Entity (`MinionEntity`) & Spawn Egg](#4-autonomous-minion-thrall-entity-minionentity--spawn-egg)
5. [Combat Survivability & Post-Combat Return](#5-combat-survivability--post-combat-return)
6. [Minion Management GUI (`MinionScreen` & `MinionScreenHandler`)](#6-minion-management-gui-minionscreen--minionscreenhandler)
7. [Biped Model & Client Rendering Pipeline (`MinionEntityRenderer`)](#7-biped-model--client-rendering-pipeline-minionentityrenderer)
8. [Minion Dismissal & Networking Protocol (`DismissMinionPayload`)](#8-minion-dismissal--networking-protocol-dismissminionpayload)
9. [Loki Scepter Enthrallment (Transfiguration Lifecycle)](#9-loki-scepter-enthrallment-transfiguration-lifecycle)
10. [Curated Blueprint Catalog & Topological Sorting](#10-curated-blueprint-catalog--topological-sorting)
11. [Multiblock Construction Manager & Session Orchestration](#11-multiblock-construction-manager--session-orchestration)
12. [Minion Construction AI & Resource Scavenging](#12-minion-construction-ai--resource-scavenging)
13. [Custom Items: TNT Stick](#13-custom-items-tnt-stick)
14. [Custom Entities: TNT Projectile & Renderer](#14-custom-entities-tnt-projectile--renderer)
15. [Screen Handlers & Block Registration Architecture](#15-screen-handlers--block-registration-architecture)
16. [Bytecode Injections & Mixins: ExampleMixin](#16-bytecode-injections--mixins-examplemixin)
17. [Assets, Models & Localization Pipeline](#17-assets-models--localization-pipeline)
18. [Extensibility & Developer Roadmap](#18-extensibility--developer-roadmap)
19. [Tactical Army & Squad Architecture (Roles, Squads, Formations, Rally & SFX)](#19-tactical-army--squad-architecture-roles-squads-formations-rally--sfx)
20. [Testing & Verification Architecture](#20-testing--verification-architecture)
21. [Long-Range Crosshair Targeting & Combat Raycasting (`CommandScepterItem`)](#21-long-range-crosshair-targeting--combat-raycasting-commandscepteritem)
22. [Combat Sappers & Ephemeral Traversal Scaffolding (`MinionSapperGoal` & `TraversalScaffoldingManager`)](#22-combat-sappers--ephemeral-traversal-scaffolding-minionsappergoal--traversalscaffoldingmanager)
23. [Structure Deconstruction & Dismantling Mode (`SessionMode.DISMANTLE`)](#23-structure-deconstruction--dismantling-mode-sessionmodedismantle)
24. [RTS Minion Selection, Selective Ground Waypoints & Decoupled Guard Stance](#24-rts-minion-selection-selective-ground-waypoints--decoupled-guard-stance)
25. [Formation Yaw Anchoring, Rank Resolution & Two-Pass Badge Rendering](#25-formation-yaw-anchoring-rank-resolution--two-pass-badge-rendering)
26. [Architectural Scaffolding Navigation, Platform Kinematics & Multi-Minion Coordination](#26-architectural-scaffolding-navigation-platform-kinematics--multi-minion-coordination)
27. [Shift-to-Close GUI Architecture, Open-State Guard & Fast Dismissals (`CommandScepterScreen`)](#27-shift-to-close-gui-architecture-open-state-guard--fast-dismissals-commandscepterscreen)
28. [The 5 Architectural Refinements: Rotation Mathematics, Door Beacons, Ownership Persistence, Descent Kinematics & Smart Shift-Close](#28-the-5-architectural-refinements-rotation-mathematics-door-beacons-ownership-persistence-descent-kinematics--smart-shift-close)

---

## 1. Overview & Mod Metadata

The mod is engineered using **The Recommended Fabric Architecture**, enforcing strict domain separation between items, entities, data components, networking, AI goals, multiblock construction orchestration, rendering pipelines, and GUI screens across a split client/server environment.

| Property                     | Value                                   | Notes                                                     |
| :--------------------------- | :-------------------------------------- | :-------------------------------------------------------- |
| **Mod ID / Namespace**       | `modid-mmcli-agent-modding`             | Registered identifier across registries & assets          |
| **Target Minecraft Version** | `1.21`                                  | Compatible with vanilla 1.21 clients and servers          |
| **Target Java Version**      | `Java 21` (Bytecode Class Version 65.0) | Standard JVM for 1.21+                                    |
| **Yarn Mappings**            | `1.21+build.9`                          | Deobfuscation symbol map                                  |
| **Fabric Loader**            | `>=0.16.0` (Configured: `0.16.10`)      | Runtime classloader and mixin engine                      |
| **Fabric API**               | `0.100.4+1.21`                          | Lifecycle events, registries, rendering, networking hooks |

### Architectural Entrypoints

- **Common Entrypoint (`ExampleMod.java`)**: Implements `net.fabricmc.api.ModInitializer`. Initializes static registries:
  - `ModDataComponents.registerModDataComponents()`: Registers 1.21 Data Components for command modes and blueprints.
  - `ModItems.registerModItems()`: Registers TNT Stick, Minion Spawn Egg, and Loki Command Scepter.
  - `ModBlocks.registerModBlocks()`: Scaffolding for block and BlockItem registration.
  - `ModEntities.registerModEntities()`: Registers TNT Projectile and Minion entities, attributes, and goal profiles.
  - `ModScreenHandlers.registerScreenHandlers()`: Registers extended screen handlers for the minion GUI.
  - `ModNetworking.registerC2SPayloads()` & `registerServerReceivers()`: Registers C2S network packets (`UpdateScepterPayload`, `DismissMinionPayload`, `TeleportMinionPayload`).
  - Registers `ServerTickEvents.END_WORLD_TICK` for `ConstructionManager` ticking.
  - Registers `AttackBlockCallback.EVENT` for scepter sneak-left-click blueprint cycling.
- **Client Entrypoint (`ExampleModClient.java`)**: Implements `net.fabricmc.api.ClientModInitializer`. Loaded exclusively on the physical client to register renderers and client event handlers:
  - `EntityRendererRegistry.register(ModEntities.TNT_PROJECTILE, TntProjectileRenderer::new)`
  - `EntityRendererRegistry.register(ModEntities.MINION, MinionEntityRenderer::new)`: Biped player model with armor and held item feature layers.
  - `HandledScreens.register(ModScreenHandlers.MINION_SCREEN_HANDLER, MinionScreen::new)`: Client GUI screen binder for minion equipment and inventory management.
  - `ModClientNetworking.registerClientNetworking()`: Client networking dispatchers.
  - `BlueprintHologramRenderer.register()`: Translucent 3D wireframe hologram rendering.
  - Keybinding registration (`V` key) to open the Command Hub GUI.

---

## 2. Loki Command Scepter (`CommandScepterItem`)

The **Loki Command Scepter** is a high-tier tactical relic that allows players to command minion thralls, recruit mobs into servitude, and orchestrate automated multiblock construction.

```
                                         [Loki Command Scepter]
                                                   │
         ┌───────────────────┬─────────────────────┼─────────────────────┬───────────────────┐
         ▼                   ▼                     ▼                     ▼                   ▼
  [Shift + Right-Click] [BUILD Mode: Ground]  [MINE Mode: Ground]  [RECRUIT Mode: Mob]   [Tactical Broadcast]
   Open Command Hub GUI  Anchor Construction   Anchor Dismantle     Enthrall Mob into     FOLLOW / STAY / ATTACK
   (Or press [V] key)    (Sneak: Dismantle)    Deconstruction       Minion Thrall         Radius: 32 blocks
```

### Technical Specifications

- **Identifier**: `modid-mmcli-agent-modding:command_scepter`
- **Class**: `com.example.item.custom.CommandScepterItem`
- **Registry Holder**: `ModItems.COMMAND_SCEPTER`
- **Base Class**: `net.minecraft.item.Item`
- **Creative Tabs**: `ItemGroups.COMBAT` and `ItemGroups.TOOLS`
- **Rarity**: `Rarity.EPIC` (purple item name with persistent enchanted glint)
- **Max Stack Size**: `1` (single handheld focus)

### Interaction Matrix

| Action                                    | Condition / Mode         | Behavior                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| :---------------------------------------- | :----------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Shift + Right-Click**                   | Any Mode                 | Opens the interactive **Command Hub GUI** (`CommandScepterScreen`) allowing direct mode selection, paginated blueprint catalog inspection, directive execution, and broadcast dismissal.                                                                                                                                                                                                                                                                    |
| **Press [V] Key**                         | Scepter in Inventory     | Keybind shortcut opening the Command Hub GUI if a scepter is equipped in main hand, off hand, or player inventory.                                                                                                                                                                                                                                                                                                                                          |
| **Right-Click / Left-Click Owned Minion** | Any Mode                 | **Minion Selection Toggle (`toggleMinionSelection`)**: Clicking an owned minion with the Scepter toggles its tactical selection (`isSelected()`). Selecting clears guard anchor and starts following (chime SFX, hearts). Deselecting anchors the minion at its current post without sitting (bass SFX, smoke). Left-clicking cancels vanilla attack and prevents friendly-fire damage.                                                                     |
| **Shift + Left-Click (Block/Air/Mob)**    | Non-`BUILD` Modes        | **Deselect All Minions (`deselectAllMinions`)**: Deselects all owned minions across a 64-block battlefield radius, anchoring them at their current posts without sitting. Works when clicking blocks, mobs, or open air (in main or offhand).                                                                                                                                                                                                               |
| **Hold Right-Click (Channel)**            | Any Mode                 | **Banner of Courage Rally Ring**: Charges an expanding circular particle ring (`PORTAL` and `FLAME`, radius 3.0 to 16.0 blocks). Releasing triggers goat horn sound, gathers all enclosed minions into the selected squad channel, selects them, and orders them to `FOLLOW`. Quick-taps (<8 ticks) evaluate 32-block crosshair raycasting: instant blueprint cycling (`BUILD`), long-range focus-fire entity pings, or 32-block RTS ground waypoint pings. |
| **Right-Click Ground**                    | Non-`BUILD`/`MINE` Modes | **Ground Waypoint Ping / Crosshair Raycast**: Crosshair raycasts up to 32.0 blocks (`MINION_COMMAND_RADIUS`). If cursor aligns with a hostile entity, prioritizes focus-fire. Otherwise, emits a vertical beacon beam (`END_ROD` and `GLOW` particles) with beacon SFX (`SoundEvents.BLOCK_BEACON_ACTIVATE`). Moves **ONLY currently selected minions** matching the active squad channel filter, anchoring them to hold the waypoint post.                 |
| **Right-Click Hostile Entity**            | Any Mode (non-Recruit)   | **Hostile Entity Focus-Fire Ping**: Line-of-sight raycasted up to 32.0 blocks (bypassing 3.0 vanilla reach limitations). Emits lock-on particles (`ANGRY_VILLAGER` and `CRIT`) with note block drum cadence SFX (`BLOCK_NOTE_BLOCK_BASEDRUM`). All matching selected squad minions focus-fire that specific target.                                                                                                                                         |
| **Right-Click Ground**                    | `BUILD` Mode             | Anchors a new multiblock `ConstructionSession` (`SessionMode.BUILD`) at the clicked block face using the active blueprint. Emits beacon sound and enchantment particle blast.                                                                                                                                                                                                                                                                               |
| **Shift + Right-Click Ground**            | `BUILD` Mode             | Anchors a **Structure Dismantling Session** (`SessionMode.DISMANTLE`) at the clicked block face, commanding builders and miners to dismantle the active blueprint top-down.                                                                                                                                                                                                                                                                                 |
| **Right-Click Ground / Box**              | `MINE` Mode              | Anchors a **Structure Dismantling Session** (`SessionMode.DISMANTLE`) at the clicked block or existing active structure bounding box. If an active session is clicked, targets its blueprint; otherwise targets the held blueprint.                                                                                                                                                                                                                         |
| **Shift + Left-Click**                    | `BUILD` Mode             | **Cycle Blueprint Rotation (`cycleRotation`)**: Cycles rotation through 0° → 90° → 180° → 270° → 0°, plays chime audio, updates scepter component, dispatches `UpdateScepterPayload` to server, and renders updated actionbar readout with door sparkle guide. Supported in main or offhand against air or blocks without breaking blocks. |
| **Right-Click Air**                       | `BUILD` Mode             | Alternate blueprint cycling trigger without targeting a block.                                                                                                                                                                                                                                                                                                                                                                                              |
| **Right-Click Mob**                       | `RECRUIT` Mode           | Enthralls target living mob into an obedient `MinionEntity` thrall primed in standby (supports up to 32-block crosshair alignment).                                                                                                                                                                                                                                                                                                                         |
| **Right-Click Ground / Air**              | `FOLLOW` Mode            | Orders all owned minions within 32 blocks to stand up, selects them, and follows the player (`speed: 1.25`). Quick-taps evaluate 32-block crosshair hits.                                                                                                                                                                                                                                                                                                   |
| **Right-Click Ground / Air**              | `STAY` Mode              | Orders all owned minions within 32 blocks to deselect, sit down, hold position, and clear targets. Quick-taps evaluate 32-block crosshair hits.                                                                                                                                                                                                                                                                                                             |
| **Right-Click Mob / Ground**              | `ATTACK` Mode            | Crosshair raycast up to 32 blocks: prioritizes target entity in crosshairs, acquires closest hostile along cursor ray near clicked block/player, or broadcasts attack directive within 32 blocks to active matching squad thralls.                                                                                                                                                                                                                          |

### 2.1 Tactical Unit Selection, Decoupled Guard Stance & Faction Teammates

1. **Discrete Unit Selection & Glowing Outlines**:
   - Commanders select specific units by left-clicking or right-clicking owned minions with the Command Scepter, or charging the Banner of Courage.
   - Selected minions glow with their squad's distinct team outline color (`0xE74C3C` Red for Alpha, `0x3498DB` Blue for Bravo, `0x2ECC71` Green for Charlie, `0xF39C12` Gold for Delta, `0xFFFFFF` White for All).
   - Unselected units remain stationary and undisturbed during ground waypoint pings.

2. **Decoupled Standing Guard Stance**:
   - Deselecting a unit assigns an anchor position (`setGuardAnchorPos(pos)`) while keeping `isSitting() == false`. Units stand upright at attention like sentinels rather than forcing an unnatural sitting posture.
   - `MinionEntity` synchronizes `GUARDING` via `DataTracker`. Both sitting units and standing sentinels report `isHoldingPosition() == true`.
   - The overhead badge displays `[HOLD]` above their heads, rendered in fullbright (`LightmapTextureManager.MAX_LIGHT_COORDINATE`) for pitch-black visibility.

3. **Faction Teammates & Friendly-Fire Immunity**:
   - `MinionEntity.isTeammate(Entity other)` binds owner and minions under one faction.
   - Prevents Ranger minion arrows from injuring allies and prevents player Sweeping Edge attacks from striking minions.
   - `MinionEntity.damage` cancels any incoming friendly fire damage from the owner or allied thralls.

### 2.2 Command Hub GUI & Blueprint Catalog Pagination (`CommandScepterScreen`)

To eliminate GUI overflow where large blueprint catalogs overlapped bottom action buttons on higher GUI scales:

- **Fixed 3-Item Viewport (`BLUEPRINT_PAGE_SIZE = 3`)**: Blueprints render in a structured 3-card vertical stack on the right side of the screen (`startX + 165`).
- **Pagination Navigation**: Previous (`<`) and Next (`>`) button widgets appear dynamically when total blueprints exceed 3.
- **Action Bar Clearance**: Bottom controls (`Execute`, `Teleport All`, `Dismiss All`, `Close`) are safely positioned below the catalog viewport (`startY + 222`), guaranteeing zero overlap across all screen resolutions and GUI scale settings.
- **Fast-Dismissal & Ergonomic Close Bindings**: Command Hub modal supports intuitive fast-closing via `Shift` tap once already opened, alongside standard `V` (toggle), `E` (inventory), and `Esc` (vanilla exit) bindings, guarded against immediate dismissal during sneak-right-click opens (see [Section 27](#27-shift-to-close-gui-architecture-open-state-guard--fast-dismissals-commandscepterscreen)).

---

## 3. Data Components Architecture (`ModDataComponents` & `CommandMode`)

In Minecraft 1.21, ItemStack NBT has been replaced by type-safe, immutable **Data Components**.

### Components Defined

```java
// Registered in ModDataComponents.java
public static final ComponentType<CommandMode> COMMAND_MODE = Registry.register(
    Registries.DATA_COMPONENT_TYPE,
    Identifier.of(ExampleMod.MOD_ID, "command_mode"),
    ComponentType.<CommandMode>builder()
        .codec(CommandMode.CODEC)
        .packetCodec(CommandMode.PACKET_CODEC)
        .build()
);

public static final ComponentType<String> ACTIVE_BLUEPRINT = Registry.register(
    Registries.DATA_COMPONENT_TYPE,
    Identifier.of(ExampleMod.MOD_ID, "active_blueprint"),
    ComponentType.<String>builder()
        .codec(Codec.STRING)
        .packetCodec(PacketCodecs.STRING)
        .build()
);

public static final ComponentType<Integer> STRUCTURE_ROTATION = Registry.register(
    Registries.DATA_COMPONENT_TYPE,
    Identifier.of(ExampleMod.MOD_ID, "structure_rotation"),
    ComponentType.<Integer>builder()
        .codec(Codec.INT)
        .packetCodec(PacketCodecs.INTEGER)
        .build()
);
```

### `CommandMode` Enumeration

Implements `StringIdentifiable` with full `Codec` and `PacketCodec` serialization:

- `FOLLOW` (`0.8F` pitch, `§aFollow`)
- `STAY` (`1.0F` pitch, `§eStay`)
- `ATTACK` (`1.2F` pitch, `§cAttack`)
- `MINE` (`1.4F` pitch, `§6Mine`)
- `BUILD` (`1.6F` pitch, `§bBuild`)
- `RECRUIT` (`1.8F` pitch, `§dRecruit`)

---

## 4. Autonomous Minion Thrall Entity (`MinionEntity`) & Spawn Egg

The **Minion Entity** is an autonomous worker and combat thrall bound to a player master.

```
                    [Minion Entity Architecture]
                                 │
     ┌───────────────────────────┼───────────────────────────┐
     ▼                           ▼                           ▼
[TameableEntity Core]   [InventoryOwner (9 slots)]   [AI Goal Selectors]
 • Owner UUID tracking   • 9-slot SimpleInventory     • Priority 0: SwimGoal
 • Health: 40.0 HP       • 6 Equipment Slots          • Priority 1: SitGoal
 • Armor: 4.0            • Sneak+Click: Opens GUI     • Priority 2: LongDoorInteractGoal
 • Speed: 0.3            • Empty-Hand: Sit/Follow     • Priority 3: MinionBuildGoal
 • Attack: 5.0           • Food/Gold: Healing         • Priority 4: MeleeAttackGoal
 • Follow Range: 32.0    • Auto-equips from storage   • Priority 5: FollowOwnerGoal
```

### Technical Specifications

- **Identifier**: `modid-mmcli-agent-modding:minion`
- **Class**: `com.example.entity.custom.MinionEntity`
- **Registry Holder**: `ModEntities.MINION`
- **Base Class**: `net.minecraft.entity.passive.TameableEntity` implements `InventoryOwner`
- **Hitbox Dimensions**: `0.6` width x `1.95` height, eye height `1.74` (standard player biped)
- **Spawn Egg**: `modid-mmcli-agent-modding:minion_spawn_egg` (Colors: `0x2C3E50` deep navy base, `0xF1C40F` arcane gold spots; right-clicking immediately tames the spawned minion to the placing player and primes it in standby)

### Minion Player Interaction Matrix

| Interaction                  | Condition      | Behavior                                                                                                                                                                                                             |
| :--------------------------- | :------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Sneak + Right-Click**      | Owned Minion   | Opens the interactive **Minion Management GUI** (`MinionScreen`) displaying 6 equipment slots, 9-slot inventory, live 3D preview, and Dismiss button. Direct item drop onto the minion is retired.                   |
| **Empty Hand Right-Click**   | Owned Minion   | Toggles holding position state (updates both `sitting` and `inSittingPose`). Clears targets and plays frame rotate / orb audio.                                                                                      |
| **Right-Click with Scepter** | Owned Minion   | Orders the individual minion to break holding stance and follow master at 1.35D, emitting `HEART` particles, note block chime SFX, and an actionbar message.                                                         |
| **Food / Gold Right-Click**  | Injured Minion | Heals the wounded minion: <br>• Food restores health equal to food nutrition.<br>• Gold Nugget heals 1.0 HP, Gold Ingot heals 4.0 HP, Gold Block heals 20.0 HP.<br>• Emits `ParticleTypes.HEART` and level-up audio. |
| **Gold Ingot Right-Click**   | Untamed Minion | Binds the wild minion to the player as its permanent owner.                                                                                                                                                          |

### 4.1 Standby Summoning & At-Ease Priming

In previous versions, minions spawned from `MinionSpawnEggItem` or recruited via `CommandScepterItem.transfigureEntityToMinion` immediately engaged nearby hostiles because `MinionRole.WARRIOR` is the default role and `MinionActiveTargetGoal` scans a 24-block volume.

- **At-Ease / Standby Priming**: Newly spawned and recruited minions now initialize in an explicit standby state:
  ```java
  minion.setSitting(true);
  minion.setGuardAnchorPos(minion.getBlockPos());
  minion.getNavigation().stop();
  minion.setTarget(null);
  ```
- **Operational Safety**: Newly placed thralls calmly hold their spawn position and remain stationed until the commander issues an explicit follow, waypoint, or rally order.

### 4.2 Custom Scaffolding Travel Physics (`travel(Vec3d)` & `isClimbing`)

In vanilla Minecraft, `MobEntity` instances cannot climb `Blocks.SCAFFOLDING` upward autonomously because the ascending mechanic relies on player client jump input packets (`Input.jumping`), which AI pathing mobs do not send.

- **Travel Physics Override**: `MinionEntity.travel(Vec3d)` detects when a minion is inside a scaffolding block (`getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)`) and navigating towards an elevated waypoint or climbing shaft:

  ```java
  boolean ascendingScaffolding = this.isAlive()
      && this.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING)
      && this.isNavigatingUpwardInScaffolding();

  if (ascendingScaffolding) {
      this.fallDistance = 0.0F;
      Vec3d currentVelocity = this.getVelocity();
      this.setVelocity(currentVelocity.x, 0.25D, currentVelocity.z);
      this.velocityModified = true;
  }
  ```

- **Continuous Impulse & Fall Distance Suppression**: Applies a continuous $+0.25\text{D}$ upward impulse while actively traversing vertical scaffolding columns, resetting `fallDistance = 0.0F` to eliminate impact damage on descent.
- **`isClimbing()` Alignment**: Overrides `isClimbing()` to return true whenever `climbingScaffolding || isNavigatingUpwardInScaffolding()`, ensuring entity animation controllers reflect climbing posture.

---

## 5. Combat Survivability & Post-Combat Return

To eliminate the issue where minions appeared to "disappear" after battle (caused by low base stats dying in 2–3 hits and wide wandering follow deadzones), the minion combat and survivability systems have been completely overhauled:

```
                  [Minion Combat & Survivability Loop]
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         ▼                         ▼                         ▼
 [Boosted Attributes]    [Auto-Regeneration]       [Post-Combat Regroup]
  • Max HP: 40.0          • Out-of-combat ticks     • onKilledOther hook
  • Armor: 4.0            • +1.0 HP / 40 ticks      • Target loss detection
  • Base Damage: 5.0      • Active when tamed       • Speed 1.25 sprint back
  • Drop Chance: 2.0F       and injured (>=60 ticks)   to player master
```

### 1. Robust Base Attributes & Equipment Drop Guarantees

- **Max Health**: Increased from 20.0 HP to **40.0 HP** (`EntityAttributes.GENERIC_MAX_HEALTH`), doubling baseline durability.
- **Inherent Armor**: Given **4.0 base armor** (`EntityAttributes.GENERIC_ARMOR`), providing built-in physical damage mitigation before equipping player armor.
- **Base Attack Damage**: Elevated to **5.0 base damage** (`EntityAttributes.GENERIC_ATTACK_DAMAGE`).
- **Guaranteed Equipment Preservation**: Equipment drop chance is set to `2.0F` across all equipment slots, ensuring all gear drops 100% reliably if a minion ever dies or is dismissed.

### 2. Passive Out-of-Combat Regeneration

- Minions track their combat state via an internal `outOfCombatTicks` counter in `tick()`.
- When a minion is alive, tamed, out of combat for at least 60 ticks (3 seconds), and injured (`health < maxHealth`), it regenerates **1.0 HP every 40 ticks (2 seconds)** until fully restored.

### 3. Post-Combat Regrouping (`returnToOwnerPostCombat()`)

- Previously, minions could become separated or lost after a combat encounter due to a wide follow goal deadzone.
- Overrides `onKilledOther(ServerWorld, LivingEntity)` and tracks target clearing in `tick()`. The instant a hostile target dies or disappears, `returnToOwnerPostCombat()` is invoked.
- If the minion is tamed, not sitting, and more than 4 blocks away from its owner, it immediately navigates back to the player at sprint speed (`1.25D`).
- `FollowOwnerGoal` parameters were tightened to `minDistance: 3.0F, maxDistance: 1.5F` at speed `1.15D`, keeping minions grouped closely with the player.

### 4. Overhauled Weapon Combat & Audio

- `tryAttack(Entity target)` is overridden to invoke `swingHand(Hand.MAIN_HAND)` to drive client swing animations.
- Attacks calculate equipped weapon damage, enchantments (Sharpness, Smite, Fire Aspect), and play sound effects:
  - `SoundEvents.ENTITY_PLAYER_ATTACK_STRONG` for Swords, Axes, and Maces.
  - `SoundEvents.ENTITY_PLAYER_ATTACK_WEAK` for fists or other items.

---

## 6. Minion Management GUI (`MinionScreen` & `MinionScreenHandler`)

Sneak + Right-Clicking an owned minion opens a specialized management screen designed to give players full oversight and control of the thrall's inventory, equipment, and lifecycle.

```
+-------------------------------------------------------+
|  Equipment          [Minion Name]          Inventory  |
|  [Head]    [OffH]   +-----------+          [ ][ ][ ]  |
|  [Chest]   [Main]   |  3D Live  |          [ ][ ][ ]  |
|  [Legs]             |  Preview  |          [ ][ ][ ]  |
|  [Feet]             +-----------+                     |
|                                                       |
|  Player Inventory                                     |
|  [ ][ ][ ][ ][ ][ ][ ][ ][ ]                          |
|  [ ][ ][ ][ ][ ][ ][ ][ ][ ]                          |
|  [ ][ ][ ][ ][ ][ ][ ][ ][ ]                          |
|  Hotbar                                               |
|  [ ][ ][ ][ ][ ][ ][ ][ ][ ]                          |
+-------------------------------------------------------+
|  [ Role: Warrior ]             [ Squad: Alpha ]       |
|  [ ✦ Teleport to Me ]          [ ✖ Dismiss Minion ]   |
+-------------------------------------------------------+
```

### Layout Specifications

- **Dimensions & Responsive Dynamic Centering**:
  - Container frame with unified total modal height (`TOTAL_MODAL_HEIGHT = 222`), partitioned into a top badge header panel (`TOP_PANEL_HEIGHT = 26`), standard container body (`backgroundHeight = 166`), and bottom action row (`ACTION_ROW_HEIGHT = 26`).
  - Dynamically calculates screen placement:
    ```java
    int idealTopY = (this.height - TOTAL_MODAL_HEIGHT) / 2;
    int topY = Math.max(2, idealTopY);
    if (topY + TOTAL_MODAL_HEIGHT > this.height) {
        topY = Math.max(0, this.height - TOTAL_MODAL_HEIGHT);
    }
    this.y = topY + TOP_PANEL_HEIGHT;
    ```
  - Guarantees the entire unified modal (top badges, inventory slots, 3D entity preview, and bottom action buttons) remains perfectly centered and never clips off screen on any GUI scale or display resolution.
- **Top Badge Header Panel**:
  - Housed in a dark container plate (`0xEE141923`) directly above the inventory grid, bordered by the minion's squad division color with horizontal indicator stripes for role and squad.
  - **Role Cycling Button**: Native cycling control switching archetype roles between `WARRIOR` (melee frontline), `SENTINEL` (perimeter defense), `BUILDER` (scaffolding and structure construction), `MINER` (excavation), and `RANGER` (ranged skirmishing). Supports left-click (next), right-click / shift-click (previous), and mouse scroll wheel.
  - **Squad Cycling Button**: Assigns the minion to a discrete tactical command squad (`ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`) for targeted scepter directives.
  - Changes are instantly synced to the server via `UpdateMinionConfigPayload` and reflected immediately in the minion UI.
- **Bottom Action Bar Enclosure**:
  - Encloses `✦ Teleport to Me` (`x + 4, y + 169`, width 82) and `✖ Dismiss Minion` (`x + 90, y + 169`, width 82) in a bordered action tray matching the modal styling.
- **6 Dedicated Equipment Slots** (Slots 0..5):
  - `HEAD` (Slot 0): Accepts helmets, mob heads, pumpkins; displays empty helmet ghost sprite.
  - `CHEST` (Slot 1): Accepts chestplates, elytras; displays empty chestplate ghost sprite.
  - `LEGS` (Slot 2): Accepts leggings; displays empty leggings ghost sprite.
  - `FEET` (Slot 3): Accepts boots; displays empty boots ghost sprite.
  - `MAINHAND` (Slot 4): Accepts weapons and tools (`SwordItem`, `MiningToolItem`, `RangedWeaponItem`, `TridentItem`, `MaceItem`).
  - `OFFHAND` (Slot 5): Accepts shields and totems (`ShieldItem`, `Items.TOTEM_OF_UNDYING`); displays empty offhand shield ghost sprite.
- **9-Slot Minion Storage Grid** (Slots 6..14): 3x3 storage matrix backed by `MinionEntity.getInventory()`.
- **36 Player Inventory & Hotbar Slots** (Slots 15..50): Full integration with player hotbar and inventory.

### Shift-Click Logic (`quickMove`)

- Shift-clicking from minion equipment (0..5) or minion inventory (6..14) deposits items into player inventory.
- Shift-clicking from player inventory/hotbar intelligently routes items:
  1. Helmets, chestplates, leggings, and boots route to their respective empty armor slot.
  2. Swords, axes, tools, bows, and maces route to the mainhand weapon slot.
  3. Shields and Totems of Undying route to the offhand slot.
  4. Any other item or overflowing gear routes to the minion's 9-slot storage grid.

### Live 3D Entity Preview

- Centered at `(x+47, y+17)` with dimensions 66x56 in a dark tactical frame (`0xEE141923`).
- Rendered via `InventoryScreen.drawEntity`, reflecting equipped armor, held weapons/shields, and mouse-following head rotation.
- **Status Tooltip**: Hovering over the preview card displays a comprehensive stats tooltip:
  - Minion name (`§6✦ <Name>`)
  - Role (`§7Role: <Archetype>`)
  - Squad Channel (`§7Squad: <Squad Channel>`)
  - Current & Max Health (`§c❤ Health: 40.0 / 40.0`)
  - Total Armor Rating (`§b🛡 Armor: 4 (+Armor)`)
  - Current Stance (`§aStatus: Guarding / Following Master` or `§eStatus: Holding Position`)

### Auto-Equip from Internal Storage (`autoEquipFromInventory`)

- When the screen closes (`onClosed`) or periodically every 20 ticks (1 second) in server tick:
  - Minions automatically inspect their 9 storage slots.
  - **Smart Role-Based Matching**:
    - **Ranger (`MinionRole.RANGER`)**: Dedicated to ranged weaponry. Automatically equips Bows and Crossbows into mainhand. Rejects melee swords, axes, maces, and tools.
    - **Warrior (`MinionRole.WARRIOR`)**: Dedicated to frontline shock combat. Automatically equips Swords, Axes, Maces, and Tridents. Rejects ranged weapons.
    - **Sentinel (`MinionRole.SENTINEL`)**: Dedicated to perimeter defense. Equips melee weapons in mainhand and specifically prioritizes Shields in offhand (automatically displacing Totems of Undying).
    - **Miner (`MinionRole.MINER`)**: Resource specialist. Prioritizes Pickaxes and mining tools in mainhand. Rejects ranged weapons.
    - **Builder (`MinionRole.BUILDER`)**: Architectural constructor. Equips construction tools (Pickaxes, Shovels) and melee weapons. Rejects ranged weapons.
  - **Protective Armor**: All roles automatically equip available protective armor pieces (Head, Chest, Legs, Feet) into vacant armor slots with equip audio (`ITEM_ARMOR_EQUIP_GENERIC`).
  - **Displacement Preservation**: When a preferred weapon or shield replaces an equipped item, the displaced item is safely returned to internal storage, strictly preserving item conservation invariants without loss or duplication.

---

## 7. Biped Model & Client Rendering Pipeline (`MinionEntityRenderer`)

The minion client rendering pipeline has been fully transitioned from the restricted folded-arm villager model to a fully featured **Biped Player Model** (`PlayerEntityModel<MinionEntity>`).

```
                    [MinionEntityRenderer Pipeline]
                                  │
     ┌────────────────────────────┼────────────────────────────┐
     ▼                            ▼                            ▼
[PlayerEntityModel Base]  [ArmorFeatureRenderer]     [Dynamic Arm Poses]
 • Wide Steve base skin    • Inner layer (Leggings)   • Main/Offhand poses
 • Scale: 0.9375F          • Outer layer (Helmets,    • Bow drawing
 • Crouching & Sitting       Chestplates, Boots)      • Crossbow charging
   offset adjustments      • Full trim/glint support  • Shield blocking
```

### Renderer Components

1. **Model Architecture**:
   - Class: `MinionEntityRenderer` extends `BipedEntityRenderer<MinionEntity, PlayerEntityModel<MinionEntity>>`.
   - Base Texture: `textures/entity/player/wide/steve.png`.
   - Scale: Scaled to `0.9375F` for a distinct thrall silhouette.
2. **Dual-Layer Armor Rendering (`ArmorFeatureRenderer`)**:
   - Inner Layer: `EntityModelLayers.PLAYER_INNER_ARMOR` (renders leggings model).
   - Outer Layer: `EntityModelLayers.PLAYER_OUTER_ARMOR` (renders helmets, chestplates, boots).
   - Renders all vanilla and modded armor materials, durability overlays, glints, and armor trims.
3. **Dynamic Arm Pose Engine (`getArmPose`)**:
   - `BipedEntityModel.ArmPose.BLOCK`: Applied when actively blocking with a shield in offhand or mainhand.
   - `BipedEntityModel.ArmPose.BOW_AND_ARROW`: Applied during bow charge animations.
   - `BipedEntityModel.ArmPose.CROSSBOW_CHARGE` & `CROSSBOW_HOLD`: Applied when charging or carrying loaded crossbows.
   - `BipedEntityModel.ArmPose.THROW_SPEAR`: Applied when aiming tridents.
   - `BipedEntityModel.ArmPose.ITEM`: Standard pose for swords, tools, and blocks.
4. **Pose & Offset Handling (`getPositionOffset`)**:
   - Sitting Posture: Lowers Y coordinate by `-0.3125D` and sets `model.riding = true` when minion is in sitting/holding mode.
   - Sneaking Posture: Applies `-2.0F * scale / 16.0F` offset and enables `model.sneaking = true`.
5. **Decoupled Outer Clothing Feature (`MinionClothingFeatureRenderer`)**:
   - Adapted to `PlayerEntityModel` to ensure hat, jacket, sleeve, and pant outer layers render properly on biped models.
6. **Overhead Billboard Crest Badge Feature (`MinionOverheadBadgeFeatureRenderer`)**:
   - Renders a floating, camera-facing billboard badge directly above each minion's head using `EntityRenderDispatcher.getRotation()`.
   - **Squad Banner (Top Line)**: Displays squad channel flag, name, Roman numeral designation (`⚑ SQUAD ALPHA [I]`, `⚑ SQUAD BRAVO [II]`, `⚑ SQUAD CHARLIE [III]`, `⚑ SQUAD DELTA [IV]`, `⚑ SQUAD ALL [*]`), and an amber gold star prefix (`§6★ `) when the minion is actively selected by the commander.
   - **Role Crest & Lettering (Bottom Line)**: Displays tactical archetype icon and uppercase lettering (`⚔ WARRIOR`, `🛡 SENTINEL`, `🔨 BUILDER`, `⛏ MINER`, `🏹 RANGER`), appended with `[HOLD]` when holding position.
   - **Exact LIFO Matrix Reversal & Upright Coordinate System**:
     - Vanilla Minecraft's `LivingEntityRenderer` applies four cumulative transformations before feature rendering: body yaw rotation (`setupTransforms`), `scale(-1.0F, -1.0F, 1.0F)`, `scale(MODEL_SCALE)`, and `translate(0.0F, -1.501F, 0.0F)`.
     - In previous versions, naive translations in model space compounded with `LivingEntityRenderer`'s internal offsets, resulting in upside-down font orientation and badges floating ~1.8 blocks too high into the air.
     - `MinionOverheadBadgeFeatureRenderer` cleanly unwinds all model-space transformations in exact Last-In First-Out (LIFO) order:
       ```java
       matrices.translate(0.0F, 1.501F, 0.0F); // Invert translate
       matrices.scale(1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE, 1.0F / MODEL_SCALE); // Invert model scale
       matrices.scale(-1.0F, -1.0F, 1.0F); // Invert negative scale
       float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
       matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - 180.0F)); // Invert body yaw
       ```
     - Once restored to upright world space at entity feet, `getOverheadYTranslation(height, hasCustomName, isSneaking)` positions the badge safely above the minion's head:
       ```java
       float headClearance = hasCustomName ? 0.85F : 0.55F;
       if (isSneaking) headClearance -= 0.20F;
       return height + headClearance;
       ```
     - Head clearance was increased from 0.20F to 0.55F (+0.85F for custom named minions) to prevent badge mesh clipping into 3D biped helmets or skull geometry.
     - Followed by camera billboarding (`dispatcher.getRotation()`) and font scaling (`scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)`), ensuring badges render completely upright, stable across all perspectives, and nestled comfortably above the helmet.
   - **Vanilla Two-Pass Billboard Nametag Pipeline**:
     - Follows Minecraft's native nametag rendering architecture (`EntityRenderer.renderLabelIfPresent`) to resolve depth testing and occluded visibility.
     - **Pass 1 (Translucent See-Through Pass)**: Draws text using `TextRenderer.TextLayerType.SEE_THROUGH`, translucent base color `553648127` (`0x21FFFFFF`), and background plate color derived from client options (`options.getTextBackgroundOpacity(0.25F)`). Renders behind walls and blocks so commanders maintain situational awareness of thralls across obstacles.
     - **Pass 2 (Crisp Depth-Tested Foreground Pass)**: When `!entity.isInSneakingPose()`, draws text using `TextRenderer.TextLayerType.NORMAL`, solid fullbright color `-1` (`0xFFFFFFFF`), and transparent background `0`. Renders crisp, depth-tested foreground lettering that conforms to scene geometry.
     - **Stealth / Sneaking Compatibility**: When sneaking, Pass 2 is bypassed, rendering only the translucent Pass 1 to honor vanilla stealth nametag mechanics.
   - **Distance Culling**: Automatically culled beyond 64 blocks (`MAX_RENDER_DISTANCE_SQ = 4096.0D`) for optimal battlefield performance during mass unit combat.

---

## 8. Minion Dismissal & Networking Protocol (`DismissMinionPayload`)

Minions can be safely dismissed using dedicated UI controls, eliminating accidental thrall loss while providing a clean decommissioning workflow.

```
[Dismiss Action in GUI]
         │
         ▼
[DismissMinionPayload (C2S)]
 • minionId: Entity ID (or -1 for broadcast)
 • dismissAll: boolean
         │
         ▼
[Server: ModNetworking.handleDismissMinion]
 1. Locate minion(s) owned by player
 2. Drop all 6 equipped items
 3. Drop all 9 internal inventory items
 4. Spawn 15x POOF particles + Teleport sound
 5. Safely discard minion entity
 6. Close open GUI & send action-bar notification
```

### Dismissal Entry Points

1. **Individual Dismissal (`MinionScreen`)**:
   - Located at the bottom right of the Minion GUI (`x+90, y+170`, width 82).
   - Button: `§c✖ Dismiss` with descriptive tooltip.
   - Sends `DismissMinionPayload(minionId, false)` to dismiss only the currently inspected minion.
2. **Broadcast Dismissal (`CommandScepterScreen`)**:
   - Located on the Loki Command Hub GUI action bar (`x+174, y+185`, width 78).
   - Button: `§c✖ Dismiss`.
   - Sends `DismissMinionPayload(-1, true)` to dismiss all owned minions within the 32-block radius.

---

## 8.1 Instant Teleportation & Recall Protocol (`TeleportMinionPayload`)

Minions can be recalled instantly to the player's position using dedicated teleportation buttons in both the Minion Screen and the Command Scepter Screen:

```
[Teleport Action in GUI]
         │
         ▼
[TeleportMinionPayload (C2S)]
 • minionId: Entity ID (or -1 for broadcast)
 • teleportAll: boolean
         │
         ▼
[Server: ModNetworking.handleTeleportMinion]
 1. Locate target minion(s) owned by player
 2. Verify safe destination ground (solid floor, 2-block clearance, non-hazardous)
 3. Origin VFX & SFX: 20x PORTAL particles + Enderman teleport audio
 4. Move minion, cancel velocity, reset fallDistance to 0.0F, stop navigation
 5. Destination VFX & SFX: 25x PORTAL + 10x REVERSE_PORTAL + Enderman audio
 6. Clear sitting pose if sitting so minion can immediately follow master
 7. Emit action-bar feedback message
```

### Teleportation Entry Points

1. **Individual Teleportation (`MinionScreen`)**:
   - Located at the bottom left of the Minion GUI (`x+4, y+170`, width 82).
   - Button: `§d✦ Teleport to Me` with tooltip `Teleports this minion directly to your current position.`
   - Dispatches `TeleportMinionPayload(minionId, false)`.
2. **Broadcast Teleportation (`CommandScepterScreen`)**:
   - Located on the Loki Command Hub GUI action bar (`x+92, y+185`, width 78).
   - Button: `§d✦ Teleport` with tooltip `Teleport all <count> nearby owned minion(s) to you.`
   - Automatically disabled when 0 minions are within range; active when thralls are bound.
   - Dispatches `TeleportMinionPayload(-1, true)` to recall all minions in the 32-block radius.

### Server Decommissioning Guarantees (`MinionEntity.dismiss()`)

- Drops every equipped armor piece, held weapon, and offhand item onto the ground.
- Drops all 9 inventory items onto the ground.
- Emits 15 `ParticleTypes.POOF` particles and plays `SoundEvents.ENTITY_ENDERMAN_TELEPORT`.
- Calls `discard()` to remove the entity cleanly without firing hostile death alerts or corrupting chunk state.

---

## 9. Loki Scepter Enthrallment (Transfiguration Lifecycle)

When the Loki Command Scepter is used in `RECRUIT` mode against a living vanilla mob, it triggers server-side transfiguration:

```
[Target Living Mob]
        │
        ▼
1. Validate Target (Living mob, not player, not minion)
        │
        ▼
2. Instantiate MinionEntity on ServerWorld
        │
        ▼
3. Transcribe State:
   • Spatial Coordinates & Angles (X, Y, Z, Yaw, Pitch)
   • Velocity Vector
   • Custom Name & Custom Name Visibility
   • Equipment across all 6 EquipmentSlots (MainHand, OffHand, Head, Chest, Legs, Feet)
   • Inventory items (if target was InventoryOwner)
        │
        ▼
4. Bind Ownership to Player & Prime in Standby:
   • setOwner(player) & mark tamed
   • setSitting(true) (At-Ease holding stance)
   • setGuardAnchorPos(spawnPos)
   • clear target & stop navigation
        │
        ▼
5. VFX & SFX:
   • 50x ParticleTypes.ENCHANT
   • 30x ParticleTypes.PORTAL
   • SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED
        │
        ▼
6. target.discard() & world.spawnEntity(minion)
```

---

## 10. Curated Blueprint Catalog & Topological Sorting

Multiblock structures are modeled as deterministic blueprints with bottom-up topological sorting.

### Topological Ordering Guarantee (`BlueprintBlock`)

Every block in a blueprint is ordered by:

1. **Vertical Ascent (`offset.getY()`)**: Lower foundations and pillar tiers are guaranteed to be placed before upper walls and arches.
2. **Horizontal Proximity (Manhattan distance from center)**: Inner core blocks are placed before outer perimeter blocks on the same elevation.
3. This guarantees that dependent blocks (such as hanging lanterns or inverted stair arches) always have supporting anchor blocks already placed before construction.

### Registered Blueprints

```
1. Overlord Watchtower (7x7x9, 137 blocks)
   • Foundation: Deepslate bricks and polished deepslate corner footings
   • Pillar Supports: Polished andesite vertical columns
   • Defensive Walls: Stone bricks with iron bar arrow slits
   • Arches: Inverted stone brick stairs supporting observation deck
   • Parapet: Deepslate brick crenellations with soul lanterns

2. Arcane Obelisk (5x5x8, 48 blocks)
   • Foundation: Crying obsidian steps and polished blackstone bricks
   • Core: Chiseled deepslate vertical conduit
   • Energy Nodes: Crying obsidian pulse blocks
   • Pinnacle: Lit soul campfire brazier and perimeter soul lanterns

3. Defensive Barricade (9x3x3, 49 blocks)
   • Base: Stone brick and cobblestone heavy foundation
   • Palisades: Dark oak logs alternating with iron bar arrow slits
   • Sightlines: Dark oak fence palisades and rear stone-brick firing steps
   • Lighting: Vigil lanterns atop terminal battlements
```

---

## 11. Multiblock Construction Manager & Session Orchestration

The **`ConstructionManager`** is a server-side singleton that tracks and coordinates all active construction sessions across dimensions.

```
                              [ConstructionManager]
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           ▼                            ▼                            ▼
  [Session Management]         [Worker Leases]             [Visual Holograms]
   • Map<UUID, Session>         • 15-second worker leases   • 20-tick interval
   • Map<BlockPos, Session>     • Auto-reclaim stale tasks  • Glow bounding box
   • Dim & Owner filtering      • 5-second cleanup sweep    • Wax-on task aura
```

### Key Capabilities

- **Ground Anchoring & Session Modes (`SessionMode`)**:
  - `SessionMode.BUILD`: Right-clicking ground in `BUILD` mode initiates bottom-up construction.
  - `SessionMode.DISMANTLE`: Shift + Right-clicking ground in `BUILD` mode or clicking ground/structures in `MINE` mode initiates top-down structure deconstruction (`startDismantleSession`).
- **Mode-Specific Hologram Projections**:
  - Every 20 ticks (1 second), the manager projects holographic bounding-box outlines:
    - **Construction Sessions (`BUILD`)**: Renders `PORTAL` anchor core particles, `GLOW` corner wireframes, and `WAX_ON` auras on actively claimed placement blocks.
    - **Deconstruction Sessions (`DISMANTLE`)**: Renders `FLAME` anchor pulse particles, `SMALL_FLAME` corner wireframes, and `CRIT` sparks on actively claimed demolition blocks.
- **Worker Lease Management**: When a minion claims a task, a lease timestamp is recorded. If a minion is killed, disconnected, or pathfinding-stuck for over 15 seconds (300 ticks), the task is automatically returned to the unclaimed task pool.
- **Completion Ceremonies**:
  - **Construction Complete**: Plays `UI_TOAST_CHALLENGE_COMPLETE` and `ENTITY_PLAYER_LEVELUP`, erupts celebratory `HAPPY_VILLAGER` and `TOTEM_OF_UNDYING` particles, and notifies the commander.
  - **Deconstruction Complete**: Plays `ENTITY_IRON_GOLEM_DAMAGE` and `ENTITY_PLAYER_LEVELUP`, erupts `EXPLOSION` and `SMOKE` particle clouds, clears any lingering scaffolding, and sends an actionbar message: `"§6✔ Deconstruction Complete! §f<Name> §6has been completely dismantled by your minions!§r"`.

---

## 12. Minion Construction AI & Resource Scavenging

The **`MinionBuildGoal`** enables minion thralls to autonomously carry out architectural blueprints.

```
                           [MinionBuildGoal Execution]
                                       │
        ┌──────────────────────────────┴──────────────────────────────┐
        ▼                                                             ▼
[Creative Mode]                                               [Survival Mode]
 Instant placement                                             1. Check Minion 9-slot Inventory
 Zero resource cost                                            2. If missing, scavenge nearby
                                                                  Chests/Barrels within 12 blocks
                                                               3. If absent:
                                                                  • Smoke particles
                                                                  • Dispenser fail sound
                                                                  • Release task lease
                                                                  • Action-bar alert to player
```

### Placement & Deconstruction Dynamics

- **Dual Session Mode Execution**:
  - In `BUILD` mode, executes bottom-up construction, consuming inventory/chest resources and placing blocks.
  - In `DISMANTLE` mode, executes top-down deconstruction, harvesting existing structure blocks in reverse topological order.
- **Role Participation Invariants**:
  - `MinionRole.BUILDER`: Participates in both `BUILD` and `DISMANTLE` sessions.
  - `MinionRole.MINER`: Participates exclusively in `DISMANTLE` sessions (excavation and demolition specialist).
  - Combat roles (`WARRIOR`, `SENTINEL`, `RANGER`): Excluded from construction and deconstruction tasks.
- **Dynamic Tool Equipping (`resolveDismantleTool`)**:
  - In dismantle mode, minions dynamically equip appropriate harvesting tools based on target block hardness and material:
    - Iron Pickaxe (`Items.IRON_PICKAXE`) for stone, brick, deepslate, obsidian, lanterns, iron bars.
    - Iron Shovel (`Items.IRON_SHOVEL`) for dirt, sand, gravel, clay.
    - Iron Axe (`Items.IRON_AXE`) for logs, planks, wood, doors.
- **Demolition VFX & Resource Drops**:
  - Minion swings main arm (`swingHand(Hand.MAIN_HAND)`), spawns dense `BlockStateParticleEffect` breaking particles, and plays block break audio.
  - In survival mode (`!creative`), harvested blocks drop as collectible item stacks into the world via `Block.dropStacks(targetState, world, targetPos, be, minion, heldTool)`.
  - Block is cleared: `serverWorld.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)`.
- **Scaffolding Teardown on Descent**:
  - In dismantle mode, as the minion descends after completing upper layers, temporary scaffolding blocks above the minion's current height (`y > minionBlockY + 1`) are automatically dismantled with breaking particles and audio, leaving a clean, cleared site upon landing.
- **Preserved Weapons**: Before equipping tools or preview blocks, the minion's equipped weapon is preserved and restored immediately upon task completion or release.
- **Block Preview**: The minion visibly holds the required block (or harvesting tool) in `EquipmentSlot.MAINHAND` while pathfinding.
- **Navigation Proximity**: Pathfinds to within 3.8 horizontal and 5.5 vertical blocks of the target block position.
- **Deliberate Work Delay**: 4-tick work delay for visual realism.
- **True Kinematic Platform Landing**:
  - Vertical ascent is executed prior to reach validation, preventing premature ground reach fallbacks during climbing.
  - Applies continuous $+0.25\text{D}$ upward impulse with horizontal centering lock.
  - Upon crossing the landing threshold ($\ge \text{targetScaffoldTopY} + 0.95\text{D}$), the minion cleanly snaps to the platform standing surface at $(\text{scaffoldCenterX}, \text{targetScaffoldTopY} + 1.0\text{D}, \text{scaffoldCenterZ})$, zeroes velocity, clears climbing flags, and completes arrival before block placement begins.
- **Elevated Task Chaining (`isTaskReachableFromPlatform`)**:
  - When completing an elevated block placement or deconstruction task, minions evaluate the newly claimed task from their current platform coordinates.
  - If reachable (horizontal $\text{distSq} \le 16.0$ and vertical $\Delta Y \le 2.5$), the minion stays elevated on the scaffolding platform, equips the next item/tool, and continues building without triggering unnecessary descents, ground pathfinding, or column re-allocations.
- **Exterior Perimeter Column Selection**:
  - Automatically projects candidate column positions 1 block outside the session's `worldBoundingBox` perimeter ($\text{minX} - 1$, $\text{maxX} + 1$, $\text{minZ} - 1$, $\text{maxZ} + 1$).
  - Evaluates candidates sorted by combined distance to target and minion position ($\text{distToTarget} + 0.5 \times \text{distToMinion}$), completely eliminating directional "North bias" and naturally distributing multiple builders across structure faces.
  - Validates full vertical clearance in both world blocks and uncompleted blueprint tasks up through $\text{targetY} + 2$, protecting minions from overhangs, observation decks, and eaves.
- **Ceiling Collision & Stall Sensors with Abort Recovery**:
  - During ascent, continuously monitors overhead head clearance at $\text{minion.getY()} + 2.0\text{D}$.
  - Dynamic displacement stall sensor detects lack of upward progress ($>10$ ticks).
  - On obstruction or stall, immediately aborts ascent, blacklists the column coordinate to prevent re-selection loops, releases column reservations, and initiates a controlled downward descent to ground.
- **Multi-Minion Column Reservation Integration**:
  - Coordinates with `ConstructionSession.claimScaffoldColumn` and `isScaffoldColumnAvailable` to ensure separate builders working on the same structure reserve distinct perimeter columns.
  - Column claims are released upon landing, task release, timeout, or goal cancellation.
- **Top-to-Bottom Demobilization Teardown**:
  - On descent, minions dismantle temporary scaffolding nodes top-to-bottom on the way down in both `DISMANTLE` mode and upon completion of all session tasks (`demobilization`) in `BUILD` mode.
  - `ConstructionSession.clearScaffolding` sweeps any remaining nodes when sessions complete or cancel.

---

## 13. Custom Items: TNT Stick

The **TNT Stick** is a handheld weapon that enables players to launch explosive TNT projectiles on right-click.

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
- **Creative Tab**: `ItemGroups.COMBAT`
- **Rarity**: `Rarity.EPIC`
- **Max Stack Size**: `1`
- **Anti-Spam Cooldown**: 5 ticks (0.25s)

---

## 14. Custom Entities: TNT Projectile & Renderer

The **TNT Projectile** is a high-velocity throwable projectile that exhibits in-flight visual effects and triggers a 4.0F explosion immediately upon contact with blocks or living entities.

### Technical Specifications

- **Identifier**: `modid-mmcli-agent-modding:tnt_projectile`
- **Class**: `com.example.entity.custom.TntProjectileEntity`
- **Registry Holder**: `ModEntities.TNT_PROJECTILE`
- **Base Class**: `net.minecraft.entity.projectile.thrown.ThrownItemEntity`
- **Hitbox Dimensions**: `0.25` width x `0.25` height
- **Explosion Strength**: `4.0F` (standard TNT power, respects `mobGriefing`)
- **Particle Trails**: Continuous `SMOKE` trail + 60% probability `FLAME` sparks per tick.
- **Renderer (`TntProjectileRenderer`)**: Extends `FlyingItemEntityRenderer<TntProjectileEntity>` to render airborne projectiles as spinning 3D TNT blocks with full pitch/yaw interpolation.

---

## 15. Screen Handlers & Block Registration Architecture

### Screen Handlers (`ModScreenHandlers`)

Registers extended screen handlers backed by Fabric's `ExtendedScreenHandlerType`:

```java
public static final ScreenHandlerType<MinionScreenHandler> MINION_SCREEN_HANDLER = Registry.register(
    Registries.SCREEN_HANDLER,
    Identifier.of(ExampleMod.MOD_ID, "minion_screen_handler"),
    new ExtendedScreenHandlerType<>(MinionScreenHandler::new, PacketCodecs.INTEGER.cast())
);
```

### ModBlocks

Centralized, reusable architecture for registering custom blocks and their corresponding `BlockItem` instances:

```java
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

---

## 16. Bytecode Injections & Mixins: ExampleMixin

The mod includes SpongePowered Mixin integration for runtime bytecode manipulation.

### Technical Specifications

- **Mixin Config**: `src/main/resources/modid.mixins.json`
- **Class**: `com.example.mixin.ExampleMixin`
- **Target Class**: `net.minecraft.server.MinecraftServer`
- **Injected Method**: `loadWorld`
- **Injection Point**: `@At("HEAD")`
- **Compatibility Level**: `JAVA_21`

---

## 17. Assets, Models & Localization Pipeline

All assets adhere to vanilla 1.21 resource pack specifications under namespace `modid-mmcli-agent-modding`.

```
src/main/resources/assets/modid-mmcli-agent-modding/
├── lang/
│   └── en_us.json                # English (US) localization keys
├── models/
│   └── item/
│       ├── tnt_stick.json        # Handheld TNT Stick model
│       ├── command_scepter.json  # Handheld Loki Command Scepter model
│       └── minion_spawn_egg.json # Template spawn egg model
└── textures/
    └── item/                     # Texture storage directory
```

---

## 18. Extensibility & Developer Roadmap

| Extension Target                           | Status / Implementation Strategy                                                                                                                                                                                                                 |
| :----------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Minion Equipment & GUI**                 | **Completed**: Dedicated `MinionScreen` GUI, 6 equipment slots, auto-equipping, 3D live entity preview, and dismiss mechanics.                                                                                                                   |
| **Biped Player Model**                     | **Completed**: `MinionEntityRenderer` with `PlayerEntityModel`, dynamic arm poses (blocking, bow, crossbow), and dual-layer armor trims.                                                                                                         |
| **Combat Durability**                      | **Completed**: 40 HP, 4 armor, 5 attack damage, passive out-of-combat regeneration, weapon swing animations, and post-combat return.                                                                                                             |
| **Scaffolding Builder AI**                 | **Completed**: Temporary scaffolding deployment for elevated construction tasks and weapon saving/restoration.                                                                                                                                   |
| **Radial Menu HUD**                        | In Design: Client radial HUD overlay to select scepter command modes and blueprints via mouse wheel.                                                                                                                                             |
| **Minion Mining AI**                       | Roadmap: Add `MinionMineGoal` to execute automated area excavation and ore vein quarrying.                                                                                                                                                       |
| **Blueprint Schematics**                   | Roadmap: Add NBT/JSON structure file loader to convert `.nbt` structure templates into `StructureBlueprint` instances.                                                                                                                           |
| **Minion Classes / Professions**           | **Completed**: Differentiated into specialized roles (`WARRIOR`, `SENTINEL`, `BUILDER`, `MINER`, `RANGER`) with partitioned AI goals and behavior profiles.                                                                                      |
| **Tactical Squads & Channeling**           | **Completed**: Squad partitioning (`ALL`, `ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`) with scepter channel filtering and interactive GUI selection bars.                                                                                               |
| **Banner of Courage Rally Ring**           | **Completed**: Channeled expanding circular particle ring gathering enclosed minions into selected squad with war horn audio.                                                                                                                    |
| **Tactical Ground & Hostile Pings**        | **Completed**: Ground waypoint sprint & hold (`WaypointHoldGoal`), and hostile focus-fire with war drum cadences.                                                                                                                                |
| **Smart Role Auto-Equip**                  | **Completed**: Archetype weapon & tool restrictions (Bows for Rangers, Shields for Sentinels, Pickaxes for Miners, etc.).                                                                                                                        |
| **Dynamic Formations**                     | **Completed**: Distributed parametric stations (Vanguard wedge, Bulwark wings, Core support, Skirmishers rearguard) with anti-crowding geometry.                                                                                                 |
| **Overhead Billboard Badges**              | **Completed**: Dynasty Warriors overhead crests with squad colors, Roman numerals, tactical role icons, and status flags.                                                                                                                        |
| **Sentinel Perimeter Leash**               | **Completed**: Autonomous anchor tethering, 8-block perimeter guard, 12-block leash distance with aggro drop and 1.35D sprint retreat.                                                                                                           |
| **Ranger Archery AI**                      | **Completed**: Implements `RangedAttackMob` with dynamic strafing pocket (8-16 blocks), bow pull animation, and backpedaling under 8 blocks.                                                                                                     |
| **32-Block Crosshair Raycasting**          | **Completed**: Full 32.0D line-of-sight raycasting for entity focus-fire and RTS ground waypoints, with solid block obstruction clipping and ray-to-point math.                                                                                  |
| **Combat Sappers & Traversal Scaffolding** | **Completed**: Autonomous chasm/ravine bridging (up to 6 blocks), cliff climbing shafts (up to 6 blocks), zero-cost `BUILDER` sappers, 24-block signaling, and 400-tick ephemeral decay with entity safety guards.                               |
| **Structure Deconstruction Mode**          | **Completed**: Top-down reverse topological dismantling (`SessionMode.DISMANTLE`), role authorization (`BUILDER` & `MINER`), tool resolution, survival drops, and progressive scaffolding teardown on descent.                                   |
| **Scepter Individual Minion Follow**       | **Completed**: Direct right-click or 32-block crosshair quick-tap on owned minion commands unit to follow with chime SFX and heart particles.                                                                                                    |
| **Standby Summoning & Hold Safeguards**    | **Completed**: Newly summoned/transfigured minions initialize in standby holding state; waypoint pings and attack broadcasts guard stationed units (`!m.isSitting()`).                                                                           |
| **GUI Pagination & Unified Framing**       | **Completed**: 3-item viewport pagination for blueprint catalog in Command Hub; dynamically centered, fully framed Minion Screen preventing clipping on any GUI scale.                                                                           |
| **Overhead Crest Alignment & Hitbox**      | **Completed**: Inverted Y-translation in `MinionOverheadBadgeFeatureRenderer`, elevated head clearance to 0.55F/0.85F, and vanilla two-pass nametag rendering pipeline (`SEE_THROUGH` + `NORMAL`) completely eliminating helmet mesh z-clipping. |
| **Formation Yaw Anchoring & Rank Filter**  | **Completed**: `FormationAnchor` state machine with 0.04 blocks^2 (0.2m) movement hysteresis freezing station yaw during stationary look sweeps, manual refresh on scepter directives, and rank resolution filtering unselected/guarding units.  |
| **Kinematic Scaffolding Platform Landing** | **Completed**: Custom vertical climbing velocity impulse (+0.25D) with horizontal centering lock, clean surface landing snap to `targetScaffoldTopY + 1.0D`, zeroing velocities, and resetting climbing flags before work begins.                |
| **Elevated Task Chaining**                 | **Completed**: `isTaskReachableFromPlatform` allows consecutive execution of adjacent elevated blocks (horizontal distSq <= 16.0, vertical diff <= 2.5) directly from the platform without descending or ground pathfinding.                     |
| **Exterior Perimeter Column Allocation**   | **Completed**: 1-block exterior perimeter candidate projection eliminating directional North bias, sorting by distance to target and minion, and overhead clearance checks through `targetY + 2`.                                                |
| **Multi-Minion Column Reservation**        | **Completed**: `ConstructionSession` scaffolding column reservation system preventing multiple builders from colliding on the same ladder shaft.                                                                                                 |
| **Combat Sapper Suppression**              | **Completed**: Suppresses `MinionSapperGoal` for `BUILDER` and `MINER` minions engaged in or situated within 48 blocks of active construction sessions to eliminate structure wall misidentification.                                            |
| **Radial Menu HUD**                        | In Design: Client radial HUD overlay to select scepter command modes and blueprints via mouse wheel.                                                                                                                                             |
| **Minion Mining AI**                       | Roadmap: Add `MinionMineGoal` to execute automated area excavation and ore vein quarrying.                                                                                                                                                       |
| **Blueprint Schematics**                   | Roadmap: Add NBT/JSON structure file loader to convert `.nbt` structure templates into `StructureBlueprint` instances.                                                                                                                           |

---

## 19. Tactical Army & Squad Architecture (Roles, Squads, Formations, Rally & SFX)

To support RTS-style squad tactics and specialized minion operations, the mod introduces an integrated army management architecture comprising **Archetype Roles**, **Squad Channels**, **Role-Gated AI Goals**, and **Networked C2S Configuration**.

```
                           [Loki Command Scepter]
                                     │
                 ┌───────────────────┴───────────────────┐
                 ▼                                       ▼
        [Active CommandMode]                   [Target SquadGroup]
       (FOLLOW / STAY / ATTACK)            (ALL / ALPHA / BRAVO / ...)
                 │                                       │
                 └───────────────────┬───────────────────┘
                                     ▼
                       [Predicate Army Broadcast]
             m -> isAlive && isOwner && targetSquad.matches(m.squad)
                                     │
       ┌──────────────┬──────────────┼──────────────┬──────────────┐
       ▼              ▼              ▼              ▼              ▼
   [WARRIOR]     [SENTINEL]      [BUILDER]       [MINER]       [RANGER]
  Frontline Melee Perimeter Leash Multiblock   Excavation    Strafing Archery
  Sprint Attack   8-block guard   Construction  Specialist   8-16 block sweet spot
  24-block aggro  12-block leash  Exclusive                  Backpedal < 8 blocks
```

### Minion Archetype Roles (`MinionRole`)

Each minion thrall can be assigned one of five distinct behavioral roles:

| Role       | ID  | Color / Formatting | Primary Specialization & AI Behavior                                                                                                                                                 |
| :--------- | :-- | :----------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `WARRIOR`  | 0   | `§c` (Red)         | Aggressive frontline shock infantry. Scans hostile mobs within 24 blocks, engages in close-quarters melee combat, and sprint-attacks at 1.35D speed.                                 |
| `SENTINEL` | 1   | `§a` (Green)       | Perimeter defensive sentry. Tethered to an anchor position (`guardAnchorPos` or owner). Holds post within 2 blocks, maintains 8-block guard perimeter, and enforces 12-block leash.  |
| `BUILDER`  | 2   | `§9` (Blue)        | Dedicated architectural builder. Only minions assigned the `BUILDER` role can claim construction tasks or execute `MinionBuildGoal`. Non-builders never claim tasks.                 |
| `MINER`    | 3   | `§6` (Gold)        | Resource extraction and underground excavation specialist.                                                                                                                           |
| `RANGER`   | 4   | `§5` (Dark Purple) | Artillery skirmisher. Implements `RangedAttackMob` with bow/arrow projectiles, dynamic strafing in an 8–16 block pocket, and tactical backpedaling if enemies close within 8 blocks. |

- **Serialization**: Fully serialized via `StringIdentifiable`, Mojang `Codec<MinionRole>`, and Netty `PacketCodec<ByteBuf, MinionRole>` (indexed via `ValueLists`).
- **Cycling**: Supports cyclic navigation via `role.next()` and `role.previous()` with out-of-bounds safety fallbacks.

### Sentinel Leash & Perimeter Guard Logic (`SentinelGuardGoal`)

The `SentinelGuardGoal` governs sentries to prevent them from being lured away from defended positions:

```
[Anchor Post: BlockPos] ◄────── 2.0 blocks ──────► [Arrival Zone: Holds Station]
         │
         ├────────────── 8.0 blocks ─────────────► [Guard Perimeter: Engages Intruders]
         │
         └────────────── 12.0 blocks ────────────► [Leash Limit: BREAK AGGRO & RETREAT]
                                                   • setTarget(null)
                                                   • Sprint back to anchor @ 1.35D
```

1. **Stationary Vigilance**: When within 2 blocks (`ARRIVAL_TOLERANCE_SQ = 4.0`), navigation stops and the sentinel stands guard facing outward.
2. **Perimeter Engagement**: When hostile mobs enter within 8 blocks (`PERIMETER_RADIUS_SQ = 64.0`), the sentinel engages and attacks.
3. **Leash Enforcement**: If the target flees or the sentinel is lured beyond 12 blocks (`LEASH_DISTANCE_SQ = 144.0`), aggro is immediately severed (`setTarget(null)`), combat goals are overridden, and the sentinel sprints back to the anchor post at 1.35D movement speed.
4. **Anchor Fallback**: If no static `guardAnchorPos` is designated, the sentinel dynamically tethers to its living owner's position.

### Squad Groups (`SquadGroup`) & Scepter Tactical Routing

Minions can be assigned to discrete squads to allow multi-front tactical delegation:

| Squad Group | ID  | Formatting | Scope & Role                                                                    |
| :---------- | :-- | :--------- | :------------------------------------------------------------------------------ |
| `ALL`       | 0   | `§f` White | **Wildcard channel**: Targets all owned minions regardless of squad assignment. |
| `ALPHA`     | 1   | `§c` Red   | First tactical unit.                                                            |
| `BRAVO`     | 2   | `§9` Blue  | Second tactical unit.                                                           |
| `CHARLIE`   | 3   | `§a` Green | Third tactical unit.                                                            |
| `DELTA`     | 4   | `§6` Gold  | Fourth tactical unit.                                                           |

- **Wildcard Predicate Matching**: `squad.matches(target)` returns true if `this == ALL` or `this == target`.
- **Selectable Squads**: `SquadGroup.getSelectableSquads()` exposes `[ALPHA, BRAVO, CHARLIE, DELTA]`, omitting the wildcard `ALL` for individual minion assignment.
- **Scepter Data Component**: `ModDataComponents.TARGET_SQUAD` stores the active squad channel directly on the `CommandScepterItem` ItemStack.
- **Command Hub GUI Squad Bar**: `CommandScepterScreen` renders an interactive squad selection bar (`[ ALL ] [ ALPHA ] [ BRAVO ] [ CHARLIE ] [ DELTA ]`), color-coded buttons, active selection markers (`▶`), and dynamic nearby minion counts per channel.
- **Broadcast Filtering**: Scepter broadcasts (`broadcastFollow`, `broadcastStay`, `broadcastAttack`, `broadcastMine`) apply the predicate:
  ```java
  world.getEntitiesByClass(
      MinionEntity.class,
      searchBox,
      m -> m.isAlive() && m.isOwner(player) && targetSquad.matches(m.getSquad())
  );
  ```

### Minion GUI Configuration & C2S Synchronization

- **Minion Screen Controls**: `MinionScreen` includes interactive `Role: <Name>` and `Squad: <Name>` cycle buttons. Clicking either button updates the local state and sends an `UpdateMinionConfigPayload` C2S packet.
- **Server Verification (`ModNetworking`)**: The server receiver validates entity existence, verifies player ownership (`minion.isOwner(player)`), applies `minion.setRole(payload.role())` and `minion.setSquad(payload.squad())`, and emits audio/visual feedback.

### "Banner of Courage" Rally & Tactical Pings Architecture

Minecraft Legends style RTS control mechanisms are integrated directly into the `CommandScepterItem`:

1. **Channeled Rally Ring ("Banner of Courage")**:
   - Holding right-click starts channeling the scepter (`getMaxUseTime: 72000`, `getUseAction: BLOCK`).
   - Every tick during channeled use, an expanding circular particle ring of `ParticleTypes.PORTAL` and `ParticleTypes.FLAME` charges outward from 3.0 to 16.0 blocks around the commanding player, accompanied by rising chime audio.
   - Upon release (`onStoppedUsing`), a war horn blast sounds (`SoundEvents.ITEM_GOAT_HORN_PLAY` / `item.goat_horn.sound.0`), gathering all enclosed minions within the charged radius into the currently selected squad channel, clearing any stale anchor posts, unsitting them, and commanding them to `FOLLOW`.
   - Quick taps (<8 ticks) seamlessly preserve instant blueprint cycling or standard directive broadcasts.

2. **Ground Waypoint Ping**:
   - Right-clicking the ground in non-build modes instantly drops a tactical waypoint marker.
   - Spawns a vertical beacon column (`ParticleTypes.END_ROD` and `ParticleTypes.GLOW`) and plays beacon SFX (`SoundEvents.BLOCK_BEACON_ACTIVATE`).
   - All minions matching the active squad channel sprint to the target position at 1.35D speed and hold position via `WaypointHoldGoal` or `SentinelGuardGoal`.

3. **Hostile Entity Focus-Fire Ping**:
   - Right-clicking an enemy entity tags it with lock-on particles (`ParticleTypes.ANGRY_VILLAGER` and `ParticleTypes.CRIT`) and plays a rhythmic battlefield drum cadence (`SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM`).
   - Matching squad units break current tasks and focus-fire that specific target.

### Dynamic Formations & Anti-Crowding Engine (`MinionFormationFollowGoal`)

To eliminate the unsightly collision clustering of vanilla follow goals where thralls crowd into a single collision pile, the mod implements a dynamic, parametric squad formation engine:

```
                            [Commander / Master] (Yaw)
                                       │
                      ▲                │                ▲
               Left Flank              │           Right Flank
                                       │
              [Warrior 0]              │           [Warrior 1]       ◄── Vanguard Wedge (3.5 blocks forward)
         (-1.5 flank, +3.5 fwd)        │       (+1.5 flank, +3.5 fwd)
                                       │
         [Sentinel 0]                  │                  [Sentinel 1]◄── Bulwark Wings (Level with master)
         (-3.0 flank, 0.0 fwd)         │          (+3.0 flank, 0.0 fwd)
                                       │
               [Builder 0]             │             [Miner 0]       ◄── Core Support (-2.5 blocks rear)
         (-1.5 flank, -2.5 fwd)        │       (+1.5 flank, -2.5 fwd)
                                       │
              [Ranger 0]               │               [Ranger 1]    ◄── Skirmisher Line (-6.0 blocks deep rear)
         (-2.0 flank, -6.0 fwd)        │       (+2.0 flank, -6.0 fwd)
```

1. **Parametric Distributed Offsets (`calculateFormationOffset`)**:
   - **Vanguard (Warriors)**: Forward-flanking wedge opening outward (`forward = 3.5D - tier * 1.5D`, `flank = side * (1.5D + tier * 2.0D)`). Frontline shock troops lead the march, ready to intercept head-on threats.
   - **Bulwark (Sentinels)**: Protective escort wings flanking the master's left and right sides (`forward = -tier * 1.5D`, `flank = side * (3.0D + tier * 1.5D)`).
   - **Core (Builders & Miners)**: Non-combatant support echelon tucked safely behind the master and vanguard (`forward = -2.5D - tier * 2.0D`, `flank = side * (1.5D + (tier % 2) * 1.0D)`).
   - **Skirmishers (Rangers)**: Rearguard line holding deep rear (`forward = -6.0D - tier * 1.5D`, `flank = side * (2.0D + tier * 2.0D)`), ensuring clear line-of-sight for ranged arrow fire.
2. **Trigonometric Coordinate Rotation & Formation Yaw Anchoring (`calculateFormationStation`)**:
   - Computes world coordinates using the commander's anchored formation yaw in Minecraft world space:
     $$\text{Forward Vector} = (-\sin(\theta_{\text{formation}}), \cos(\theta_{\text{formation}}))$$
     $$\text{Flank Vector} = (\cos(\theta_{\text{formation}}), \sin(\theta_{\text{formation}}))$$
   - **Kinematic Decoupling & Movement Hysteresis**: In earlier versions, tying formation stations directly to camera look yaw caused minions to wildly orbit the player whenever the commander looked around while standing still. `MinionFormationFollowGoal` now tracks a per-commander `FormationAnchor`. When horizontal displacement is $\le 0.04\text{ blocks}^2$ (0.2 blocks), the formation yaw remains locked at the movement heading. Only deliberate movement ($> 0.04\text{ blocks}^2$) or explicit scepter directives (`refreshFormationAnchor`) snap the formation heading.
3. **Deterministic Intra-Role Ranking & Selection Filtering (`resolveRank`)**:
   - Automatically orders active thralls belonging to the owner by entity ID to assign stable, flicker-free station ranks ($0, 1, 2, \dots$) within each role.
   - **Garrison Exclusion**: Filters out unselected minions, sitting units, and stationary sentinels (`m.isSelected() && !m.isHoldingPosition() && m.getGuardAnchorPos() == null`), preventing parked units from claiming front ranks or distorting active march formations.
4. **Walkable Surface Column Detection (`resolveWalkableY`)**:
   - Scans the vertical block column from $+2$ to $-3$ blocks at the calculated target coordinate, locking onto solid walkable ground and preventing thralls from navigating into midair or solid stone.
5. **Anti-Crowding Clearance**:
   - Every station in the formation maintains $\ge 2.0$ blocks spacing from the commander and within its role, and $\ge 1.5$ blocks clearance across tactical echelons, completely eliminating collision pushing and mob crowding.
6. **Dynamic Pacing & Emergency Recall**:
   - **Arrival Tolerance**: Halts navigation within 2.0 blocks of assigned station (`STOPPING_DISTANCE_SQ = 4.0`) to avoid jitter.
   - **Steady March**: Navigates at $1.15\times$ speed during normal following.
   - **Sprint Catch-Up**: Accelerates to $1.35\times$ sprint speed when lagging behind ($> 8.0$ blocks).
   - **Emergency Teleport**: Instantly recalls thrall via `teleportToPlayer()` if separated by $> 24.0$ blocks (`TELEPORT_DISTANCE_THRESHOLD_SQ = 576.0`).

### Overhead Crests & Battlefield Visual Hierarchy (`MinionOverheadBadgeFeatureRenderer`)

Dynasty Warriors inspired overhead billboard badges render above every minion's head, providing immediate situational awareness on the battlefield:

```
+------------------------------------+
|        §c⚑ SQUAD ALPHA [I]         |  ◄── Squad Banner: Color + Flag + Name + Roman Numeral
|         §c⚔ WARRIOR §e[HOLD]       |  ◄── Role Crest: Color + Tactical Icon + Role + Stance
+------------------------------------+
```

1. **Squad Banner (Top Line)**:
   - Displays the squad flag symbol (`⚑`), squad name, and Roman numeral designation:
     - `SquadGroup.ALPHA`: `§c⚑ SQUAD ALPHA [I]`
     - `SquadGroup.BRAVO`: `§9⚑ SQUAD BRAVO [II]`
     - `SquadGroup.CHARLIE`: `§a⚑ SQUAD CHARLIE [III]`
     - `SquadGroup.DELTA`: `§6⚑ SQUAD DELTA [IV]`
     - `SquadGroup.ALL`: `§f⚑ SQUAD ALL [*]`
2. **Role Crest & Stance (Bottom Line)**:
   - Displays the tactical archetype icon and uppercase lettering:
     - `MinionRole.WARRIOR`: `§c⚔ WARRIOR`
     - `MinionRole.SENTINEL`: `§a🛡 SENTINEL`
     - `MinionRole.BUILDER`: `§9🔨 BUILDER`
     - `MinionRole.MINER`: `§6⛏ MINER`
     - `MinionRole.RANGER`: `§5🏹 RANGER`
   - When the minion is sitting or holding a designated position, an amber `§e[HOLD]` suffix is appended automatically.
3. **Billboard Rendering Mechanics**:
   - Extends Fabric `FeatureRenderer<MinionEntity, PlayerEntityModel<MinionEntity>>`.
   - Utilizes `dispatcher.getRotation()` to align billboard text directly with the client camera regardless of player pitch, yaw, or third-person perspective.
   - **Dynamic Head Clearance**: Standard clearance positioned at $+0.55\text{F}$ above entity height ($+0.85\text{F}$ when custom named, and $-0.20\text{F}$ deduction when sneaking), completely eliminating helmet and skull mesh z-clipping.
   - **Vanilla Two-Pass Billboard Pipeline**: Employs Pass 1 (`SEE_THROUGH` with translucent white `553648127` and background plate) for obstacle penetration, and Pass 2 (`NORMAL` fullbright `-1` without background, gated by `!isSneaking`) for crisp foreground text.
   - **Distance Culling**: Culled beyond 64 blocks (`MAX_RENDER_DISTANCE_SQ = 4096.0D`) for optimal frame rates during large-scale army battles.

### Battlefield Sound Effects & Audio Punch

Tactile auditory feedback accompanies all tactical maneuvers:

- **War Horn Blasts (`SoundEvents.ITEM_GOAT_HORN_PLAY`)**: Resonant horn blast on releasing the Banner of Courage rally, signaling army-wide regrouping.
- **Battlefield Drum Cadences (`SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM` & `BLOCK_NOTE_BLOCK_SNARE`)**: Rhythmic war drum strikes when issuing attack directives or hostile focus-fire pings.
- **Beacon Resonance (`SoundEvents.BLOCK_BEACON_ACTIVATE`)**: Resonant activation chime when anchoring ground waypoints.
- **Armor Clanking (`SoundEvents.ITEM_ARMOR_EQUIP_GENERIC`)**: Equipment clinking audio when minions auto-equip weapons, shields, and armor from internal storage.

---

## 20. Testing & Verification Architecture

The project features a dedicated JUnit 5 testing suite validating domain serialization, state machines, AI thresholds, formation geometry, and networking payloads without requiring Minecraft server bootstrap:

| Test Class                           | Package                       | Verified Systems & Coverage                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| :----------------------------------- | :---------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MinionSquadAndRoleTest`             | `com.example.entity`          | • `MinionRole` & `SquadGroup` Mojang Codec JSON round-trip serialization.<br>• Netty `PacketCodec` ByteBuf byte-level serialization.<br>• Complete 5-cycle permutation invariants.<br>• Tactical squad filtering predicate on mock armies.<br>• Large-scale army squad partitioning (140 thralls) rejecting foreign and dead units.<br>• Multi-squad isolated simultaneous directive dispatch.<br>• `SentinelGuardGoal` state machine simulation (leash break, aggro clearing, sprint retreat).<br>• Ranged combat engagement pockets (8–16 blocks sweet spot, backpedal < 8 blocks).<br>• Channeled Banner of Courage rally gathering simulation.<br>• Ground waypoint and hostile focus-fire ping state transitions.<br>• Squad glowing outline color resolution (`0xE74C3C` Alpha, `0x3498DB` Bravo, `0x2ECC71` Charlie, `0xF39C12` Delta, `0xFFFFFF` All).<br>• Selective ground waypoint dispatch (only selected units matching active channel receive move orders).<br>• Decoupled guard stance (deselected units hold post upright without forced sitting).                                                                                                                                                                                                                                   |
| `MinionFormationAndEquipTest`        | `com.example.entity`          | • Vanguard (Warrior) forward-flanking wedge parametric geometry.<br>• Bulwark (Sentinel) escort wings flanking commander.<br>• Core (Builder/Miner) protected support column placement.<br>• Skirmisher (Ranger) rearguard line placement.<br>• Anti-crowding station clearance ($\ge 2.0$ blocks from master & comrades).<br>• 16-thrall 4-echelon cohort pairwise clearance ($\ge 1.5$ blocks).<br>• Compass yaw rotation invariants across 8 cardinal/intercardinal headings and negative angles.<br>• Dynamic pacing speeds ($1.15\text{D}$ march, $1.35\text{D}$ sprint, $2.0$ block arrival, $24.0$ block teleport).<br>• Role-based smart auto-equip rules and comprehensive item matrix across all 5 roles.<br>• Inventory displacement item preservation without duplication or loss.<br>• Formation offset reflectional symmetry and monotonic wing flare.<br>• Yaw hysteresis stationary 360° look sweep invariance.<br>• Sub-threshold displacement freezing ($\le 0.04\text{ blocks}^2$) and deliberate movement unlocking.<br>• Instant anchor refresh (`refreshFormationAnchor`) on scepter follow/rally directives.<br>• Formation rank eligibility predicate filtering unselected, sitting, and guarding units.<br>• Seamless rank collapse when units are deselected or stationed. |
| `MinionOverheadBadgeTest`            | `com.example.client.renderer` | • Squad banner text formatting and Roman numeral designations (`[I]`, `[II]`, `[III]`, `[IV]`, `[*]`).<br>• Role crest icons (`⚔`, `🛡`, `🔨`, `⛏`, `🏹`) and uppercase lettering.<br>• Active vs `[HOLD]` status suffix formatting.<br>• Upright world-space Y translation and elevated head clearance (+0.55F standing, +0.85F named, -0.20F sneaking).<br>• Selected squad banner gold star (`§6★ `) prefix formatting.<br>• Renderer configuration constants (text scale, line spacing, 64-block distance culling).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `NetworkingPayloadTest`              | `com.example.network`         | • `UpdateMinionConfigPayload` record fields, IDs, and equality.<br>• `UpdateScepterPayload` channel synchronization and backward-compatible constructor.<br>• `DeselectMinionsPayload` record registration and server dispatch.<br>• PacketCodec registration integrity.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `ScaffoldingTest`                    | `com.example.blueprint`       | • Temporary scaffolding column deployment and vertical reach.<br>• Doorway corridor avoidance logic.<br>• Headroom clearance validation.<br>• Climbing state machine transitions.<br>• Travel physics impulse (+0.25D) and fall distance suppression.<br>• Multi-minion scaffolding column reservation and conflict resolution.<br>• Stale claim pruning and administrative force-release.<br>• Kinematic climb horizontal centering lock, surface threshold snap (+1.0D), and 140-tick safety timeout abort.<br>• Elevated task chaining sequential execution without premature ground descent.<br>• Exterior perimeter candidate sorting eliminating directional North bias.<br>• Ceiling collision and vertical stall sensors with safe descent recovery.<br>• Demobilization teardown state machine across build and dismantle modes.                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `CommandScepterRaycastTargetingTest` | `com.example.item`            | • Exact 3D point-to-ray squared perpendicular distance math and clamped $[0, \text{maxRange}]$ projection segments.<br>• Origin clamping for rear targets and tip clamping for beyond-32-block targets.<br>• Direction vector normalization invariance and zero-length degeneration handling.<br>• Crosshair candidate sorting (angular alignment priority, depth tie-breaking, rear hostile rejection).<br>• Line-of-sight solid block obstruction clipping and entity-over-block hit priority.<br>• Quick-tap (<8 ticks) dispatch state machine matrix across all command modes (`BUILD`, `ATTACK`, `FOLLOW`, `STAY`, `RECRUIT`).<br>• Entity targetability invariants (commander exclusion, owned minion immunity, enemy thrall targeting, spectator rejection).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `MinionSapperAndScaffoldingTest`     | `com.example.entity`          | • Ravine and chasm bridging coordinate calculation across cardinal North/East and diagonal headings.<br>• Zero heading vector safety and Y-level stability.<br>• Cliff and mountain ascent climbing column coordinate generation for 3-block and 6-block ledges.<br>• Flat and inverted elevation edge-case handling.<br>• Combat sapper role archetype gating (`BUILDER` zero-cost vs standard thrall item consumption).<br>• Squad sapper 24-block assistance request dispatch and 200-tick timeout lifecycle.<br>• `TraversalScaffoldingManager` decay state machine simulation (400-tick default lifetime, 40-tick entity safety extension while occupied, clean removal on expiration).<br>• Combat sapper goal suppression rules when engaged in construction.<br>• Minion engagement tracking across task claims and scaffolding reservations.<br>• Construction proximity boundary (48-block threshold), expanded bounding box, and dimension filtering.<br>• Squad sapper assistance dispatch excluding active construction workers.<br>• Sheer structure wall vs natural cliff suppression preventing false bridge deployment.                                                                                                                                                             |
| `StructureDismantlingTest`           | `com.example.construction`    | • `SessionMode` enum values and mode accessors (`BUILD` vs `DISMANTLE`).<br>• Reverse topological task sorting (highest effective Y first, foundation last).<br>• Roof-to-foundation deconstruction prerequisites (clearing non-hanging upper blocks before foundations, hanging decorations before supporting ceilings).<br>• Minion role authorization matrix (`BUILDER` & `MINER` participation; combat roles excluded).<br>• Progressive scaffolding teardown on descent simulation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `CommandScepterScreenCloseTest`      | `com.example.client.gui`      | • Open-state guard (`shiftHeldOnOpen`) latching suppressing immediate screen dismissal on sneak-right-click.<br>• Sustained GLFW key repeat event absorption for Left and Right Shift.<br>• Left Shift and Right Shift physical release transition detecting finger lift.<br>• Immediate modal dismiss on subsequent Shift tap once open guard is cleared.<br>• Unarmed open fast-close on initial Left or Right Shift press.<br>• Command Hub toggle key (`V`) dismissal under both armed and unarmed guard states.<br>• Inventory hotkey (`E`) dismissal under both armed and unarmed guard states.<br>• Vanilla `Escape` key close delegation.<br>• Non-closing gameplay key filtering (`WASD`, `Space`, `Enter`, numbers, etc.).<br>• Zero-latency tick fallback clearing guard in headless/tick cycles.<br>• Open-state initialization idempotency and flag preservation.<br>• GLFW key identification helpers (`isShiftOrSneakKey`, `isCommandHubKey`, `isInventoryKey`).<br>• Screen lifecycle getters/setters and close idempotency.                                                                                                                                                                                                                                                         |

All 126 unit tests execute and pass cleanly via `./gradlew test`.

---

## 21. Long-Range Crosshair Targeting & Combat Raycasting (`CommandScepterItem`)

Phase 1 introduces a crosshair raycast targeting engine in `CommandScepterItem`, resolving long-range reach limitations and unifying entity focus-fire with point-and-click tactical navigation.

```
                                [Command Scepter Cursor Aiming]
                                               │
                                  [Player Line of Sight: 32.0D]
                                               │
                       ┌───────────────────────┴───────────────────────┐
                       ▼                                               ▼
              [Solid Terrain Check]                          [Entity Hit Query]
          RaycastContext OUTLINE Clipping               ProjectileUtil.raycast (0-32.0D)
          Clips search reach at obstacle                Is targetable living mob/thrall?
                       │                                               │
                       ├───────────────────────┬───────────────────────┤
                       ▼                       ▼                       ▼
               [Entity in Sights]       [Block Targeted]        [Aiming into Sky]
                Focus-Fire Ping         32-Block Waypoint       Broadcast Directive
                (Drum SFX & Crit)       (Beacon Beam & SFX)     (Follow / Stay / Attack)
```

### Architectural Problem & Motivation

In vanilla Minecraft, entity interaction through `Item.useOnEntity` is hard-coded to an reach of approximately $3.0$ blocks (`REACH_DISTANCE`), while block clicks fall back to `Item.useOnBlock` up to $\approx 4.5$ blocks. In prior implementations:

1. Attempting to click an enemy further than $3.0$ blocks away would fail entity interaction. If the cursor was near terrain within $4.5$ blocks, it triggered `useOnBlock`, unconditionally dropping a ground waypoint at the enemy's feet instead of issuing an attack order.
2. Quick-taps in `ATTACK` mode previously scanned an un-oriented 16-block axis-aligned bounding box and arbitrarily picked the first index (`hostiles.get(0)`), disregarding the player's crosshair and failing to engage enemies between 16 and 32 blocks away.

### Core Raycasting Architecture & Mathematical Specification

#### 1. Coordinate Space & Projection

Given player eye position $\vec{S} = \text{player.getCameraPosVec}(1.0\text{F})$, unit rotation vector $\vec{D} = \text{player.getRotationVec}(1.0\text{F})$, and command range $R_{\text{max}} = 32.0\text{D}$ (`MINION_COMMAND_RADIUS`):

$$\vec{E} = \vec{S} + \vec{D} \cdot R_{\text{max}}$$

#### 2. Line-of-Sight Terrain Obstruction (`raycastEntityTarget`)

Before querying entities, the raycast tests line of sight against solid blocks via `player.getWorld().raycast(RaycastContext)` with `ShapeType.OUTLINE` and `FluidHandling.NONE`. If a block collision occurs at point $\vec{P}_{\text{block}}$:

$$d_{\text{max}}^2 = \min\left(R_{\text{max}}^2, \|\vec{P}_{\text{block}} - \vec{S}\|^2\right)$$

The entity query volume is constrained to $d_{\text{max}}^2$. Entities obscured behind solid walls, hills, or bulkheads cannot be targeted, preventing wallhack exploits.

#### 3. Exact 3D Point-to-Ray Perpendicular Distance (`calculateDistanceSqToRay`)

To accurately score and sort candidate hostiles along the crosshair vector, the perpendicular distance from an entity center $\vec{P}$ to the finite ray segment $[\vec{S}, \vec{E}]$ is calculated:

$$t = (\vec{P} - \vec{S}) \cdot \frac{\vec{D}}{\|\vec{D}\|}$$

$$t_{\text{clamped}} = \max\left(0.0, \min(R_{\text{max}}, t)\right)$$

$$\vec{C} = \vec{S} + \frac{\vec{D}}{\|\vec{D}\|} \cdot t_{\text{clamped}}$$

$$d^2(\vec{P}, \text{Ray}) = \|\vec{P} - \vec{C}\|^2$$

- **Rear Target Clamping**: If a target lies behind the player ($t < 0$), $t_{\text{clamped}} = 0$, clamping $\vec{C}$ to $\vec{S}$. Its score evaluates as the full Euclidean distance squared from the player's head, rejecting enemies behind the commander.
- **Max-Range Tip Clamping**: If a target lies beyond 32 blocks ($t > 32$), $t_{\text{clamped}} = 32$, clamping $\vec{C}$ to the ray tip $\vec{E}$.

#### 4. Angular Alignment Candidate Sorting (`findBestHostileTargetNear`)

When searching for hostiles near a clicked block or origin point, candidates are sorted using a two-tier comparator:

```java
hostiles.sort(Comparator
    .comparingDouble((MobEntity mob) -> calculateDistanceSqToRay(rayStart, rayDir, MINION_COMMAND_RADIUS, getEntityCenter(mob)))
    .thenComparingDouble(mob -> mob.squaredDistanceTo(player))
);
```

The hostile closest to the player's line-of-sight ray vector is selected. Ties along the same angular vector are broken by absolute distance to the player.

### Mode-Dependent Quick-Tap (<8 Ticks) Execution Matrix

When the player releases right-click after a quick tap (<8 ticks), `CommandScepterItem.onStoppedUsing` evaluates `raycastTarget(player, 32.0D)`:

| Command Mode | Crosshair Hit: Entity                                          | Crosshair Hit: Block (0–32 blocks)                          | Crosshair Hit: Sky / Miss           |
| :----------- | :------------------------------------------------------------- | :---------------------------------------------------------- | :---------------------------------- |
| `BUILD`      | Cycles active blueprint (`cycleBlueprint`)                     | Cycles active blueprint (`cycleBlueprint`)                  | Cycles active blueprint             |
| `ATTACK`     | Focus-fires targeted entity (`executeHostileEntityPing`)       | Focus-fires nearby hostile; otherwise drops ground waypoint | Global attack directive broadcast   |
| `FOLLOW`     | Focus-fires targeted entity (if enemy); otherwise ground ping  | Long-range RTS ground waypoint ping (up to 32 blocks)       | Global follow directive broadcast   |
| `STAY`       | Focus-fires targeted entity (if enemy); otherwise ground ping  | Long-range RTS ground waypoint ping (up to 32 blocks)       | Global stay directive broadcast     |
| `RECRUIT`    | Enthralls living mob into minion (`transfigureEntityToMinion`) | Long-range RTS ground waypoint ping                         | Emits action-bar tip on recruitment |
| `MINE`       | Focus-fires targeted entity (if enemy); otherwise ground ping  | Long-range RTS ground waypoint ping                         | Global mine directive broadcast     |

### Entity Targetability Invariants (`isTargetableEntity`)

- **Spectator Immunity**: Spectators (`entity.isSpectator()`) are ignored.
- **Dead Entity Rejection**: Inactive or dead entities (`!entity.isAlive()`) are ignored.
- **Self-Targeting Guard**: Commanding player is never targetable.
- **Friendly Thrall Immunity**: Owned minions (`minion.isOwner(player)`) are immune to focus-fire pings.
- **PvP Targetability**: Enemy thralls belonging to other players or hostile vanilla mobs are fully targetable.

---

## 22. Combat Sappers & Ephemeral Traversal Scaffolding (`MinionSapperGoal` & `TraversalScaffoldingManager`)

Phase 2 introduces autonomous combat engineering for minion thralls, enabling squad movements, charges, and retreats across rugged survival terrain, ravines, and mountain cliffs.

```
                           [Minion Facing Terrain Obstacle]
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
         [Ravine / Chasm Drop]                           [Cliff / Mountain Face]
         • Drop >= 2 blocks (air/lava/water)              • Obstacle height > 1.0625 blocks
         • Scan span: up to 6 blocks                      • Upward ledge scan: up to 6 blocks
         • Generate horizontal bridge coords              • Generate vertical column coords
                  │                                               │
                  └───────────────────────┬───────────────────────┘
                                          ▼
                             [Sapper Role Authorization]
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
     [BUILDER: Combat Sapper]                        [Other Archetypes]
      Zero-cost placement                             1. Consume Items.SCAFFOLDING from inv
      Immediate deployment                            2. If empty: Signal nearby squad BUILDER
                                                         within 24.0D (SapperRequest)
                                          │
                                          ▼
                         [TraversalScaffoldingManager]
                          • Default decay: 400 ticks (20s)
                          • Entity safety check: occupied scaffolds
                            extend timer by +40 ticks
                          • Break SFX and cloud particles on decay
```

### 1. Ephemeral Traversal Scaffolding Manager (`TraversalScaffoldingManager`)

`TraversalScaffoldingManager` is a server-side singleton governing the lifecycle of temporary scaffolding deployed for dynamic traversal.

#### Architectural Properties

- **Multi-Dimension Tracking**: `Map<RegistryKey<World>, Map<BlockPos, TraversalEntry>>` guarantees dimension isolation.
- **Ephemeral Decay Lifecycle**: Placed traversal scaffolding is registered with `DEFAULT_DECAY_TICKS = 400` (20.0 seconds).
- **Entity Safety Extension Guard (`isOccupied`)**:
  - Before decaying any block, the manager scans an expanded safety volume:
    $$\text{Box}(x - 0.1, y, z - 0.1, x + 1.1, y + 2.1, z + 1.1)$$
  - If any living `MinionEntity` or `PlayerEntity` is currently standing on or inside the scaffold, decay is postponed:
    $$\text{expiryTick} = \max(\text{expiryTick}, \text{currentTick} + \text{SAFETY\_DELAY\_TICKS})$$
    where `SAFETY_DELAY_TICKS = 40` (2.0 seconds).
  - This guarantees thralls and commanders crossing a ravine or ascending a cliff will never have the platform disintegrate beneath their feet.
- **Decay Breakdown Ceremony**:
  - Replaces block with `Blocks.AIR`.
  - Spawns 8 `BlockStateParticleEffect` (`Blocks.SCAFFOLDING`) breaking particles.
  - Plays `SoundEvents.BLOCK_SCAFFOLDING_BREAK` (0.8F volume, 1.0F pitch).
- **Server Tick Hook**: Registered in `ExampleMod.java` via `ServerTickEvents.END_WORLD_TICK`.

### 2. Autonomous Combat Sapper Goal (`MinionSapperGoal`)

`MinionSapperGoal` executes at Priority 3 in `MinionEntity.initGoals()`.

#### Obstacle Detection Engines

1. **Ravine & Chasm Bridging (`detectRavineGap`)**:
   - Evaluates a position $1.2\text{--}2.0$ blocks forward along the horizontal heading vector $(\hat{d}_x, \hat{d}_z)$ towards the minion's active destination (combat target, waypoint, or owner).
   - If the forward block is passable and both $\text{pos}.\text{down}(1)$ and $\text{pos}.\text{down}(2)$ are hazard drops (air, replaceable, water, or lava), a ravine is confirmed.
   - Traces horizontal bridge coordinates step-by-step up to `MAX_BRIDGE_SPAN = 6` blocks until solid, walkable ground is found on the opposite rim:
     $$\text{Pos}_{\text{bridge}}(s) = \left\lfloor x_{\text{feet}} + 0.5 + \hat{d}_x \cdot s \right\rfloor, y_{\text{feet}}, \left\lfloor z_{\text{feet}} + 0.5 + \hat{d}_z \cdot s \right\rfloor$$

2. **Cliff & Mountain Ascent (`detectCliffAscent`)**:
   - Evaluates solid obstacles $1.0\text{--}1.5$ blocks ahead rising $> 1.0625$ blocks (`STEP_HEIGHT`).
   - Scans upward from $h = 2$ to `MAX_CLIFF_HEIGHT = 6` blocks for a walkable landing ledge (solid footing block plus 2 blocks of clear vertical headroom).
   - Generates vertical climbing shaft coordinates in the open air column immediately adjacent to the cliff face:
     $$\text{Pos}_{\text{column}}(y) = (x_{\text{base}}, y, z_{\text{base}}) \quad \text{for } y \in [y_{\text{feet}}, y_{\text{ledge}} - 1]$$

#### Combat Sapper Role Dynamics & Squad Delegation

- **Builder Archetype**: Thralls with `MinionRole.BUILDER` act as squad combat sappers, authorized to place traversal scaffolding at **zero resource cost** (`canRoleBuildZeroCost`).
- **Standard Thralls (`WARRIOR`, `SENTINEL`, `MINER`, `RANGER`)**:
  - Check their 9-slot inventory for `Items.SCAFFOLDING` and consume 1 block per placement.
  - If no scaffolding is carried, the thrall initiates a **Squad Sapper Request**:
    - Scans for an allied `BUILDER` within $24.0$ blocks (`SQUAD_SIGNAL_RADIUS`) matching the squad channel filter.
    - Registers a `SapperRequest` in the shared sapper registry (`ACTIVE_REQUESTS`).
    - Emits `HAPPY_VILLAGER` particles and chime audio.
    - The nearby builder rushes to the obstacle, deploys the bridge or climbing shaft, and releases the waiting ally.
    - Requests automatically time out after 200 ticks (`SIGNAL_TIMEOUT_TICKS`) if unfulfilled.

#### Vertical Climbing State Machine & Physics

When ascending a climbing column:

1. `minion.setClimbingScaffolding(true)` activates custom climbing logic in `MinionEntity.isClimbing()`.
2. Movement navigation is paused (`navigation.stop()`).
3. Physics velocity is applied every tick:
   $$\vec{v} = (\Delta x_{\text{align}} \cdot 0.25, +0.24\text{D}, \Delta z_{\text{align}} \cdot 0.25)$$
   centering the minion inside the column while ascending at a controlled rate.
4. `BLOCK_SCAFFOLDING_STEP` audio plays every 6 ticks.
5. Upon reaching the target ledge elevation $y \ge y_{\text{top}}$, the minion dismounts onto the ledge surface, resets climbing and jumping flags, and calls `navigation.recalculatePath()`.

---

## 23. Structure Deconstruction & Dismantling Mode (`SessionMode.DISMANTLE`)

Phase 3 introduces automated, reverse-topological structure deconstruction to the multiblock architecture. Commanders can order their builder and miner thralls to systematically dismantle active blueprints and erected fortifications, recovering building materials while leaving zero architectural debris.

```
                           [Deconstruction Order]
                       (Shift+Right-Click: BUILD Mode)
                        (Right-Click: MINE Mode)
                                   │
                                   ▼
                       [ConstructionSession]
                    • Mode: SessionMode.DISMANTLE
                    • Tasks: Reversed Topological Order
                    • Roof / Apex First -> Foundations Last
                                   │
         ┌─────────────────────────┴─────────────────────────┐
         ▼                                                   ▼
[Role Eligibility Check]                            [Dynamic Tool Equipping]
 • BUILDER: Eligible                                • Stone / Deepslate -> Pickaxe
 • MINER: Eligible (Demolition Spec)                • Dirt / Sand       -> Shovel
 • WARRIOR / SENTINEL / RANGER: Excluded            • Logs / Planks     -> Axe
                                   │
                                   ▼
                       [Task Prerequisite Check]
          1. Any non-hanging block resting above dismantled first
          2. Any hanging decoration underneath dismantled first
          3. Higher effective Y cleared before lower
          4. Same layer: hanging items dismantled before ceiling
                                   │
                                   ▼
                       [Demolition & Salvage]
          • Swing mainhand with resolved tool
          • BlockStateParticleEffect & break audio
          • Block.dropStacks (item salvage in survival)
          • serverWorld.setBlockState(targetPos, AIR)
          • Progressive scaffolding teardown on descent
```

### 1. Reverse Topological Task Ordering & Dependency Graph

In naive deconstruction systems, removing lower foundation blocks first causes gravity-affected blocks to fall or leaves upper tiers, parapets, and hanging lanterns floating unnaturally in midair.

The deconstruction engine guarantees deterministic top-down demolition:

1. **Reversed Task Ordering**:
   - The session instantiates `tasks` in the exact reverse of bottom-up construction order:
     $$\text{Task Order}_{\text{dismantle}} = \text{reverse}(\text{Task Order}_{\text{build}})$$
   - The highest architectural blocks (roof apex, battlements, observation parapets) are assigned lowest task IDs and evaluated first.
2. **Topological Prerequisite Invariants (`isDismantleTaskReady`)**:
   - **Overhead Block Protection**: If a block is not hanging, any non-hanging block resting directly above it ($\text{pos}.\text{up}()$) must be dismantled first.
   - **Hanging Item Clearance**: Any hanging decoration attached underneath ($\text{pos}.\text{down}()$, such as lanterns) must be dismantled before the ceiling block can be cleared.
   - **Vertical Layer Monotonicity**: All tasks on higher effective Y coordinates ($\text{effectiveY} = y + (\text{isHanging} ? 1 : 0)$) must be completed before lower tasks can be claimed.
   - **Intra-Layer Precedence**: On the same effective Y coordinate, hanging blocks are cleared before non-hanging ceiling blocks.

### 2. Dual-Role Participation: Builders & Miners

To give the `MINER` archetype high utility alongside `BUILDER` units, the task distribution filter dynamically adapts based on session mode:

| Role Archetype | `SessionMode.BUILD` Eligibility | `SessionMode.DISMANTLE` Eligibility  |
| :------------- | :------------------------------ | :----------------------------------- |
| `BUILDER`      | **Eligible**                    | **Eligible**                         |
| `MINER`        | _Excluded_                      | **Eligible** (Demolition Specialist) |
| `WARRIOR`      | _Excluded_                      | _Excluded_                           |
| `SENTINEL`     | _Excluded_                      | _Excluded_                           |
| `RANGER`       | _Excluded_                      | _Excluded_                           |

### 3. Dynamic Tool Equipping & Resource Salvage (`resolveDismantleTool`)

Before breaking a block, the minion inspects the target block's material properties and equips an optimal harvesting tool:

```java
public static ItemStack resolveDismantleTool(BlockState state) {
    if (state.isIn(BlockTags.PICKAXE_MINEABLE) || state.isOf(Blocks.STONE)
            || state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.DEEPSLATE_BRICKS)
            || state.isOf(Blocks.POLISHED_DEEPSLATE) || state.isOf(Blocks.OBSIDIAN)
            || state.isOf(Blocks.CRYING_OBSIDIAN) || state.isOf(Blocks.LANTERN)
            || state.isOf(Blocks.SOUL_LANTERN) || state.isOf(Blocks.IRON_BARS)) {
        return new ItemStack(Items.IRON_PICKAXE);
    }
    if (state.isIn(BlockTags.SHOVEL_MINEABLE)) {
        return new ItemStack(Items.IRON_SHOVEL);
    }
    if (state.isIn(BlockTags.AXE_MINEABLE) || state.isOf(Blocks.DARK_OAK_LOG)
            || state.isOf(Blocks.DARK_OAK_PLANKS) || state.isOf(Blocks.DARK_OAK_DOOR)) {
        return new ItemStack(Items.IRON_AXE);
    }
    return new ItemStack(Items.IRON_PICKAXE);
}
```

- **Survival Resource Recovery**: In non-creative survival worlds, `Block.dropStacks(targetState, world, targetPos, be, minion, heldTool)` is invoked immediately before block removal, dropping authentic item drops onto the ground for collection.
- **Weapon Preservation**: The minion's original combat weapon is safely preserved in memory and restored the moment deconstruction work concludes.

### 4. Progressive Scaffolding Teardown on Descent

When dismantling tall watchtowers or high obelisks, minions deploy scaffolding columns to ascend to the roof. As upper layers are demolished:

1. When navigating down the column, scaffolding blocks situated above the minion's current height ($y > \text{minionBlockY} + 1$) are dismantled automatically.
2. Upon reaching the ground, any remaining temporary scaffolding blocks in that column are stripped and cleared with breaking particles and audio.
3. This ensures no stray scaffolding columns remain standing after a structure has been dismantled.

### 5. Demolition Hologram Projections & Ceremonies

- **Fiery Hologram Outline**: Every 20 ticks (1 second), the `ConstructionManager` renders deconstruction boundary auras:
  - Anchor point emits rising flame particles (`ParticleTypes.FLAME`).
  - 8 bounding-box corners project `ParticleTypes.SMALL_FLAME`.
  - Actively claimed demolition blocks sparkle with `ParticleTypes.CRIT` sparks.
- **Completion Ceremony**:
  - When the final foundation block is cleared, the manager triggers anvil/golem breakdown audio (`SoundEvents.ENTITY_IRON_GOLEM_DAMAGE` and `SoundEvents.ENTITY_PLAYER_LEVELUP`).
  - Spawns dense `ParticleTypes.EXPLOSION` and `ParticleTypes.SMOKE` clouds across the demolished footprint.
  - Clears all tracked temporary scaffolding in the session and sends the commander an actionbar confirmation:
    `"§6✔ Deconstruction Complete! §f<Blueprint> §6has been completely dismantled by your minions!§r"`.

---

## 24. RTS Minion Selection, Selective Ground Waypoints & Decoupled Guard Stance

Phase 2 elevates the Loki Command Scepter into a precision real-time strategy (RTS) battlefield controller. Commanders can select individual squads or units to reposition across the battlefield, while unselected units stand guard at attention without being forced into an unnatural sitting pose.

### 1. Selective Ground Waypoint Directives

Previously, right-clicking the ground dispatched a ground waypoint ping that rallied _all_ nearby matching minions, inadvertently dislodging garrisoned or defensive units.

Now, ground waypoint pings route exclusively to active, selected units:

- **Predicate**: `m.isAlive() && m.isOwner(player) && m.isSelected() && filterSquad.matches(m.getSquad())`.
- **Targeted Dispatch**: Only minions with `isSelected() == true` sprint to the waypoint beacon and secure the perimeter.
- **Fail-Safe Feedback**: If the commander issues a ground ping with zero matching units selected, the system plays an informative fail tone (`SoundEvents.BLOCK_CHEST_LOCKED`) and renders an actionable actionbar hint:
  `"§e⚠ No minions selected! Right-click a minion to select, or use Banner of Courage."`

### 2. Direct Unit Selection & Deselection Mechanics

Commanders can intuitively manage unit selection through multiple in-world and interface workflows:

- **Point-and-Click Direct Selection**:
  - Right-clicking an owned minion (with empty hand or Command Scepter) or quick-tapping via 32-block crosshair raycasting toggles its selection status.
  - **Select Feedback**: Plays a high-pitched chime (`SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME`), spawns `ParticleTypes.HEART` particles, marks the unit `setSelected(true)`, and orders it to follow.
  - **Deselect Feedback**: Plays a grounded bass note (`SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM`), spawns `ParticleTypes.SMOKE` puffs, marks the unit `setSelected(false)`, and sets its `guardAnchorPos` to its current position.
- **Deselect All Shortcuts**:
  - **Command Hub GUI**: The GUI displays a live counter of nearby selected units (`Selected: X`) and provides a prominent **§e✕ Deselect** button in the bottom action tray that dispatches `DeselectMinionsPayload`.
  - **In-World Scepter Shortcut**: Sneak + Left-Click with the Command Scepter in hand (in non-`BUILD` modes) instantly deselects all active minions within 32 blocks and anchors them at their current posts.

### 3. Decoupling Guard Stance from Forced Sitting

In vanilla `TameableEntity` mechanics, keeping a pet stationary forces `isSitting() == true`, causing minions to sit on the ground awkwardly even when on combat guard duty.

The new architecture cleanly decouples stationary positioning from sitting postures:

- **Standing Sentinel Posture**: Deselected units have `isSelected() == false` and `guardAnchorPos != null`, but `isSitting() == false`. They stand proudly at attention like Roman legionary sentinels.
- **Goal Gating**:
  - `MinionFormationFollowGoal`: Hard-gated with `if (!this.minion.isSelected()) return false;`. Unselected minions never break rank to follow the player.
  - `WanderAroundFarGoal`: Gated so units with `getGuardAnchorPos() != null` or `isSelected() == true` do not wander away from their designated posts.
  - `WaypointHoldGoal` & `SentinelGuardGoal`: Continuously enforce the stationary anchor position, returning wandering or combat units to their post once threats are neutralized.

### 4. Squad-Colored Glowing Outlines & Overhead Indicators

Selection state provides immediate, high-fidelity visual feedback across all distances:

- **Zero-Packet Glowing Silhouette**:
  - `MinionEntity` overrides `isGlowing()` to return `this.isSelected() || super.isGlowing()`.
  - When selected, the vanilla Minecraft glowing outline shader is activated locally for that unit without consuming network bandwidth.
- **Squad Color Tinting**:
  - `MinionEntity` overrides `getTeamColorValue()` to return `this.getSquad().getOutlineColor()`:
    - **Squad Alpha**: Crimson Red (`0xE74C3C`)
    - **Squad Bravo**: Azure Blue (`0x3498DB`)
    - **Squad Charlie**: Emerald Green (`0x2ECC71`)
    - **Squad Delta**: Amber Gold (`0xF39C12`)
    - **Squad All**: Pure White (`0xFFFFFF`)
- **Overhead Gold Star Indicator**:
  - Selected units render a shimmering gold star prefix (`§6★ `) on their overhead billboard squad banner (e.g. `§6★ §c⚑ SQUAD ALPHA [I]`), immediately signaling active commander control from afar.

---

## 25. Formation Yaw Anchoring, Rank Resolution & Two-Pass Badge Rendering

This section documents the tactical refinement addressing kinematically coupled formation stations and overhead badge visual fidelity.

```
                           [Player Movement & Gaze]
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
     [Stationary Look Sweep]                        [Deliberate Displacement]
     (dx^2 + dz^2 <= 0.04)                          (dx^2 + dz^2 > 0.04)
              │                                               │
              ▼                                               ▼
     [FormationAnchor Frozen]                       [FormationAnchor Unlocked]
      Station coordinates held                       Anchor updates (x, z)
      Minions hold station; zero orbit               Formation yaw snaps to gaze
              │                                               │
              └───────────────────────┬───────────────────────┘
                                      ▼
                        [Rank Resolution Filter]
             Only m.isSelected() && !m.isHoldingPosition()
             Stationary sentinels excluded; active cohort collapses
                                      │
                                      ▼
                      [Two-Pass Badge Rendering]
           Pass 1: SEE_THROUGH translucent base + background
           Pass 2: NORMAL crisp fullbright foreground (!sneaking)
           Head clearance: +0.55F (+0.85F custom named)
```

### 1. Kinematic Decoupling & Formation Yaw Anchoring (`MinionFormationFollowGoal`)

#### The Problem

In earlier iterations, `calculateFormationStation` evaluated parametric station positions directly against the commander's instantaneous view yaw (`owner.getYaw()`). Whenever a player stood stationary on a vantage point and scanned the horizon or swept their crosshairs 360°, the entire formation's station offsets revolved along a circle around the player. This forced following minions to constantly sprint in circles or frantically reposition, causing visual chaos and breaking tactical cohesion.

#### The Solution: Spatial Hysteresis & FormationAnchor

`MinionFormationFollowGoal` implements a decoupled anchoring state machine via a static `FormationAnchor` registry keyed by commander UUID:

1. **State Invariant**: Each anchor stores `(double lastX, double lastZ, float anchoredYaw)`.
2. **Hysteresis Threshold (`MOVEMENT_HYSTERESIS_THRESHOLD_SQ = 0.04D`)**:
   - Represents a horizontal displacement radius of $0.20$ blocks:
     $$\Delta r^2 = (x_{\text{owner}} - x_{\text{anchor}})^2 + (z_{\text{owner}} - z_{\text{anchor}})^2$$
   - **Stationary Sweep**: If $\Delta r^2 \le 0.04$, the commander is considered stationary. `getFormationYaw` returns `anchor.getAnchoredYaw()`, completely freezing the formation stations. Minions hold their ground calmly while the commander looks in any direction.
   - **Deliberate Movement**: If $\Delta r^2 > 0.04$, the commander has deliberately marched or sprinted. The anchor updates its spatial coordinates and snaps `anchoredYaw` to the player's new movement yaw.
3. **Instant Re-alignment Directives (`refreshFormationAnchor`)**:
   - When a commander issues an explicit directive (`broadcastFollow` via the Command Scepter or releases the Banner of Courage rally ring), `MinionFormationFollowGoal.refreshFormationAnchor(player)` immediately snaps `anchoredYaw` to the commander's current gaze, instantly aligning the army's formation heading before movement begins.
4. **Station Calculation Alignment**:
   - `calculateFormationStation(LivingEntity owner, ...)` resolves the effective yaw via `getFormationYaw(owner)` rather than `owner.getYaw()`, guaranteeing kinematic decoupling across all AI navigation calls.

### 2. Intra-Role Rank Resolution Filtering (`resolveRank`)

#### The Problem

Previously, `resolveRank` sorted all owned living minions within proximity to determine station indices ($0, 1, 2, \dots$). Unselected sentinels holding static guard posts or sitting thralls were included in the rank count. If two vanguard warriors were left on guard duty at a gate, the two following warriors would be assigned Rank 2 and Rank 3, leaving Rank 0 and Rank 1 vacant and causing gaping holes at the front of the wedge.

#### The Solution: Active Cohort Eligibility

`resolveRank` now filters the cohort using `isEligibleForFormationRank`:

```java
m -> m.isAlive()
    && m.isTamed()
    && m.isOwner(owner)
    && m.isSelected()
    && !m.isHoldingPosition()
    && m.getGuardAnchorPos() == null
```

- **Stationary Unit Exclusion**: Minions with `!isSelected()`, sitting thralls, and units with an active `guardAnchorPos` are completely excluded from rank resolution.
- **Dynamic Rank Collapse**: When units are deselected to guard an area, remaining followers immediately collapse into lead ranks (Rank 0, Rank 1), maintaining tight, complete tactical formations without manual re-assignment.

### 3. Two-Pass Billboard Nametag Rendering Pipeline (`MinionOverheadBadgeFeatureRenderer`)

#### The Problem

Earlier badge implementations used a single-pass `TextRenderer.TextLayerType.NORMAL` draw call with standard nametag clearance (+0.20F standing, +0.45F named). This introduced two visual defects:

1. **Mesh Occlusion / Z-Clipping**: With armor helmets or player skull models equipped, the badge's bottom text line frequently clipped into helmet crests and horns.
2. **Loss of Occlusion Signaling**: Vanilla Minecraft nametags render through walls as translucent text plates, allowing players to track allies through terrain. A single `NORMAL` pass rendered nothing behind walls.

#### The Solution: Elevated Clearance & Two-Pass Rendering

1. **Elevated Head Clearance (`getOverheadYTranslation`)**:

   ```java
   float headClearance = hasCustomName ? 0.85F : 0.55F;
   if (isSneaking) headClearance -= 0.20F;
   return height + headClearance;
   ```

   - Standard clearance is elevated from $0.20\text{F}$ to **$0.55\text{F}$** above entity height ($1.95\text{F} \rightarrow 2.50\text{F}$ total).
   - Named clearance is elevated from $0.45\text{F}$ to **$0.85\text{F}$** ($2.80\text{F}$ total).
   - Crouching/sneaking deducts $-0.20\text{F}$, matching the lowered biped head height.
   - Completely eliminates mesh z-fighting and geometry clipping across all helmet models.

2. **Vanilla Two-Pass Nametag Pipeline**:
   - Matches `EntityRenderer.renderLabelIfPresent`:
     - **Pass 1 (Translucent Base & Background)**:
       ```java
       this.textRenderer.draw(squadBannerText, -this.textRenderer.getWidth(squadBannerText) / 2.0F,
           -LINE_SPACING, 553648127, false, matrix4f, vertexConsumers,
           TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, light);
       this.textRenderer.draw(roleCrestText, -this.textRenderer.getWidth(roleCrestText) / 2.0F,
           0.0F, 553648127, false, matrix4f, vertexConsumers,
           TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, light);
       ```
       Color `553648127` (`0x21FFFFFF`) renders translucent lettering accompanied by the dark background plate. Visible through terrain and obstacles.
     - **Pass 2 (Crisp Foreground Fullbright)**:
       ```java
       if (!entity.isInSneakingPose()) {
           this.textRenderer.draw(squadBannerText, -this.textRenderer.getWidth(squadBannerText) / 2.0F,
               -LINE_SPACING, -1, false, matrix4f, vertexConsumers,
               TextRenderer.TextLayerType.NORMAL, 0, light);
           this.textRenderer.draw(roleCrestText, -this.textRenderer.getWidth(roleCrestText) / 2.0F,
               0.0F, -1, false, matrix4f, vertexConsumers,
               TextRenderer.TextLayerType.NORMAL, 0, light);
       }
       ```
       When the entity is not sneaking, Pass 2 draws solid white (`-1` / `0xFFFFFFFF`) with `backgroundColor = 0`, providing crisp, anti-aliased foreground text that depth-tests against visible geometry.
     - **Sneaking Posture**: When sneaking, Pass 2 is skipped, leaving only the dim translucent text to respect vanilla stealth mechanics.

---

## 26. Architectural Scaffolding Navigation, Platform Kinematics & Multi-Minion Coordination

This section documents the comprehensive architectural overhaul resolving building minion scaffolding mechanics, climbing physics, elevated task chaining, and multi-minion construction coordination.

```
                         [Elevated Construction / Dismantle Task]
                                            │
               ┌────────────────────────────┴────────────────────────────┐
               ▼                                                         ▼
    [Standing on Platform?]                                    [At Ground Level]
     Horizontal distSq <= 16.0                                  • Exterior Perimeter Projection
     Vertical diff <= 2.5                                         (minX-1, maxX+1, minZ-1, maxZ+1)
               │                                                • Eliminated North Bias
               ▼                                                  (distTarget + 0.5 * distMinion)
    [Elevated Task Chaining]                                    • Overhead Clearance Scan (Y+2)
     • Stay elevated on platform                                • Column Reservation (claimScaffoldColumn)
     • Equip next item/tool                                              │
     • Zero descent / zero pathfinding                                   ▼
               │                                            [Kinematic Platform Ascent]
               │                                             • v = (dx*0.3, +0.25D, dz*0.3)
               │                                             • Head clearance & stall sensors (>10t)
               │                                             • Landing snap @ topY + 1.0D
               │                                             • Zero velocity & clear climb flags
               │                                                         │
               └────────────────────────────┬────────────────────────────┘
                                            ▼
                               [Execute Block Placement / Dismantle]
                                            │
                                            ▼
                              [Task Completed -> Next Task?]
                                            │
               ┌────────────────────────────┴────────────────────────────┐
               ▼                                                         ▼
     [Adjacent Elevated Task]                                  [Demobilization / Descent]
      Chain on platform                                         • Teardown top-to-bottom on way down
      (Loop back to Chaining)                                   • Sweep lingering scaffolding (clearScaffolding)
                                                                • Release column reservation
```

### 1. Architectural Problem & Root Cause Breakdown

In earlier iterations, minion construction AI suffered from critical navigation and coordination failures during multi-level structure erection and deconstruction:

1. **Premature Fall & Scaffolding Spam**:
   - In `MinionBuildGoal.setupNavigationForTask()`, whenever `diffY <= 2` occurred immediately after placing an elevated block (because the minion was already standing on the platform), `activeScaffoldColumn` and `targetScaffoldTopY` were cleared, and ground-level navigation was commanded.
   - The minion walked off the edge into empty air, plummeted to the ground, and immediately deployed a brand-new scaffolding column.
   - Furthermore, reach checks (`inRange`) in `tick()` executed _before_ the climb state completed, aborting the ascent mid-ladder whenever reach conditions became momentarily valid.
2. **Fixed Directional Bias ("North Spam")**:
   - `findScaffoldColumn()` iterated cardinal directions in fixed sequence (`NORTH, SOUTH, EAST, WEST`).
   - If the North side was clear, every builder chose the North column regardless of where the target block or minion was situated, leaving South and East walls unworked while minions queued single-file on North ladders.
3. **Ceiling Entrapment & Overhang Collisions**:
   - Columns were deployed directly adjacent to target blocks beneath structure overhangs (e.g. Overlord Watchtower Layer 4 inverted stone brick arches and Layer 5 observation decks).
   - Scans only checked blueprint tasks up to `targetY - 1`, completely missing scheduled upper-layer overhangs and trapping minions inside solid ceilings.
4. **Mid-Climb Stalls & Integer Snapping**:
   - Climbing ascent terminated at `minion.getY() >= targetScaffoldTopY`, snapping the minion to integer `targetScaffoldTopY` (inside the scaffolding block mesh) rather than the standing surface at `targetScaffoldTopY + 1.0D`.
5. **Combat Sapper Goal Preemption**:
   - `MinionSapperGoal` ran at Priority 2 while `MinionBuildGoal` ran at Priority 3.
   - When near structures, `MinionSapperGoal` interpreted sheer structure walls as natural cliffs, preempting the builder AI and deploying erratic traversal bridges against the building.

---

### 2. True Kinematic Platform Landing & Ascent Lock

To guarantee smooth, deterministic vertical ascension without ladder stalling or premature ground fallbacks, the ascent engine was completely re-architected in `MinionBuildGoal`:

1. **Pre-Reach Ascent Execution**:
   - Vertical climbing is processed at the very beginning of `MinionBuildGoal.tick()` _before_ distance-to-target or reach checks are evaluated:
     ```java
     if (this.isAscendingScaffolding) {
         this.climbTicks++;
         this.minion.getNavigation().stop();
         this.minion.fallDistance = 0.0F;
         ...
         return; // Freeze reach checks and lateral pathfinding until landed!
     }
     ```
2. **Centering Lock & Velocity Impulse**:
   - Applies an upward velocity of $+0.25\text{D}$ coupled with a proportional horizontal centering vector:
     $$\vec{v}_x = (x_{\text{center}} - x_{\text{minion}}) \cdot 0.3\text{D}, \quad \vec{v}_y = +0.25\text{D}, \quad \vec{v}_z = (z_{\text{center}} - z_{\text{minion}}) \cdot 0.3\text{D}$$
   - Locks the minion strictly to the column center axis, preventing horizontal drift off the ladder rungs.
   - Plays `SoundEvents.BLOCK_SCAFFOLDING_STEP` audio and emits `ParticleTypes.CLOUD` puffs every 8 ticks.
3. **Platform Surface Snapping**:
   - The minion ascends until its feet cross the landing threshold at $y \ge \text{targetScaffoldTopY} + 0.95\text{D}$.
   - The minion then cleanly snaps to the standing surface atop the scaffolding block:
     ```java
     this.minion.setPosition(scCenterX, (double) this.targetScaffoldTopY + 1.0D, scCenterZ);
     this.minion.setVelocity(0.0D, 0.0D, 0.0D);
     this.minion.velocityModified = true;
     this.minion.fallDistance = 0.0F;
     this.minion.setJumping(false);
     this.minion.setClimbingScaffolding(false);
     this.isAscendingScaffolding = false;
     ```
   - Zeroes vertical and horizontal velocity, clears climbing flags, and completes arrival before block placement begins.
4. **Ceiling Collision & Stall Sensors**:
   - **Headroom Sensor**: Scans overhead blocks at $y_{\text{feet}} + 2.0\text{D}$. If a solid block obstructs the ascent path, climbing aborts immediately.
   - **Displacement Stall Detector**: Monitors vertical displacement. If $y \le y_{\text{last}} + 0.05\text{D}$ for $>10$ consecutive ticks or total climbing exceeds $140$ ticks, climbing aborts.
   - **Safe Recovery**: Upon abort, the column coordinate is blacklisted to prevent re-selection loops, reservations are released, and the minion initiates a controlled descent to ground.

---

### 3. Elevated Task Chaining (`isTaskReachableFromPlatform`)

Minions standing on an elevated scaffolding platform can now consecutively place or dismantle multiple adjacent structure blocks without descending to ground level:

1. **Platform Reach Criterion**:

   ```java
   public boolean isTaskReachableFromPlatform(ConstructionTask task) {
       if (task == null || this.activeScaffoldColumn == null || this.targetScaffoldTopY == -1) {
           return false;
       }
       double platformCenterX = this.activeScaffoldColumn.getX() + 0.5D;
       double platformCenterZ = this.activeScaffoldColumn.getZ() + 0.5D;
       double platformStandingY = (double) this.targetScaffoldTopY + 1.0D;

       BlockPos targetPos = task.getWorldPos();
       double dx = (targetPos.getX() + 0.5D) - platformCenterX;
       double dz = (targetPos.getZ() + 0.5D) - platformCenterZ;
       double horizontalDistSq = dx * dx + dz * dz;
       double verticalDiff = Math.abs(platformStandingY - (double) targetPos.getY());

       return horizontalDistSq <= 16.0D && verticalDiff <= 2.5D;
   }
   ```

2. **Persistent Platform Stance**:
   - When a block placement completes, the minion queries the next ready task in topological order.
   - If `isTaskReachableFromPlatform(nextTask)` is true:
     - The minion remains standing elevated at $(x_{\text{platform}}, y_{\text{top}} + 1.0\text{D}, z_{\text{platform}})$.
     - `activeScaffoldColumn` and `targetScaffoldTopY` remain intact.
     - Equips the required item/tool, resets task ticks to 0, and continues work immediately.
   - If the next task is out of horizontal reach ($>4.0$ blocks) or vertical reach ($>2.5$ blocks), the minion initiates an orderly descent, demobilizing temporary scaffolding nodes if no more elevated work remains.

---

### 4. Exterior Perimeter Column Allocation & Elimination of North Bias

To prevent scaffolding from being erected inside rooms, under arches, or concentrated exclusively on the North wall, `findScaffoldColumn` implements exterior perimeter projection:

1. **Perimeter Projection Coordinates**:
   - Projects candidate columns 1 block outside the structure's world bounding box:
     $$\text{North Face}: z = \min Z - 1, \quad x \in [\min X - 1, \max X + 1]$$
     $$\text{South Face}: z = \max Z + 1, \quad x \in [\min X - 1, \max X + 1]$$
     $$\text{West Face}: x = \min X - 1, \quad z \in [\min Z, \max Z]$$
     $$\text{East Face}: x = \max X + 1, \quad z \in [\min Z, \max Z]$$
2. **Distance-Weighted Scoring (Eliminating North Bias)**:
   - Candidates within horizontal reach ($\text{distSq} \le 16.0\text{D}$) are sorted by combined Euclidean distance:
     $$\text{Score} = \text{distToTarget} + 0.5 \times \text{distToMinion}$$
   - The column closest to both the target block and the approaching minion is selected first. This completely eliminates fixed-order North bias and naturally distributes builders around the perimeter.
3. **Full Vertical Clearance Verification**:
   - The candidate column must be completely unobstructed from ground up through $\text{targetY} + 2$ in both:
     - Current world block states (non-solid, air/scaffolding).
     - Future scheduled blueprint tasks (ensuring upper overhangs, roofs, and inverted stairs do not clip the column).

---

### 5. Multi-Minion Scaffolding Column Reservation (`ConstructionSession`)

To prevent multiple builders from pathfinding to the same scaffolding column, stacking inside each other, or knocking comrades off platforms, `ConstructionSession` manages column reservations:

```java
public synchronized boolean claimScaffoldColumn(BlockPos pos, UUID minionUuid);
public synchronized boolean releaseScaffoldColumn(BlockPos pos, UUID minionUuid);
public synchronized boolean isScaffoldColumnAvailable(BlockPos pos, UUID minionUuid);
public synchronized boolean isScaffoldColumnClaimed(BlockPos pos);
public synchronized UUID getScaffoldColumnClaimant(BlockPos pos);
public synchronized void releaseScaffoldColumnsForMinion(UUID minionUuid);
public synchronized boolean isScaffoldColumnClaimedBy(UUID minionUuid);
public Map<BlockPos, UUID> getClaimedScaffoldColumns();
```

- **Vertical Column Invariance**: Reservations match horizontal $(X, Z)$ coordinates regardless of $Y$ elevation. A minion claiming $(10, 64, 20)$ reserves the entire vertical shaft $(10, *, 20)$.
- **Engagement Invariant (`isMinionEngaged`)**: A minion is considered actively engaged in construction if it holds an active task claim _or_ a reserved scaffolding column. This prevents premature goal preemption between chained tasks.
- **Stale Claim Pruning**: `cleanStaleClaims(currentTick, 300L)` prunes orphaned column claims held by minions with no remaining active tasks, freeing columns if a minion disconnects or dies.
- **Session Cleanup**: `cancel()` and `clearScaffolding()` automatically flush all column reservations.

---

### 6. Combat Sapper Goal Suppression During Construction

To prevent `MinionSapperGoal` (Priority 2) from interpreting building walls as natural cliffs and interrupting `MinionBuildGoal` (Priority 3):

1. **Suppression Gating (`isSuppressedByConstruction`)**:
   ```java
   public boolean isSuppressedByConstruction() {
       MinionRole role = this.minion.getRole();
       if (role == MinionRole.BUILDER || role == MinionRole.MINER) {
           return ConstructionManager.getInstance().isMinionEngagedInConstruction(this.minion);
       }
       return false;
   }
   ```
2. **Proximity & Session Filtering (`isMinionEngagedInConstruction`)**:
   - `ConstructionManager` evaluates whether the minion holds an active task/column claim, or is situated within $48.0$ blocks of an active compatible session (`BUILD` or `DISMANTLE` for builders; `DISMANTLE` for miners).
   - Distance is measured against both the session anchor and the expanded world bounding box.
   - Suppresses `MinionSapperGoal.canStart()` and `shouldContinue()`, allowing `MinionBuildGoal` to manage all structure navigation and scaffolding deployment.
   - Combat archetypes (`WARRIOR`, `SENTINEL`, `RANGER`) are never suppressed, retaining full combat sapper capabilities.
3. **Squad Sapper Assistance Exclusion**:
   - When non-builder minions call for sapper assistance (`findNearbySquadBuilder`), builders engaged in or situated near active construction sites are excluded (`!isMinionEngagedInConstruction(m)`).
   - Builders building towers are never pulled away from their construction sites to build distant traversal bridges.

---

### 7. Top-to-Bottom Scaffolding Demobilization Teardown

To ensure temporary scaffolding does not pollute the game world after architectural work concludes:

1. **Controlled Descent Teardown**:
   - In `DISMANTLE` mode, or upon completing all session tasks (`demobilization`) in `BUILD` mode, descending minions dismantle scaffolding nodes top-to-bottom on the way down:
     $$\text{Scaffolding at } y > y_{\text{minion}} + 1 \implies \text{dismantleWithFeedback()}$$
   - Plays `SoundEvents.BLOCK_SCAFFOLDING_BREAK` and emits 6 `BlockStateParticleEffect` breaking particles per block.
2. **Session Completion Sweep (`clearScaffolding`)**:
   - When all tasks in a session complete or the session is cancelled, `ConstructionSession.clearScaffolding(ServerWorld)` sweeps any lingering scaffolding blocks in `temporaryScaffolding`.
   - Before removing blocks, safely grounds any minions standing on temporary scaffolding, setting `fallDistance = 0.0F` and snapping them to solid ground below.
   - Emits breaking particles and clears tracking sets, leaving a clean, pristine architectural site.

---

## 27. Shift-to-Close GUI Architecture, Open-State Guard & Fast Dismissals (`CommandScepterScreen`)

This section documents the ergonomics overhaul and state-machine transitions enabling seamless Shift-to-close interactions on the Loki Command Hub GUI without unintended immediate dismissals upon sneak-opening.

```
                    [Sneak + Right-Click Open]            [Press 'V' Hotkey Open]
                               │                                     │
                               ▼                                     ▼
                    [shiftHeldOnOpen = TRUE]              [shiftHeldOnOpen = FALSE]
                               │                                     │
            ┌──────────────────┴──────────────────┐                  │
            ▼                                     ▼                  │
    [Held Shift Repeats]                  [Shift Released]           │
   • Absorbed & suppressed            • keyReleased / tick / render  │
   • Screen remains open              • shiftHeldOnOpen -> FALSE     │
                                                  │                  │
                                                  └─────────┬────────┘
                                                            ▼
                                                [Subsequent Shift Press]
                                                  • Fast-dismiss GUI
                                                  • Zero latency close()
```

### 1. The Immediate-Dismissal Problem & Root Cause

The Loki Command Hub (`CommandScepterScreen`) can be opened through two gameplay paths:

1. **Physical Sneak + Right-Click**: Sneaking (`Shift`) while right-clicking with the Loki Command Scepter in hand (`CommandScepterItem.use`).
2. **Dedicated Hotkey**: Pressing the `[V]` keybind registered via Fabric KeyBinding API (`ExampleModClient.commandHubKey`).

When opened via Sneak + Right-Click, the player's physical finger is actively holding the `Left Shift` key down as the screen initializes. In a naive implementation where any Shift press closes the GUI:

- GLFW immediately sends key events or repeats for Shift into the newly mounted screen (`Screen.keyPressed`).
- The GUI would instantly close on the very frame it opened, causing a frustrating screen flicker.
- Furthermore, inventory screens (`MinionScreen`) rely on Shift for quick-moving items (Shift-click transfer), so Shift-to-close must be strictly constrained to non-container tactical HUD modals.

### 2. The Open-State Guard State Machine (`shiftHeldOnOpen`)

`CommandScepterScreen` implements a robust state machine with three core lifecycle flags:

- `shiftHeldOnOpen`: Set to `true` if Shift or Sneak was physically down when the screen was initialized.
- `initializedOpenState`: Latches initialization so window resizing does not re-arm the guard.
- `closed`: Tracks dismissal state cleanly for unit testing and lifecycle assertions.

#### State Transitions & Invariants:

1. **Screen Initialization (`init()`)**:

   ```java
   if (!this.initializedOpenState) {
       if (!this.shiftHeldOnOpen) {
           this.shiftHeldOnOpen = isShiftOrSneakDown();
       }
       this.initializedOpenState = true;
   }
   ```

   Physical key state is queried via `isShiftOrSneakDown()`. If physical `GLFW_KEY_LEFT_SHIFT` or `GLFW_KEY_RIGHT_SHIFT` is pressed (or sneak keybinding is active), `shiftHeldOnOpen` is armed.

2. **Hold & Repeat Suppression (`keyPressed`)**:

   ```java
   if (isShiftOrSneakKey(keyCode, scanCode)) {
       if (this.shiftHeldOnOpen) {
           // Suppress immediate close and absorb GLFW key repeats
           return true;
       }
       this.close();
       return true;
   }
   ```

   While `shiftHeldOnOpen` is `true`, key press and repeat events are safely consumed without closing the screen.

3. **Zero-Latency Release Transition (`keyReleased`, `tick`, `render`)**:
   - As soon as the player lifts their finger from Shift, `keyReleased` resets the guard:
     ```java
     if (isShiftOrSneakKey(keyCode, scanCode)) {
         this.shiftHeldOnOpen = false;
     }
     ```
   - To guarantee zero latency even if the release event was consumed by OS focus or missed between ticks, both `render()` (frame-rate synchronized, 60-240+ FPS) and `tick()` verify physical state:
     ```java
     if (this.shiftHeldOnOpen && !isShiftOrSneakDown()) {
         this.shiftHeldOnOpen = false;
     }
     ```
   - Once `shiftHeldOnOpen` transitions to `false`, the GUI is recognized as "already open". Any subsequent press of Shift (Left or Right) closes the modal immediately.

### 3. Multi-Key Dismissal Bindings (`Shift`, `V`, `E`, `Esc`)

To maximize commander ergonomics and muscle memory, `CommandScepterScreen` unifies all standard Minecraft dismissal triggers:

- **`Shift` (Left / Right / Sneak)**: Fast tap-to-close once already open.
- **`V` (Command Hub Toggle)**: Pressing `[V]` toggles the Command Hub open and closed seamlessly.
- **`E` (Inventory Key)**: Matches standard vanilla container habits (`client.options.inventoryKey`).
- **`Esc` (Escape)**: Delegated to `super.keyPressed(keyCode, scanCode, modifiers)`.

### 4. Visual Indicators & UX Visual Cues

To ensure the fast-close mechanic is discoverable and visually clear:

- **Header Fast-Close Badge**: Rendered on the header bar at `startX + WINDOW_WIDTH - shiftCloseWidth - 10, startY + 6`:
  `§e[Shift] §7Close`
- **Close Button Tooltip**: Close button widget configured with an informative multikey tooltip:
  `Close the Command Hub [Shift / Esc / E]`

### 5. Comprehensive Verification & Test Suite (`CommandScepterScreenCloseTest.java`)

To verify the open-state guard, key repeat filtering, zero-latency release transitions, multi-key dismissal routes, and state idempotency without requiring a live Minecraft client graphics context, `CommandScepterScreenCloseTest` provides 17 headless JUnit 5 tests utilizing LWJGL GLFW keycodes:

| Test Method                                              | Assertions & Verified State Machine Behavior                                                                                                                                                     |
| :------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `testShiftHeldOnOpenSuppressesImmediateClose`            | Confirms opening with Shift armed (`shiftHeldOnOpen = true`) consumes initial `GLFW_KEY_LEFT_SHIFT` press and sustained repeat events without prematurely closing modal (`isClosed() == false`). |
| `testRightShiftHeldOnOpenSuppressesImmediateClose`       | Validates `GLFW_KEY_RIGHT_SHIFT` press and sustained repeat events are safely consumed while the open guard is active.                                                                           |
| `testReleaseTransitionEnablesShiftClose`                 | Confirms physical release of Left Shift (`keyReleased`) transitions `shiftHeldOnOpen` to `false`, enabling immediate modal closure on the subsequent Left Shift press (`isClosed() == true`).    |
| `testRightShiftReleaseTransition`                        | Confirms physical release of Right Shift transitions `shiftHeldOnOpen` to `false`, and the next Right Shift press triggers immediate closure.                                                    |
| `testOpenWithoutShiftClosesImmediatelyOnShiftPress`      | Confirms opening via hotkey or unarmed state (`shiftHeldOnOpen = false`) closes the GUI immediately on the very first Left Shift press.                                                          |
| `testOpenWithoutShiftClosesImmediatelyOnRightShiftPress` | Confirms unarmed opening closes the GUI immediately on the very first Right Shift press.                                                                                                         |
| `testCommandHubKeyDismissalWhenShiftDisarmed`            | Confirms Command Hub hotkey (`GLFW_KEY_V`) closes screen immediately when shift guard is unarmed.                                                                                                |
| `testCommandHubKeyDismissalWhenShiftArmed`               | Validates `GLFW_KEY_V` dismisses GUI immediately even when `shiftHeldOnOpen` was actively armed during sneak-open.                                                                               |
| `testInventoryKeyDismissalWhenShiftDisarmed`             | Confirms standard Inventory hotkey (`GLFW_KEY_E`) closes modal immediately when shift guard is unarmed.                                                                                          |
| `testInventoryKeyDismissalWhenShiftArmed`                | Validates `GLFW_KEY_E` dismisses GUI immediately even when `shiftHeldOnOpen` was actively armed.                                                                                                 |
| `testEscapeKeyDismissal`                                 | Validates vanilla `GLFW_KEY_ESCAPE` delegates to `super.keyPressed` and reliably closes the modal regardless of shift guard state.                                                               |
| `testNonClosingKeysDoNotDismiss`                         | Asserts standard non-closing keys (`WASD`, `Space`, `Enter`, `1`, `B`, `C`, `F`, `R`, `X`) do not close the screen or corrupt state.                                                             |
| `testZeroLatencyTransitionInTick`                        | Validates `tick()` lifecycle fallback clears `shiftHeldOnOpen` once physical Shift is no longer held down in the client window.                                                                  |
| `testInitializedOpenStatePreservesGuard`                 | Asserts `initializedOpenState` latching prevents window resize callbacks or repeated initialization from re-arming the shift guard.                                                              |
| `testKeyRecognitionHelpers`                              | Validates `isShiftOrSneakKey`, `isCommandHubKey`, and `isInventoryKey` helper methods against GLFW key codes.                                                                                    |
| `testStateFlagsAndSetters`                               | Tests lifecycle getters and setters (`setShiftHeldOnOpen`, `setClosed`, `setInitializedOpenState`) for test harness control.                                                                     |
| `testCloseIdempotency`                                   | Validates that repeated invocations of `close()` remain strictly idempotent without throwing exceptions or corrupting lifecycle state.                                                           |

---

## 28. The 5 Architectural Refinements: Rotation Mathematics, Door Beacons, Ownership Persistence, Descent Kinematics & Smart Shift-Close

This section provides comprehensive engineering documentation for the five pivotal architectural refinements implemented across the tactical minion ecosystem, multiblock construction engine, client rendering pipeline, and GUI lifecycle.

```
                                  [5 Architectural Refinements]
                                                │
         ┌──────────────────┬───────────────────┼───────────────────┬──────────────────┐
         ▼                  ▼                   ▼                   ▼                  ▼
   [Refinement 1]     [Refinement 2]      [Refinement 3]      [Refinement 4]     [Refinement 5]
   Singleplayer       Scaffolding         Synchronous 3D      Smart Shift-Close  Sneak + Left-Click
   Host Ownership     Descent Phase-      Hologram Wireframe  with Item Transfer Blueprint Rotation
   Auto-Adoption      Through Kinematics  & Particle Preview  Latching in GUI    & Door Sparkle HUD
```

---

### Refinement 1: Singleplayer Host Ownership Persistence & Auto-Adoption (Server-Safe)

#### Problem & Root Cause Breakdown
In Minecraft singleplayer environments—particularly within development instances launched via Gradle Loom—the player's profile UUID can change between sessions (e.g. offline dev profile `--username Developer` vs authenticated Mojang account UUIDs). Previously, when a world was reloaded:
1. `MinionEntity` restored its owner UUID from NBT (`Owner`).
2. If the current player's UUID did not strictly equal the stored NBT UUID, `super.isOwner(player)` returned `false`.
3. Consequently, the minion treated its creator as an unauthorized stranger:
   - In `MinionScreen`, the Role and Squad cycling buttons as well as the Teleport and Dismiss action buttons were completely disabled (`button.active = false`).
   - Ground waypoint directives and Banner of Courage rally rings failed to command the minions.
4. **Dedicated Server Crash Hazard**: Attempting to resolve this naively by querying client singleplayer status (`MinecraftClient.getInstance().isInSingleplayer()`) inside common entity code (`MinionEntity.java`) causes immediate `NoClassDefFoundError: net/minecraft/client/MinecraftClient` crashes on dedicated servers.

#### Architectural Solution: Server-Authoritative Host Auto-Adoption
`MinionEntity` overrides `isOwner(LivingEntity entity)` with server-safe host resolution:

```java
@Override
public boolean isOwner(LivingEntity entity) {
    if (super.isOwner(entity)) {
        return true;
    }
    // Server-safe singleplayer host fallback: adopt the hosting player
    if (entity instanceof PlayerEntity player && this.getWorld() instanceof ServerWorld serverWorld) {
        MinecraftServer server = serverWorld.getServer();
        if (server != null && server.isSingleplayer() && server.isHost(player.getGameProfile())) {
            if (this.isTamed()) {
                this.setOwner(player); // Adopt host player and persist new UUID
                return true;
            }
        }
    }
    return false;
}
```

- **Server-Safe Singleplayer Validation**: Evaluates `server.isSingleplayer() && server.isHost(player.getGameProfile())` purely through `ServerWorld` and `MinecraftServer` APIs. Zero references to client classes in `src/main/java`.
- **Automatic Adoption & NBT Re-binding**: When the singleplayer host interacts with an owned tamed minion whose stored UUID is mismatched, the minion automatically updates its owner binding (`setOwner(player)`), persisting the active session's UUID into NBT.
- **Client GUI Interactivity Parity (`MinionScreen.java`)**:
  In the client GUI, button enablement evaluates:
  ```java
  boolean isOwner = minion != null && (
      (this.client != null && this.client.isInSingleplayer() && minion.isTamed())
      || (this.client != null && minion.isOwner(this.client.player))
      || minion.getOwnerUuid() == null
  );
  ```
  The host player in singleplayer always has interactive access to role, squad, teleport, and dismiss buttons.
- **Gradle Loom Dev Stability**: In `build.gradle`, client launch arguments configure `programArgs "--username", "Developer"`, guaranteeing consistent offline UUID generation across debug sessions.

---

### Refinement 2: Scaffolding Descent Phase-Through Fix & Landing Kinematics

#### Problem & Root Cause Breakdown
During multiblock construction and deconstruction, when a minion completed elevated tasks and attempted to descend down a scaffolding column:
1. `MinionBuildGoal.initiateDescent()` historically set `minion.setClimbingScaffolding(true)`.
2. Vanilla Minecraft scaffolding physics treats entities with `isClimbing() == true` as climbing upwards whenever horizontal motion collides with a ladder block.
3. Because the minion was standing on the platform directly above the top scaffolding block, any downward gravity or horizontal centering collided with the top face, immediately triggering upward climbing velocity (`+0.20D`).
4. This trapped the minion in an infinite jitter loop at the top platform: hopping up and down, unable to penetrate the scaffolding column surface to descend.

#### Architectural Solution: Descent Phase-Through & Kinematic Snapping
`MinionBuildGoal` re-engineers both descent and ascent transitions:

1. **Climbing Flag Suppression During Descent**:
   - `minion.setClimbingScaffolding(false)` is strictly maintained throughout descent.
   - Prevents horizontal contact from firing vanilla upward climbing impulses.
2. **Top Block Penetration Snapping**:
   - In `initiateDescent(ServerWorld world, boolean demobilizing)`:
     ```java
     this.isDescendingScaffolding = true;
     this.isAscendingScaffolding = false;
     this.minion.setClimbingScaffolding(false);
     double topYBoundary = (double) this.targetScaffoldTopY + 0.75D;
     this.minion.setPosition(scCenterX, Math.min(this.minion.getY(), topYBoundary), scCenterZ);
     this.minion.setVelocity(0.0D, -0.25D, 0.0D);
     this.minion.velocityModified = true;
     this.minion.fallDistance = 0.0F;
     ```
   - The minion is snapped 0.25 blocks below the platform top (`targetScaffoldTopY + 0.75D`), placing its collision box inside the permeable interior of the scaffolding column.
   - Downward velocity is directly set to `-0.25D` with zeroed fall distance, allowing the minion to glide downwards smoothly through the column rungs.
3. **Ground Bypass Guard**:
   - If `targetScaffoldTopY <= targetScaffoldBottomY`, descent is immediately bypassed, flags are cleared, and the column reservation is safely released.
4. **Relaxed Landing Threshold & Timeout Safety**:
   - In `tick()`, descent completes when `minion.getY() <= (double) targetScaffoldBottomY + 0.35D` or solid ground is detected under feet (`isOnGround()`).
   - Descent safety timeout is reduced from 140 ticks to 50 ticks (2.5 seconds).
5. **Ascent Platform Snapping Calibration**:
   - Platform landing arrival threshold lowered from `+0.95D` to `+0.70D`.
   - When `minion.getY() >= targetScaffoldTopY + 0.70D`, minion cleanly snaps to `(scCenterX, targetScaffoldTopY + 1.0D, scCenterZ)`, vertical velocity is zeroed, and climbing flags are disarmed.
   - Dynamic displacement stall threshold expanded from 10 to 25 ticks, preventing false aborts caused by tick rate fluctuations.
   - Post-failure navigation cooldown reduced from 40L to 15L ticks.

---

### Refinement 3: Synchronous 3D Holographic Wireframe & Particle Preview Rotation

#### Problem & Root Cause Breakdown
When rotating a blueprint (e.g. from 0° to 90°), the particle perimeter and server-authoritative construction sessions were correctly rotated, but the client-side `BlueprintHologramRenderer` rendered the unrotated base blueprint wireframe. As a result:
- The neon-cyan 3D wireframe box and ghost blocks faced North/South while the particles and spawned building faced East/West.
- Commanders experienced severe visual disorientation when placing rotated structures.

#### Architectural Solution: Rotation Matrix & Synchronous Hologram Rendering
1. **Blueprint Coordinate Transformation Engine (`StructureBlueprint.rotate`)**:
   `StructureBlueprint` implements exact origin-centered $(0, 0)$ rotation matrices:
   $$\begin{aligned}
   R_0(x, y, z) &= (x, y, z) \\
   R_{90}(x, y, z) &= (-z, y, x) \\
   R_{180}(x, y, z) &= (-x, y, -z) \\
   R_{270}(x, y, z) &= (z, y, -x)
   \end{aligned}$$
   - Rotates all block states using `state.rotate(rotation)` to properly re-orient stairs, doors, logs, and directional blocks.
   - Transposes dimensions: $(S_x, S_y, S_z) \mapsto (S_z, S_y, S_x)$ on 90° and 270° rotations.
   - Recomputes exact `BlockBox` bounding geometry.
   - Topologically re-sorts blocks in bottom-up construction order (`Collections.sort(rotatedBlocks)`).
2. **Synchronous Hologram Pipeline (`BlueprintHologramRenderer.java`)**:
   In `render(WorldRenderContext context)`:
   ```java
   String blueprintId = CommandScepterItem.getBlueprintId(scepterStack);
   StructureBlueprint baseBlueprint = BlueprintRegistry.getOrDefault(blueprintId);
   BlockRotation rotation = CommandScepterItem.getRotation(scepterStack);
   StructureBlueprint blueprint = baseBlueprint.rotate(rotation);
   ```
   - Dynamically resolves the rotated blueprint before deriving the render bounding box and block schematic offsets.
   - The neon-cyan wireframe, yellow anchor box, and translucent cyan ghost blocks rotate synchronously in 3D world space, matching particle boundaries and server placement with zero visual drift.

---

### Refinement 4: Smart Shift-to-Close in `MinionScreen` with Shift-Click Transfer Latching

#### Problem & Root Cause Breakdown
Commanders frequently use `Shift-Click` (`quickMove`) to rapidly transfer armor, weapons, and construction materials into a minion's 9-slot inventory and 6 equipment slots. If Shift-to-close were implemented naively on key release:
- The moment a player released `Shift` after transferring a sword or chestplate, the modal would instantly close, disrupting inventory management.
- If a player opened the screen while sneaking, releasing `Shift` would close the modal before they could inspect anything.

#### Architectural Solution: `SmartCloseHandler` & Transfer Latching
`MinionScreen` incorporates `SmartCloseHandler` to manage the smart close lifecycle:

```
                           [Player Presses Shift]
                                     │
                    ┌────────────────┴────────────────┐
                    ▼                                 ▼
         [Click Item Slot]                   [Zero Slot Clicks]
       slotClickedWithShift = TRUE                    │
                    │                                 ▼
                    ▼                       [Release Shift Key]
          [Release Shift Key]                        │
       • Close SUPPRESSED                            ▼
       • slotClickedWithShift = FALSE          [Modal Closes]
       • Modal remains open
```

1. **Open-State Guard (`shiftHeldOnOpen`)**:
   - If Shift is held down when sneak-right-clicking a minion, `shiftHeldOnOpen` is armed.
   - GLFW key repeats are absorbed; releasing the initial opening Shift disarms the guard without dismissing the screen.
2. **Shift-Click Item Transfer Latching (`slotClickedWithShift`)**:
   - In `MinionScreen.mouseClicked(double mouseX, double mouseY, int button)`:
     ```java
     if (this.smartCloseHandler.isShiftDown()) {
         this.smartCloseHandler.onMouseClicked(true);
     }
     ```
   - Clicking any slot while Shift is down latches `slotClickedWithShift = true`.
3. **Ergonomic Release Gate (`keyReleased`)**:
   - When the player releases Shift:
     ```java
     this.smartCloseHandler.onKeyReleased(keyCode, scanCode, isShift, isEscape, () -> this.close());
     ```
   - If `slotClickedWithShift` was latched, the close callback is **bypassed**, the flag is cleared, and the screen stays open.
   - If no item slot was clicked (a deliberate Shift tap), the screen closes immediately.
4. **Universal Hotkey Dismissals**:
   - Pressing `'E'` (`client.options.inventoryKey`) or `Escape` dismisses the screen immediately, bypassing transfer latching.
5. **Header UX Indicator**:
   - Framed header renders an intuitive indicator badge: `§e[Shift] §7Close`.

---

### Refinement 5: Sneak + Left-Click Blueprint Rotation Cycling & Tactical Door Sparkle Readouts

#### Problem & Root Cause Breakdown
Prior to this refinement, rotating a blueprint required repeatedly opening the Command Hub GUI or cycling through unrelated modes. Furthermore, players could not easily tell which side had the doorway or entrance, frequently placing watchtowers facing the wrong direction.

#### Architectural Solution: Real-Time Scepter Controls & Door Guidance
1. **Mode-Aware Left-Click Dispatch Invariant**:
   - **`BUILD` Mode**: Sneak + Left-Click cycles the blueprint's rotation angle:
     $$0^\circ \longrightarrow 90^\circ \longrightarrow 180^\circ \longrightarrow 270^\circ \longrightarrow 0^\circ$$
     - Intercepted on the client (`ExampleModClient`) and server (`AttackBlockCallback` & `AttackEntityCallback`).
     - Dispatches `ModClientNetworking.sendUpdateScepter(...)` with the updated rotation index.
     - Plays item pickup chime (`SoundEvents.ENTITY_ITEM_PICKUP`) and emits an instant actionbar update.
   - **Non-`BUILD` Modes**: Sneak + Left-Click deselects all active minions within 64 blocks and sets their standing guard anchors.
2. **Door Offset Discovery (`StructureBlueprint.getDoorOffsets`)**:
   - During blueprint construction, `StructureBlueprint` inspects all blocks for `DoorBlock` instances:
     ```java
     if (state.getBlock() instanceof DoorBlock) {
         if (!state.contains(DoorBlock.HALF) || state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
             doors.add(block.offset());
         }
     }
     ```
   - Extracts relative offsets for lower door blocks, automatically rotating them via `rotate(rotation)`.
3. **Tactical Door Sparkle Beams & Front Guide (`inventoryTick`)**:
   - When holding the Command Scepter in `BUILD` mode, `inventoryTick` performs a 32-block crosshair raycast.
   - Every 4 ticks:
     - Spawns rotating perimeter particles outlining the structure footprint.
     - For blueprints with doors (e.g. Overlord Watchtower), spawns vibrant vertical sparkle beams (`ParticleTypes.HAPPY_VILLAGER` + `ParticleTypes.END_ROD`) at each discovered door entrance.
     - For doorless structures (e.g. Arcane Obelisk, Defensive Barricade), spawns an emerald front guide line along the forward perimeter.
4. **Actionbar Real-Time HUD Readout**:
   - Displays live structural telemetry in the actionbar:
     `"§6🏗 Overlord Watchtower §8| §bRotation: 90° §8| §a🚪 Door: West"`
   - Direction dynamically reflects rotated door facing (`South` → `West` → `North` → `East`), giving commanders total confidence prior to right-click placement.

---

### Verification Matrix: The 5 Refinements

| Refinement | Primary Class / Component | Verification Test Suite | Verified Invariants & Assertions |
| :--- | :--- | :--- | :--- |
| **Refinement 1** (Singleplayer Ownership) | `MinionEntity`<br>`MinionScreen` | `MinionScreenCloseTest`<br>`MinionSquadAndRoleTest` | Singleplayer host auto-adoption; zero server `MinecraftClient` imports; GUI button active state parity across restarts. |
| **Refinement 2** (Scaffolding Descent) | `MinionBuildGoal`<br>`TraversalScaffoldingManager` | `ScaffoldingTest` | Centering penetration at $y \le \text{topY} + 0.75\text{D}$; climbing flag suppression during descent; relaxed landing detection; ground bypass. |
| **Refinement 3** (Synchronous Hologram) | `BlueprintHologramRenderer`<br>`StructureBlueprint` | `BlueprintRotationTest`<br>`CommandScepterRotationTest` | Bounding box dimension swaps ($S_x \leftrightarrow S_z$ on 90°/270°); topological sorting stability; ghost block schematic alignment. |
| **Refinement 4** (Smart Shift-Close) | `MinionScreen`<br>`SmartCloseHandler` | `MinionScreenCloseTest` | Shift-click item transfer latching (`slotClickedWithShift`); repeat suppression; 'E' and Esc dismissal; clean Shift tap close. |
| **Refinement 5** (Rotation & Door Beacons) | `CommandScepterItem`<br>`ModDataComponents` | `CommandScepterRotationTest`<br>`NetworkingPayloadTest` | 4-quadrant rotation index mapping; door offset extraction; sneak left-click BUILD dispatch vs minion deselection. |

