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

| Action                         | Condition / Mode       | Behavior                                                                                                                                                                                                                                                                                                                                                            |
| :----------------------------- | :--------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Shift + Right-Click**        | Any Mode               | Opens the interactive **Command Hub GUI** (`CommandScepterScreen`) allowing direct mode selection, paginated blueprint catalog inspection, directive execution, and broadcast dismissal.                                                                                                                                                                         |
| **Press [V] Key**              | Scepter in Inventory   | Keybind shortcut opening the Command Hub GUI if a scepter is equipped in main hand, off hand, or player inventory.                                                                                                                                                                                                                                                  |
| **Right-Click Owned Minion**   | Any Mode               | **Individual Minion Follow (`commandIndividualMinionFollow`)**: Direct or 32-block crosshair quick-tap on an owned minion orders that specific unit to break its station, clear sitting/guard anchors, and sprint to the master at 1.35D. Emits 6x `HEART` particles, note block chime SFX (`BLOCK_NOTE_BLOCK_CHIME`), and an actionbar notification.         |
| **Hold Right-Click (Channel)** | Any Mode               | **Banner of Courage Rally Ring**: Charges an expanding circular particle ring (`PORTAL` and `FLAME`, radius 3.0 to 16.0 blocks). Releasing triggers goat horn sound, gathers all enclosed minions into the selected squad channel, and orders them to `FOLLOW`. Quick-taps (<8 ticks) evaluate 32-block crosshair raycasting: instant blueprint cycling (`BUILD`), long-range focus-fire entity pings, or 32-block RTS ground waypoint pings. |
| **Right-Click Ground**         | Non-`BUILD`/`MINE` Modes| **Ground Waypoint Ping / Crosshair Raycast**: Crosshair raycasts up to 32.0 blocks (`MINION_COMMAND_RADIUS`). If cursor aligns with a hostile entity, prioritizes focus-fire. Otherwise, emits a vertical beacon beam (`END_ROD` and `GLOW` particles) with beacon SFX (`SoundEvents.BLOCK_BEACON_ACTIVATE`). Active squad minions that are **not sitting** (`!m.isSitting()`) sprint at 1.35D to that coordinate and hold position (`WaypointHoldGoal` / `SentinelGuardGoal`). Stationed/holding minions are preserved. |
| **Right-Click Hostile Entity** | Any Mode (non-Recruit) | **Hostile Entity Focus-Fire Ping**: Line-of-sight raycasted up to 32.0 blocks (bypassing 3.0 vanilla reach limitations). Emits lock-on particles (`ANGRY_VILLAGER` and `CRIT`) with note block drum cadence SFX (`BLOCK_NOTE_BLOCK_BASEDRUM`). All matching squad minions focus-fire that specific target. |
| **Right-Click Ground**         | `BUILD` Mode           | Anchors a new multiblock `ConstructionSession` (`SessionMode.BUILD`) at the clicked block face using the active blueprint. Emits beacon sound and enchantment particle blast.                                                                                                                                                                                      |
| **Shift + Right-Click Ground** | `BUILD` Mode           | Anchors a **Structure Dismantling Session** (`SessionMode.DISMANTLE`) at the clicked block face, commanding builders and miners to dismantle the active blueprint top-down.                                                                                                                                                                                       |
| **Right-Click Ground / Box**   | `MINE` Mode            | Anchors a **Structure Dismantling Session** (`SessionMode.DISMANTLE`) at the clicked block or existing active structure bounding box. If an active session is clicked, targets its blueprint; otherwise targets the held blueprint.                                                                                                                             |
| **Shift + Left-Click**         | `BUILD` Mode           | Intercepted via `AttackBlockCallback.EVENT`: Cycles active blueprint (`WATCHTOWER` → `OBELISK` → `BARRICADE`), plays bell sound, and displays action-bar notification without breaking blocks.                                                                                                                                                                      |
| **Right-Click Air**            | `BUILD` Mode           | Alternate blueprint cycling trigger without targeting a block.                                                                                                                                                                                                                                                                                                      |
| **Right-Click Mob**            | `RECRUIT` Mode         | Enthralls target living mob into an obedient `MinionEntity` thrall primed in standby (supports up to 32-block crosshair alignment).                                                                                                                                                                                                                                 |
| **Right-Click Ground / Air**   | `FOLLOW` Mode          | Orders all owned minions within 32 blocks to stand up and follow the player (`speed: 1.25`). Quick-taps evaluate 32-block crosshair hits.                                                                                                                                                                                                                            |
| **Right-Click Ground / Air**   | `STAY` Mode            | Orders all owned minions within 32 blocks to sit down, hold position, and clear targets. Quick-taps evaluate 32-block crosshair hits.                                                                                                                                                                                                                               |
| **Right-Click Mob / Ground**   | `ATTACK` Mode          | Crosshair raycast up to 32 blocks: prioritizes target entity in crosshairs, acquires closest hostile along cursor ray near clicked block/player, or broadcasts attack directive within 32 blocks to active (non-sitting) matching squad thralls.                                                                                                                   |

