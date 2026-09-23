# Sovereign: Arcane Strategy & Engineering

A tactical real-time strategy (RTS) and multiblock engineering mod for Minecraft 1.21, built on the **Fabric Loader**, **Fabric API**, and **Gradle Loom** toolchain.

**Sovereign: Arcane Strategy & Engineering** transforms your Minecraft world into an RTS battlefield and automated construction sandbox. Command squads of autonomous minion thralls, deploy ranked battle formations, execute coordinated 90° mass assaults, and construct or dismantle complex multiblock structures in real time.

---

## What Is This Project?

**Sovereign: Arcane Strategy & Engineering** introduces strategic army management, autonomous AI companions, and structural engineering into Minecraft 1.21:

- **Autonomous Minion Thralls**: Tameable, persistent companions with 4 versatile archetype roles:
  - ⚔ **Warrior**: Versatile combat unit automatically acting as frontline melee swordsman, ranged archer, or thrown javelin specialist. Features **Trident Duality** (melee thrusts at $\le 5\text{D}$, thrown piercing javelins at $5\text{D}\text{--}20\text{D}$), thrown tactical ordnance (Frost Grenades, TNT Sticks), and accepts builder **Squad Material Procurement** hunting contracts.
  - 🛡 **Sentinel**: Sturdy defensive bulwark and combat-medic channeling the *Aegis of Restoration* to heal wounded players (commander priority), allied minions, and non-hostile **Iron Golems** under 70% HP within 10 blocks (restoring 6 HP + Regeneration II for 5s with amethyst chime SFX and heart particles), maintaining an expanded 128-block operational leash and teammate alignment with Iron Golems.
  - 🔨 **Builder**: Master architect, resource excavator, and logistics specialist. Autonomously constructs blueprints with persistent zero-timeout execution, **Builder Block Phasing (`noClip`)** while working to seamlessly navigate through walls/ceilings, **Post-Construction Structure Egress** evacuating buildings before collision returns, **Builder Kinematics Isolation** protecting hover stations and deconstruction paths from travel levitation conflicts, automatic **Perimeter Waypoint Deployment & Stationing** on build completion, and Arcane Phase-Shift obstacle resolution. Also deconstructs areas, quarries natural stone, harvests timber via agro-forestry (using bone meal for instant growth), self-crafts replacement tools, shares blocks with peer minions via energy beams, deposits surplus materials into autonomous supply depot chests, and commissions **Squad Material Procurement** contracts to nearby Warriors for mob-derived materials with in-inventory resource synthesis.
  - ⚙ **Auto (`MinionRole.AUTO`)**: The **default archetype** for all newly created, summoned, and recruited minions. Acts as an autonomous agent that dynamically re-evaluates its environment every second, morphing on the fly into **Sentinel** (if any ally < 70% HP or holding position), **Warrior** (if hostiles < 16m or peacetime escort), or **Builder** (if blueprints or mining areas are active).
  - Minions feature customizable 9-slot backpacks, 6 equipment slots, dynamic overhead hearts health indicators, **Player-Like Ground Navigation** ($1.25\text{D}$ step height for smooth stepping over slabs, stairs, and 1-block steps), **Smart Companion Catch-Up Teleportation** (rescuing trapped/stuck followers without skyrocket launches), **Universal Door Auto-Opening** (`autoOpenNearbyDoors` for fluid doorway traversal), and builder-exclusive 3D Arcane Levitation and block phasing.
- **The Sovereign Command Scepter**: Handheld tactical relic enabling:
  - 64-block raycast unit selection and waypoint deployment into straight, parallel ranked army battle lines with **Unified Stationing** (`holdingPosition`) and expanded 128-block operational leash freedom, allowing single-click toggling back to follow.
  - **Banner of Courage (90° Forward Sector)**: Hold right-click to project an expanding tactical cone; releasing launches a synchronized Mass Assault queue hunting down all enclosed hostiles.
  - **Command Hub GUI (`V` key)**: Interactive tactical hub for squad routing (Alpha through Delta), mode cycling, 4-role mass archetype assignment, **Patrol Route Dashboard**, and blueprint selection.
  - **Dual-Tier Panic Retreat (`R` vs `Shift + R`)**: Quick **`R`** disengages active squad members within 64m and recalls them into formation; **`Shift + R`** sounds a fortress-wide **Emergency Citadel Call** (128m), unbinding all patrol duties, sounding a raid horn & bell, and sprinting all units to the commander's defense!
