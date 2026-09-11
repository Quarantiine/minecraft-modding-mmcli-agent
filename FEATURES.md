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
         ┌───────────────────┬────────────┴────────────┬───────────────────┐
         ▼                   ▼                         ▼                   ▼
  [Shift + Right-Click] [BUILD Mode: Ground]    [RECRUIT Mode: Mob]   [Tactical Broadcast]
   Open Command Hub GUI  Anchor Construction     Enthrall Mob into     FOLLOW / STAY / ATTACK
   (Or press [V] key)    Session at Block Face   Minion Thrall         Radius: 32 blocks
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

| Action                       | Condition / Mode     | Behavior                                                                                                                                                                                       |
| :--------------------------- | :------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Shift + Right-Click**      | Any Mode             | Opens the interactive **Command Hub GUI** (`CommandScepterScreen`) allowing direct mode selection, blueprint switching, directive execution, and broadcast dismissal.                          |
| **Press [V] Key**            | Scepter in Inventory | Keybind shortcut opening the Command Hub GUI if a scepter is equipped in main hand, off hand, or player inventory.                                                                             |
| **Right-Click Ground**       | `BUILD` Mode         | Anchors a new `ConstructionSession` at the clicked block face using the active blueprint. Emits beacon sound and enchantment particle blast.                                                   |
| **Shift + Left-Click**       | `BUILD` Mode         | Intercepted via `AttackBlockCallback.EVENT`: Cycles active blueprint (`WATCHTOWER` → `OBELISK` → `BARRICADE`), plays bell sound, and displays action-bar notification without breaking blocks. |
| **Right-Click Air**          | `BUILD` Mode         | Alternate blueprint cycling trigger without targeting a block.                                                                                                                                 |
| **Right-Click Mob**          | `RECRUIT` Mode       | Enthralls target living mob into an obedient `MinionEntity` thrall.                                                                                                                            |
| **Right-Click Ground / Air** | `FOLLOW` Mode        | Orders all owned minions within 32 blocks to stand up and follow the player (`speed: 1.25`).                                                                                                   |
| **Right-Click Ground / Air** | `STAY` Mode          | Orders all owned minions within 32 blocks to sit down, hold position, and clear targets.                                                                                                       |
| **Right-Click Mob / Ground** | `ATTACK` Mode        | Directs all owned minions within 32 blocks to focus-fire on the clicked mob or engage nearby hostile mobs.                                                                                     |

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
- **Hitbox Dimensions**: `0.6` width x `1.95` height (standard player biped)
- **Spawn Egg**: `modid-mmcli-agent-modding:minion_spawn_egg` (Colors: `0x2C3E50` deep navy base, `0xF1C40F` arcane gold spots; right-clicking immediately tames the spawned minion to the placing player)

### Minion Player Interaction Matrix