### 2.1 Individual Minion Follow & Tactical Hold Safeguards

1. **Individual Follow Ordering**:
   - Commanders can address specific thralls without disrupting surrounding squads or issuing blanket march orders.
   - Right-clicking directly on an owned minion (`useOnEntity`) or aiming within 32 blocks and quick-tapping triggers `CommandScepterItem.commandIndividualMinionFollow(player, minion)`.
   - The targeted minion immediately breaks its holding stance (`setSitting(false)`), clears any tether anchor (`setGuardAnchorPos(null)`), clears target, and sprints to the player at $1.35\times$ movement speed.
   - Distinct audiovisual feedback: 6 floating hearts (`ParticleTypes.HEART`), note block chime SFX (`SoundEvents.BLOCK_NOTE_BLOCK_CHIME`), and an actionbar message: `"§a✦ Minion §f<Name> §ais now following you."`.

2. **Squad Hold Safeguards (`!m.isSitting()`)**:
   - Previously, global ground waypoint pings and attack broadcasts queried all owned minions matching the squad filter and forcibly unseated them (`setSitting(false)`), pulling stationed defenders and sentinels into unintended marches.
   - Both `executeGroundWaypointPing` and `broadcastAttack` now strictly verify `!m.isSitting()`. Stationed minions holding posts remain locked at their stations, protected against stray command pings until given an explicit follow or rally directive.

### 2.2 Command Hub GUI & Blueprint Catalog Pagination (`CommandScepterScreen`)

To eliminate GUI overflow where large blueprint catalogs overlapped bottom action buttons on higher GUI scales:
- **Fixed 3-Item Viewport (`BLUEPRINT_PAGE_SIZE = 3`)**: Blueprints render in a structured 3-card vertical stack on the right side of the screen (`startX + 165`).
- **Pagination Navigation**: Previous (`<`) and Next (`>`) button widgets appear dynamically when total blueprints exceed 3.
- **Action Bar Clearance**: Bottom controls (`Execute`, `Teleport All`, `Dismiss All`, `Close`) are safely positioned below the catalog viewport (`startY + 222`), guaranteeing zero overlap across all screen resolutions and GUI scale settings.

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

