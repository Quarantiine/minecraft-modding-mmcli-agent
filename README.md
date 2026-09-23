# ⚔️ Sovereign Simulator: Arcane Strategy & Engineering

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21-brightgreen.svg)](https://www.minecraft.net/)
[![Mod Loader](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![Java Version](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Visible%20Source%20%26%20ARR-red.svg)](LICENSE)
[![Unit Tests](https://img.shields.io/badge/Tests-530%2B%20Passing-success.svg)](src/test/java)

A tactical real-time strategy (RTS) and multiblock engineering mod for **Minecraft 1.21**, built on the **Fabric Loader**, **Fabric API**, and **Gradle Loom** toolchain.

**Sovereign Simulator** transforms your Minecraft world into a living strategic theater and automated architectural sandbox. Command squads of autonomous minion thralls, deploy military ranked formations, execute coordinated 90° mass assaults, script visual laser patrol routes, and capture or erect complex multiblock structures in real time.

---

## ⚖️ License & Permissions: At a Glance

This project is distributed under a **Visible Source & All Rights Reserved (ARR)** license model. We believe in open, transparent code that developers and players can inspect and learn from, paired with firm legal safeguards protecting our brand, custom art assets, and player community.

> 📖 Read the full legal agreement in the [**LICENSE**](LICENSE) file.

### Quick Reference Matrix

| Activity                                   |      Status       | Conditions / Details                                                                                                                        |
| :----------------------------------------- | :---------------: | :------------------------------------------------------------------------------------------------------------------------------------------ |
| **Inclusion in Modpacks**                  |  ✅ **ALLOWED**   | Free inclusion in public or private packs (CurseForge, Modrinth, FTB, Prism, etc.) with author attribution (`Mino`) and intact JAR/LICENSE. |
| **Content Creation & Streaming**           |  ✅ **ALLOWED**   | Record gameplay, stream on Twitch/YouTube, and monetize your videos without restriction.                                                    |
| **Source Code Inspection**                 |  ✅ **ALLOWED**   | Read, study, debug, and review the code for educational and security purposes.                                                              |
| **Local Personal Compilation**             |  ✅ **ALLOWED**   | Clone and build the project locally on your machine for private, non-commercial gameplay.                                                   |
| **Developing Addons / Compatibility Mods** |  ✅ **ALLOWED**   | Build 3rd-party addon mods compiling against Sovereign's public API without bundling core JARs or assets.                                   |
| **Submitting Bugfixes & Pull Requests**    |  ✅ **ALLOWED**   | Contributions to the official repository are welcomed under the [Contributor License Agreement](LICENSE#4-contributions--pull-requests).    |
| **Re-Hosting & Scraper Mirror Sites**      | ❌ **PROHIBITED** | No re-uploading binaries to external sites (e.g., 9Minecraft), custom launchers, or URL shorteners (AdFly).                                 |
| **Commercial Exploitation & Paywalls**     | ❌ **PROHIBITED** | No selling the mod, charging for access, or locking mod features behind server VIP ranks, donations, or crypto/NFTs.                        |
| **Art Asset & Texture Reuse**              | ❌ **PROHIBITED** | 3D models, textures, animations, audio, and UI graphics are proprietary All Rights Reserved. No extracting or repurposing in other mods.    |
| **Rebranded Clones & Public Forks**        | ❌ **PROHIBITED** | No publishing identical clones, rebranded derivatives, or repackaged versions that mimic the mod.                                           |

---

## 🧭 Navigation Portal

- [🎮 Player & Commander Guide](#-player--commander-guide)
  - [The 4 Minion Archetypes](#the-4-minion-archetypes)
  - [Core Strategic Systems](#core-strategic-systems)
  - [Tactical Controls Cheat Sheet](#tactical-controls-cheat-sheet)
  - [Installation Guide](#installation-guide-for-players)
- [🛠 Developer & Contributor Hub](#-developer--contributor-hub)
  - [Architecture Blueprint & Codebase Map](#architecture-blueprint--codebase-map)
  - [Local Development Setup](#local-development-setup)
  - [Compiling & Running](#compiling--running)
  - [Writing Addons & API Usage](#writing-addons--api-usage)
  - [Automated Testing & Invariants](#automated-testing--invariants)
- [📚 Companion Documentation](#-companion-documentation)

---

## 🎮 Player & Commander Guide

### The 4 Minion Archetypes

Minions are tameable, persistent humanoid thralls equipped with backpacks, equipment inventories, dynamic overhead hearts indicators, and specialized tactical AI routines.

```
       [⚔ WARRIOR]                  [🛡 SENTINEL]                  [🔨 BUILDER]                  [⚙ AUTO]
  Frontline Melee & Thrown      Perimeter Guard & Medic       Architect, Quarry & Logistics   Autonomous Adaptive AI
```

1. **⚔ Warrior (`MinionRole.WARRIOR`)**:
   - **Frontline Combatant & Hunter**: Masters swords, axes, maces, bows, and crossbows.
   - **Trident Duality**: Throws piercing tridents at long range ($5\text{D}\text{--}20\text{D}$) and seamlessly transitions into deadly close-quarters melee thrusts ($\le 5\text{D}$) with server-side loyalty retrieval.
   - **Tactical Ordnance**: Wields throwable TNT Sticks and cryogenic Frost Grenades.
   - **Procurement Contracts**: Automatically accepts hunting commissions from Builders to hunt mob resources (wool, bones, slime, leather) and delivers them through peer-to-peer logistics.

2. **🛡 Sentinel (`MinionRole.SENTINEL`)**:
   - **Perimeter Guard & Combat Medic**: Guards designated anchor posts with an 8-block perimeter and 128-block leash, prioritizing shields in offhand.
   - **Aegis of Restoration**: Autonomously channels healing beams to wounded player commanders, allied minions, and friendly **Iron Golems** under 70% HP within 10 blocks (restoring 6.0 HP + Regeneration II for 5s with heart VFX and amethyst resonance).
   - **Warrior Combat Dual-Mode**: Sentinels fight aggressively like Warriors (active hostile target scanning, melee charge at $1.35\text{D}$, and ranged weapon skirmishing) **strictly when all nearby minions, Iron Golems, and commanders need zero healing** (100% full health). The instant an ally takes damage, the Sentinel disengages immediately (`setTarget(null)`) to prioritize medical protection.

3. **🔨 Builder (`MinionRole.BUILDER`)**:
   - **Master Architect & Demolition Specialist**: Autonomously constructs captured blueprints, deconstructs designated volumes, and quarries stone top-to-bottom.
   - **Builder Block Phasing (`noClip`)**: Seamlessly passes through blocks while building or evacuating structures, preventing entombment and wall-trapping.
   - **Logistics Autonomy**: Agro-forestry timber harvesting (using bone meal for instant sapling maturation), tool self-crafting, supply depot chest drops, and peer-to-peer wireless block transfers.
   - **Perimeter Stationing**: Automatically circles finished structures across all 4 flanks, deploying defensive waypoints with fanfare beams and chimes.

4. **⚙ Auto (`MinionRole.AUTO`)**:
   - **The Default Dynamic Archetype**: Newly summoned or recruited minions start in `AUTO` mode, evaluating their tactical environment every second:
     - Morphs to **Sentinel** if an ally drops below 70% HP or when holding a guard post.
     - Morphs to **Warrior** if hostile mobs enter 16m or when escorting during peacetime.
     - Morphs to **Builder** if an active blueprint or quarry task requires engineering hands.

---

### Core Strategic Systems

- **The Sovereign Command Scepter**:
  - Handheld tactical conduit supporting 64-block raycast targeting.
  - Left-Click to select/deselect individual units or hold for 90° cone mass sweeps.
  - Right-Click the ground to deploy military waypoint lines.
- **Ranked Army Battle Formations**:
  - Minions automatically calculate disciplined, parallel 4-unit battle ranks: Warriors form the vanguard frontline ($+4.0\text{D}$), Sentinels form the protective midline escort ($+1.8\text{D}$), and Builders bring up the rear support ($ -2.0\text{D}$).
- **Minion Escort Hierarchy & Squad Leaders**:
  - Delegate minions to bodyguard fellow minions. Shift-Punch an owned minion with the Scepter to designate a **Squad Leader**; all selected escorts dynamically form protective flanking ranks around their leader with active cyan-to-magenta Arcane Tether Beams.
- **Custom In-World Blueprint Capture ('DESIGN' Mode)**:
  - Select two corners with left-clicks to enclose any building in a 3D holographic ghost prism.
  - Adjust height in real time with `Ctrl` + Scroll.
  - Name and save the structure directly to world data without destroying the original building. Minion builders can then replicate it anywhere with 100% exact block fidelity!
- **Visual Pathway Patrols**:
  - Script custom patrol loops or ping-pong routes with glowing checkpoint badges (`[ 1 ]`, `[ 2 ]`) and luminous laser vector tethers.
  - Customize routes with 5 built-in channels or 24-bit RGB hex colors via the interactive modal GUI.
- **Dual-Tier Panic Retreat**:
  - Press **`R`** to immediately recall active squad members within 64m.
  - Press **`Shift + R`** for an army-wide **Emergency Citadel Call** (128m), sounding war horns and tolling fortress bells to sprint all stationed units to the commander's defense.

---

### Tactical Controls Cheat Sheet

| Key / Action           | Context            | Tactical Function                                                                                |
| :--------------------- | :----------------- | :----------------------------------------------------------------------------------------------- |
| **`V` Key**            | Scepter in Hand    | Opens the **Command Hub GUI** (squad channels, modes, patrol dashboard, blueprints).             |
| **`R` Key**            | Scepter in Hand    | **Squad Retreat**: Recalls selected active squad minions within 64m.                             |
| **`Shift + R`**        | Scepter in Hand    | **Citadel Call**: Army-wide emergency alarm (128m) recalling all units and unbinding patrols.    |
| **`H` Key**            | Scepter in `BUILD` | Toggles the elevated **Tactical RTS Build Camera**.                                              |
| **`Ctrl` + Scroll**    | `DESIGN` / `MINE`  | Real-time in-world 3D boundary height adjustment (hold `Shift` for $\pm 5$ fast stepping).       |
| **Right-Click (Hold)** | `FOLLOW` / `STAY`  | Charges the **Banner of Courage (90° Sector)**; release to launch a synchronized Mass Assault.   |
| **Shift + Left-Click** | Minion Target      | **Designate Squad Leader**: Assigns all currently selected minions to escort the clicked leader. |
| **Sneak + Left-Click** | `DESIGN` / `MINE`  | Clears both `Pos1` and `Pos2` selection corners, restarting spatial targeting cleanly.           |

> 📖 For the exhaustive controls manual, see [**COMMANDS.md**](COMMANDS.md).

---

### Installation Guide for Players

1. Install **Minecraft: Java Edition (1.21)** via the official launcher.
2. Install **Fabric Loader** ($\ge 0.16.0$) from [fabricmc.net](https://fabricmc.net/use/installer/).
3. Download the matching **Fabric API (1.21)** from [Modrinth](https://modrinth.com/mod/fabric-api) or [CurseForge](https://curseforge.com/minecraft/mc-mods/fabric-api).
4. Drop both the **Fabric API JAR** and the **Sovereign mod JAR** into your `.minecraft/mods` folder:
   - **Windows**: `%APPDATA%\.minecraft\mods\`
   - **macOS**: `~/Library/Application Support/minecraft/mods/`
   - **Linux**: `~/.minecraft/mods/`
5. Select the **fabric-loader-1.21** profile in the Minecraft launcher and launch the game!

---

## 🛠 Developer & Contributor Hub

### Architecture Blueprint & Codebase Map

The mod follows an event-driven, server-authoritative architecture built on Fabric 1.21:

```
src/main/java/com/example/
├── ExampleMod.java                   # Mod initialization, lifecycle events, item registration
├── block/                            # Custom blocks and interactive multiblock stations
├── construction/
│   ├── ConstructionManager.java      # Server-authoritative build session manager & voxel iterator
│   ├── ConstructionSession.java      # Active blueprint/mining session state machine
│   └── BlueprintManager.java         # NBT serialization and world-save persistence (.dat)
├── entity/
│   ├── custom/
│   │   ├── MinionEntity.java         # Core entity: state, equipment, physics, navigation & AI goals
│   │   ├── MinionRole.java           # Archetype roles (WARRIOR, SENTINEL, BUILDER, AUTO)
│   │   └── FrostGrenadeEntity.java   # Cryogenic projectile entity & terrain freeze logic
│   └── ai/
│       ├── goal/                     # Dedicated AI goals (Aegis healing, formations, patrols, sappers)
│       └── logistics/                # Autonomous harvesting, peer sharing & chest deposition
├── item/custom/
│   └── CommandScepterItem.java       # Handheld tactical relic, raycasts, UI triggers & packet dispatches
├── network/                          # Fabric Networking API (Custom payloads, client-server sync)
└── sound/                            # Custom SFX (amethyst chimes, war horns, bells, arcane resonance)

src/client/java/com/example/client/
├── ExampleModClient.java             # Client entrypoint, keybinding registration & render tick events
├── gui/                              # Screens (Command Hub, Minion GUI, Route Editor, Modals)
└── renderer/                         # 3D world renderers (holographic ghost grids, lasers, badges)
```

---

### Local Development Setup

#### Prerequisites

- **Java Development Kit (JDK) 21+** (required for Minecraft 1.21). Check with `java -version`.
- **Git** installed on your system.
- Modern IDE: **IntelliJ IDEA** (strongly recommended with Minecraft Development plugin) or **VS Code**.

#### Clone & Initialize

```bash
git clone https://github.com/<your-username>/minecraft-modding.git
cd minecraft-modding
```

---

### Compiling & Running

All builds and runtime tasks are managed via the included Gradle wrapper (`./gradlew`):

```bash
# Launch the Loom sandboxed Minecraft client with hot-reload and Yarn mappings
./gradlew runClient

# Launch a standalone dedicated server for network/multiplayer debugging
./gradlew runServer

# Run the complete automated test suite (530+ tests)
./gradlew test

# Compile and package the production mod JAR (outputs to build/libs/)
./gradlew build
```

---

### Writing Addons & API Usage

Sovereign explicitly permits independent third-party mods to compile against its public interfaces:

- **Querying Minion Archetypes**: Access `minion.getRole()`, `minion.getEffectiveRole()`, and `minion.matchesRole(MinionRole.X)`.
- **Interrogating Healing Status**: Query `minion.hasNearbyAlliesNeedingHealing(radius)` to evaluate squad medical status.
- **Teammate Integration**: `MinionEntity.isTeammate(Entity other)` seamlessly respects allied minions, owners, and non-hostile Iron Golems.
- **Listening to Construction Events**: Hook into `ConstructionManager.getInstance()` to track blueprint start, progress, and completion callbacks.

---

### Automated Testing & Invariants

We maintain an extensive automated unit and invariant test suite located in `src/test/java/com/example/`. Tests execute in headless Java without launching a full graphical client:

- **Entity & AI Goals**: `SentinelHealGoalTest`, `MinionFormationAndEquipTest`, `MinionSquadAndRoleTest`
- **Logistics & Harvesting**: `MinionLogisticsAndHarvestingTest`, `MinionAntiJitterAndLevitationSafetyTest`
- **Construction & Dismantling**: `StructureDismantlingTest`, `BedrockAndCeilingSafeguardTest`
- **Ordnance & Physics**: `FrostGrenadeTest`

Always verify that the entire suite passes cleanly before submitting contributions:

```bash
./gradlew test --rerun-tasks
```

---

## 📚 Companion Documentation

For in-depth guides and technical specifications, consult our companion documentation:

- 🎮 [**COMMANDS.md**](COMMANDS.md) — Exhaustive scepter control manual, keybindings, and tactical commands.
- ⚙️ [**FEATURES.md**](FEATURES.md) — Deep-dive architectural specification, math formulations, packet schemas, and AI goal hierarchies.
- ⚖️ [**LICENSE**](LICENSE) — The complete Visible Source & All Rights Reserved legal license.

---

<p align="center">
  <b>Sovereign Simulator: Arcane Strategy & Engineering</b><br>
  Copyright © 2026 Mino (and Project Contributors). All Rights Reserved.
</p>