- **Visual Pathway Patrols & Escort Hierarchy**:
  - **Dynamic Custom Hex-Color Routes**: Supports 5 built-in color channels (🟡 Gold, 🔵 Cyan, 🟢 Emerald, 🟣 Purple, 🔴 Crimson) and custom 24-bit RGB hex colors (`#FFD700`, etc.) with a dedicated in-game Route Edit Modal Screen (`PatrolRouteEditModalScreen`).
  - **Paginated Pathway Dashboard in Command Hub (`V`)**: Browse routes 4 per page with previous/next navigation, `+ New Route` creation, modal color swatch editing, waypoint clearing, and route deletion with automatic minion unbinding and safe stationing.
  - **3D Block Wireframes & Luminous Laser Vectors**: Numbered checkpoint badges (`[ 1 ]`, `[ 2 ]`, etc.) with glowing surface laser vector tethers, visible exclusively when holding the Scepter in `PATHWAY` mode. Zero-overlap route protection guarantees different channel routes cannot collide or stack on the same block coordinate.
  - **Universal Cross-Channel Tile Deletion**: Left-click (punch) or right-click any placed waypoint block with the Scepter in `PATHWAY` mode to undo it cleanly from whichever route channel owns it without channel-switching traps.
  - **Ordered Traversal & End-Only Linger**: Minions smoothly march sequentially through each placed tile without intermediate delays, only pausing for a 10s–15s sentry wait upon reaching the terminal ends of the route.
  - **Closed Loop vs. Linear Ping-Pong**: Toggle between continuous loop patrols and ping-pong linear patrols.
  - **World Save Persistence**: All routes are natively saved in world data (`data/minion_patrol_routes.dat`) and restore automatically on world reload and player reconnect.
  - **Patrol Breach Alarm**: Sentry horn and bell alerts nearby allies within 16m when hostiles cross patrol routes.
  - **Minion Escort Hierarchy**: Prime escorts (Shift + Left-Click) and bind to squad leaders (Right-Click) with visible **Arcane Tether Beams**. Escorts of any squad size dynamically deploy into disciplined, non-overlapping military formations around the squad leader, pacing at march/sprint speeds, defending the leader with target coordination, respecting a 16-block combat leash, and returning directly to the leader post-combat.
  - **Dynamic Overhead Badges**: Displays custom hex-colored `[ 🟡 Route # ]`, `[ 🛡 Escort ]`, and `[ ⚙ AUTO: <Role> ]`.
- **Custom Blueprint Construction (100% Exact Block Fidelity)**:
  - **100% Exact Block Preservation**: Minion builders place the exact blocks captured in DESIGN mode with zero procedural alteration, biome substitution, or noise weathering.
  - **Creative Mode Zero-Drop Guarantee**: Zero chests needed. Builders discard unneeded blocks directly from inventory to keep slots clean, while pre-clearing and post-session sweeps eliminate 100% of loose dropped item entities from popping flowers, grass, and leaf decay.
  - **Survival Mode Direct Chest Storage**: Dismantling structures and `MINE` mode area-clearing collect mined blocks directly into builder backpacks instead of scattering onto the ground. When backpacks fill or sessions finish, builders deposit 100% of excess materials into nearby containers or autonomous chests, and vacuum loose ground items.