| Interaction                 | Condition      | Behavior                                                                                                                                                                                                             |
| :-------------------------- | :------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Sneak + Right-Click**     | Owned Minion   | Opens the interactive **Minion Management GUI** (`MinionScreen`) displaying 6 equipment slots, 9-slot inventory, live 3D preview, and Dismiss button. Direct item drop onto the minion is retired.                   |
| **Empty Hand Right-Click**  | Owned Minion   | Toggles holding position state (updates both `sitting` and `inSittingPose`). Clears targets and plays frame rotate / orb audio.                                                                                      |
| **Right-Click with Scepter**| Owned Minion   | Orders the individual minion to break holding stance and follow master at 1.35D, emitting `HEART` particles, note block chime SFX, and an actionbar message.                                                          |
| **Food / Gold Right-Click** | Injured Minion | Heals the wounded minion: <br>• Food restores health equal to food nutrition.<br>• Gold Nugget heals 1.0 HP, Gold Ingot heals 4.0 HP, Gold Block heals 20.0 HP.<br>• Emits `ParticleTypes.HEART` and level-up audio. |
| **Gold Ingot Right-Click**  | Untamed Minion | Binds the wild minion to the player as its permanent owner.                                                                                                                                                          |

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
   - **Squad Banner (Top Line)**: Displays squad channel flag, name, and Roman numeral designation (`⚑ SQUAD ALPHA [I]`, `⚑ SQUAD BRAVO [II]`, `⚑ SQUAD CHARLIE [III]`, `⚑ SQUAD DELTA [IV]`, `⚑ SQUAD ALL [*]`) styled with each squad's signature color.
   - **Role Crest & Lettering (Bottom Line)**: Displays tactical archetype icon and uppercase lettering (`⚔ WARRIOR`, `🛡 SENTINEL`, `🔨 BUILDER`, `⛏ MINER`, `🏹 RANGER`), appended with `[HOLD]` when holding position.
   - **Overhead Y-Translation & Coordinate Inversion Fix**:
     - In vanilla Minecraft's `LivingEntityRenderer`, the model matrix is subjected to an inverted scale transformation `(-1.0F, -1.0F, 1.0F)` in `setupTransforms`.
     - In earlier versions, a positive Y translation (`+baseHeight / MODEL_SCALE`) erroneously translated the badge downward into the ground at the minion's feet.
     - The renderer inverts the Y translation in `getOverheadYTranslation(entityHeight, hasCustomName, isSneaking)`:
       ```java
       float baseHeight = entityHeight + 0.35F;
       if (hasCustomName) baseHeight += 0.30F;
       if (isSneaking) baseHeight -= 0.20F;
       return -(baseHeight / MODEL_SCALE);
       ```
     - This projects the badge upwards directly above the minion's head, preserving clear margins above vanilla custom nametags and dynamically adjusting for sneaking postures.
   - **Dynamic Clearance & Culling**: Automatically adjusts elevation above custom nametags and crouching postures, culling beyond 64 blocks for optimal battlefield performance.

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

