# Fabric 1.21 RTS Minion Mod

A tactical real-time strategy (RTS) and multiblock engineering mod for Minecraft 1.21, built on the **Fabric Loader**, **Fabric API**, and **Gradle Loom** toolchain.

This mod transforms your Minecraft world into an RTS battlefield and automated construction sandbox. Command squads of autonomous minion thralls, deploy ranked battle formations, execute coordinated 90° mass assaults, and construct or dismantle complex multiblock structures in real time.

---

## What Is This Project?

The mod introduces strategic army management, autonomous AI companions, and structural engineering into Minecraft 1.21:

- **Autonomous Minion Thralls**: Tameable, persistent companions with 4 distinct archetype roles:
  - ⚔ **Warrior**: Versatile combat unit automatically acting as frontline melee swordsman or ranged archer based on equipped weapons.
  - 🛡 **Sentinel**: Sturdy defensive bulwark and combat-medic channeling the *Aegis of Restoration* to heal wounded allies within 10 blocks.
  - 🏗 **Builder**: Multiblock assembly specialist equipped with 3D Arcane Levitation flight for scaffold-free construction.
  - ⛏ **Miner**: Area excavation and demolition specialist with bedrock immunity safeguards.
  - Minions feature customizable 9-slot backpacks, 6 equipment slots, dynamic overhead hearts health indicators, and obstacle-vaulting traversal.
- **The Loki Command Scepter**: Handheld tactical relic enabling:
  - 64-block raycast unit selection and waypoint deployment into straight, parallel ranked army battle lines.
  - **Banner of Courage (90° Forward Sector)**: Hold right-click to project an expanding tactical cone; releasing launches a synchronized Mass Assault queue hunting down all enclosed hostiles.
  - **Command Hub GUI (`V` key)**: Interactive tactical hub for squad routing (Alpha through Delta), mode cycling, mass role assignment, and blueprint selection.
  - **Panic Retreat (`R` key)**: Emergency recall sounding a warning bell, clearing combat targets, and sprinting minions back into formation.
- **Multiblock Construction & Deconstruction**:
  - Live 3D holographic wireframe and ghost-block previews with 4-quadrant rotation (0°, 90°, 180°, 270°).
  - Pre-engineered topological blueprints: *Overlord Watchtower* (7×7×9), *Arcane Obelisk* (5×5×8), and *Defensive Barricade* (9×3×3).
  - Top-down area clearance and demolition engine with Bedrock and indestructible block protection.
- **Tactical Ordnance**: Throwable TNT Sticks and cryogenic Frost Grenades (flash-freezes water, turns lava to obsidian, and applies Slowness III).

> 📖 **In-Game Controls & Commands**: For the full controls cheat sheet and scepter input manual, see [**COMMANDS.md**](COMMANDS.md).
>
> 📖 **Technical Architecture & Specifications**: For deep implementation details, entity physics, AI goal architectures, and packet protocols, see [**FEATURES.md**](FEATURES.md).

---

## Local Development Setup

Follow these instructions to set up the mod for local development, run tests, and launch the development client sandbox.

### 1. Prerequisites

- **Java Development Kit (JDK)**: **Java 21 or newer** (required for Minecraft 1.20.5+ and 1.21).
  Verify your installed version:
  ```bash
  java -version
  ```
- **Git**: Installed and available in your terminal path.
- **Gradle**: Provided directly via the included Gradle wrapper (`./gradlew`). No standalone installation is required.

### 2. Clone & Setup Workspace

```bash
# Clone the repository
git clone https://github.com/<your-username>/minecraft-modding.git
cd minecraft-modding
```

### 3. Running the Development Client (`runClient`)

Gradle Loom runs a sandboxed Minecraft 1.21 client with hot code reload, Yarn mappings, and isolated save files in the `./run` directory:

```bash
./gradlew runClient
```

Loom will automatically download the required Minecraft client assets, map deobfuscated Yarn symbols, inject Fabric Loader & Fabric API, and start the game.

### 4. Running the Dedicated Server (`runServer`)

To test server-authoritative logic, multiplayer sync, or network packets:

```bash
./gradlew runServer
```
*(Accept the Minecraft EULA when prompted in `run/eula.txt` by setting `eula=true`.)*

### 5. Running Automated Unit Tests

Run the automated test suite (220 unit tests across 20 suites covering AI, math, networking, and safeguards):

```bash
./gradlew test
```

### 6. Building the Mod JAR

To compile and remap the production mod JAR:

```bash
./gradlew build
```

The compiled, ready-to-distribute mod JAR will be located at:
```text
build/libs/fabric-mmcli-agent-modding-1.0.0.jar
```

### 7. Recommended IDE Setup

- **IntelliJ IDEA** (Recommended):
  1. Open IntelliJ IDEA and select **Open**.
  2. Choose the root `build.gradle` file and open as a project.
  3. Install the **Minecraft Development** plugin.
  4. Loom will automatically configure run configurations for `Minecraft Client` and `Minecraft Server` with full breakpoint and debugging support.