- **Dual Mining Modes (AREA Default vs. DIRECT) & Confirmation Modal**:
  - **`AREA` Mode (Default — Configurable 3D Boundary Box Quarry)**: Define custom 3D excavation volumes working identically to `DESIGN` mode grid selections but to remove blocks top-to-bottom. Sequential left-clicks (1st sets `Pos1`, 2nd sets `Pos2`, cycle on next click) adhere to the Unified Surface Anchoring Contract. Sneak + Left-Click resets corners.
  - **`DIRECT` Mode (Alternate — Point / Structure Dismantle)**: Instant point-and-click deconstruction anchoring directly on targeted structures or terrain up to 96m away without boundary setup.
  - **In-World Real-Time Height Adjustment**: Hold `Ctrl` + Mouse Scroll (or `[` / `]` bracket keys / `PageUp` / `PageDown`) to adjust excavation height live without scaffolding blocks (Shift for $\pm 5$ fast stepping).
  - **Fiery Orange/Amber Ghost Grid Wireframes**: Pulsing 3D bounding box (`1.0F, 0.45F, 0.05F`), 8 vertex gold accent cubes, and semantic ghost grid wireframes around all solid destructible blocks in the volume.
  - **Mining Confirmation Modal (`MiningConfirmModalScreen`)**: Right-clicking (or clicking `[ ⛏ Confirm Area ]` in the Command Hub) with corners set opens the confirmation modal displaying coordinates, $W \times H \times D$ dimensions, total volume, and a destructible block counter, with interactive height steppers (`[ -5 ]`, `[ -1 ]`, `[ +1 ]`, `[ +5 ]`), `[ ✔ Start Mining ]`, `[ ⌫ Reset ]`, and `[ ✖ Cancel ]`.
  - **Top-to-Bottom Excavation & Zero Air-Mining**: Builders systematically clear all destructible blocks from the highest Y layer down to the lowest, strictly bypassing air and bedrock, and automatically dismissing wireframes upon 100% clearance.
  - **Player-Driven Custom Catalog**: Relies 100% on player-captured structures; retired hardcoded preset buildings, legacy architecture styles, and arbitrary size selectors to focus entirely on user creations.
  - **Free Survival Build Flight**: Unconstrained 3D vanilla flight in Survival mode when holding the Scepter in `BUILD` mode (Space to ascend, Shift to descend, full WASD navigation).
  - **Water Flight Cancellation Safeguard**: Automatically cancels construction, revokes flight, and switches scepter mode to `FOLLOW` with extinguish SFX and warning cues if the player flies over or enters water in `BUILD` mode.
  - **360° Perimeter Flank Spread**: Builders automatically encircle the finished build upon completion across all four flanks (Front, East, Back, West) with beacon beams and chime fanfare regardless of squad assignments.
  - **Tactical RTS Build Camera (`H` Key or `Ctrl` + Scroll)**: Size-scaled elevated perspective with intelligent ceiling raycast clamping inside caves and low rooms.
  - **Semantic Color-Coded Ghost Outlines**: Clear visual wireframes color-coded by element (Emerald Green doors, Amber Gold lights, Arcane Purple utilities/beds, Cyan walls, Ice Blue roof).
  - **Autonomous Stationed Mobilization & Vicinity Chaining**: Placed blueprints wake up stationed builders within an expanded 112–128 block radius. When builder minions complete a blueprint, they automatically transition to the next nearest placed blueprint in their vicinity ($\le 128$ blocks) without falling asleep or requiring manual re-commanding. Blueprints placed down mid-build are automatically queued and initiated immediately upon finishing prior structures.
- **In-World Spatial Blueprint Capture ('DESIGN' Mode)**: Non-destructively convert any in-world structure into an autonomous minion construction blueprint:
  - **Sequential Left-Click Spatial Selection & Ground Offset Protection**: Single-button progression where the 1st left-click sets `Pos1` anchored on top of the clicked block face (preventing terrain dirt from being captured), the 2nd left-click sets `Pos2`, and subsequent left-clicks restart a new selection cycle with real-time candidate raycasting and chime audio cues. Unified client-server attack event interception with GLFW key queue draining (`DESIGN_CLICK_CONSUMER`) and 150ms debounce eliminates double-click bugs when setting `Pos2`. Sneak + Left-Click (or GUI `⌫ Reset` / Modal Discard) cleanly wipes both `Pos1` and `Pos2` across both the tracker and held scepter components, guaranteeing the very next click begins anew at `Pos1`.
  - **In-World Real-Time Height Adjustment (No Blocks Required)**: Hold `Ctrl` + Mouse Scroll (or `[` / `]` or `Page Up` / `Page Down` keys) to raise and lower the top boundary in real time without placing temporary dirt blocks, with `Shift` for $\pm 5$ fast stepping and modal stepper buttons (`[ -5 ]`, `[ -1 ]`, `[ +1 ]`, `[ +5 ]`).
  - **In-World Holographic Ghost Grid Structure Rendering**: Full 3D pulsing magenta bounding prism with 8 vertex accent cubes, candidate aiming volume expansion, and real-time semantic color-coded ghost grid block outlines for doors (Emerald Green), lighting (Amber Gold), containers/beds/workstations (Arcane Purple), stairs/slabs (Ice Blue), defenses/walls (Arcane Orange-Gold), windows/glass (Crystal Cyan), and structural masonry/foundation (Cyan-Magenta grid) around the ground and target building.
  - **Capture Modal GUI (`BlueprintCaptureModalScreen`)**: Right-Clicking the ground or air (without Shift) with corners set opens the design modal dialog to assign custom names, optional descriptions (placeholder indicates optionality; blank descriptions preserve all blocks), and structural archetype categories with live solid non-air block detection.
  - **Non-Destructive Coordinate Normalization & Sorting**: Ingests structures without destroying world terrain, shifts negative/arbitrary corner coordinates to relative origin $(0, 0, 0)$, strips air blocks, and applies deterministic bottom-up topological sorting (`SessionMode.BUILD`) and reverse top-down deconstruction sorting (`SessionMode.DISMANTLE`).
  - **Spatial Safeguards & World Persistence**: Enforces 64x64 footprint, 96-block height, and 393,216-voxel limits, saving custom blueprints directly to world save data (`data/minion_custom_blueprints.dat`) with instant multiplayer synchronization.
