# Fabric 1.21 Mod Development & Deployment Guide

A modern Minecraft 1.21 mod template built using the Fabric toolchain: **Fabric Loader**, **Fabric API**, and **Gradle Loom** with **Yarn** mappings.

This guide outlines the end-to-end mod development lifecycle, detailing how the development sandbox operates via Gradle Loom, how to configure Fabric API, and how to build and deploy your compiled mod JAR into the official Minecraft Launcher.

---

## Features & Added Content

This mod builds on the Fabric foundation with custom gameplay mechanics implemented using Fabric's recommended domain architecture. Custom features are organized into dedicated packages (`item`, `entity`, `block`, and client `renderer`) with decoupled registry lifecycles and asset schemas.

> 📖 **Comprehensive Feature Guide**: For full mechanical specifications, entity physics details, explosion parameters, and architecture patterns, explore the dedicated [**FEATURES.md**](FEATURES.md) showcase.

### High-Level Content Overview

| Feature | Category | Registry Identifier / Class | Core Mechanics & Characteristics |
| :--- | :--- | :--- | :--- |
| **TNT Stick** | Custom Item | `modid-mmcli-agent-modding:tnt_stick`<br>`com.example.item.custom.TntStickItem` | • Single-stack (`maxCount: 1`), `EPIC` rarity item in the Combat creative tab.<br>• Right-click triggers vanilla `ENTITY_TNT_PRIMED` audio feedback, launches a `TntProjectileEntity` server-side (velocity 1.5, divergence 1.0), increments player usage statistics, and enforces a 5-tick (0.25s) anti-spam cooldown.<br>• See [FEATURES.md — TNT Stick Item](FEATURES.md#1-tnt-stick-item). |
| **TNT Projectile** | Custom Entity | `modid-mmcli-agent-modding:tnt_projectile`<br>`com.example.entity.custom.TntProjectileEntity` | • Thrown entity with `0.25 x 0.25` dimensions and `SpawnGroup.MISC`.<br>• Active client-side flight particles (continuous `SMOKE` trail + 60% chance `FLAME` particles per tick).<br>• Server-authoritative collision creates a 4.0F power TNT explosion (`World.ExplosionSourceType.TNT`) on impact with blocks or entities, discarding the entity afterwards.<br>• See [FEATURES.md — TNT Projectile Entity](FEATURES.md#2-tnt-projectile-entity). |
| **Block Architecture** | Registry Scaffolding | `com.example.block.ModBlocks` | • Modular registration system pairing `Registries.BLOCK` entries with automatic `BlockItem` registration in `Registries.ITEM`.<br>• Clean initialization hook invoked from `ExampleMod.java`, prepared for custom explosive and decorative block expansion.<br>• See [FEATURES.md — Block Architecture](FEATURES.md#3-block-architecture). |
| **Client Rendering** | Visuals & Models | `com.example.client.renderer.TntProjectileRenderer` | • Dedicated client entity renderer extending `FlyingItemEntityRenderer`.<br>• Registered in `ExampleModClient` via `EntityRendererRegistry` to render airborne projectiles as spinning 3D TNT blocks.<br>• Standard handheld model JSON and English localization (`en_us.json`).<br>• See [FEATURES.md — Client Rendering & Assets](FEATURES.md#4-client-rendering--assets). |

---

## 1. Architectural Overview

Fabric provides a modular, lightweight modding stack designed for fast compilation, minimal overhead, and rapid updates across Minecraft versions:

| Component | Role | Purpose |
| :--- | :--- | :--- |
| **Fabric Loader** | Runtime Core | Loads mods into the game process, manages Mixin transformations, and resolves dependencies. |
| **Fabric API** | Essential Library | Hook library providing standard event listeners, registry wrappers, network channels, and rendering utilities. |
| **Fabric Loom** | Gradle Plugin | Automates Minecraft deobfuscation, handles Yarn mapping generation, injects dev runs, and remaps production JARs. |
| **Yarn Mappings** | Deobfuscation | Clean, open-source community mappings translating obfuscated Minecraft bytecode into human-readable symbols. |
| **Sponge Mixin** | Bytecode Injection | Injects custom logic directly into Minecraft's runtime classes at class-load time without altering binary files. |

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
    │   │   ├── entity/
    │   │   │   ├── custom/
    │   │   │   │   └── TntProjectileEntity.java # Explosive projectile entity with smoke/flame particle trails
    │   │   │   └── ModEntities.java           # EntityType registration, hitboxes & spawn groups
    │   │   ├── item/
    │   │   │   ├── custom/
    │   │   │   │   └── TntStickItem.java      # Handheld launcher item with sound & cooldown handling
    │   │   │   └── ModItems.java              # Item registry & Combat creative tab integration
    │   │   ├── mixin/
    │   │   │   └── ExampleMixin.java          # Bytecode injection into MinecraftServer lifecycle
    │   │   └── ExampleMod.java                # ModInitializer entrypoint bootstrapping registries
    │   └── resources/
    │       ├── assets/modid-mmcli-agent-modding/
    │       │   ├── lang/
    │       │   │   └── en_us.json             # Translation keys (TNT Stick, TNT Projectile)
    │       │   ├── models/item/
    │       │   │   └── tnt_stick.json         # Handheld item model definition
    │       │   └── textures/item/             # Item texture asset storage
    │       ├── fabric.mod.json                # Mod metadata, entrypoints, and dependency rules
    │       └── modid.mixins.json              # Mixin configuration and rules
    └── client/                                # Client-only code & resources (split environment)
        └── java/com/example/client/
            ├── renderer/
            │   └── TntProjectileRenderer.java # FlyingItemEntityRenderer for 3D spinning projectile in flight
            └── ExampleModClient.java          # ClientModInitializer registering entity renderers
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
*(Accept the Minecraft EULA when prompted in `run/eula.txt` by setting `eula=true`.)*

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