- **Visual Studio Code**:
  1. Install the **Extension Pack for Java** and **Gradle for Java** extensions.
  2. Open the project root folder.
  3. Launch via the Gradle sidebar under `Tasks > loom > runClient`.

---

## Playing with the Official Minecraft Launcher

Follow these steps to deploy and play the compiled mod inside the official Mojang Minecraft Launcher.

### 1. Requirements

- **Minecraft: Java Edition** installed via the official Minecraft Launcher.
- **Fabric Loader 0.16.10+** for Minecraft **1.21**.
- **Fabric API** for Minecraft **1.21**.
- The compiled mod JAR (`fabric-mmcli-agent-modding-1.0.0.jar`).

### 2. Step-by-Step Installation

#### Step A: Install Minecraft 1.21
1. Open the official Minecraft Launcher.
2. Select **Minecraft: Java Edition** and launch the **Latest Release (1.21)** once to the main title screen to ensure all vanilla assets are downloaded.
3. Close Minecraft.

#### Step B: Install Fabric Loader
1. Download the Fabric Installer from [fabricmc.net/use/installer](https://fabricmc.net/use/installer/).
2. Run the installer:
   - Select the **Client** tab.
   - **Minecraft Version**: `1.21`
   - **Loader Version**: `0.16.10` (or latest stable)
   - Ensure the default `.minecraft` directory is selected.
   - Check **Create profile** and click **Install**.
3. Reopen the Minecraft Launcher. A new installation profile named **fabric-loader-1.21** will be available.

#### Step C: Download Fabric API
1. Fabric Loader requires the Fabric API mod JAR to provide gameplay and rendering hooks.
2. Download the Minecraft 1.21 release from:
   - [Modrinth: Fabric API](https://modrinth.com/mod/fabric-api)
   - [CurseForge: Fabric API](https://curseforge.com/minecraft/mc-mods/fabric-api)

#### Step D: Install the Mod JARs
Copy both the **mod JAR** (`fabric-mmcli-agent-modding-1.0.0.jar`) and the **Fabric API JAR** into your Minecraft `mods` folder:

- **Windows**:
  - Path: `%APPDATA%\.minecraft\mods\`
  - Shortcut: Press `Win + R`, paste `%appdata%\.minecraft\mods`, and press Enter.
- **macOS**:
  - Path: `~/Library/Application Support/minecraft/mods/`
  - Shortcut: In Finder, press `Cmd + Shift + G`, paste `~/Library/Application Support/minecraft/mods`, and press Enter.
- **Linux**:
  - Path: `~/.minecraft/mods/`

*(If the `mods` folder does not exist, create it manually).*

#### Step E: Launch the Game
1. In the Minecraft Launcher, select the **fabric-loader-1.21** profile from the drop-down menu next to the **Play** button.
2. Click **Play**.
3. Once in-game, you can obtain mod items via Creative inventory tabs (`Combat` and `Tools`) or craft them in Survival.

---

## Verification & Troubleshooting

### How to Verify the Mod is Active
- **Mod Menu**: If you have [Mod Menu](https://modrinth.com/mod/modmenu) installed, open **Mods** on the title screen to see **Example Mod (`modid-mmcli-agent-modding`)** listed.
- **Game Logs**: Check `.minecraft/logs/latest.log` for the initialization messages:
  ```text
  [main/INFO] (modid-mmcli-agent-modding) Initializing Fabric 1.21 Example Mod: modid-mmcli-agent-modding
  [Render thread/INFO] (modid-client) Initializing Fabric 1.21 Example Mod Client
  ```

### Common Issues

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **`Incompatible mod set!` or `Mod requires fabric-api`** | Fabric API JAR is missing from the `mods` folder or has a mismatched version. | Download the Fabric API JAR matching Minecraft 1.21 and place it in `.minecraft/mods/`. |
| **`UnsupportedClassVersionError (class file version 65.0)`** | Minecraft is running on an older Java runtime (e.g. Java 8 or 17 instead of Java 21). | In Minecraft Launcher, edit the **fabric-loader-1.21** profile > **More Options** > **Java Executable** and browse to your Java 21 binary (`bin/java`). |
| **Gradle mapping or dependency cache errors** | Stale cache after switching versions. | Run `./gradlew clean --refresh-dependencies` in your workspace root, then rebuild. |

---

## Helpful Resources

- [COMMANDS.md](COMMANDS.md) — Controls cheat sheet, scepter commands, and tactical orders
- [FEATURES.md](FEATURES.md) — Comprehensive technical breakdown and feature specifications
- [Fabric Official Documentation](https://fabricmc.net/wiki/)
- [Fabric Loom Documentation](https://fabricmc.net/wiki/documentation:loom)
