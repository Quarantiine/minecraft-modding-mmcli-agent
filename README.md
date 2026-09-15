# Fabric 1.21 Mod Development & Deployment Guide

A modern Minecraft 1.21 mod template built using the Fabric toolchain: **Fabric Loader**, **Fabric API**, and **Gradle Loom** with **Yarn** mappings.

This guide outlines the end-to-end mod development lifecycle, detailing how the development sandbox operates via Gradle Loom, how to configure Fabric API, and how to build and deploy your compiled mod JAR into the official Minecraft Launcher.

---

## Features & Added Content

This mod builds on the Fabric foundation with custom gameplay mechanics implemented using Fabric's recommended domain architecture. Custom features are organized into dedicated packages (`item`, `entity`, `block`, `component`, `blueprint`, `construction`, and client `renderer`) with decoupled registry lifecycles and asset schemas.

> 📖 **Command & Controls Quick Guide**: For a quick-reference cheat sheet of all controls, scepter inputs, keybinds, and squad orders, view [**COMMANDS.md**](COMMANDS.md).
>
> 📖 **Comprehensive Feature Guide**: For full mechanical specifications, entity physics details, explosion parameters, and architecture patterns, explore the dedicated [**FEATURES.md**](FEATURES.md) showcase.

### High-Level Content Overview

The mod's custom gameplay mechanics are organized across four core pillars: RTS unit command, multiblock engineering, tactical explosives, and client GUI/rendering pipelines.

#### Quick Reference Matrix

