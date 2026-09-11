# Fabric 1.21 Mod Development & Deployment Guide

A modern Minecraft 1.21 mod template built using the Fabric toolchain: **Fabric Loader**, **Fabric API**, and **Gradle Loom** with **Yarn** mappings.

This guide outlines the end-to-end mod development lifecycle, detailing how the development sandbox operates via Gradle Loom, how to configure Fabric API, and how to build and deploy your compiled mod JAR into the official Minecraft Launcher.

---

## Features & Added Content

This mod builds on the Fabric foundation with custom gameplay mechanics implemented using Fabric's recommended domain architecture. Custom features are organized into dedicated packages (`item`, `entity`, `block`, `component`, `blueprint`, `construction`, and client `renderer`) with decoupled registry lifecycles and asset schemas.

> 📖 **Comprehensive Feature Guide**: For full mechanical specifications, entity physics details, explosion parameters, and architecture patterns, explore the dedicated [**FEATURES.md**](FEATURES.md) showcase.

### High-Level Content Overview

| Feature                         | Category             | Registry Identifier / Class                                                                      | Core Mechanics & Characteristics                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| :------------------------------ | :------------------- | :----------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Loki Command Scepter**        | Custom Item          | `modid-mmcli-agent-modding:command_scepter`<br>`com.example.item.custom.CommandScepterItem`      | • Single-stack (`maxCount: 1`), `EPIC` rarity item in Combat and Tools creative tabs with enchanted glint.<br>• **32-Block Crosshair Raycasting**: Full line-of-sight view-vector raycast up to 32.0 blocks (`MINION_COMMAND_RADIUS`), clipping against solid terrain and overcoming vanilla 3.0-block interaction reach.<br>• **Individual Minion Follow**: Right-clicking directly or 32-block crosshair quick-tapping an owned minion orders that specific unit to follow the master (speed 1.35D, heart particles, chime SFX).<br>• **Squad Hold Safeguards**: Waypoint pings and attack broadcasts strictly respect stationed units (`!m.isSitting()`), never pulling guarded thralls into marches.<br>• Shift + Right-Click opens the **Command Hub GUI** with command modes, **Paginated Blueprint Catalog** (fixed 3-item viewport with `<` and `>` buttons), and **Squad Selection Bar** (`ALL`, `ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`).<br>• Hold Right-Click charges the **Banner of Courage Rally Ring** (expanding `PORTAL`/`FLAME` ring), releasing a goat horn sound, gathering enclosed minions into the selected squad, and ordering them to follow.<br>• Right-Click on ground/crosshair places a **Ground Waypoint Ping** (`END_ROD`/`GLOW` beacon beam, beacon SFX), ordering matching non-sitting minions to sprint and hold position.<br>• Right-Click on hostile mobs triggers a **Focus-Fire Ping** (`ANGRY_VILLAGER`/`CRIT` lock-on particles, note block drum SFX).<br>• Right-Click on ground in `BUILD` mode anchors automated construction; **Shift + Right-Click** anchors **Structure Deconstruction** (`SessionMode.DISMANTLE`).<br>• Right-Click in `MINE` mode anchors **Structure Deconstruction** for existing active sessions or selected blueprints.<br>• Shift + Left-Click or Right-Click in air in `BUILD` mode cycles active blueprints.<br>• Right-Click on mobs in `RECRUIT` mode transfigures them into obedient Minion thralls primed in standby.<br>• See [FEATURES.md — Loki Command Scepter](FEATURES.md#2-loki-command-scepter-commandscepteritem). |
| **Minion Thrall**               | Custom Entity        | `modid-mmcli-agent-modding:minion`<br>`com.example.entity.custom.MinionEntity`                   | • Autonomous worker and combat thrall extending `TameableEntity` and implementing `InventoryOwner` & `RangedAttackMob`.<br>• Contains a 9-slot persistent inventory and 6 equipment slots accessible via Sneak + Right-Click GUI (`MinionScreen`).<br>• **Standby Summoning**: Newly spawned and recruited minions initialize in an At-Ease holding stance (`setSitting(true)` with anchor at spawn pos), preventing immediate aggro on hostiles in 24 blocks.<br>• **Scaffolding Travel Physics**: Overrides `travel(Vec3d)` to apply continuous +0.25D vertical velocity impulse when inside scaffolding and ascending, resetting fall distance to 0.0F.<br>• Supports 5 archetype roles (`WARRIOR`, `SENTINEL`, `BUILDER`, `MINER`, `RANGER`) and 4 assignable squads (`ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`) configurable via GUI.<br>• Sentinels hold perimeter within 8 blocks and break aggro to retreat when lured past 12 blocks; Rangers dynamically strafe in an 8–16 block pocket with bows.<br>• See [FEATURES.md — Autonomous Minion Thrall Entity](FEATURES.md#4-autonomous-minion-thrall-entity-minionentity--spawn-egg). |
| **Structure Deconstruction**    | Multiblock & Demolition | `com.example.construction.ConstructionSession`<br>`com.example.construction.ConstructionManager` | • **Top-Down Reverse Topological Dismantling**: Systematically demolishes blueprints in reverse topological order (roofs/ceilings first, foundations last).<br>• **Dependency Safeguards**: Upper blocks and hanging decorations must be cleared before supporting foundation and ceiling blocks.<br>• **Role Participation**: Both `BUILDER` and `MINER` roles participate in deconstruction (`MINER` participates exclusively in dismantle sessions).<br>• **Resource Salvage & Tools**: Dynamic tool resolution (`resolveDismantleTool`: pickaxe, shovel, axe), survival block drops (`Block.dropStacks`), and scaffolding teardown on descent.<br>• See [FEATURES.md — Structure Deconstruction](FEATURES.md#23-structure-deconstruction--dismantling-mode-sessionmodedismantle). |
| **Combat Sappers & Traversal Scaffolding** | Combat Engineering | `com.example.construction.TraversalScaffoldingManager`<br>`com.example.entity.ai.goal.MinionSapperGoal` | • **Ravine & Chasm Bridging**: Detects $\ge 2$-block drops 1.2–2.0 blocks ahead and builds horizontal bridges across gaps up to 6 blocks wide.<br>• **Cliff & Mountain Ascent**: Detects vertical obstacles rising $> 1.0625$ blocks and constructs vertical climbing shafts up to 6 blocks high.<br>• **Combat Sapper Dynamics**: `BUILDER` thralls build temporary scaffolding at zero resource cost; other thralls consume internal inventory or signal nearby squad builders within 24 blocks (`SapperRequest`).<br>• **Decay & Entity Safety**: Ephemeral scaffolding decays after 400 ticks (20s); occupancy detector postpones decay by +40 ticks while units cross, preventing fall deaths.<br>• See [FEATURES.md — Combat Sappers & Ephemeral Traversal Scaffolding](FEATURES.md#22-combat-sappers--ephemeral-traversal-scaffolding-minionsappergoal--traversalscaffoldingmanager). |
| **Tactical Army Architecture**  | Combat & Squad AI    | `com.example.entity.custom.MinionRole`<br>`com.example.component.SquadGroup`                     | • RTS squad command hierarchy routing orders across wildcard (`ALL`) or discrete squads (`ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`).<br>• Partitioned AI goals: `SentinelGuardGoal` (perimeter leash & anchor return), `MinionRangedAttackGoal` (dynamic archery strafe), `MinionFormationFollowGoal` (parametric formations), and `MinionBuildGoal` (builder isolation).<br>• Network synchronization via `UpdateMinionConfigPayload` and `UpdateScepterPayload`.<br>• See [FEATURES.md — Tactical Army & Squad Architecture](FEATURES.md#19-tactical-army--squad-architecture-roles-squads-formations-rally--sfx). |
| **Minion Spawn Egg**            | Custom Item          | `modid-mmcli-agent-modding:minion_spawn_egg`<br>`net.minecraft.item.SpawnEggItem`                | • Registered in `ItemGroups.SPAWN_EGGS` with custom theme colors (`0x2C3E50` deep navy base, `0xF1C40F` arcane gold spots).<br>• Spawns a minion entity that auto-tames to the player and primes in standby holding stance.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Blueprint Catalog & Sorting** | Multiblock Engine    | `com.example.blueprint.BlueprintRegistry`<br>`com.example.blueprint.StructureBlueprint`          | • Curated blueprints: Overlord Watchtower (7x7x9), Arcane Obelisk (5x5x8), Defensive Barricade (9x3x3).<br>• Deterministic bottom-up topological sorting ensures foundations and inverted stair arches are placed before dependent upper blocks.<br>• See [FEATURES.md — Curated Blueprint Catalog](FEATURES.md#10-curated-blueprint-catalog--topological-sorting).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **Construction Manager**        | Server Architecture  | `com.example.construction.ConstructionManager`<br>`com.example.construction.ConstructionSession` | • Server-side orchestrator tracking multiblock construction and deconstruction sessions (`SessionMode.BUILD` vs `SessionMode.DISMANTLE`), task leasing, and completion ceremonies.<br>• Generates periodic holographic bounding-box particles (`GLOW`/`PORTAL`/`WAX_ON` for build, `FLAME`/`SMALL_FLAME`/`CRIT` for dismantle).<br>• Creative mode (zero-cost) and Survival mode (inventory drops and scavenging).<br>• See [FEATURES.md — Multiblock Construction Manager](FEATURES.md#11-multiblock-construction-manager--session-orchestration).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **TNT Stick**                   | Custom Item          | `modid-mmcli-agent-modding:tnt_stick`<br>`com.example.item.custom.TntStickItem`                  | • Single-stack (`maxCount: 1`), `EPIC` rarity item in Combat creative tab.<br>• Right-click triggers vanilla `ENTITY_TNT_PRIMED` audio, launches `TntProjectileEntity` server-side (velocity 1.5), increments usage stats, and enforces a 5-tick (0.25s) anti-spam cooldown.<br>• See [FEATURES.md — Custom Items: TNT Stick](FEATURES.md#13-custom-items-tnt-stick).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **TNT Projectile**              | Custom Entity        | `modid-mmcli-agent-modding:tnt_projectile`<br>`com.example.entity.custom.TntProjectileEntity`    | • Thrown entity with `0.25 x 0.25` dimensions and `SpawnGroup.MISC`.<br>• In-flight smoke trail and flame sparks.<br>• Server-authoritative collision creates a 4.0F power TNT explosion on impact.<br>• See [FEATURES.md — Custom Entities: TNT Projectile](FEATURES.md#14-custom-entities-tnt-projectile).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Block Architecture**          | Registry Scaffolding | `com.example.block.ModBlocks`                                                                    | • Modular registration system pairing `Registries.BLOCK` entries with automatic `BlockItem` registration in `Registries.ITEM`.<br>• See [FEATURES.md — Block Registration Architecture](FEATURES.md#15-screen-handlers--block-registration-architecture).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **Client Rendering & GUIs**     | Visuals & UI         | `com.example.client.renderer.*`<br>`com.example.client.gui.*`                                    | • `MinionEntityRenderer`: Biped renderer supporting equipped armor layers and held tools/blocks.<br>• `MinionOverheadBadgeFeatureRenderer`: Overhead crests billboarding above minion heads with inverted Y-translation fix and custom nametag/sneaking clearance.<br>• `MinionScreen`: Dynamically centered unified modal with framed header and bottom action tray.<br>• `CommandScepterScreen`: Interactive Command Hub with 3-item paginated blueprint catalog.<br>• See [FEATURES.md — Client Rendering Pipeline](FEATURES.md#7-biped-model--client-rendering-pipeline-minionentityrenderer).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |

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
    └── test/                                  # Unit testing suite (67 unit tests)
        └── java/com/example/
            ├── blueprint/
            │   └── ScaffoldingTest.java       # Scaffolding reach, doorway corridor, and climbing tests
            ├── client/renderer/
            │   └── MinionOverheadBadgeTest.java # Squad banners, Roman numerals, and role crest tests
            ├── construction/
            │   └── StructureDismantlingTest.java # Reverse topological sorting, deconstruction prerequisites, role matrix
            ├── entity/
            │   ├── MinionFormationAndEquipTest.java # Parametric geometry, clearance, and auto-equip tests
            │   ├── MinionSapperAndScaffoldingTest.java # Sapper bridging, climbing shafts, and decay safety
            │   └── MinionSquadAndRoleTest.java# Roles, squads, serialization, leash logic & combat pockets
            ├── item/
            │   └── CommandScepterRaycastTargetingTest.java # 32-block crosshair raycast & targeting math
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
