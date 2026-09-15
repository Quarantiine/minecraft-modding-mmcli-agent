# Fabric 1.21 Feature Showcase & Technical Specifications

This document provides a comprehensive technical breakdown of all gameplay features, items, entities, blocks, AI behaviors, construction mechanics, client rendering pipelines, GUIs, pathfinding engines, and networking protocols implemented in `modid-mmcli-agent-modding`.

---

## Table of Contents

1. [Loki Command Scepter (`CommandScepterItem`)](#1-loki-command-scepter-commandscepteritem)
2. [Autonomous Minion Thralls (`MinionEntity`) & Spawn Egg](#2-autonomous-minion-thralls-minionentity--spawn-egg)
3. [Tactical Army Architecture: Roles, Squads & Formations](#3-tactical-army-architecture-roles-squads--formations)
4. [Client Visuals, Holograms & Overhead Crest Badges](#4-client-visuals-holograms--overhead-crest-badges)
5. [Interactive GUIs: Command Hub & Minion Management](#5-interactive-guis-command-hub--minion-management)
6. [Multiblock Construction & Blueprint Engine](#6-multiblock-construction--blueprint-engine)
7. [Structure Deconstruction, Mining Area Clearance & Bedrock Immunity](#7-structure-deconstruction-mining-area-clearance--bedrock-immunity)
8. [Combat Sappers & Ephemeral Scaffolding](#8-combat-sappers--ephemeral-scaffolding)
9. [Dedicated Construction Block Subsystem (`ConstructionBlock`)](#9-dedicated-construction-block-subsystem-constructionblock)
10. [Advanced Mob Pathfinding Engine](#10-advanced-mob-pathfinding-engine)
11. [Tactical Ordnance: TNT Stick & Frost Grenade Stick](#11-tactical-ordnance-tnt-stick--frost-grenade-stick)
12. [Data Components & Network Protocol Architecture](#12-data-components--network-protocol-architecture)
13. [Survival vs. Creative Mode Economy & Mechanics](#13-survival-vs-creative-mode-economy--mechanics)

---

## 1. Loki Command Scepter (`CommandScepterItem`)

The **Loki Command Scepter** is a high-tier tactical relic allowing players to command minion thralls, recruit mobs into servitude, and orchestrate automated multiblock construction.

```
                                         [Loki Command Scepter]
                                                   │
         ┌───────────────────┬─────────────────────┼─────────────────────┬───────────────────┐
         ▼                   ▼                     ▼                     ▼                   ▼
   [Shift + Right-Click] [BUILD Mode: Ground]  [MINE Mode: Ground]  [RECRUIT Mode: Mob]   [Tactical Broadcast]
    Open Command Hub GUI  Anchor Construction   Anchor Area Mine     Enthrall Mob into     FOLLOW / STAY
    (Or press [V] key)    (Sneak: Dismantle)    & Deconstruction     Minion Thrall         Radius: 64 blocks
```

### Technical Specifications

- **Identifier**: `modid-mmcli-agent-modding:command_scepter`
- **Class**: `com.example.item.custom.CommandScepterItem`
- **Creative Tabs**: `ItemGroups.COMBAT` and `ItemGroups.TOOLS`
- **Rarity**: `Rarity.EPIC` (purple item name with persistent enchanted glint)
- **Max Stack Size**: `1` (single handheld focus)

### 5 Operating Modes (`CommandMode`)

The scepter cycles through 5 distinct operational modes via **Sneak + Right-Click** (in air) or the Command Hub GUI:

1. **`FOLLOW`** (`0.8F` pitch, `§aFollow`): Directs matching squad thralls to break stationary posts, assemble into formation, and escort the commander.
2. **`STAY`** (`1.0F` pitch, `§eStay`): Directs matching squad thralls to halt movement and hold ground at attention.
3. **`MINE`** (`1.4F` pitch, `§6Mine`): Directs miners and builders to begin top-down deconstruction of clicked multiblock structures.
4. **`BUILD`** (`1.6F` pitch, `§bBuild`): Projects blueprint holographic wireframes and anchors new multiblock construction sessions.
5. **`RECRUIT`** (`1.8F` pitch, `§dRecruit`): Targets wild or enemy mobs to transfigure them into loyal minion thralls.

> [!TIP]
> **Contextual Combat Control**:
> Operating mode `ATTACK` is retired. Combat is handled dynamically: quick-tap an enemy to focus-fire, or channel the 90° forward sector to launch a coordinated mass attack on an entire enemy formation!

### Long-Range Crosshair Targeting (64.0D Reach)

The scepter features an integrated 64-block line-of-sight raycasting engine:

- **Direct Minion Selection**: Aiming crosshair at an owned minion and right-clicking toggles unit selection with audio/visual feedback (chime + hearts to select; bass + smoke to deselect). Selected minions display team glowing outlines and an amber star in their overhead badge.
- **Selective Ground Waypoint Pings**: Right-clicking terrain up to 64 blocks away drops a ground beacon beam (`END_ROD` + `GLOW`). Selected minions matching the active squad channel sprint to the ping and automatically arrange into tactical combat stations, holding position upright at attention. Deployed minions are **automatically deselected** (`minion.setSelected(false)`), clearing selection outlines and freeing the commander's selection buffer for immediate subsequent squad micro-management without requiring manual deselection inputs.
- **Hostile Focus-Fire Raycasting**: Aiming crosshair directly at a hostile mob up to 64 blocks away and right-clicking issues a squad-wide focus-fire ping, accompanied by war drum cadences and crit particles.
- **Direct Scepter Follow**: Right-clicking an owned minion while it is holding position commands that individual unit to break guard stance and follow master.
- **Skyward Broadcast Directives**: Right-clicking the open sky broadcasts the active mode (`FOLLOW` or `STAY`) to all matching squad units within 64 blocks.
- **Rapid Army Deselection**: In-world Sneak + Left-Click against any block in non-`BUILD` modes immediately deselects all active minions with bass audio and smoke puffs.

### Banner of Courage (Channeled 90° Forward Sector & Real-Time Highlighting)

- **90° Directional Forward Sector**: Holding right-click projects an expanding 90° forward conical sector (fan) aligned with the commander's horizontal line-of-sight yaw ($\pm 45^\circ$ FOV), extending from 3.0 up to 16.0 blocks away over ~36 ticks with rising pitch chime audio.
- **Visual Projection VFX**: Renders a curved outer arc and two radiating boundary rays along the left ($-45^\circ$) and right ($+45^\circ$) borders using alternating `PORTAL` and `FLAME` particles.
- **Real-Time Targeting Illumination**:
  - While charging, all owned minions located inside the expanding 90° sector immediately illuminate with team glowing outlines and emit beacon sparkle particles in real time. Minions outside the cone or moving out of view have their preview glow automatically extinguished.
  - All hostile mobs inside the sector are marked with real-time targeting cues (`ANGRY_VILLAGER` and `CRIT` particles), providing full visual targeting confirmation.
- **Coordinated Mass Assault on Release**:
  - If hostile mobs are enclosed in the 90° sector upon release, minions automatically unleash a coordinated **Mass Attack**! Frontline enemies are assigned to melee fighters, while backline or ranged enemies are assigned to archers. The attack is heralded by a raid horn fanfare (`SoundEvents.EVENT_RAID_HORN`) and war drum cadences.
  - Any owned minions enclosed in the cone are simultaneously selected and rallied into the attack squad.
- **Persistent 90° Mass Assault Queue Targeting (Multi-Target Chaining)**:
  - When the 90° forward sector encloses multiple hostile mobs upon release, all enclosed hostiles are sorted frontline-to-backline and assigned to each participating minion as a persistent combat queue via `minion.setAssaultTargets(enclosedHostiles)`.
  - **Sequential Target Eradication**: Minions do not disengage after slaying a single mob. When an assigned mob is defeated (`onKilledOther`) or invalidated in `tick()`, the minion checks `hasAssaultTargets()`, immediately selects the nearest alive enemy in the queue within a 48-block engagement radius (`2304.0D` sq blocks) via `acquireNextAssaultTarget()`, sprints to engage at 1.35D velocity, and continues fighting relentlessly until **all selected enemies in the sector are eliminated**.
  - **Frontline vs. Backline Distribution**: Frontline melee combatants engage proximal targets, while ranged archers prioritize rearward and backline hostiles (`enclosedHostiles.size() - 1 - ...`).
  - **Formation Leash Preservation**: `MinionFormationFollowGoal` yields priority to the ongoing assault while alive queued targets remain in the engagement zone, preventing minions from prematurely disengaging back into marching formation during active combat.
  - **Graceful Post-Combat Regrouping**: Only after the entire assault queue is cleared does `returnToOwnerPostCombat()` trigger, recalling thralls smoothly to the commander.
  - **Tactical Override & Queue Flushing**: Issuing a panic retreat (Keybind `R` / `RetreatPayload`), dropping a ground waypoint ping, or commanding units to sit/hold immediately flushes the assault queue (`clearAssaultTargets()`), allowing instant tactical disengagement.
- **War Horn Blast & Focused Rally**: When no hostiles are enclosed, releasing sounds a goat horn blast (`SoundEvents.ITEM_GOAT_HORN_PLAY`), gathering all enclosed minions strictly within the 90° sector into the active squad channel, clearing stale guard posts, commanding them to `FOLLOW`, and discharging an arcane release burst along the 90° perimeter arc and boundary rays.
- **Channeled Mass Transfiguration**: If a target archetype role has been primed on the scepter (via the Command Hub GUI toggle bar or `TARGET_ROLE` data component), all minions enclosed in the 90° sector are automatically transfigured into that role, auto-equipped from their backpacks, enveloped in `ENCHANT` particles, and celebrated with a level-up chime (`SoundEvents.ENTITY_PLAYER_LEVELUP`).
- **Quick-Tap Preservation**: Quick taps (<8 ticks) instantly dispatch targeted raycast commands or blueprint placement without charging the sector or leaving residual glow states.

### 4-Quadrant Blueprint Rotation & Door Offset Beacons

In `BUILD` mode, commanders can rotate structures prior to placement:

- **Rotation Cycling**: Sneak + Left-Click against any block cycles rotation through 0° → 90° → 180° → 270° around origin $(0, 0)$.
- **Door Offset Discovery**: The rotation engine automatically tracks lower door coordinates, projecting vertical sparkle beams (`HAPPY_VILLAGER` + `END_ROD`) and displaying the door's facing compass direction in the HUD actionbar (`§6🏗 [Name] §8| §bRotation: [Deg]° §8| §a🚪 Door: [Dir]`).

### Arcane Mob Recruitment (`RECRUIT` Mode)

- Right-clicking any living non-minion mob transfigures it into an obedient Minion Thrall bound to the player.
- Preserves existing worn armor and held items, binds the player's owner UUID, triggers arcane conversion particle VFX, and plays level-up chime audio.

### Interaction Matrix

| Interaction | Condition | Behavior |
| :--- | :--- | :--- |
| **Right-Click (Quick Tap)** | Hostile Mob ($\le 64\text{D}$) | Focus-fire attack order; squad units focus target with drum cadence. |
| **Right-Click (Quick Tap)** | Ground Block ($\le 64\text{D}$) | Drops ground waypoint; selected squad units sprint, form up, hold station, and **automatically deselect**. |
| **Right-Click (Quick Tap)** | Owned Minion ($\le 64\text{D}$) | Toggles unit selection (chime/hearts vs bass/smoke). |
| **Right-Click (Quick Tap)** | Open Sky / Air | Broadcasts active directive (`FOLLOW`, `STAY`) to 64-block radius. |
| **Hold Right-Click (>8 ticks)** | Hostiles in Sector | Charges 90° forward sector; launches coordinated **Mass Attack** across enemy formation. |
| **Hold Right-Click (>8 ticks)** | Minions in Sector | Charges 90° forward sector; rallies, selects, and transfigures enclosed minions into primed role. |
| **Sneak + Right-Click** | Aiming at Air | Cycles scepter command mode forward (`FOLLOW` → `STAY` → `MINE` → `BUILD` → `RECRUIT`). |
| **Sneak + Right-Click** | Owned Minion | Opens interactive **Minion Management GUI** (`MinionScreen`). |
| **Sneak + Left-Click** | Non-`BUILD` Modes | Deselects all active minions immediately with bass/smoke feedback. |
| **Sneak + Left-Click** | `BUILD` Mode | Cycles blueprint rotation (0° → 90° → 180° → 270°) with actionbar HUD. |
| **Press [V] Key** | In-Game Hotkey | Opens the **Command Hub GUI** (`CommandScepterScreen`). |
| **Press [R] Key** | In-Game Hotkey | **Tactical Panic Retreat**: Sounds warning bell, clears minion targets, and recalls all units to formation. |

---

## 2. Autonomous Minion Thralls (`MinionEntity`) & Spawn Egg

The **Minion Thrall** is an autonomous bipedal worker, builder, and combat entity bound to its summoning commander.

### Technical Specifications

- **Entity Identifier**: `modid-mmcli-agent-modding:minion`
- **Entity Class**: `com.example.entity.custom.MinionEntity`
- **Base Class**: `net.minecraft.entity.passive.TameableEntity`
- **Spawn Egg**: `modid-mmcli-agent-modding:minion_spawn_egg` (navy base `0x2C3E50`, arcane gold spots `0xF1C40F`)

### Combat Attributes & Movement Kinematics

- **Max Health**: `40.0` HP (20 hearts)
- **Base Armor**: `4.0` points (+ dynamic worn armor defense)
- **Movement Speed**: `0.30` base speed
- **Attack Damage**: `5.0` base physical damage
- **Step Height**: `1.0625D` base step height, allowing smooth traversal over slabs, stairs, and 1-block terrain steps without jumping.
- **Follow Tracking Range**: `64.0D` base tracking radius (`EntityAttributes.GENERIC_FOLLOW_RANGE`), enabling minions to acquire targets, maintain formation, and respond to orders across large battlefields.
- **Emergency Teleport Leash**: `64.0D` distance threshold (`TELEPORT_DISTANCE_THRESHOLD`), allowing units to engage distant hostiles and maneuver across complex terrain without prematurely snapping back to the commander.

### Upright Attention Stance & Posture

- Minions holding position or stationed on guard stand tall at full 100% height at attention.
- The legacy sitting offset (`-0.3125D`) and decoupled riding pose have been eliminated, ensuring units maintain a disciplined military posture on station.

### Clean-Slate Spawning & Dynamic Role Disarming

- **Bare-Handed Initialization**: Minions summoned via spawn egg or recruited from wild mobs initialize with empty equipment hands and an empty 9-slot backpack.
- **Dynamic Role Disarming**: When a minion's role changes (e.g. from Warrior to Builder), any equipped mainhand weapon incompatible with the new role is immediately disarmed and moved into the backpack, or dropped at feet if the backpack is full.
- **Warrior Ranged vs. Melee Suppression**: When a Warrior equips a bow or crossbow, melee strike behaviors are suppressed, allowing them to strafe and skirmish as an archer. When equipped with a sword, axe, or mace, melee attack goals engage automatically.

### Health Regeneration & Interactive Feeding

- **Passive Out-of-Combat Regeneration**: Regenerates 1.0 HP every 40 ticks (2.0s) when free from hostile combat for $\ge 60$ ticks.
- **Interactive Healing**: Right-clicking an injured minion restores health:
  - **Food Items**: Restores health proportional to food hunger points.
  - **Gold Nugget**: Restores `+1.0` HP.
  - **Gold Ingot**: Restores `+4.0` HP.
  - **Gold Block**: Restores `+20.0` HP (full heal).
  - Triggers eating sound effects and rising heart particles.

### Allied Friendly-Fire & Damage Gating

- **Friendly-Fire Immunity**: Allied damage gating cancels incoming damage from the owner, allied minions, and stray friendly warrior arrows.
- **In-Wall Suffocation Immunity**: Minions levitating, climbing, or standing within `ConstructionBlock` or terrain obstacles are completely immune to `DamageTypes.IN_WALL` suffocation damage.

### Post-Combat Regrouping & Assault Queue Chaining (`assaultTargets`)

- **Assault Queue Lifecycle**:
  - `hasAssaultTargets()`: Validates and dynamic-prunes dead, removed, or out-of-world targets, returning `true` if alive enemies remain in the queue.
  - `getAssaultTargets()`: Returns an unmodifiable view of pending assault targets.
  - `acquireNextAssaultTarget()`: Automatically selects the closest living target in the queue within 48 blocks (`2304.0D` sq blocks), updates entity target, and sets sprint navigation at 1.35D.
  - `clearAssaultTargets()`: Immediately purges the queue upon retreat, ground repositioning, or hold directives.
- **Relentless Combat Chaining**: When a minion kills its target (`onKilledOther`) or its combat target dies/despawns in `tick()`, it queries `hasAssaultTargets()`. If targets remain, it immediately chains to the next target instead of disengaging.
- **Orderly Post-Combat Formation Return**: Only once the assault queue is completely empty (`hasAssaultTargets() == false`) does the minion increment `outOfCombatTicks` and execute `returnToOwnerPostCombat()`, returning to its commander or sentinel guard anchor.

### Minion Hearts Health Display & Real-Time Visualization

To ensure commanders can monitor their thralls' vital status at a glance during combat and building operations, the mod provides real-time hearts health rendering across both in-world visual badges and management GUIs:

- **Dual-Layer Health Monitoring**:
  - **In-World Overhead Billboard**: Renders an animated hearts health bar directly above the minion's head (`MinionOverheadBadgeFeatureRenderer.getHealthDisplay`).
  - **GUI Preview Panel**: Renders a dedicated health plate at the base of the 3D entity preview in `MinionScreen`.
- **Automatic Server-Client Synchronization**: Powered directly by vanilla `LivingEntity.HEALTH` tracked data (`TrackedData<Float>`), updating smoothly on client displays whenever minions take damage or heal without requiring custom network packets.

### Singleplayer Host Auto-Adoption

- Server-side singleplayer host validation automatically evaluates ownership on server load, rebinding orphaned or unowned minions to the host player across offline development restarts.

### Direct Player Interactions (Without Scepter)

- **Empty-Hand Right-Click**: Toggles the minion between Stay (holding position) and Follow.
- **Sneak + Right-Click**: Opens the **Minion Management GUI** (`MinionScreen`).

### Minion Spawn Egg (`MinionSpawnEggItem`)

- Summons minion thralls bound to the player in standby guard stance with level-up chime audio and heart particles.
- **Spawner Reconfiguration**: Right-clicking a vanilla mob spawner with the spawn egg reconfigures the spawner to produce Minion Thralls.

---

## 3. Tactical Army Architecture: Roles, Squads & Formations

### 4 Specialized Archetype Roles (`MinionRole`)

| Role | Color | Combat Profile | Primary Equipment | Behaviors |
| :--- | :--- | :--- | :--- | :--- |
| **`WARRIOR`** | Red (`§c`) | Frontline Melee **or** Ranged Skirmish | Swords, Axes, Maces **OR** Bows, Crossbows | **Dual Combatant**: Functions as a frontline melee striker or as a ranged archer based on equipped weapon. Leads formation frontline. |
| **`SENTINEL`** | Green (`§a`) | Perimeter Guard & Combat Medic | Sword + Shield | Holds designated anchor; 8-block guard zone; 12-block leash retreat; prioritizes shields. **Aegis of Restoration**: Autonomously channels healing to allied minions under 70% HP within 10 blocks, restoring 6.0 HP (3 hearts) and granting Regeneration II for 5s with heart VFX and chime audio (6s cooldown; fallback self-heal below 40% HP). |
| **`BUILDER`** | Blue (`§9`) | Construction | Pickaxes, Shovels | Autonomous multiblock construction and deconstruction with full 3D Arcane Levitation hover flight. |
| **`MINER`** | Gold (`§6`) | Demolition | Pickaxes | Specializes in structure deconstruction and excavation. |

### 5 Tactical Squad Channels (`SquadGroup`)

Commanders can organize forces into discrete squad channels or issue army-wide directives:

- **`ALL`** (`§f`, White): Army-wide wildcard broadcast channel.
- **`ALPHA`** (`§c`, Red `0xE74C3C`): Shock vanguard division.
- **`BRAVO`** (`§9`, Blue `0x3498DB`): Heavy bulwark division.
- **`CHARLIE`** (`§a`, Green `0x2ECC71`): Scout and archery division.
- **`DELTA`** (`§6`, Gold `0xF39C12`): Engineering and demolition division.

### Ranked Army Line Formations (`MinionFormationFollowGoal`)

When following the commander or holding waypoint stations, minions deploy into straight, parallel military battle ranks (lines of 4 units) with uniform forward depth per rank line:

```
                            [Commander / Master] (Yaw)
                                       │
     Line -1 (Rank 1): [W4]     [W5]   │   [W6]     [W7]      ◄── Warrior Line 1 (+2.0 forward)
     Line  0 (Rank 0): [W2]   [W0]     │     [W1]   [W3]      ◄── Warrior Line 0 (+4.0 forward)
                       (-3.6) (-1.35)  │    (+1.35) (+3.6)
                                       │
                       [S2]   [S0]     │     [S1]   [S3]      ◄── Sentinel Midline (+1.8 forward)
                                       │
                                   [Master]
                                       │
                       [B2]   [B0]     │     [B1]   [B3]      ◄── Builder Support (-2.0 rear)
                                       │
                       [M2]   [M0]     │     [M1]   [M3]      ◄── Miner Logistics (-4.2 deep rear)
```

- **Straight Parallel Battle Ranks**: Every 4 units in a role share the exact same `forwardOffset`, eliminating curving arcs or wedges and presenting a disciplined military battle front.
- **Frontline Lines (Warriors)**: $+4.0\text{D}$ forward (stepping back $2.0\text{D}$ per rank line), intercepting head-on threats with both melee swordsmen and archers.
- **Midline Escort Lines (Sentinels)**: $+1.8\text{D}$ forward, providing an immediate defensive bulwark shielding the commander.
- **Rearguard Support Lines (Builders)**: $-2.0\text{D}$ rear, staying tucked safely behind the commander.
- **Logistics Lines (Miners)**: $-4.2\text{D}$ deep rear, maintaining clear operational buffer behind builders.
- **Open Central Command Lane**: Inner units flank at $\pm 1.35\text{D}$ and outer units at $\pm 3.60\text{D}$, leaving an open central corridor for the commander to lead, aim scepters, and shoot without friendly obstruction.
- **Movement Hysteresis Yaw Anchoring**: Formation heading locks during stationary camera sweeps ($\le 0.04\text{ blocks}^2$ displacement), preventing minions from orbiting dizzyingly when the player looks around.
- **Universal Arcane Levitation Traversal & Obstacle Vaulting**: All minions possess 3D Arcane Levitation mobility. When navigating across extreme vertical terrain, descending off roofs/high cliffs ($\Delta Y < -1.5\text{D}$), ascending sheer bluffs ($\Delta Y > 1.25\text{D}$), or encountering pathfinding stalls ($\ge 4$ ticks), thralls smoothly levitate in 3D with purple and cyan particle spirals (`PORTAL` + `ENCHANT`) and glide directly to their destination, landing safely with zero fall damage. Unanchored thralls also retain automatic 14-tick obstacle vaulting when bumping into fences or 1-block steps. Builders retain dedicated 3D hover station calculation for multiblock assembly.

### Automatic Waypoint Combat Formations & Auto-Deselect Flow

When the commander drops a ground waypoint ping, selected minions automatically deploy into their respective Ranked Army Lines facing the commander's line-of-sight yaw:

- **Automatic Selection Clearance**: Selected minions dispatched to the waypoint are immediately deselected (`setSelected(false)`). Their overhead star and squad glowing outlines extinguish as they lock into stationary guard attention at the destination coordinates.
- **Warriors**: Form straight battle lines ahead of the objective.
- **Sentinels**: Form protective bulwark lines behind the vanguard.
- **Builders & Miners**: Form disciplined support and logistics lines to the rear.
- **Safe Surface & Headroom Detection**: Evaluates vertical column to ensure stations land on solid ground with 2 blocks of clear headroom.
- **Elevation Gliding**: If the designated waypoint is elevated on a ledge or down in a ravine ($\Delta Y > 1.25\text{D}$ or $\Delta Y < -1.5\text{D}$), minions engage Arcane Levitation to glide smoothly to their designated formation slots.

### Formation Geometries

Commanders can also choose overarching formation shapes via the Command Hub GUI:
- **`WEDGE`**: Arrowhead wedge spearheading forward.
- **`LINE`**: Lateral battle line perpendicular to commander heading.
- **`BOX`**: Defensive perimeter box surrounding the commander.

---

## 4. Client Visuals, Holograms & Overhead Crest Badges

### Billboarded Overhead Crest Badges & Hearts Health Display (`MinionOverheadBadgeFeatureRenderer`)

Floating billboard badges render directly above each minion's head using exact LIFO matrix reversal:

- **3-Line Vertical Layout**:
  - **Line 1 (Top Line, $y = -\text{LINE\_SPACING}$)**: **Squad Banner** — Displays squad flag, name, Roman numerals (`⚑ SQUAD ALPHA [I]`), and an amber star (`§6★ `) when selected.
  - **Line 2 (Middle Line, $y = 0.0\text{F}$)**: **Role Crest** — Displays tactical icon and role (`⚔ WARRIOR`, `🛡 SENTINEL`, etc.) with an amber `[HOLD]` indicator when stationed.
  - **Line 3 (Bottom Line, $y = +\text{LINE\_SPACING}$)**: **Hearts Health Bar** — Real-time hearts health visualization (`getHealthDisplay(float health, float maxHealth)`).
- **Dynamic Heart Glyph Formatting (`getHealthDisplay`)**:
  - **10-Heart Visual Scale**: Renders up to 10 heart glyphs (`TOTAL_HEARTS = 10`, glyph `❤`).
  - **Vivid Color Contrast**: Filled hearts render in vibrant red (`§c❤`), while missing/depleted health renders in dark gray (`§8❤`), followed by exact numeric HP (`§f[current]/[max]`, e.g. `§c❤❤❤❤❤§8❤❤❤❤❤ §f20/40`).
  - **Boundary Safeguards**: Damaged minions ($0 < \text{HP} < \text{Max}$) never display 100% filled hearts, and living minions ($\text{HP} > 0$) never display 0 filled hearts, guaranteeing immediate visual injury recognition.
- **Two-Pass Fullbright Rendering**:
  - Pass 1: Translucent see-through pass with background plate (`0x20FFFFFF`) visible through solid terrain walls.
  - Pass 2: High-contrast normal pass with full opacity text and transparent plate when in direct line of sight.
  - Rendered with `LightmapTextureManager.MAX_LIGHT_COORDINATE` for crisp, fullbright legibility in deep caves, underwater trenches, and midnight skirmishes.

### Biped Player Model & Dynamic Arm Poses (`MinionEntityRenderer`, `MinionClothingFeatureRenderer`)

- **Player Biped Model**: Renders using `PlayerEntityModel<MinionEntity>` with dual-layer armor trims and outer clothing layers.
- **Dynamic Arm Poses**: Automatically switches poses for shield blocking, bow drawing, crossbow charging/holding, and trident aiming.
- **Glowing Squad Outlines**: Selected minions glow with squad-specific team outline colors (Alpha Red, Bravo Blue, Charlie Green, Delta Gold, All White).

### Persistent 3D Blueprint Holograms (`BlueprintHologramRenderer` & `ClientConstructionTracker`)

- **Server-Synchronized Lifecycle**: Dispatches `SyncConstructionSessionPayload` on start and `EndConstructionSessionPayload` on completion/cancel to client trackers.
- **In-World Projection**:
  - `BUILD` Sessions: **Neon cyan** wireframe bounding box with translucent ghost block outlines.
  - `DISMANTLE` Sessions: **Fiery orange** wireframe bounding box.
  - Dynamic Quadrant Rotation: Wireframe and ghost blocks dynamically rotate to match the active scepter rotation.
  - Remains visible in the world while minions construct, vanishing automatically once the last block is placed.

---

## 5. Interactive GUIs: Command Hub & Minion Management

### 5.1 Command Hub GUI (`CommandScepterScreen`)

Opened via **Shift + Right-Click** with the scepter or pressing the **`V`** key. Features an expanded 280px modal:

```
+-------------------------------------------------------------------+
|                     LOKI COMMAND SCEPTER HUB                      |
+-------------------------------------------------------------------+
|  [ SQUAD CHANNEL: ALL / ALPHA / BRAVO / CHARLIE / DELTA ]         |
+---------------------------------+---------------------------------+
|  DIRECTIVES                     |  BLUEPRINT CATALOG (Page 1/2)   |
|  [FOLLOW]  [STAY]  [MINE]       |  +---------------------------+  |
|  [BUILD]   [RECRUIT]            |  | Overlord Watchtower (7x7) |  |
|                                 |  +---------------------------+  |
|  FORMATIONS                     |  | Arcane Obelisk (5x5)      |  |
|  [WEDGE]   [LINE]  [BOX]        |  +---------------------------+  |
|                                 |  [ < Prev ]       [ Next > ]    |
+---------------------------------+---------------------------------+
|  MASS ARCHETYPES:                                                 |
|  [⚔ Warrior]    [🛡 Sentinel]    [🏗 Builder]    [⛏ Miner]        |
+-------------------------------------------------------------------+
|  [Execute]     [Teleport All]     [§c✖ Destroy All]      [Close]   |
+-------------------------------------------------------------------+
```

- **4-Button Mass Archetype Bar ($y = 232$)**: 
  - **Direct Batch Conversion**: Clicking an archetype immediately dispatches a `MassRolePayload` converting all selected minions (or all minions within the active squad channel) within a **64-block radius** (`CommandScepterItem.MINION_COMMAND_RADIUS = 64.0D`) into the chosen role. If no minions are currently selected, it falls back to batch-assigning all owned minions within the 64-block radius matching the active squad filter.
  - **Stateful Toggle & Transfiguration Priming**: Buttons operate as stateful toggle controls. Clicking an unselected role primes it as the scepter's active `TARGET_ROLE` (displaying a colored active indicator bar beneath the button and updating the modal title to `[Role] (Rally Transform)`). Clicking the already-selected role toggles it off. When primed, releasing a Banner of Courage rally ring in the world will automatically transfigure all gathered minions into this role.
  - **Contextual Tooltips**: Hovering over each button displays rich contextual tooltips explaining current selection state, role abilities, and rally transfiguration behavior.
- **Squad Filter Bar**: Selects target squad division for directives and mass assignments.
- **Directives & Formations**: Radio buttons for quick operational mode and marching shape toggles.
- **Paginated Blueprint Viewport**: Displays 3 cards per page with dynamic `<` and `>` controls, block counts, and dimensions.
- **Global Actions**:
  - `Execute`: Dispatches active configuration.
  - `Teleport All`: Recalls all owned minions to player location.
  - `§c✖ Destroy All`: Decommissions all owned minions within range.
  - `Close`: Exits the GUI.
- **Shift-to-Close Architecture**: Tap Shift to dismiss the GUI immediately once opened. Latching open-state guard prevents accidental closing when opened via sneak-right-click.

### 5.2 Minion Management GUI (`MinionScreen`)

Opened via **Sneak + Right-Click** directly on an owned minion. Features an expanded 248px modal:

- **Header Plate**: Displays custom minion name/title, squad division color banner, and interactive Role / Squad cycle buttons.
- **Labels**: Clean, prominent bolded `"Inventory"` ($x = 116, y = 5$) header above the 9-slot backpack (redundant Equipment text removed for streamlined visual clarity).
- **6 Dedicated Equipment Slots**: Head, Chest, Legs, Feet, Mainhand, and Offhand with ghost item sprites.
- **9-Slot Backpack Grid**: Internal storage matrix for minion resources and scavenged blocks.
- **Live 3D Entity Preview & Hearts Health Plate**:
  - Interactive model preview reflecting equipped armor and held weapons, with comprehensive status tooltip (HP, armor rating, role, squad, stance).
  - **Prominent Hearts Health Plate**: Positioned directly beneath the 3D entity preview ($y = \text{previewBottom} - 12$ to $\text{previewBottom} - 1$) with a squad-colored border and translucent dark backdrop (`0xDD101520`), rendering the live hearts display (`getHealthDisplay`) centered with auto-width scaling (`healthScale`).
- **Dual-Row Action Buttons**:
  - **Row 1**: `✦ Teleport to Me` (168px full-width button).
  - **Row 2**: `§c✖ Destroy` on the left (82px) and `Cancel` on the right (82px) for safe exit.
- **Smart Shift-to-Close (`SmartCloseHandler`)**: Clicking inventory slots while holding Shift transfers items (`quickMove`) without closing the modal; clean Shift taps close immediately.

---

## 6. Multiblock Construction & Blueprint Engine

### Curated Blueprint Catalog (`BlueprintRegistry`)

1. **Overlord Watchtower ($7 \times 7 \times 9$, 108 blocks)**: Fortified stone and wood tower with observation battlement, arrow slits, and wooden door entrance.
2. **Arcane Obelisk ($5 \times 5 \times 8$, 64 blocks)**: Mystical spire featuring obsidian foundation, polished deepslate, gold core, and redstone lantern finials.
3. **Defensive Barricade ($9 \times 3 \times 3$, 45 blocks)**: Heavy frontline barrier with oak logs, cobblestone wall embrasures, and wooden palisade fencing.

### Deterministic Topological Sorting

- Blueprints are compiled into dependency-ordered placement sequences.
- Structural foundations, corner pillars, and inverted stair arches are always scheduled and placed prior to dependent upper blocks, roofs, and decorations.

### Server Construction Manager (`ConstructionManager` & `ConstructionSession`)

Server-authoritative engine orchestrating automated multiblock building and demolition:

- **Task Queue & Topological Ordering**: Breaks blueprints into discrete block placement tasks sorted bottom-up.
- **Task Leasing**: Minions claim leases on available tasks. If a builder runs out of materials or stalls, the lease releases for other workers.
- **Dual Economics**:
  - **Creative Mode**: Zero-cost instant block placement.
  - **Survival Mode**: Consumes blocks from minion backpacks or scavenges nearby chests within 12 blocks.

### Arcane Builder Levitation & Universal 3D Levitation Traversal (`MinionBuildGoal`, `MinionEntity`, `WaypointHoldGoal`, `MinionFormationFollowGoal`, `SentinelGuardGoal`)

- **Universal 3D Arcane Levitation Traversal**: All minion thralls (Builders, Warriors, Sentinels, Miners, and Rangers) utilize 3D Arcane Levitation to seamlessly traverse extreme vertical terrain and multiblock structures:
  - **High Cliff & Rooftop Descent**: When stationed on high cliffs, rooftops, or newly completed buildings, minions smoothly glide off high structures ($\Delta Y < -1.5\text{D}$) down to the ground or their target post without taking fall damage or getting trapped on roofs.
  - **Elevated Cliff & Building Ascent**: When navigating towards elevated waypoints, cliffs, or commanders perched on structures ($\Delta Y > 1.25\text{D}$), minions automatically engage 3D levitation flight to ascend sheer vertical walls and land safely at their destination.
  - **Obstacle & Navigation Stall Recovery**: If ground navigation stalls or hits horizontal obstructions for $\ge 4$ ticks, minions engage Arcane Levitation flight in 3D to bypass the barrier.
  - **Hierarchical Destination Resolution**: `resolveActiveTargetDestination()` dynamically reconciles explicit goal traversal stations, active combat targets, waypoint/guard anchors, commander positions, and navigation waypoints.
  - **Safe Arrival & Landing Fanfare**: Upon reaching their destination within tolerance (horizontal $\le 2\text{D}$, vertical $\le 1.5\text{D}$), minions float smoothly to solid ground, deactivate levitation, restore gravity, and burst into arcane purple and cyan runes (`PORTAL` and `ENCHANT`).

- **Arcane Builder Levitation**: Elevated multiblock construction completely transcends temporary scaffolding generation and block climbing. When assigned an elevated task ($\Delta Y > 1$), builders activate Arcane Levitation:
  - **Dynamic 3D Hover Station**: The minion calculates an optimal 3D air station adjacent to the target block ($1.4\text{D} \to 1.8\text{D}$ horizontally away, with unobstructed headroom) and smoothly glides to it at $0.35\text{D}$ velocity.
  - **Arcane Runes & Safe Hovering**: Emits purple and cyan portal/enchant particles (`PORTAL` and `ENCHANT`) around the builder's boots while hovering. Gravity is suppressed and fall damage is 100% neutralized.
  - **Elevated Task Chaining**: Upon placing or dismantling a block, the builder immediately leases the next topological task in the sequence and glides directly to the next block station without descending to the ground.
  - **Smooth Earthward Landing & Roof Exit**: When all tasks are completed or work is paused, the builder's `activelyBuilding` state releases, allowing universal levitation to carry the worker smoothly off the roof to the master or ground post.
  - **Zero Scaffolding Clutter**: Permanently eliminates minion suffocation inside scaffolding blocks, wall-scraping friction, and leftover scaffolding clutter. (Combat sapper bridging via `MinionSapperGoal` remains intact for infantry chasm crossings).

---

## 7. Structure Deconstruction, Mining Area Clearance & Bedrock Immunity

### Deconstruction & Mining Area Clearance (`SessionMode.DISMANTLE`)

- **Full 3D Selected Area Clearance**: When initiated in `MINE` mode, the session scans the entire 3D volume (`worldBoundingBox`) from highest Y to lowest Y, generating tasks for **all** solid, destructible blocks contained within the area (natural stone, ores, dirt, trees, structures).
- **Reverse Topological Top-Down Execution**: Demolishes upper roofs, battlements, and highest layers first, ensuring structures and terrain are cleared safely top-to-bottom.
- **Zero Air-Mining Guarantee**:
  - Air blocks are completely excluded from task generation.
  - When leasing tasks in `claimNextTask()` or ticking in `MinionBuildGoal`, any task position that is already air in the world is auto-completed immediately. Minions **never** pathfind to, hover at, or swing pickaxes at air blocks, and never play villager sounds at empty air.
- **Automatic Wireframe Dismissal & Celebration**:
  - The construction manager continuously monitors active dismantle sessions (`hasRemainingBlocksInWorld()`).
  - As soon as all destructible blocks in the selected volume are cleared, the session marks `COMPLETED`, broadcasts `EndConstructionSessionPayload`, plays challenge completion fanfare (`UI_TOAST_CHALLENGE_COMPLETE`), spawns celebratory particles (`HAPPY_VILLAGER`, `TOTEM_OF_UNDYING`), and **instantly dismisses the wireframe outline**.
- **Dynamic Tool Equipping**: Automatically equips pickaxes for stone/brick/ore, shovels for dirt/gravel, and axes for wood.
- **Survival Drops vs. Creative Demolition**: In Survival mode, broken blocks drop as collectible items for full resource recovery. In Creative mode, blocks are cleared cleanly without spawning entity drops, preventing world and inventory clutter during large excavations.
- **Ground-Anchored MINE Mode**: Right-clicking in `MINE` mode anchors the session directly at `clickedPos` (rather than offsetting into the sky above), guaranteeing that the clicked block and the surrounding ground volume are included.
- **Fiery Orange/Red Crosshair Preview**: Scepter crosshair targeting in `MINE` mode renders a fiery orange/red wireframe box preview anchored directly on the targeted ground or block.

### 4-Tier Bedrock & Indestructible Immunity Safeguards

Guarantees minions never break bedrock, barrier blocks, or world boundaries:

1. **Session Generation Filter**: Omits blocks with hardness $< 0.0\text{F}$ or `Blocks.BEDROCK` during initial task queue generation.
2. **Task Readiness Check**: Re-verifies indestructible criteria before assigning tasks to minions.
3. **Execution Guard**: In `executeDismantleWork()`, if an indestructible block is encountered, block destruction is bypassed, an anvil clank SFX (`BLOCK_ANVIL_HIT`) plays with smoke particles, and the task marks complete safely.
4. **Zero-Scaffolding Architecture**: With builders utilizing 3D Arcane Levitation, temporary scaffolding generation and cleanup are completely retired, preventing any unintended block modifications or ground corruption.

---

## 8. Combat Sappers & Ephemeral Scaffolding

The **`MinionSapperGoal`** empowers **non-builder** minions (Warriors, Sentinels, Miners) to autonomously bridge chasms and scale cliffs:

- **Builder Exclusion**: Builders never self-deploy sapper scaffolding blocks. They use Arcane Levitation and Universal Obstacle Vaulting for all terrain traversal. Builders may still respond to squad sapper signal-assist requests from allies who need a builder to place bridging blocks at an obstacle.
- **Levitation Suppression**: Any minion currently levitating (Arcane Levitation or mid-vault) is excluded from sapper scaffolding deployment.
- **Chasm & Ravine Bridging**: Detects drops $\ge 2$ blocks deep and deploys horizontal bridge spans up to 6 blocks wide.
- **Cliff Climbing Columns**: Deploys vertical climbing columns up to 6 blocks high when facing sheer ledges.
- **Ceiling Clearance & Headroom Avoidance**: Scans the climbing column for overhead ceilings and enforces 2 blocks of clear headroom at landing ledges and across bridges.
- **Overhead Collision & Stall Sensors**: Aborts climbing immediately upon overhead ceiling contact (`up(2)`) or if vertical progress stalls ($< 0.02\text{D}$ for $> 20$ ticks).
- **Ephemeral Decay Lifecycle (`TraversalScaffoldingManager`)**: Traversal scaffolding automatically decays after 400 ticks (20s).
- **Occupancy Safety Delay**: Extends decay timer by +40 ticks whenever a minion or player is standing on or inside the scaffold.
- **Multi-Minion Column Reservations**: Claims unique vertical columns to prevent thralls from crowding into the same climbing shaft.

---

## 9. Dedicated Construction Block Subsystem (`ConstructionBlock`)

### Technical Specifications

- **Identifier**: `modid-mmcli-agent-modding:construction_block`
- **Class**: `com.example.block.custom.ConstructionBlock`
- **Registry Holder**: `ModBlocks.CONSTRUCTION_BLOCK`
- **Creative Tab**: `ItemGroups.BUILDING_BLOCKS`
- **Hardness**: `0.2F` (fragile, single-hit break)
- **Drops**: None (`.dropsNothing()`)

### Non-Suffocating Voxel Shapes & Kinematics

- **Pass-Through Interior**: Overrides `getCollisionShape()` to return `VoxelShapes.empty()` when an entity is inside or descending, enabling friction-free climbing without vanilla scaffolding collision traps.
- **Solid-Top Platform**: Projects a solid 2-pixel top platform (`TOP_OUTLINE_SHAPE`, $y = 14..16$) only when an entity is standing above (`context.isAbove(VoxelShapes.fullCube(), pos, true) && !context.isDescending()`).
- **Suffocation Prevention**: Configured with `.suffocates((state, world, pos) -> false)` and `.blockVision((state, world, pos) -> false)`.
- **In-Wall Damage Immunity**: `MinionEntity.damage` cancels `DamageTypes.IN_WALL` when touching or standing within construction blocks.
- **Infinite Horizontal Span**: Eliminates vanilla scaffolding's 6-block collapse limit; spans ravines of arbitrary width.

---

## 10. Advanced Mob Pathfinding Engine

### Scaffolding & Construction Block Evaluation (`MinionPathNodeMaker`)

Vanilla Minecraft's `LandPathNodeMaker` treats scaffolding as `PathNodeType.BLOCKED`, preventing mobs from climbing or traversing temporary platforms. The mod's custom path node maker re-evaluates both vanilla scaffolding and `ModBlocks.CONSTRUCTION_BLOCK`:

- **Column Navigation & Sapper Bridges**: Evaluates scaffolding and construction blocks as `PathNodeType.OPEN` (or `WALKABLE` when supported from below), allowing vertical ascent and horizontal span crossing across sapper bridges.
- **Elevated Platforms**: Evaluates standing on top of scaffolding or construction blocks as `PathNodeType.WALKABLE`, enabling fluid ground navigation across high-altitude platforms.

### Custom Navigation (`MinionNavigation`)

- Integrates `MinionPathNodeMaker` into a custom `MobNavigation` pipeline.
- Pre-configured with open and closed door traversal (`setCanPathThroughDoors(true)`, `setCanEnterOpenDoors(true)`).

### Interior Door & Portal Traversal

- Sets pathfinding penalties for `DOOR_OPEN`, `DOOR_WOOD_CLOSED`, `WALKABLE_DOOR`, and `TRAPDOOR` to `0.0F`.
- Leverages `LongDoorInteractGoal` to allow minions to open, walk through, and close doors smoothly without pathing stalls.

---

## 11. Tactical Ordnance: TNT Stick & Frost Grenade Stick

### TNT Stick (`TntStickItem`)

- **Identifier**: `modid-mmcli-agent-modding:tnt_stick`
- **Creative Tab**: `ItemGroups.COMBAT`
- **Rarity**: `Rarity.EPIC`
- **Cooldown**: 5 ticks (0.25s) anti-spam delay
- **Action**: Right-clicking launches a TNT projectile with fuse priming sound (`ENTITY_TNT_PRIMED`).

### TNT Projectile (`TntProjectileEntity`)

- **Identifier**: `modid-mmcli-agent-modding:tnt_projectile`
- **Flight Physics**: Aerodynamic velocity with continuous smoke trail and flame particles.
- **Detonation**: Explodes on server collision with solid blocks or entities with a $4.0\text{F}$ explosive power.

### Frost Grenade Projectile Stick (`FrostGrenadeStickItem`)

- **Identifier**: `modid-mmcli-agent-modding:frost_grenade_stick`
- **Creative Tab**: `ItemGroups.COMBAT`
- **Rarity**: `Rarity.RARE`
- **Max Stack Size**: `1`
- **Cooldown**: 10 ticks (0.5s) anti-spam delay
- **Action**: Right-clicking launches an aerodynamic cryogenic projectile with custom throwing audio (`ENTITY_SNOWBALL_THROW` and `BLOCK_POWDER_SNOW_STEP`).
- **In-Game Tooltip**: Color-coded tactical summary detailing fluid conversion, powder snow perimeter, and freezing debuffs.

### Frost Grenade Projectile (`FrostGrenadeEntity`)

- **Identifier**: `modid-mmcli-agent-modding:frost_projectile`
- **Flight Physics**: Arcing thrown item physics leaving client-side snowflake (`SNOWFLAKE`) and snowball (`ITEM_SNOWBALL`) particle trails.
- **Direct Impact (`onEntityHit`)**: Deals $3.0\text{F}$ direct cold/blunt damage on entity impact (+5.0 bonus damage against fire-elemental mobs like Blazes and Magma Cubes).
- **Zero Block Destruction**: Causes no explosive block damage, preserving player structures, redstone, and terrain.
- **Fluid & Fire Transmutation ($r = 3.5\text{D}$)**:
  - **Water Flash-Freeze**: Both still and flowing water blocks instantly crystallize into solid ice (`Blocks.ICE`).
  - **Lava Crystallization**: Still lava pools turn into obsidian (`Blocks.OBSIDIAN`), and flowing lava converts to cobblestone (`Blocks.COBBLESTONE`) accompanied by `BLOCK_LAVA_EXTINGUISH` audio.
  - **Fire Extinguishment**: Active fires (`Blocks.FIRE`, `Blocks.SOUL_FIRE`) and lit campfires are quenched with steam and `BLOCK_FIRE_EXTINGUISH` audio.
- **Perimeter Powder Snow Ring ($r \in [2.0\text{D}, 3.5\text{D}]$)**:
  - Scans ground level on the outer circle of the blast zone.
  - Places a ring of powder snow (`Blocks.POWDER_SNOW`) only on air/replaceable positions supported by solid ground or ice below, preserving an open center around the impact point.
- **Entity Debuffs ($r = 5.0\text{D}$)**:
  - **Fire Quenching**: Burning entities caught in the blast are extinguished (`entity.extinguishWithSound()`).
  - **Freezing Ticks**: Inflicts $360$ frozen ticks (`FREEZE_TICKS`), instantly covering the player's screen in frost vignette and triggering shivering/cold damage for non-immune entities. Respects `entity.canFreeze()`.
  - **Slowness III**: Inflicts `StatusEffects.SLOWNESS` level III (amplifier 2) for 160 ticks (8.0 seconds).
- **Visual & Auditory Feedback**: Spawns 60 snowflake, 30 snowball, 20 cloud, and 1 flash particles, accompanied by glass shattering (`BLOCK_GLASS_BREAK`) and snow crunch audio.

---

## 12. Data Components & Network Protocol Architecture

### Minecraft 1.21 Data Components (`ModDataComponents`)

Type-safe, immutable components attached to items such as the Loki Command Scepter:

- `COMMAND_MODE`: Encodes active operational mode (`FOLLOW`, `STAY`, `MINE`, `BUILD`, `RECRUIT`).
- `ACTIVE_BLUEPRINT`: Encodes active blueprint identifier string.
- `TARGET_SQUAD`: Encodes active squad channel filter (`ALL`, `ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`).
- `STRUCTURE_ROTATION`: Encodes integer quadrant rotation index ($0 = 0^\circ, 1 = 90^\circ, 2 = 180^\circ, 3 = 270^\circ$).
- `TARGET_ROLE`: Encodes the optional target `MinionRole` archetype for channeled rally ring transfiguration.

### Client-to-Server (C2S) Payloads

1. **`UpdateScepterPayload`**: Synchronizes active command mode, selected blueprint, target squad channel, rotation index, optional target archetype role, and directive dispatch flags.
2. **`UpdateMinionConfigPayload`**: Updates an individual minion's role archetype and squad assignment.
3. **`MassRolePayload`**: Batch-assigns a role archetype to all selected minions (or all matching squad minions) within a 64-block radius.
4. **`TeleportMinionPayload`**: Recalls an individual minion or broadcast-teleports all squad minions to the player.
5. **`DismissMinionPayload`**: Decommissions an individual minion or broadcast-decommissions squad minions within 64 blocks.
6. **`DeselectMinionsPayload`**: Clears active unit selection for an individual minion or the entire army.
7. **`RetreatPayload`**: Dispatches instant tactical panic retreat (`Keybind R`), resetting combat targets, canceling guard posts, and recalling all squad minions to formation at sprint speed.

### Server-to-Client (S2C) Payloads

1. **`SyncConstructionSessionPayload`**: Broadcasts the start, anchor coordinate, blueprint ID, rotation index, and mode of an active construction session to tracking clients for 3D holographic rendering.
2. **`EndConstructionSessionPayload`**: Broadcasts the termination (completion or cancellation) of a construction session so client wireframe renderers clear holographic geometry.

---

## 13. Survival vs. Creative Mode Economy & Mechanics

The commander's active game mode dynamically dictates minion logistics, resource consumption, and world interaction across building, mining, recruitment, and maintenance:

| Subsystem | Survival Mode (`/gamemode survival`) | Creative Mode (`/gamemode creative`) |
| :--- | :--- | :--- |
| **Structure Building (`BUILD`)** | **Resource-Constrained**: Builders consume blocks from their 9-slot backpack. If depleted, they autonomously search containers within 12 blocks. If missing, construction halts with actionbar alerts. | **Zero-Cost Free Placement**: Builders place blocks freely and continuously without consuming items or requiring container inventories. |
| **Area Mining (`MINE`)** | **Full Resource Recovery**: Excavated blocks drop as collectible world item entities for player salvage. | **Zero-Drop Demolition**: Blocks are cleared without entity drops, preventing world and inventory clutter during large excavations. |
| **Minion Taming** | Consumes **1 Gold Ingot** from player hand when binding an untamed minion. | Tames the minion instantly **without consuming** the held Gold Ingot. |
| **Feeding & Healing** | Consumes **1 food or gold item** per healing interaction from the player's hand. | Restores health to full **without consuming** held food or gold items. |
| **Spawn Egg Usage** | Consumes **1 spawn egg** per mob spawned from the item stack. | Spawns minions infinitely **without depleting** the held spawn egg stack. |
| **Scepter Recruitment (`RECRUIT`)** | Transfigures target wild or enemy mobs into loyal minion thralls. | Transfigures target wild or enemy mobs into loyal minion thralls. |
| **Combat Sapper Bridges** | Ephemeral `ConstructionBlock` has zero drops in both modes to prevent debris. Builders levitate and do not use scaffolding. | Zero drops in both modes. |