- **Custom Blueprint Catalog, Modal Layering & Decommissioning Safeguards**: Seamless lifecycle management for player-captured structures:
  - **Pure Custom Blueprint Catalog**: The blueprint catalog relies 100% on player-captured structures with zero preset bloat, displaying custom names, dimensions, and total block counts.
  - **Custom Blueprint Deletion (`[✕]`)**: Single-click delete buttons on custom blueprints with confirmation modal dialogs and world persistence deletion.
  - **Elevated Z-Plane Confirmation Modals**: Confirmation dialogs (Blueprint Deletion and Minion Decommissioning) render on an elevated matrix depth plane (`Z = 400.0F`), ensuring modal backdrops, dialog text, and action buttons cleanly occlude background UI elements with zero text bleed-through.
  - **Minion Decommissioning Scope Safeguard**: Command Hub **Dismiss / Destroy** offers safe confirmation modal with choice between **Destroy Selected** or **Destroy All**, complemented by a two-step confirmation toggle in `MinionScreen`.
  - **Responsive Mode Description Banner**: Dynamic auto-scaling and width fitting ensure mode descriptions (including DESIGN mode's compact capture banner) stay strictly within the 144px box bounds without overflowing or clipping buttons.
- **Tactical Ordnance & Thrown Arsenal**: Throwable TNT Sticks and cryogenic Frost Grenades (transmutes blocks into snow, flash-freezes water, turns lava to obsidian, summons a powder snow ring, and applies Slowness III), along with full Warrior thrown weapon support and Trident duality.
- **Unified Surface Anchoring Contract & Hollow Grid Mechanics**: Universal surface placement standard applied seamlessly across `BUILD`, `MINE`, `DESIGN`, `PATHWAY`, and RTS Waypoint Ping modes:
  - **The Canonical Anchor Formula**: `anchorPos = world.getBlockState(hitPos).isReplaceable() ? hitPos : hitPos.offset(hitSide)`.
  - **Replaceable vs. Solid Invariance**: Replaceable blocks (tall grass, flowers, double plants, snow layers, water) anchor directly at `hitPos` without floating upward, while solid blocks sit cleanly atop the targeted face normal (`hitPos.offset(hitSide)`), guaranteeing 0-gap ground alignment and zero solid-block embedding.
  - **Holistic Mode Integration**: Powers 3D neon blueprint holograms, deconstruction boundaries, spatial corner boxes (`Pos1`/`Pos2`), live candidate previews, glowing pathway laser tethers, and RTS beacon pings.
- **Clean & Crisp In-Game GUI Overlays (Zero Background Blur)**: All custom mod interfaces (`CommandScepterScreen`, `MinionScreen`, `BlueprintCaptureModalScreen`, `MiningConfirmModalScreen`) cleanly bypass Minecraft 1.21's `applyBlur` post-processing shader pass. Overlays render over razor-sharp in-game viewports with dark translucent slate backdrops and gold borders, preserving 100% tactical situational awareness and eliminating visual blur fatigue during active combat, minion management, and blueprint construction.

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

Run the automated test suite (518+ @Test cases across 45+ suites covering AI, math, networking, logistics, zero-drop creative construction, survival autonomous chest depots, exact blueprint capture, phasing, patrols, zero-overlap routing, escort formations, surface anchoring, and safeguards):

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
- **Mod Menu**: If you have [Mod Menu](https://modrinth.com/mod/modmenu) installed, open **Mods** on the title screen to see **Sovereign: Arcane Strategy & Engineering (`modid-mmcli-agent-modding`)** listed.
- **Game Logs**: Check `.minecraft/logs/latest.log` for the initialization messages:
  ```text
  [main/INFO] (modid-mmcli-agent-modding) Initializing Sovereign: Arcane Strategy & Engineering: modid-mmcli-agent-modding
  [Render thread/INFO] (modid-client) Initializing Sovereign: Arcane Strategy & Engineering Client
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
