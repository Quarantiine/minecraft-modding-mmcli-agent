# Fabric 1.21 Mod Development & Deployment Guide

A modern Minecraft 1.21 mod template built using the Fabric toolchain: **Fabric Loader**, **Fabric API**, and **Gradle Loom** with **Yarn** mappings.

This guide outlines the end-to-end mod development lifecycle, detailing how the development sandbox operates via Gradle Loom, how to configure Fabric API, and how to build and deploy your compiled mod JAR into the official Minecraft Launcher.

---

## Features & Added Content

This mod builds on the Fabric foundation with custom gameplay mechanics implemented using Fabric's recommended domain architecture. Custom features are organized into dedicated packages (`item`, `entity`, `block`, `component`, `blueprint`, `construction`, and client `renderer`) with decoupled registry lifecycles and asset schemas.

> 📖 **Comprehensive Feature Guide**: For full mechanical specifications, entity physics details, explosion parameters, and architecture patterns, explore the dedicated [**FEATURES.md**](FEATURES.md) showcase.

### High-Level Content Overview

The mod's custom gameplay mechanics are organized across four core pillars: RTS unit command, multiblock engineering, tactical explosives, and client GUI/rendering pipelines.

#### Quick Reference Matrix

| Feature                      | Domain            | Identifier / Class                                                    | Core Capability                                                                                                 |                                                           Specs                                                            |
| :--------------------------- | :---------------- | :-------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------: |
| **Loki Command Scepter**     | Custom Item       | `modid-mmcli-agent-modding:command_scepter`<br>`CommandScepterItem`   | 32-block raycast unit selection, ground waypoints, rally ring, 4-quadrant rotation & door sparkle HUD           |                             [Section 2](FEATURES.md#2-loki-command-scepter-commandscepteritem)                             |
| **Minion Thrall**            | Custom Entity     | `modid-mmcli-agent-modding:minion`<br>`MinionEntity`                  | 9-slot inventory, 5 archetype roles, upright guard posture, friendly-fire immunity & host auto-adoption         |                     [Section 4](FEATURES.md#4-autonomous-minion-thrall-entity-minionentity--spawn-egg)                     |
| **Minion Spawn Egg**         | Custom Item       | `modid-mmcli-agent-modding:minion_spawn_egg`<br>`SpawnEggItem`        | Deep navy & arcane gold spawn egg; primes minions in standby stance                                             |                     [Section 4](FEATURES.md#4-autonomous-minion-thrall-entity-minionentity--spawn-egg)                     |
| **Tactical Army AI**         | Squad AI          | `MinionRole`<br>`SquadGroup`                                          | Wildcard (`ALL`) and discrete squads (`ALPHA`–`DELTA`), formations & focus-fire                                 |             [Section 19](FEATURES.md#19-tactical-army--squad-architecture-roles-squads-formations-rally--sfx)              |
| **Construction Manager**     | Server Engine     | `ConstructionManager`<br>`ConstructionSession`                        | Multi-phase build/dismantle sessions with holographic bounding particles                                        |                    [Section 11](FEATURES.md#11-multiblock-construction-manager--session-orchestration)                     |
| **Blueprint Catalog**        | Multiblock Engine | `BlueprintRegistry`<br>`StructureBlueprint`                           | Topologically sorted blueprints (Watchtower, Arcane Obelisk, Barricade)                                         |                        [Section 10](FEATURES.md#10-curated-blueprint-catalog--topological-sorting)                         |
| **Structure Deconstruction** | Demolition Engine | `ConstructionSession`<br>`ConstructionManager`                        | Reverse topological dismantling (roofs first, foundations last) with bedrock immunity safeguards & tool salvage |                [Section 23](FEATURES.md#23-structure-deconstruction--dismantling-mode-sessionmodedismantle)                |
| **Combat Sappers**           | Traversal AI      | `MinionSapperGoal`<br>`TraversalScaffoldingManager`                   | Autonomous chasm bridging, cliff ascent ladders with ceiling clearance avoidance & stall recovery               | [Section 22](FEATURES.md#22-combat-sappers--ephemeral-traversal-scaffolding-minionsappergoal--traversalscaffoldingmanager) |
| **Construction Block**       | Custom Block      | `modid-mmcli-agent-modding:construction_block`<br>`ConstructionBlock` | Non-collapsing infinite span construction block with solid-top support and zero-drop demolition                 |   [Section 29](FEATURES.md#29-construction-block-architecture-bedrock-immunity-safeguards--ceiling-clearance-avoidance)    |
| **TNT Stick**                | Custom Item       | `modid-mmcli-agent-modding:tnt_stick`<br>`TntStickItem`               | Single-stack throwable explosive stick with 5-tick anti-spam cooldown                                           |                                    [Section 13](FEATURES.md#13-custom-items-tnt-stick)                                     |
| **TNT Projectile**           | Custom Entity     | `modid-mmcli-agent-modding:tnt_projectile`<br>`TntProjectileEntity`   | Server-authoritative projectile with smoke trail and 4.0F explosion                                             |                           [Section 14](FEATURES.md#14-custom-entities-tnt-projectile--renderer)                            |
| **Client Rendering & GUIs**  | Visuals & UI      | `com.example.client.renderer.*`<br>`com.example.client.gui.*`         | Billboarded overhead badges, 3D rotating wireframes, MinionScreen (Smart Shift-Close) & Command Hub             |                   [Section 7](FEATURES.md#7-biped-model--client-rendering-pipeline-minionentityrenderer)                   |
| **Block Architecture**       | Registry System   | `ModBlocks`                                                           | Automated dual registration pairing `Registries.BLOCK` with `Registries.ITEM`                                   |                       [Section 15](FEATURES.md#15-screen-handlers--block-registration-architecture)                        |

---

#### Feature Pillar Spotlights

<details open>
<summary><b>👑 1. RTS Command & Tactical Army Systems</b></summary>
<br>

- **Loki Command Scepter** ([`CommandScepterItem`](FEATURES.md#2-loki-command-scepter-commandscepteritem)):
  - **Selective Waypoints**: Right-click ground positions to move _only_ minions currently selected and assigned to the active squad channel.
  - **Direct Unit Selection**: Right-click an owned minion (or aim crosshair within 32 blocks) to toggle selection with audio/particle feedback (chime + hearts to select; bass + smoke to deselect).
  - **Squad Outlines**: Selected units glow with squad-specific team colors (Alpha Red, Bravo Blue, Charlie Green, Delta Gold, All White).
  - **Banner of Courage**: Hold right-click to project a charging rally ring, gathering enclosed thralls into the active squad channel.
  - **Command Hub GUI**: Shift + Right-click opens the interactive hub for squad switching, formation selection, and the paginated blueprint catalog.
  - **Rapid Deselection**: In-world Sneak + Left-Click (in non-`BUILD` modes) or GUI **✕ Deselect** clears active unit selections instantly.
  - **Focus-Fire Pings**: Right-click hostile mobs to order squad-wide coordinated strikes.
  - **Sneak + Left-Click Rotation Cycling**: In `BUILD` mode, Sneak + Left-Click cycles blueprint rotation through 0° → 90° → 180° → 270° with chime audio and actionbar updates.

- **Autonomous Minion Thrall** ([`MinionEntity`](FEATURES.md#4-autonomous-minion-thrall-entity-minionentity--spawn-egg)):
  - **Decoupled Guard Posture**: Idle/holding units stand upright at attention at their post rather than dropping into a seated pose.
  - **Persistent Inventory**: 9 inventory slots + 6 equipment slots managed via Sneak + Right-Click modal GUI.
  - **Friendly-Fire Immunity**: Custom damage gating prevents allied arrow fire, Sweeping Edge strikes, or accidental hits among teammates.
  - **5 Archetype Roles**: `WARRIOR` (melee sweep), `SENTINEL` (8-block perimeter guard), `BUILDER` (architectural construction), `MINER` (excavation), and `RANGER` (dynamic archery strafing in an 8–16 block pocket).
  - **Scaffolding Kinematics**: Ascends scaffolding with continuous `+0.25D` vertical velocity impulses and descends via top-block phase-through snapping.
  - **Singleplayer Host Auto-Adoption**: Server-safe ownership evaluation automatically adopts and rebinds tamed minions to the host player across client/server restarts.

- **Tactical Army Hierarchy** ([`SquadGroup`](FEATURES.md#19-tactical-army--squad-architecture-roles-squads-formations-rally--sfx)):
  - Flexible routing across `ALL` or dedicated squads (`ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`) with network synchronization via custom Fabric C2S packets.
  </details>

<details open>
<summary><b>🏗️ 2. Autonomous Multiblock & Demolition Engine</b></summary>
<br>

- **Construction Manager** ([`ConstructionManager`](FEATURES.md#11-multiblock-construction-manager--session-orchestration)):
  - Server-authoritative session tracking for `BUILD` and `DISMANTLE` modes with holographic bounding-box particles (`GLOW`/`PORTAL` for build; `FLAME`/`CRIT` for dismantle).
  - Supports both Creative zero-cost mode and Survival inventory drops/scavenging.

- **Blueprint Catalog & Topological Sorting** ([`BlueprintRegistry`](FEATURES.md#10-curated-blueprint-catalog--topological-sorting)):
  - Pre-engineered structures: _Overlord Watchtower_ (7×7×9), _Arcane Obelisk_ (5×5×8), and _Defensive Barricade_ (9×3×3).
  - Deterministic bottom-up topological sorting ensures foundations, pillars, and inverted stair arches are constructed prior to upper dependent blocks.
  - **4-Quadrant Rotation Engine**: Full origin $(0, 0)$ rotation matrices (0°, 90°, 180°, 270°) with automatic BlockState rotation, bounding box recalculation, and topological re-sorting.
  - **Door Offset Discovery & Sparkle Beams**: Automatically discovers lower door coordinates, projecting vertical sparkle beams (`HAPPY_VILLAGER` + `END_ROD`) and HUD actionbar door direction readouts.

- **Structure Deconstruction** ([`ConstructionSession`](FEATURES.md#23-structure-deconstruction--dismantling-mode-sessionmodedismantle)):
  - Reverse topological dismantling demolishes roofs and upper decorations before clearing foundational supports.
  - Dynamic tool resolution selects pickaxes, shovels, or axes based on block hardness, dropping harvested items and clearing scaffolding on descent.
  - **Bedrock & Indestructible Block Immunity**: Strictly checks `currentState.isOf(Blocks.BEDROCK) || currentState.getHardness(...) < 0.0F`, preventing minions from breaking bedrock, barrier blocks, command blocks, or void boundaries. Plays anvil hit SFX (`BLOCK_ANVIL_HIT`), emits smoke, and safely completes tasks without world damage. Scaffolding cleanup strictly verifies `isScaffoldBlock` before removal, leaving natural ground and bedrock untouched.

- **Combat Sappers & Traversal Scaffolding** ([`MinionSapperGoal`](FEATURES.md#22-combat-sappers--ephemeral-traversal-scaffolding-minionsappergoal--traversalscaffoldingmanager)):
  - Detects $\ge 2$-block drops ahead and bridges chasms up to 6 blocks wide.
  - Builds vertical climbing shafts up to 6 blocks high when confronting steep cliffs.
  - **Ceiling Clearance & Headroom Avoidance**: Scans the climbing shaft for overhead ceilings and requires 2 blocks of clear headroom at ledge landings and across ravine bridges, preventing sappers from deploying into low ceilings.
  - **Overhead Collision & Stall Sensors**: Scans `headPos = minion.getBlockPos().up(2)` during ascent to abort instantly upon ceiling contact; triggers safe abort if vertical progress stalls ($< 0.02\text{D}$ for $> 20$ ticks) or total climb exceeds 120 ticks.
  - **Multi-Minion Column Spacing**: Claims unique column coordinates through `TraversalScaffoldingManager.claimClimbingColumn`, eliminating crowding collisions on climbing shafts.
  - Ephemeral scaffolding auto-decays after 400 ticks (20s) with occupancy detection extending life by +40 ticks while units cross.

- **Dedicated Construction Block Architecture** ([`ModBlocks.CONSTRUCTION_BLOCK`](FEATURES.md#29-construction-block-architecture-bedrock-immunity-safeguards--ceiling-clearance-avoidance)):
  - High-performance, temporary structural block (`modid-mmcli-agent-modding:construction_block`) eliminating vanilla scaffolding's horizontal collapse limit (can span ravines of arbitrary width).
  - Features solid-top face at $y + 1.0\text{D}$ so minions traverse and stand firmly without sinking.
  - Configured with `0.2F` hardness, `BlockSoundGroup.SCAFFOLDING`, client Cutout render layer, and `.dropsNothing()` to ensure clean, zero-item-litter demolition.
  - Seamlessly recognized by both `MinionBuildGoal` and `MinionSapperGoal`.
  </details>

<details open>
<summary><b>💥 3. Tactical Explosives & Combat Entities</b></summary>
<br>

- **TNT Stick** ([`TntStickItem`](FEATURES.md#13-custom-items-tnt-stick)):
  - `EPIC` rarity Combat item. Right-click plays `ENTITY_TNT_PRIMED` sound, launches projectile at 1.5 velocity, and triggers a 5-tick (0.25s) anti-spam cooldown.

- **TNT Projectile** ([`TntProjectileEntity`](FEATURES.md#14-custom-entities-tnt-projectile--renderer)):
  - Aerodynamic projectile with smoke trail and flame spark effects; detonates on server collision with a 4.0F explosion.
  </details>

<details open>
<summary><b>🎨 4. Client Visuals, Overhead Crests & UI Pipeline</b></summary>
<br>

- **Overhead Crest Feature Renderer** ([`MinionOverheadBadgeFeatureRenderer`](FEATURES.md#7-biped-model--client-rendering-pipeline-minionentityrenderer)):
  - Billboards above minion heads using exact LIFO matrix reversal to prevent inversion or tilting.
  - Renders squad channels, role archetypes, gold star (`§6★ `) selection indicators, and stationed `[HOLD]` badges.
  - Rendered with `LightmapTextureManager.MAX_LIGHT_COORDINATE` for crisp, fullbright legibility in deep caves and night raids.

- **Custom Screen Interfaces**:
  - `MinionScreen`: Framed biped equipment modal with inventory grid, role/squad status, and **Smart Shift-to-Close** with item transfer latching (`SmartCloseHandler`).
  - `CommandScepterScreen`: Interactive Command Hub displaying real-time selected unit counts, formation controls, blueprint preview thumbnails, and a one-click **✕ Deselect** button with fast Shift dismissal.
  - `BlueprintHologramRenderer`: Translucent neon-cyan 3D wireframes rotating synchronously in real time with the active scepter rotation.
  </details>

---

### 🌟 The 5 Architectural Refinements

The codebase incorporates five foundational engineering refinements designed to maximize operational stability, eliminate edge-case crashes, and deliver seamless tactile UX:

1. **Refinement 1 — Singleplayer Host Ownership Auto-Adoption (Server-Safe)**:
   - Server-side singleplayer host validation (`server.isSingleplayer() && server.isHost(...)`) in `MinionEntity.isOwner` adopts tamed thralls if offline development UUIDs change across client restarts.
   - Strictly enforces server-safe invariants: zero imports of `MinecraftClient` in common code (`src/main/java`), completely preventing dedicated server crashes.
2. **Refinement 2 — Scaffolding Descent Phase-Through Kinematics**:
   - Suppresses climbing flags (`climbingScaffolding = false`) during descent, preventing horizontal collisions from triggering vanilla upward climbing impulses.
   - Centers and snaps the minion 0.25 blocks inside the top scaffold block (`targetScaffoldTopY + 0.75D`) with downward velocity `-0.25D`, smoothly phasing through the column without hopping.
   - Relaxes landing detection (`targetScaffoldBottomY + 0.35D` or solid ground) and bypasses descent when already at/below ground level.
3. **Refinement 3 — Synchronous 3D Holographic Wireframe Rotation**:
   - `BlueprintHologramRenderer` dynamically rotates blueprints (`blueprint.rotate(rotation)`) using the active scepter rotation component before rendering.
   - Guarantees that neon-cyan wireframes, yellow anchor boxes, and ghost blocks align with in-world particle guides and server-side placement.
4. **Refinement 4 — Smart Shift-to-Close with Item Transfer Latching (`MinionScreen`)**:
   - Tracks `slotClickedWithShift`: clicking inventory slots while holding Shift latches item transfer mode, ensuring releasing Shift after a `quickMove` does **NOT** close the screen.
   - Clean Shift taps without slot clicks dismiss the modal instantly; `'E'` and `Escape` provide universal fast exit.
5. **Refinement 5 — Sneak + Left-Click Blueprint Rotation Cycling & Door Sparkle HUD**:
   - Sneak + Left-Click in `BUILD` mode cycles rotation through $0^\circ \to 90^\circ \to 180^\circ \to 270^\circ$, updating client and server state seamlessly.
   - Scepter `inventoryTick` performs 32-block crosshair raycasting, projecting rotating perimeter particles, vertical door sparkle beams (`HAPPY_VILLAGER` + `END_ROD`), and a real-time HUD actionbar readout (`§6🏗 [Name] §8| §bRotation: [Deg]° §8| §a🚪 Door: [Dir]`).
6. **Refinement 6 — Bedrock Deconstruction Immunity & Sapper Ceiling Avoidance**:
   - Multi-tiered indestructible block immunity protects Bedrock, Barrier, End Portal, and Command Blocks across session task generation, task readiness, dismantling execution, and scaffolding cleanup.
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

# Run the 126 automated unit tests
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
    │   │   │   │   │   ├── MinionRangedAttackGoal.java # Ranger dynamic strafing & archery skirmish AI
    │   │   │   │   │   ├── MinionSapperGoal.java       # Combat sapper chasm bridging & cliff ascent AI
    │   │   │   │   │   ├── SentinelGuardGoal.java      # Sentinel anchor tethering & 12-block leash AI
    │   │   │   │   │   └── WaypointHoldGoal.java       # Non-sentinel waypoint anchor holding AI
    │   │   │   │   └── pathing/
    │   │   │   │       ├── MinionNavigation.java       # Scaffolding-aware ground pathfinding
    │   │   │   │       └── MinionPathNodeMaker.java    # Scaffolding node evaluator
    │   │   │   ├── custom/
    │   │   │   │   ├── MinionEntity.java      # Tameable thrall with roles, squads, equipment & inventory
    │   │   │   │   ├── MinionRole.java        # Archetype roles (WARRIOR, SENTINEL, BUILDER, MINER, RANGER)
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
    │   │   │   ├── ModNetworking.java            # Networking registry & server receivers
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
    │       │   ├── BlueprintHologramRenderer.java # Translucent 3D blueprint ghost-block preview
    │       │   ├── MinionClothingFeatureRenderer.java # Outer biped clothing layers
    │       │   ├── MinionEntityRenderer.java  # Biped renderer with armor, clothing, and held item feature layers
    │       │   └── TntProjectileRenderer.java # FlyingItemEntityRenderer for 3D spinning projectile in flight
    │       └── ExampleModClient.java          # ClientModInitializer registering renderers & screens
    └── test/                                  # Unit testing suite (126 unit tests)
        └── java/com/example/
            ├── blueprint/
            │   └── ScaffoldingTest.java       # Scaffolding reach, doorway corridor, and climbing tests
            ├── client/
            │   ├── gui/
            │   │   └── CommandScepterScreenCloseTest.java # Shift-to-close open-state guard, repeat absorption & dismissal tests
            │   └── renderer/
            │       └── MinionOverheadBadgeTest.java # Squad banners, Roman numerals, and role crest tests
            ├── construction/
            │   └── StructureDismantlingTest.java # Reverse topological sorting, deconstruction prerequisites, role matrix
            ├── entity/
            │   ├── MinionFormationAndEquipTest.java # Parametric geometry, clearance, and auto-equip tests
            │   ├── MinionSapperAndScaffoldingTest.java # Sapper bridging, climbing shafts, and decay safety
            │   └── MinionSquadAndRoleTest.java# Roles, squads, serialization, leash logic & combat pockets
            ├── item/
                └── CommandScepterRaycastTargetingTest.java # 32-block crosshair raycast & targeting math
            └── network/
                └── NetworkingPayloadTest.java # C2S packet records, codecs, and backward compatibility
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