| Extension Target                    | Status / Implementation Strategy                                                                                                                            |
| :---------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Minion Equipment & GUI**          | **Completed**: Dedicated `MinionScreen` GUI, 6 equipment slots, auto-equipping, 3D live entity preview, and dismiss mechanics.                              |
| **Biped Player Model**              | **Completed**: `MinionEntityRenderer` with `PlayerEntityModel`, dynamic arm poses (blocking, bow, crossbow), and dual-layer armor trims.                    |
| **Combat Durability**               | **Completed**: 40 HP, 4 armor, 5 attack damage, passive out-of-combat regeneration, weapon swing animations, and post-combat return.                        |
| **Scaffolding Builder AI**          | **Completed**: Temporary scaffolding deployment for elevated construction tasks and weapon saving/restoration.                                              |
| **Radial Menu HUD**                 | In Design: Client radial HUD overlay to select scepter command modes and blueprints via mouse wheel.                                                        |
| **Minion Mining AI**                | Roadmap: Add `MinionMineGoal` to execute automated area excavation and ore vein quarrying.                                                                  |
| **Blueprint Schematics**            | Roadmap: Add NBT/JSON structure file loader to convert `.nbt` structure templates into `StructureBlueprint` instances.                                      |
| **Minion Classes / Professions**    | **Completed**: Differentiated into specialized roles (`WARRIOR`, `SENTINEL`, `BUILDER`, `MINER`, `RANGER`) with partitioned AI goals and behavior profiles. |
| **Tactical Squads & Channeling**    | **Completed**: Squad partitioning (`ALL`, `ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`) with scepter channel filtering and interactive GUI selection bars.          |
| **Banner of Courage Rally Ring**    | **Completed**: Channeled expanding circular particle ring gathering enclosed minions into selected squad with war horn audio.                               |
| **Tactical Ground & Hostile Pings** | **Completed**: Ground waypoint sprint & hold (`WaypointHoldGoal`), and hostile focus-fire with war drum cadences.                                           |
| **Smart Role Auto-Equip**           | **Completed**: Archetype weapon & tool restrictions (Bows for Rangers, Shields for Sentinels, Pickaxes for Miners, etc.).                                   |
| **Dynamic Formations**              | **Completed**: Distributed parametric stations (Vanguard wedge, Bulwark wings, Core support, Skirmishers rearguard) with anti-crowding geometry.            |
| **Overhead Billboard Badges**       | **Completed**: Dynasty Warriors overhead crests with squad colors, Roman numerals, tactical role icons, and status flags.                                   |
| **Sentinel Perimeter Leash**        | **Completed**: Autonomous anchor tethering, 8-block perimeter guard, 12-block leash distance with aggro drop and 1.35D sprint retreat.                      |
| **Ranger Archery AI**               | **Completed**: Implements `RangedAttackMob` with dynamic strafing pocket (8-16 blocks), bow pull animation, and backpedaling under 8 blocks.                |
| **32-Block Crosshair Raycasting**   | **Completed**: Full 32.0D line-of-sight raycasting for entity focus-fire and RTS ground waypoints, with solid block obstruction clipping and ray-to-point math. |
| **Combat Sappers & Traversal Scaffolding** | **Completed**: Autonomous chasm/ravine bridging (up to 6 blocks), cliff climbing shafts (up to 6 blocks), zero-cost `BUILDER` sappers, 24-block signaling, and 400-tick ephemeral decay with entity safety guards. |
| **Structure Deconstruction Mode**   | **Completed**: Top-down reverse topological dismantling (`SessionMode.DISMANTLE`), role authorization (`BUILDER` & `MINER`), tool resolution, survival drops, and progressive scaffolding teardown on descent. |
| **Scepter Individual Minion Follow**| **Completed**: Direct right-click or 32-block crosshair quick-tap on owned minion commands unit to follow with chime SFX and heart particles. |
| **Standby Summoning & Hold Safeguards**| **Completed**: Newly summoned/transfigured minions initialize in standby holding state; waypoint pings and attack broadcasts guard stationed units (`!m.isSitting()`). |
| **GUI Pagination & Unified Framing**| **Completed**: 3-item viewport pagination for blueprint catalog in Command Hub; dynamically centered, fully framed Minion Screen preventing clipping on any GUI scale. |
| **Overhead Crest Alignment & Hitbox**| **Completed**: Inverted Y-translation in `MinionOverheadBadgeFeatureRenderer` ensuring crests render overhead, with dynamic nametag/sneaking clearance and registered eye height (1.74F). |
| **Radial Menu HUD**                 | In Design: Client radial HUD overlay to select scepter command modes and blueprints via mouse wheel.                                                        |
| **Minion Mining AI**                | Roadmap: Add `MinionMineGoal` to execute automated area excavation and ore vein quarrying.                                                                  |
| **Blueprint Schematics**            | Roadmap: Add NBT/JSON structure file loader to convert `.nbt` structure templates into `StructureBlueprint` instances.                                      |

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
2. **Trigonometric Coordinate Rotation (`calculateFormationStation`)**:
   - Computes world coordinates using the commander's yaw in Minecraft world space:
     $$\text{Forward Vector} = (-\sin(\text{yaw}), \cos(\text{yaw}))$$
     $$\text{Flank Vector} = (\cos(\text{yaw}), \sin(\text{yaw}))$$
   - Rotates all parametric stations smoothly in real time as the player turns across all 360 degrees.