| Interaction                 | Condition      | Behavior                                                                                                                                                                                                             |
| :-------------------------- | :------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Sneak + Right-Click**     | Owned Minion   | Opens the interactive **Minion Management GUI** (`MinionScreen`) displaying 6 equipment slots, 9-slot inventory, live 3D preview, and Dismiss button. Direct item drop onto the minion is retired.                   |
| **Empty Hand Right-Click**  | Owned Minion   | Toggles holding position state (updates both `sitting` and `inSittingPose`). Clears targets and plays frame rotate / orb audio.                                                                                      |
| **Food / Gold Right-Click** | Injured Minion | Heals the wounded minion: <br>• Food restores health equal to food nutrition.<br>• Gold Nugget heals 1.0 HP, Gold Ingot heals 4.0 HP, Gold Block heals 20.0 HP.<br>• Emits `ParticleTypes.HEART` and level-up audio. |
| **Gold Ingot Right-Click**  | Untamed Minion | Binds the wild minion to the player as its permanent owner.                                                                                                                                                          |

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
|  [ ✦ Teleport to Me ]          [ ✖ Dismiss Minion ]   |
+-------------------------------------------------------+
```

### Layout Specifications

- **Dimensions**: Standard 176x166 container frame with custom upper preview section.
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
  - Current & Max Health (`§c❤ Health: 40.0 / 40.0`)
  - Total Armor Rating (`§b🛡 Armor: 4 (+Armor)`)
  - Current Stance (`§aStatus: Guarding / Following Master` or `§eStatus: Holding Position`)

### Auto-Equip from Internal Storage (`autoEquipFromInventory`)

- When the screen closes (`onClosed`) or periodically every 20 ticks (1 second) in server tick:
  - Minions automatically inspect their 9 storage slots.
  - If any equipment slot (Head, Chest, Legs, Feet, Mainhand, Offhand) is vacant, matching items are automatically equipped with armor equip sounds (`ITEM_ARMOR_EQUIP_GENERIC`).

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
4. Bind Ownership to Player (setOwnerUuid, mark tamed)
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

- **Ground Anchoring**: Right-clicking on the ground in `BUILD` mode anchors a new session at the clicked block face.
- **Hologram Boundary Projection**: Every 20 ticks (1 second), the manager renders holographic particle outlines (`ParticleTypes.GLOW` and `PORTAL`) at the 8 bounding-box corners and `ParticleTypes.WAX_ON` on actively claimed placement blocks.
- **Worker Lease Management**: When a minion claims a task, a lease timestamp is recorded. If a minion is killed, disconnected, or pathfinding-stuck for over 15 seconds (300 ticks), the task is automatically returned to the unclaimed task pool.
- **Completion Ceremony**: When the final block is placed, the manager plays `UI_TOAST_CHALLENGE_COMPLETE` and `ENTITY_PLAYER_LEVELUP`, erupts celebratory `HAPPY_VILLAGER` and `TOTEM_OF_UNDYING` particles, and notifies the player.

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

### Placement Dynamics & Scaffolding

- **Preserved Weapons**: Before equipping a block preview in `MAINHAND`, the minion's equipped weapon is preserved and restored immediately after placement.
- **Scaffolding Deployment**: Automated zero-cost temporary scaffolding deployment allows minions to reach elevated blocks.
- **Block Preview**: The minion visibly holds the required block in `EquipmentSlot.MAINHAND` while pathfinding.
- **Navigation Proximity**: Pathfinds to within 3.8 horizontal and 5.5 vertical blocks of the target block position.
- **Deliberate Work Delay**: 4-tick placement delay for visual realism.
- **Placement Execution**: Minion swings its main arm (`swingHand(Hand.MAIN_HAND)`), spawns `BlockStateParticleEffect`, plays native placement sounds, and completes the task.

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

| Extension Target                 | Status / Implementation Strategy                                                                                                         |
| :------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------- |
| **Minion Equipment & GUI**       | **Completed**: Dedicated `MinionScreen` GUI, 6 equipment slots, auto-equipping, 3D live entity preview, and dismiss mechanics.           |
| **Biped Player Model**           | **Completed**: `MinionEntityRenderer` with `PlayerEntityModel`, dynamic arm poses (blocking, bow, crossbow), and dual-layer armor trims. |
| **Combat Durability**            | **Completed**: 40 HP, 4 armor, 5 attack damage, passive out-of-combat regeneration, weapon swing animations, and post-combat return.     |
| **Scaffolding Builder AI**       | **Completed**: Temporary scaffolding deployment for elevated construction tasks and weapon saving/restoration.                           |
| **Radial Menu HUD**              | In Design: Client radial HUD overlay to select scepter command modes and blueprints via mouse wheel.                                     |
| **Minion Mining AI**             | Roadmap: Add `MinionMineGoal` to execute automated area excavation and ore vein quarrying.                                               |
| **Blueprint Schematics**         | Roadmap: Add NBT/JSON structure file loader to convert `.nbt` structure templates into `StructureBlueprint` instances.                   |
| **Minion Classes / Professions** | Roadmap: Differentiate minions into specialized roles (Builder, Sentry, Miner, Courier) with distinctive equipment and behavior buffs.   |