| Feature                      | Domain            | Identifier / Class                                                    | Core Capability                                                                                                           |                                        Specs                                        |
| :--------------------------- | :---------------- | :-------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------ | :---------------------------------------------------------------------------------: |
| **Loki Command Scepter**     | Custom Item       | `modid-mmcli-agent-modding:command_scepter`<br>`CommandScepterItem`   | 64-block raycast unit selection, ground waypoints with auto-deselect, persistent 90° assault queue targeting, 4-quadrant rotation & door sparkle HUD |         [Section 1](FEATURES.md#1-loki-command-scepter-commandscepteritem)          |
| **Minion Thrall**            | Custom Entity     | `modid-mmcli-agent-modding:minion`<br>`MinionEntity`                  | 9-slot inventory, 4 archetype roles (Sentinel Aegis of Restoration healing AI), 64-block follow & teleport leash range, Universal Arcane Levitation traversal, dynamic hearts health display, persistent assault chaining & upright posture |    [Section 2](FEATURES.md#2-autonomous-minion-thralls-minionentity--spawn-egg)     |
| **Minion Spawn Egg**         | Custom Item       | `modid-mmcli-agent-modding:minion_spawn_egg`<br>`SpawnEggItem`        | Deep navy & arcane gold spawn egg; primes minions in standby stance                                                       |    [Section 2](FEATURES.md#2-autonomous-minion-thralls-minionentity--spawn-egg)     |
| **Tactical Army AI**         | Squad AI          | `MinionRole`<br>`SquadGroup`                                          | Wildcard (`ALL`) and discrete squads (`ALPHA`–`DELTA`), formations & focus-fire                                           |   [Section 3](FEATURES.md#3-tactical-army-architecture-roles-squads--formations)    |
| **Construction Manager**     | Server Engine     | `ConstructionManager`<br>`ConstructionSession`                        | Multi-phase build/dismantle sessions with holographic bounding particles                                                  |        [Section 6](FEATURES.md#6-multiblock-construction--blueprint-engine)         |
| **Blueprint Catalog**        | Multiblock Engine | `BlueprintRegistry`<br>`StructureBlueprint`                           | Topologically sorted blueprints (Watchtower, Arcane Obelisk, Barricade)                                                   |        [Section 6](FEATURES.md#6-multiblock-construction--blueprint-engine)         |
| **Structure Deconstruction** | Demolition Engine | `ConstructionSession`<br>`ConstructionManager`                        | Reverse topological dismantling (roofs first, foundations last) with bedrock immunity safeguards & tool salvage           |        [Section 7](FEATURES.md#7-structure-deconstruction--bedrock-immunity)        |
| **Combat Sappers**           | Traversal AI      | `MinionSapperGoal`<br>`TraversalScaffoldingManager`                   | Autonomous chasm bridging, cliff ascent ladders with ceiling clearance avoidance & stall recovery                         |          [Section 8](FEATURES.md#8-combat-sappers--ephemeral-scaffolding)           |
| **TNT Stick**                | Custom Item       | `modid-mmcli-agent-modding:tnt_stick`<br>`TntStickItem`               | Single-stack throwable explosive stick with 5-tick anti-spam cooldown                                                     | [Section 11](FEATURES.md#11-tactical-ordnance-tnt-stick--frost-grenade-stick) |
| **TNT Projectile**           | Custom Entity     | `modid-mmcli-agent-modding:tnt_projectile`<br>`TntProjectileEntity`   | Server-authoritative projectile with smoke trail and 4.0F explosion                                                       | [Section 11](FEATURES.md#11-tactical-ordnance-tnt-stick--frost-grenade-stick) |
| **Frost Grenade Stick**      | Custom Item       | `modid-mmcli-agent-modding:frost_grenade_stick`<br>`FrostGrenadeStickItem` | Throwable cryogenic stick with 10-tick cooldown; flash-freezes water, turns lava to obsidian, and creates powder snow ring | [Section 11](FEATURES.md#11-tactical-ordnance-tnt-stick--frost-grenade-stick) |
| **Frost Projectile**         | Custom Entity     | `modid-mmcli-agent-modding:frost_projectile`<br>`FrostGrenadeEntity`  | Zero block damage projectile; inflicts 360 freezing ticks, Slowness III, and extinguishes fire with snowflake trails       | [Section 11](FEATURES.md#11-tactical-ordnance-tnt-stick--frost-grenade-stick) |
| **Client Rendering & GUIs**  | Visuals & UI      | `com.example.client.renderer.*`<br>`com.example.client.gui.*`         | 3-line overhead badges (squad, role, hearts health), 3D rotating wireframes, MinionScreen health plate & Command Hub      |   [Section 4 & 5](FEATURES.md#4-client-visuals-holograms--overhead-crest-badges)    |
| **Block Architecture**       | Registry System   | `ModBlocks`                                                           | Automated dual registration pairing `Registries.BLOCK` with `Registries.ITEM`                                             | [Section 9](FEATURES.md#9-dedicated-construction-block-subsystem-constructionblock) |
| **Workforce Operations**     | RTS Workforce     | `MinionBuildGoal`<br>`MassRolePayload`                                | Unrestricted building AI, 3D Arcane Levitation (scaffolding-free), automatic waypoint formations & mass archetype roles |      [Section 3 & 6](FEATURES.md#6-multiblock-construction--blueprint-engine)       |

---

#### Feature Pillar Spotlights

<details open>
<summary><b>👑 1. RTS Command & Tactical Army Systems</b></summary>
<br>

- **Loki Command Scepter** ([`CommandScepterItem`](FEATURES.md#1-loki-command-scepter-commandscepteritem)):
  - **Selective Waypoints with Auto-Deselect**: Right-click ground positions (or crosshair targeting up to 64 blocks away) to move _only_ minions currently selected and assigned to the active squad channel into tactical battle ranks. Upon dispatching to the waypoint station, units **automatically deselect** (`setSelected(false)`), clearing selection halos and freeing the commander's selection buffer for rapid subsequent unit micro-management without requiring manual deselection inputs.
  - **Direct Unit Selection**: Right-click an owned minion (or aim crosshair within 64 blocks) to toggle selection with audio/particle feedback (chime + hearts to select; bass + smoke to deselect).
  - **Squad Outlines**: Selected units glow with squad-specific team colors (Alpha Red, Bravo Blue, Charlie Green, Delta Gold, All White).
  - **Ranked Army Line Formations**: Ground waypoint pings and follow directives deploy minions into straight, parallel military battle ranks (Warriors forward in frontline lines, Sentinels in midline bulwark lines, Builders in rearguard support, Miners in deep logistics) rotated along commander line-of-sight yaw with an open central command corridor.
  - **Mass Archetype Role Assignment**: 4-button Mass Archetype bar in Command Hub (`CommandScepterScreen`) and `MassRolePayload` network protocol to instantaneously batch-convert selected units or squad channels into Warriors, Sentinels, Builders, or Miners.
  - **Banner of Courage (90° Forward Sector & Persistent Mass Assault Queue)**: Hold right-click to project an expanding 90° forward conical sector ($\pm 45^\circ$ FOV). Real-time highlights preview minions and hostile targets; releasing launches a coordinated **Mass Attack** assigning the full list of enclosed enemies to participating minions as a synchronized assault queue. Minions sequentially hunt and destroy every selected enemy in the sector until all are slain before returning to formation.
  - **Tactical Panic Retreat (Keybind `R`)**: Dedicated keybind `R` rings a warning bell, clears minion combat targets, cancels stationary guard posts, and recalls all minions at sprint speed back into formation.
  - **Command Hub GUI**: Pressing `V` (or Shift + Right-click) opens the interactive hub for squad switching, mode selection, mass role assignment, and the paginated blueprint catalog.
  - **Rapid Deselection**: In-world Sneak + Left-Click (in non-`BUILD` modes) or GUI **✕ Deselect** clears active unit selections instantly.
  - **Focus-Fire Pings**: Right-click hostile mobs to order squad-wide coordinated strikes.
  - **Sneak + Left-Click Rotation Cycling**: In `BUILD` mode, Sneak + Left-Click cycles blueprint rotation through 0° → 90° → 180° → 270° with chime audio and actionbar updates.

- **Autonomous Minion Thrall** ([`MinionEntity`](FEATURES.md#2-autonomous-minion-thralls-minionentity--spawn-egg)):
  - **Decoupled Guard Posture**: Idle/holding units stand upright at attention at their post at 100% height rather than dropping into a seated pose.
  - **Clean-Slate Spawning & Recruitment**: Freshly spawned or scepter-recruited thralls initialize with empty hands and empty 9-slot backpacks (zero equipment). Active role disarming strictly stows incompatible weapons.
  - **Persistent Inventory**: 9 inventory slots + 6 equipment slots managed via Sneak + Right-Click modal GUI.
  - **Friendly-Fire Immunity**: Custom damage gating prevents allied arrow fire, Sweeping Edge strikes, or accidental hits among teammates.
  - **4 Archetype Roles**: `WARRIOR` (versatile dual-class: frontline swordsman or ranged archer based on equipped weapon), `SENTINEL` (perimeter guard, shield bulwark & combat medic channeling the **Aegis of Restoration** to heal wounded allies under 70% HP within 10 blocks), `BUILDER` (architectural construction with Arcane Levitation hover flight), and `MINER` (excavation and demolition).
  - **Extended Operational Leash**: `64.0D` base tracking radius (`GENERIC_FOLLOW_RANGE`) and `64.0D` emergency teleport threshold, allowing units to engage distant hostiles and maneuver across terrain without snapping back to the commander.
  - **Universal Arcane Levitation Traversal & Obstacle Vaulting**: All minions possess universal 3D Arcane Levitation mobility. When navigating across extreme vertical elevation gaps (descending off high cliffs/buildings with $\Delta Y < -1.5\text{D}$ or ascending onto high ledges/cliffs with $\Delta Y > 1.25\text{D}$) or encountering pathfinding stalls ($\ge 4$ ticks), thralls seamlessly engage 3D Arcane Levitation flight with purple and cyan rune particle spirals (`PORTAL` + `ENCHANT`), gliding straight to their commander, waypoint, or combat destination with zero fall damage. Builders also retain continuous 3D hover flight for scaffold-free multiblock assembly.
  - **Singleplayer Host Auto-Adoption**: Server-safe ownership evaluation automatically adopts and rebinds tamed minions to the host player across client/server restarts.
  - **Persistent Assault Target Chaining (`assaultTargets`)**: Slaying a mob automatically triggers `acquireNextAssaultTarget()`, chaining to the nearest alive hostile in the 90° sector queue within 48 blocks at 1.35D sprint speed until all targets are eliminated. Panic retreat (`R`), ground waypoints, and hold orders safely flush the queue.
  - **Dual Hearts Health Display**: Real-time hearts health visualization featuring 10 proportional heart glyphs (`§c❤` filled, `§8❤` empty) and numerical HP ratios rendered both overhead in-world and on the central GUI preview panel.

- **Tactical Army Hierarchy** ([`SquadGroup`](FEATURES.md#3-tactical-army-architecture-roles-squads--formations)):
  - Flexible routing across `ALL` or dedicated squads (`ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`) with network synchronization via custom Fabric C2S packets.
  </details>

<details open>
<summary><b>🏗️ 2. Autonomous Multiblock & Demolition Engine</b></summary>
<br>

- **Construction Manager** ([`ConstructionManager`](FEATURES.md#6-multiblock-construction--blueprint-engine)):
  - Server-authoritative session tracking for `BUILD` and `DISMANTLE` modes with holographic bounding-box particles (`GLOW`/`PORTAL` for build; `FLAME`/`CRIT` for dismantle).
  - **Persistent 3D Hologram Outlines**: Broadcasts `SyncConstructionSessionPayload` and `EndConstructionSessionPayload` to client trackers, keeping the 3D bounding wireframe and ghost blocks visible in-world until minions finish the build.
  - Supports both Creative zero-cost mode and Survival inventory drops/scavenging.

- **Arcane Builder Levitation & Scaffolding-Free Construction** ([`MinionBuildGoal`](FEATURES.md#6-multiblock-construction--blueprint-engine)):
  - Builder minions possess full 3D Arcane Levitation flight and hover capabilities, flying smoothly to optimal stations ($1.4\text{D} \to 1.8\text{D}$) adjacent to elevated target blocks without generating temporary scaffolding columns.
  - Eliminates scaffolding clutter, suffocating blocks, and climbing hitches. Builders hover stably in mid-air with portal/enchant rune particles, place/dismantle blocks, chain elevated tasks, and gently float down to earth upon completion.
  - Sapper infantry bridging (`MinionSapperGoal`) and `ConstructionBlock` traversal across chasms remain fully intact.

- **Blueprint Catalog & Topological Sorting** ([`BlueprintRegistry`](FEATURES.md#6-multiblock-construction--blueprint-engine)):
  - Pre-engineered structures: _Overlord Watchtower_ (7×7×9), _Arcane Obelisk_ (5×5×8), and _Defensive Barricade_ (9×3×3).
  - Deterministic bottom-up topological sorting ensures foundations, pillars, and inverted stair arches are constructed prior to upper dependent blocks.
  - **4-Quadrant Rotation Engine**: Full origin $(0, 0)$ rotation matrices (0°, 90°, 180°, 270°) with automatic BlockState rotation, bounding box recalculation, and topological re-sorting.
  - **Door Offset Discovery & Sparkle Beams**: Automatically discovers lower door coordinates, projecting vertical sparkle beams (`HAPPY_VILLAGER` + `END_ROD`) and HUD actionbar door direction readouts.

- **Structure Deconstruction & Mining Area Clearance** ([`ConstructionSession`](FEATURES.md#7-structure-deconstruction-mining-area-clearance--bedrock-immunity)):
  - **Full Selected Area Clearance**: In `MINE` mode, sessions scan the entire 3D selected volume from top to bottom, queuing all non-air destructible blocks (natural stone, ores, dirt, wood, structures).
  - **Zero Air-Mining Guarantee**: Air blocks are excluded and automatically skipped without minions pathfinding to or swinging at empty space.
  - **Ground-Anchoring & Fiery Preview**: Clicking in `MINE` mode anchors directly at the clicked ground coordinate and renders a fiery orange/red 3D wireframe crosshair preview.
  - **Automatic Wireframe Dismissal**: The moment the selected area is 100% cleared, the session concludes with celebratory fanfare and particles, and the highlighted wireframe immediately disappears.
  - **Survival Drops vs. Creative Demolition**: In Survival mode, broken blocks drop as collectible items in the world for full resource recovery. In Creative mode, blocks are cleared cleanly without spawning entity drops, preventing world and inventory clutter during large excavations (see [COMMANDS.md Section 10](COMMANDS.md#10-survival-vs-creative-mode-mechanics) and [FEATURES.md Section 13](FEATURES.md#13-survival-vs-creative-mode-economy--mechanics)).
  - **Bedrock & Indestructible Block Immunity**: Strictly checks `currentState.isOf(Blocks.BEDROCK) || currentState.getHardness(...) < 0.0F`, preventing minions from breaking bedrock, barrier blocks, command blocks, or void boundaries. Plays anvil hit SFX (`BLOCK_ANVIL_HIT`), emits smoke, and safely completes tasks without world damage. With builders utilizing 3D Arcane Levitation, temporary scaffolding generation and cleanup are completely retired, preventing any unintended block modifications or ground corruption.

- **Combat Sappers & Traversal Scaffolding** ([`MinionSapperGoal`](FEATURES.md#8-combat-sappers--ephemeral-scaffolding)):
  - Detects $\ge 2$-block drops ahead and bridges chasms up to 6 blocks wide.
  - Builds vertical climbing shafts up to 6 blocks high when confronting steep cliffs.
  - **Ceiling Clearance & Headroom Avoidance**: Scans the climbing shaft for overhead ceilings and requires 2 blocks of clear headroom at ledge landings and across ravine bridges, preventing sappers from deploying into low ceilings.
  - **Overhead Collision & Stall Sensors**: Scans `headPos = minion.getBlockPos().up(2)` during ascent to abort instantly upon ceiling contact; triggers safe abort if vertical progress stalls ($< 0.02\text{D}$ for $> 20$ ticks) or total climb exceeds 120 ticks.
  - **Multi-Minion Column Spacing**: Claims unique column coordinates through `TraversalScaffoldingManager.claimClimbingColumn`, eliminating crowding collisions on climbing shafts.
  - Ephemeral scaffolding auto-decays after 400 ticks (20s) with occupancy detection extending life by +40 ticks while units cross.

- **Dedicated Construction Block Architecture** ([`ModBlocks.CONSTRUCTION_BLOCK`](FEATURES.md#9-dedicated-construction-block-subsystem-constructionblock)):
  - High-performance, temporary structural block (`modid-mmcli-agent-modding:construction_block`) eliminating vanilla scaffolding's horizontal collapse limit (can span ravines of arbitrary width).
  - Context-sensitive collision shape (`VoxelShapes.empty()` inside/descending for friction-free climbing; solid 2-pixel top platform when standing above) paired with non-suffocating block settings and in-wall damage immunity.
  - Configured with `0.2F` hardness, `BlockSoundGroup.SCAFFOLDING`, client Cutout render layer, and `.dropsNothing()` to ensure clean, zero-item-litter demolition.
  - Seamlessly recognized by both `MinionBuildGoal` and `MinionSapperGoal`.
  </details>

<details open>
<summary><b>💥 3. Tactical Ordnance & Combat Entities</b></summary>
<br>

- **TNT Stick** ([`TntStickItem`](FEATURES.md#11-tactical-ordnance-tnt-stick--frost-grenade-stick)):
  - `EPIC` rarity Combat item. Right-click plays `ENTITY_TNT_PRIMED` sound, launches projectile at 1.5 velocity, and triggers a 5-tick (0.25s) anti-spam cooldown.

- **TNT Projectile** ([`TntProjectileEntity`](FEATURES.md#11-tactical-ordnance-tnt-stick--frost-grenade-stick)):
  - Aerodynamic projectile with smoke trail and flame spark effects; detonates on server collision with a 4.0F explosion.

- **Frost Grenade Projectile Stick** ([`FrostGrenadeStickItem`](FEATURES.md#11-tactical-ordnance-tnt-stick--frost-grenade-stick)):
  - `RARE` rarity Combat item with 10-tick (0.5s) cooldown. Launches a cryogenic projectile leaving snowflake trails.
  - Causes zero block damage; flash-freezes water to ice, turns lava to obsidian, extinguishes fires, deploys a perimeter ring of powder snow, and inflicts 360 freezing ticks and Slowness III on caught entities.
  </details>

<details open>
<summary><b>🎨 4. Client Visuals, Overhead Crests & UI Pipeline</b></summary>
<br>

- **Overhead Crest Feature Renderer & Hearts Display** ([`MinionOverheadBadgeFeatureRenderer`](FEATURES.md#4-client-visuals-holograms--overhead-crest-badges)):
  - Billboards 3 distinct information lines above minion heads using exact LIFO matrix reversal: Squad Banner with gold star (`§6★ `) selection markers, Role Crest with stationed `[HOLD]` status, and dynamic Hearts Health Bar (`§c❤❤❤❤❤§8❤❤❤❤❤ §f20/40`).
  - Powered by vanilla `LivingEntity.HEALTH` tracked data with proportional 10-heart scaling and boundary safeguards (damaged units never appear full; living units never appear empty).
  - Rendered with `LightmapTextureManager.MAX_LIGHT_COORDINATE` for crisp, fullbright legibility in deep caves and night raids.

- **Custom Screen Interfaces**:
  - `MinionScreen`: Framed biped equipment modal with 6 dedicated equipment slots, 9-slot backpack inventory (streamlined with redundant Equipment header removed), 3D entity preview with prominent bottom hearts health plate, role/squad status, dual-row action bar (Teleport to Me, `§c✖ Destroy`, and `Cancel`), and **Smart Shift-to-Close** with item transfer latching (`SmartCloseHandler`).
  - `CommandScepterScreen`: Interactive Command Hub displaying real-time selected unit counts, formation controls, blueprint preview thumbnails, and a one-click **✕ Deselect** button with fast Shift dismissal.
  - `BlueprintHologramRenderer`: Translucent neon-cyan 3D wireframes rotating synchronously in real time with the active scepter rotation.
  </details>

---

### 🌟 The 6 Architectural Refinements

The codebase incorporates five foundational engineering refinements designed to maximize operational stability, eliminate edge-case crashes, and deliver seamless tactile UX:

1. **Refinement 1 — Singleplayer Host Ownership Auto-Adoption (Server-Safe)**:
   - Server-side singleplayer host validation (`server.isSingleplayer() && server.isHost(...)`) in `MinionEntity.isOwner` adopts tamed thralls if offline development UUIDs change across client restarts.
   - Strictly enforces server-safe invariants: zero imports of `MinecraftClient` in common code (`src/main/java`), completely preventing dedicated server crashes.
2. **Refinement 2 — 3D Arcane Builder Levitation & Scaffolding Retirement**:
   - Builder minions hover and fly in 3D air space ($1.4\text{D} \to 1.8\text{D}$) adjacent to target blocks at any elevation, with swirling portal and enchant rune particles and fall damage immunity.
   - Completely retires temporary scaffolding column generation, multi-minion column reservations, and climbing/descent state machines, eliminating block suffocation and terrain clutter.
   - Sapper infantry bridging (`MinionSapperGoal`) across chasms and ravines remains fully intact.
3. **Refinement 3 — Synchronous 3D Holographic Wireframe Rotation**:
   - `BlueprintHologramRenderer` dynamically rotates blueprints (`blueprint.rotate(rotation)`) using the active scepter rotation component before rendering.
   - Guarantees that neon-cyan wireframes, yellow anchor boxes, and ghost blocks align with in-world particle guides and server-side placement.
4. **Refinement 4 — Smart Shift-to-Close with Item Transfer Latching (`MinionScreen`)**:
   - Tracks `slotClickedWithShift`: clicking inventory slots while holding Shift latches item transfer mode, ensuring releasing Shift after a `quickMove` does **NOT** close the screen.
   - Clean Shift taps without slot clicks dismiss the modal instantly; `'E'` and `Escape` provide universal fast exit.
5. **Refinement 5 — Sneak + Left-Click Blueprint Rotation Cycling & Door Sparkle HUD**:
   - Sneak + Left-Click in `BUILD` mode cycles rotation through $0^\circ \to 90^\circ \to 180^\circ \to 270^\circ$, updating client and server state seamlessly.
   - Scepter `inventoryTick` performs 64-block crosshair raycasting, projecting rotating perimeter particles, vertical door sparkle beams (`HAPPY_VILLAGER` + `END_ROD`), and a real-time HUD actionbar readout (`§6🏗 [Name] §8| §bRotation: [Deg]° §8| §a🚪 Door: [Dir]`).
6. **Refinement 6 — Bedrock Deconstruction Immunity & Sapper Ceiling Avoidance**:
   - Multi-tiered indestructible block immunity protects Bedrock, Barrier, End Portal, and Command Blocks across session task generation, task readiness, and dismantling execution, while builders leverage 3D Arcane Levitation without generating temporary scaffolding.
   - Sapper AI enforces upward shaft ceiling scans, 2-block ledge landing headroom checks, direct overhead ceiling collision sensors (`up(2)`), and vertical stall detection ($> 20$ ticks stall abort), preventing minions from ever hitting ceilings or stalling.
   - Dedicated `ModBlocks.CONSTRUCTION_BLOCK` provides solid-top support, infinite horizontal bridging stability, and zero-drop demolition.

---

## Quick Install Guide (For Players & Non-Technical Users)

Want to play with this mod in your regular Minecraft game? Follow these simple steps:

### 1. Requirements

- **Minecraft Java Edition** installed via the official Minecraft Launcher.
- **Fabric Loader 0.16.10+** for Minecraft **1.21**.
- **Fabric API** for Minecraft **1.21**.

### 2. Step-by-Step Installation

1. **Install Fabric Loader**:
   - Download the installer from [fabricmc.net/use/installer](https://fabricmc.net/use/installer/).
   - Run the installer, select version **1.21**, check **Create profile**, and click **Install**.
2. **Download Fabric API**:
   - Download the Minecraft 1.21 release from [Modrinth](https://modrinth.com/mod/fabric-api) or [CurseForge](https://curseforge.com/minecraft/mc-mods/fabric-api).
3. **Get the Mod JAR**:
   - Grab the compiled mod JAR (`fabric-mmcli-agent-modding-1.0.0.jar`) from the project releases or `build/libs/`.
4. **Copy to your `mods` Folder**:
   - **Windows**: Press `Win + R`, paste `%appdata%\.minecraft\mods`, and hit Enter. Copy both JAR files here.
   - **macOS**: In Finder, press `Cmd + Shift + G`, paste `~/Library/Application Support/minecraft/mods`, and copy both JAR files here.
   - **Linux**: Copy both JAR files into `~/.minecraft/mods/`.
5. **Play**:
   - Open the Minecraft Launcher, select the **fabric-loader-1.21** installation, and click **Play**!

---

## Quick Setup Guide (For Developers & Contributors)

Want to fork the project, experiment with code, or run the mod locally from source? Here is your quickstart:

### 1. Prerequisites

- **JDK 21 or newer** (Required by Minecraft 1.20.5+ and 1.21).
  ```bash
  java -version
  ```
- **Git** installed.

### 2. Clone & Launch Sandbox

```bash
# Clone your fork
git clone https://github.com/<your-username>/minecraft-modding.git
cd minecraft-modding

# Run the 220 automated unit tests
./gradlew test

# Launch the game development sandbox (runs Minecraft client with mod active)
./gradlew runClient
```

### 3. Build Production Mod JAR

```bash
# Compiles, processes resources, and remaps to production JAR
./gradlew build
```

Your remapped, ready-to-share mod JAR will be located at:
`build/libs/fabric-mmcli-agent-modding-1.0.0.jar`

---

## 1. Architectural Overview

Fabric provides a modular, lightweight modding stack designed for fast compilation, minimal overhead, and rapid updates across Minecraft versions:

| Component         | Role               | Purpose                                                                                                           |
| :---------------- | :----------------- | :---------------------------------------------------------------------------------------------------------------- |
| **Fabric Loader** | Runtime Core       | Loads mods into the game process, manages Mixin transformations, and resolves dependencies.                       |
| **Fabric API**    | Essential Library  | Hook library providing standard event listeners, registry wrappers, network channels, and rendering utilities.    |
| **Fabric Loom**   | Gradle Plugin      | Automates Minecraft deobfuscation, handles Yarn mapping generation, injects dev runs, and remaps production JARs. |
| **Yarn Mappings** | Deobfuscation      | Clean, open-source community mappings translating obfuscated Minecraft bytecode into human-readable symbols.      |
| **Sponge Mixin**  | Bytecode Injection | Injects custom logic directly into Minecraft's runtime classes at class-load time without altering binary files.  |

---

## 2. Prerequisites & Environment Setup

- **Java Development Kit (JDK)**: **Java 21 or newer** (Required for Minecraft 1.20.5+ and 1.21+).
  - Verify your active version:
    ```bash
    java -version
    ```
- **IDE**:
  - **IntelliJ IDEA** (Recommended): Install the **Minecraft Development** plugin. Import the root `build.gradle` as a Gradle project.
  - **Visual Studio Code**: Install the **Extension Pack for Java** and **Gradle for Java** extensions.
- **Gradle**: Provided directly through the project wrapper (`./gradlew`). No standalone Gradle installation is needed.

---

## 3. Project Structure

```text
minecraft-modding/
├── build.gradle                               # Build script & Loom configuration
├── settings.gradle                            # Gradle build settings & repositories
├── gradle.properties                          # Dependency versions (Minecraft, Yarn, Loader, Fabric API)
├── gradlew / gradlew.bat                      # Gradle wrapper executables
├── gradle/wrapper/                            # Gradle wrapper binaries & properties
├── FEATURES.md                                # Comprehensive showcase of custom items, entities & mechanics
└── src/
    ├── main/                                  # Common (client + dedicated server) code & resources
    │   ├── java/com/example/
    │   │   ├── block/
    │   │   │   └── ModBlocks.java             # Block registry & automatic BlockItem registration
    │   │   ├── blueprint/
    │   │   │   ├── BlueprintBlock.java        # Block record with bottom-up topological sorting
    │   │   │   ├── BlueprintRegistry.java     # Curated structure blueprints (Watchtower, Obelisk, Barricade)
    │   │   │   └── StructureBlueprint.java    # Blueprint structure model & builder
    │   │   ├── component/
    │   │   │   ├── CommandMode.java           # Scepter mode enum with Codec & PacketCodec serialization
    │   │   │   ├── ModDataComponents.java     # 1.21 Data Component registration (COMMAND_MODE, ACTIVE_BLUEPRINT, TARGET_SQUAD)
    │   │   │   └── SquadGroup.java            # Squad channels (ALL wildcard, ALPHA, BRAVO, CHARLIE, DELTA)
    │   │   ├── construction/
    │   │   │   ├── ConstructionManager.java       # Server singleton managing active construction sessions & VFX
    │   │   │   ├── ConstructionSession.java       # Session state, bounding boxes, and worker leasing
    │   │   │   ├── ConstructionTask.java          # Individual block placement task
    │   │   │   └── TraversalScaffoldingManager.java# Ephemeral sapper scaffolding decay & entity safety manager
    │   │   ├── entity/
    │   │   │   ├── ai/
    │   │   │   │   ├── goal/
    │   │   │   │   │   ├── MinionActiveTargetGoal.java # Role-filtered aggressive target acquisition
    │   │   │   │   │   ├── MinionBuildGoal.java        # Builder role-gated autonomous construction AI
    │   │   │   │   │   ├── MinionFormationFollowGoal.java # Parametric squad formation offsets & pacing
    │   │   │   │   │   ├── MinionRangedAttackGoal.java # Warrior ranged archery strafing & skirmish AI
    │   │   │   │   │   ├── MinionSapperGoal.java       # Combat sapper chasm bridging & cliff ascent AI
    │   │   │   │   │   ├── SentinelGuardGoal.java      # Sentinel anchor tethering & 12-block leash AI
    │   │   │   │   │   └── WaypointHoldGoal.java       # Non-sentinel waypoint anchor holding AI
    │   │   │   │   └── pathing/
    │   │   │   │       ├── MinionNavigation.java       # Scaffolding-aware ground pathfinding
    │   │   │   │       └── MinionPathNodeMaker.java    # Scaffolding node evaluator
    │   │   │   ├── custom/
    │   │   │   │   ├── MinionEntity.java      # Tameable thrall with roles, squads, equipment & inventory
    │   │   │   │   ├── MinionRole.java        # Archetype roles (WARRIOR, SENTINEL, BUILDER, MINER)
    │   │   │   │   └── TntProjectileEntity.java # Explosive projectile entity with smoke/flame particle trails
    │   │   │   └── ModEntities.java           # EntityType registration, hitboxes & spawn groups
    │   │   ├── item/
    │   │   │   ├── custom/
    │   │   │   │   ├── CommandScepterItem.java # Loki Command Scepter (modes, blueprints, squad channels)
    │   │   │   │   ├── MinionSpawnEggItem.java # Custom spawn egg with auto-tame on spawn
    │   │   │   │   └── TntStickItem.java      # Handheld launcher item with sound & cooldown handling
    │   │   │   └── ModItems.java              # Item registry & creative tab integration
    │   │   ├── network/
    │   │   │   ├── DismissMinionPayload.java     # C2S minion dismissal packet
    │   │   │   ├── EndConstructionSessionPayload.java # S2C wireframe dismissal packet upon completion
    │   │   │   ├── MassRolePayload.java          # C2S mass archetype role assignment packet
    │   │   │   ├── ModNetworking.java            # Networking registry & server receivers
    │   │   │   ├── RetreatPayload.java           # C2S tactical panic retreat packet (Keybind R)
    │   │   │   ├── SyncConstructionSessionPayload.java # S2C active session wireframe synchronization
    │   │   │   ├── TeleportMinionPayload.java    # C2S minion recall/teleportation packet
    │   │   │   ├── UpdateMinionConfigPayload.java# C2S minion role and squad update packet
    │   │   │   └── UpdateScepterPayload.java     # C2S scepter mode, blueprint, and squad channel packet
    │   │   ├── screen/
    │   │   │   ├── MinionScreenHandler.java   # Extended screen handler for minion inventory/equipment
    │   │   │   └── ModScreenHandlers.java     # ScreenHandlerType registration
    │   │   ├── mixin/
    │   │   │   └── ExampleMixin.java          # Bytecode injection into MinecraftServer lifecycle
    │   │   └── ExampleMod.java                # ModInitializer entrypoint bootstrapping registries & tick events
    │   └── resources/
    │       ├── assets/modid-mmcli-agent-modding/
    │       │   ├── lang/
    │       │   │   └── en_us.json             # Translation keys (Items, Entities, Modes, Roles, Squads)
    │       │   ├── models/item/
    │       │   │   ├── tnt_stick.json         # Handheld TNT Stick model
    │       │   │   ├── command_scepter.json   # Handheld Loki Command Scepter model
    │       │   │   └── minion_spawn_egg.json  # Template spawn egg model
    │       │   └── textures/item/             # Item texture asset storage
    │       ├── fabric.mod.json                # Mod metadata, entrypoints, and dependency rules
    │       └── modid.mixins.json              # Mixin configuration and rules
    ├── client/                                # Client-only code & resources (split environment)
    │   └── java/com/example/client/
    │       ├── gui/
    │       │   ├── CommandScepterScreen.java  # Interactive Command Hub with squad channel selection bar
    │       │   └── MinionScreen.java          # Minion GUI with 3D entity preview & role/squad buttons
    │       ├── network/
    │       │   └── ModClientNetworking.java   # Client C2S packet dispatchers
    │       ├── renderer/
    │       │   ├── BlueprintHologramRenderer.java # 3D blueprint ghost-block & mine wireframe preview
    │       │   ├── ClientConstructionTracker.java # Client-side active session tracking cache
    │       │   ├── MinionClothingFeatureRenderer.java # Outer biped clothing layers
    │       │   ├── MinionEntityRenderer.java  # Biped renderer with armor, clothing, and held item feature layers
    │       │   └── TntProjectileRenderer.java # FlyingItemEntityRenderer for 3D spinning projectile in flight
    │       └── ExampleModClient.java          # ClientModInitializer registering renderers & screens
    └── test/                                  # Unit testing suite (220 unit tests across 20 suites)
        └── java/com/example/
            ├── block/
            │   └── ConstructionBlockTest.java # Voxel shape, solid-top support & zero-drop demolition
            ├── blueprint/
            │   ├── BlueprintRotationTest.java # 4-quadrant rotation matrix & door discovery tests
            │   └── ScaffoldingTest.java       # Scaffolding retirement and Arcane Levitation verification tests
            ├── client/
            │   ├── gui/
            │   │   ├── CommandScepterScreenCloseTest.java # Shift-to-close open-state guard tests
            │   │   ├── CommandScepterScreenRoleSelectionTest.java # 4-role GUI buttons & toggle states
            │   │   └── MinionScreenCloseTest.java # Smart Shift-to-close latching tests
            │   └── renderer/
            │       └── MinionOverheadBadgeTest.java # Squad banners, Roman numerals, and role crest tests
            ├── construction/
            │   ├── BedrockAndCeilingSafeguardTest.java # Bedrock immunity & ceiling avoidance tests
            │   ├── MinerAreaAndAirSafeguardTest.java # Area-wide excavation, zero air-mining, and auto-dismissal
            │   └── StructureDismantlingTest.java # Reverse topological sorting & dismantle prerequisites
            ├── entity/
            │   ├── ArcaneLevitationAndSectorTest.java # 3D flight & 90° forward sector math tests
            │   ├── FrostGrenadeTest.java      # Frost grenade fluid freeze, powder snow, debuffs & tests
            │   ├── MinionAssaultTargetChainingTest.java # 90° assault queue chaining, multi-target eradication & retreat clearing
            │   ├── MinionFormationAndEquipTest.java # Ranked army line formations & warrior equipment duality
            │   ├── MinionOwnershipTest.java   # Singleplayer host auto-adoption tests
            │   ├── MinionSapperAndScaffoldingTest.java # Sapper bridging, climbing shafts, and decay safety
            │   ├── MinionSquadAndRoleTest.java# Roles, squads, serialization, and leash logic
            │   ├── SentinelHealGoalTest.java  # Sentinel Aegis of Restoration healing AI & priority tests
            │   └── WaypointFormationAndMassRoleTest.java # Waypoint rank stations & MassRolePayload tests
            ├── item/
            │   ├── CommandScepterRaycastTargetingTest.java # 64-block crosshair raycast & targeting math
            │   └── CommandScepterRotationTest.java # Scepter rotation cycling & door beacon tests
            └── network/
                └── NetworkingPayloadTest.java # C2S and S2C packet records, codecs, and compatibility
```

---

## 4. In-Development Lifecycle (`./gradlew runClient`)

### Why the Official Launcher Is Not Used During Active Coding

During active development, **you do not need the official Minecraft Launcher**.

Fabric Loom sets up a dedicated, sandboxed Minecraft instance in the `./run` folder with:

- Automatic deobfuscation using Yarn mappings (clean class, method, and field names).
- Classpath injection of your mod source code directly without creating a `.jar` first.
- Full IDE debugger attachment with hot code replacement and breakpoint support.
- Isolated save games, configurations, and logs that never alter your personal `.minecraft` directory.

### Launching the Development Client

Run the following command in the workspace root:

```bash
./gradlew runClient
```

Loom will download the vanilla Minecraft client, download Yarn mappings, map the game classes to readable names, merge in Fabric Loader and Fabric API, and launch the client window.

### Launching a Headless Dedicated Server

To verify server-side behavior, network packets, or server-only mixins:

```bash
./gradlew runServer
```

_(Accept the Minecraft EULA when prompted in `run/eula.txt` by setting `eula=true`.)_

### IDE Debugging Workflow

- **IntelliJ IDEA**: Loom automatically creates `Minecraft Client` and `Minecraft Server` run configurations under the Gradle run menu. Click the **Debug (green bug)** icon to step through breakpoints inside `ExampleMod.java` or `ExampleMixin.java`.
- **VS Code**: Use the Gradle sidebar task `loom > runClient` or configure a `.vscode/launch.json` targeting the Gradle `runClient` task.

---

## 5. Fabric API Configuration

Most gameplay mechanics (adding items, blocks, entity renderers, biomes, or events) rely on **Fabric API**.

### 1. In-Project Dependency (`gradle.properties` & `build.gradle`)

The template pre-configures Fabric API as a compile and runtime dependency.

In `gradle.properties`:

```properties
minecraft_version=1.21
yarn_mappings=1.21+build.9
loader_version=0.16.10
fabric_version=0.100.4+1.21
```

In `build.gradle`:

```groovy
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"

    // Pulls the full Fabric API bundle into your dev environment
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}
```

### 2. Declaring Runtime Dependencies (`fabric.mod.json`)

To ensure players running your mod have the matching Fabric API installed, declare it in `src/main/resources/fabric.mod.json`:

```json
"depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21",
    "java": ">=21",
    "fabric-api": "*"
}
```

If a player attempts to launch without Fabric API or on an incompatible version, Fabric Loader will stop launch and display an actionable error message identifying the missing dependency.

---

## 6. Building the Mod JAR (`./gradlew build`)

When you are ready to distribute or test your mod in a real game launcher:

```bash
./gradlew build
```

### What Happens During Build?

1. **Compilation**: Java files in `src/main` and `src/client` are compiled against Yarn mappings.
2. **Resource Processing**: `${version}` placeholders in `fabric.mod.json` are populated with `mod_version` from `gradle.properties`.
3. **Remapping (`remapJar`)**: Fabric Loom remaps the compiled bytecode from development (Yarn) mappings to **Intermediary** mappings (the standard runtime mappings shared across all Fabric mods).
4. **Artifact Packaging**: The final production JAR is generated in `build/libs/`.

### Build Outputs (`build/libs/`):

- `fabric-mmcli-agent-modding-1.0.0.jar`: **The distributable production mod.** This is the file installed in the Minecraft launcher.
- `fabric-mmcli-agent-modding-1.0.0-sources.jar`: The source code archive (useful when publishing libraries for other modders).

---

## 7. Official Minecraft Launcher Deployment Workflow

Follow these steps to run your compiled mod inside the official Minecraft Launcher alongside Fabric Loader:

### Step 1: Install Vanilla Minecraft 1.21

1. Open the **official Minecraft Launcher**.
2. Go to the **Installations** tab.
3. Verify that **Minecraft 1.21** (Latest Release) is installed. Launch it once to the title screen and exit so all core assets are downloaded.

### Step 2: Install the Fabric Loader

1. Download the Fabric Installer from [fabricmc.net/use/installer](https://fabricmc.net/use/installer/).
2. Run the installer:
   - Select the **Client** tab.
   - **Minecraft Version**: `1.21`
   - **Loader Version**: Select the latest stable version (e.g., `0.16.10`).
   - Leave the default `.minecraft` folder location selected.
   - Check **Create profile** and click **Install**.
3. Re-open or restart the Minecraft Launcher. A new installation profile named **fabric-loader-1.21** will appear in your launcher list.

### Step 3: Download Fabric API JAR

Fabric Loader only provides the core injection harness. Most mods require the separate Fabric API mod JAR:

1. Download the matching **Fabric API** build for **Minecraft 1.21** from:
   - [Modrinth: Fabric API](https://modrinth.com/mod/fabric-api)
   - [CurseForge: Fabric API](https://curseforge.com/minecraft/mc-mods/fabric-api)
2. Keep the downloaded file (e.g., `fabric-api-0.100.4+1.21.jar`).

### Step 4: Deploy Your Mod JAR

Copy both **your compiled mod JAR** and the **Fabric API JAR** into your Minecraft `mods` folder:

#### Platform Directory Paths:

- **macOS**:
  ```bash
  mkdir -p ~/Library/Application\ Support/minecraft/mods
  cp build/libs/fabric-mmcli-agent-modding-1.0.0.jar ~/Library/Application\ Support/minecraft/mods/
  cp ~/Downloads/fabric-api-*.jar ~/Library/Application\ Support/minecraft/mods/
  ```
- **Windows**:
  - Path: `%APPDATA%\.minecraft\mods\`
  - PowerShell:
    ```powershell
    New-Item -ItemType Directory -Force -Path "$env:APPDATA\.minecraft\mods"
    Copy-Item "build\libs\fabric-mmcli-agent-modding-1.0.0.jar" "$env:APPDATA\.minecraft\mods\"
    Copy-Item "$env:USERPROFILE\Downloads\fabric-api-*.jar" "$env:APPDATA\.minecraft\mods\"
    ```
- **Linux**:
  ```bash
  mkdir -p ~/.minecraft/mods
  cp build/libs/fabric-mmcli-agent-modding-1.0.0.jar ~/.minecraft/mods/
  cp ~/Downloads/fabric-api-*.jar ~/.minecraft/mods/
  ```

### Step 5: Launch & Verify

1. In the Minecraft Launcher, choose the **fabric-loader-1.21** profile.
2. Click **PLAY**.
3. Verify your mod loaded:
   - Check `logs/latest.log` in your Minecraft directory for the initialization message:
     ```text
     [main/INFO] (modid-mmcli-agent-modding) Initializing Fabric 1.21 Example Mod: modid-mmcli-agent-modding
     [main/INFO] (modid-mmcli-agent-modding) Registering Mod Data Components for modid-mmcli-agent-modding
     [main/INFO] (modid-mmcli-agent-modding) Registering Mod Items for modid-mmcli-agent-modding
     [main/INFO] (modid-mmcli-agent-modding) Registering Mod Blocks for modid-mmcli-agent-modding
     [main/INFO] (modid-mmcli-agent-modding) Registering Mod Entities for modid-mmcli-agent-modding
     [Render thread/INFO] (modid-client) Initializing Fabric 1.21 Example Mod Client
     ```
   - If you have [Mod Menu](https://modrinth.com/mod/modmenu) installed, open the **Mods** button on the title screen to verify **Example Mod (`modid-mmcli-agent-modding`)** is listed.

---

## 8. Common Troubleshooting & FAQs

### Error: `Incompatible mod set!` or `Mod 'modid' requires version X of fabric-api`

- **Cause**: The Fabric API `.jar` is missing from your `mods/` directory, or its version is mismatched with the target Minecraft version.
- **Fix**: Download the exact Fabric API `.jar` matching your Minecraft version (`1.21`) and ensure it is placed in the `mods/` folder.

### Error: `UnsupportedClassVersionError: ... (class file version 65.0)`

- **Cause**: Minecraft 1.21 requires Java 21 (class version 65.0). The launcher or IDE is attempting to run with an older Java version (e.g., Java 17 or Java 8).
- **Fix**:
  - For the official launcher: Go to **Installations** → **fabric-loader-1.21** → **Edit** → **More Options** → **Java Executable** and browse to your Java 21 binary (`bin/java`).
  - For Gradle CLI: Ensure `JAVA_HOME` points to your JDK 21 installation.

### Cache Corruption or Stale Mappings

If Gradle fails after updating dependencies or mappings in `gradle.properties`:

```bash
./gradlew clean --refresh-dependencies
./gradlew build
```

---

## 9. Useful Developer Resources

- [Fabric Official Documentation](https://fabricmc.net/wiki/)
- [Fabric Meta API](https://meta.fabricmc.net/) (Lookup game versions, loader versions, and yarn mappings)
- [Fabric Loom Documentation](https://fabricmc.net/wiki/documentation:loom)
- [Fabric Discord Server](https://discord.gg/v6v4pMv)