3. **Deterministic Intra-Role Ranking (`resolveRank`)**:
   - Automatically orders active thralls belonging to the owner by entity ID to assign stable, flicker-free station ranks ($0, 1, 2, \dots$) within each role.
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
   - **Dynamic Clearance**: Renders at $Y = 2.45\text{F}$ ($2.15\text{F}$ when sitting/sneaking), and steps up an additional $+0.35\text{F}$ if a custom nametag is present, preventing visual overlap.
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

| Test Class                    | Package                       | Verified Systems & Coverage                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| :---------------------------- | :---------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MinionSquadAndRoleTest`      | `com.example.entity`          | • `MinionRole` & `SquadGroup` Mojang Codec JSON round-trip serialization.<br>• Netty `PacketCodec` ByteBuf byte-level serialization.<br>• Complete 5-cycle permutation invariants.<br>• Tactical squad filtering predicate on mock armies.<br>• Large-scale army squad partitioning (140 thralls) rejecting foreign and dead units.<br>• Multi-squad isolated simultaneous directive dispatch.<br>• `SentinelGuardGoal` state machine simulation (leash break, aggro clearing, sprint retreat).<br>• Ranged combat engagement pockets (8–16 blocks sweet spot, backpedal < 8 blocks).<br>• Channeled Banner of Courage rally gathering simulation.<br>• Ground waypoint and hostile focus-fire ping state transitions.                                                                                                                                               |
| `MinionFormationAndEquipTest` | `com.example.entity`          | • Vanguard (Warrior) forward-flanking wedge parametric geometry.<br>• Bulwark (Sentinel) escort wings flanking commander.<br>• Core (Builder/Miner) protected support column placement.<br>• Skirmisher (Ranger) rearguard line placement.<br>• Anti-crowding station clearance ($\ge 2.0$ blocks from master & comrades).<br>• 16-thrall 4-echelon cohort pairwise clearance ($\ge 1.5$ blocks).<br>• Compass yaw rotation invariants across 8 cardinal/intercardinal headings and negative angles.<br>• Dynamic pacing speeds ($1.15\text{D}$ march, $1.35\text{D}$ sprint, $2.0$ block arrival, $24.0$ block teleport).<br>• Role-based smart auto-equip rules and comprehensive item matrix across all 5 roles.<br>• Inventory displacement item preservation without duplication or loss.<br>• Formation offset reflectional symmetry and monotonic wing flare. |
| `MinionOverheadBadgeTest`     | `com.example.client.renderer` | • Squad banner text formatting and Roman numeral designations (`[I]`, `[II]`, `[III]`, `[IV]`, `[*]`).<br>• Role crest icons (`⚔`, `🛡`, `🔨`, `⛏`, `🏹`) and uppercase lettering.<br>• Active vs `[HOLD]` status suffix formatting.<br>• Renderer configuration constants (text scale, line spacing, 64-block distance culling).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `NetworkingPayloadTest`       | `com.example.network`         | • `UpdateMinionConfigPayload` record fields, IDs, and equality.<br>• `UpdateScepterPayload` channel synchronization and backward-compatible constructor.<br>• PacketCodec registration integrity.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `ScaffoldingTest`             | `com.example.blueprint`       | • Temporary scaffolding column deployment and vertical reach.<br>• Doorway corridor avoidance logic.<br>• Headroom clearance validation.<br>• Climbing state machine transitions.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `CommandScepterRaycastTargetingTest` | `com.example.item`    | • Exact 3D point-to-ray squared perpendicular distance math and clamped $[0, \text{maxRange}]$ projection segments.<br>• Origin clamping for rear targets and tip clamping for beyond-32-block targets.<br>• Direction vector normalization invariance and zero-length degeneration handling.<br>• Crosshair candidate sorting (angular alignment priority, depth tie-breaking, rear hostile rejection).<br>• Line-of-sight solid block obstruction clipping and entity-over-block hit priority.<br>• Quick-tap (<8 ticks) dispatch state machine matrix across all command modes (`BUILD`, `ATTACK`, `FOLLOW`, `STAY`, `RECRUIT`).<br>• Entity targetability invariants (commander exclusion, owned minion immunity, enemy thrall targeting, spectator rejection).                                                                     |
| `MinionSapperAndScaffoldingTest` | `com.example.entity`       | • Ravine and chasm bridging coordinate calculation across cardinal North/East and diagonal headings.<br>• Zero heading vector safety and Y-level stability.<br>• Cliff and mountain ascent climbing column coordinate generation for 3-block and 6-block ledges.<br>• Flat and inverted elevation edge-case handling.<br>• Combat sapper role archetype gating (`BUILDER` zero-cost vs standard thrall item consumption).<br>• Squad sapper 24-block assistance request dispatch and 200-tick timeout lifecycle.<br>• `TraversalScaffoldingManager` decay state machine simulation (400-tick default lifetime, 40-tick entity safety extension while occupied, clean removal on expiration).                                                                                                                                                |
| `StructureDismantlingTest`    | `com.example.construction`    | • `SessionMode` enum values and mode accessors (`BUILD` vs `DISMANTLE`).<br>• Reverse topological task sorting (highest effective Y first, foundation last).<br>• Roof-to-foundation deconstruction prerequisites (clearing non-hanging upper blocks before foundations, hanging decorations before supporting ceilings).<br>• Minion role authorization matrix (`BUILDER` & `MINER` participation; combat roles excluded).<br>• Progressive scaffolding teardown on descent simulation.                                                                                                                                                                                                                                                                                                                                              |

All 67 unit tests execute and pass cleanly via `./gradlew test`.

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

| Command Mode | Crosshair Hit: Entity                                            | Crosshair Hit: Block (0–32 blocks)                          | Crosshair Hit: Sky / Miss                |
| :----------- | :--------------------------------------------------------------- | :---------------------------------------------------------- | :--------------------------------------- |
| `BUILD`      | Cycles active blueprint (`cycleBlueprint`)                       | Cycles active blueprint (`cycleBlueprint`)                  | Cycles active blueprint                  |
| `ATTACK`     | Focus-fires targeted entity (`executeHostileEntityPing`)         | Focus-fires nearby hostile; otherwise drops ground waypoint | Global attack directive broadcast        |
| `FOLLOW`     | Focus-fires targeted entity (if enemy); otherwise ground ping    | Long-range RTS ground waypoint ping (up to 32 blocks)        | Global follow directive broadcast        |
| `STAY`       | Focus-fires targeted entity (if enemy); otherwise ground ping    | Long-range RTS ground waypoint ping (up to 32 blocks)        | Global stay directive broadcast          |
| `RECRUIT`    | Enthralls living mob into minion (`transfigureEntityToMinion`)   | Long-range RTS ground waypoint ping                         | Emits action-bar tip on recruitment      |
| `MINE`       | Focus-fires targeted entity (if enemy); otherwise ground ping    | Long-range RTS ground waypoint ping                         | Global mine directive broadcast          |

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

| Role Archetype | `SessionMode.BUILD` Eligibility | `SessionMode.DISMANTLE` Eligibility |
| :------------- | :------------------------------ | :---------------------------------- |
| `BUILDER`      | **Eligible**                    | **Eligible**                        |
| `MINER`        | *Excluded*                      | **Eligible** (Demolition Specialist)|
| `WARRIOR`      | *Excluded*                      | *Excluded*                          |
| `SENTINEL`     | *Excluded*                      | *Excluded*                          |
| `RANGER`       | *Excluded*                      | *Excluded*                          |

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


