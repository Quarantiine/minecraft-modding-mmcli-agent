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
8. [100% Zero-Footprint Universal Arcane Levitation](#8-100-zero-footprint-universal-arcane-levitation)
9. [Advanced Mob Pathfinding Engine](#9-advanced-mob-pathfinding-engine)
10. [Tactical Ordnance & Warrior Thrown Weapon Arsenal](#10-tactical-ordnance--warrior-thrown-weapon-arsenal)
11. [Data Components & Network Protocol Architecture](#11-data-components--network-protocol-architecture)
12. [Survival vs. Creative Mode Economy & Mechanics](#12-survival-vs-creative-mode-economy--mechanics)
13. [Builder Minion Stability, Anti-Oscillation & Placement Idempotency](#13-builder-minion-stability-anti-oscillation--placement-idempotency)
14. [Multi-Modal Blueprint Rotation & Arcane Build Flight Controls](#14-multi-modal-blueprint-rotation--arcane-build-flight-controls)
15. [Builder Block Phasing, Post-Construction Structure Egress & Guaranteed Perimeter Flank Spread](#15-builder-block-phasing-post-construction-structure-egress--guaranteed-perimeter-flank-spread)
16. [Free Survival Build Flight & Water Flight Cancellation Safeguard](#16-free-survival-build-flight--water-flight-cancellation-safeguard)

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
3. **`MINE`** (`1.4F` pitch, `§6Mine`): Directs builders to begin top-down deconstruction of clicked multiblock structures or 3D terrain volumes.
4. **`BUILD`** (`1.6F` pitch, `§bBuild`): Projects blueprint holographic wireframes and anchors new multiblock construction sessions.
5. **`RECRUIT`** (`1.8F` pitch, `§dRecruit`): Targets wild or enemy mobs to transfigure them into loyal minion thralls.

> [!TIP]
> **Contextual Combat Control**:
> Operating mode `ATTACK` is retired. Combat is handled dynamically: quick-tap an enemy to focus-fire, or channel the 90° forward sector to launch a coordinated mass attack on an entire enemy formation!

### Long-Range Crosshair Targeting (64.0D Reach)

The scepter features an integrated 64-block line-of-sight raycasting engine:

- **Direct Minion Selection**: Aiming crosshair at an owned minion and right-clicking toggles unit selection with audio/visual feedback (chime + hearts to select; bass + smoke to deselect). Selected minions display team glowing outlines and an amber star in their overhead badge.
- **Selective Ground Waypoint Pings**: Right-clicking terrain up to 64 blocks away drops a ground beacon beam (`END_ROD` + `GLOW`). Selected minions matching the active squad channel sprint to the ping and automatically arrange into tactical combat stations, holding position upright at attention in the unified **Stationed** position (`holdingPosition == true`, `guardAnchorPos` set). Deployed minions are **automatically deselected** (`minion.setSelected(false)`), clearing selection outlines and freeing the commander's selection buffer for immediate subsequent squad micro-management without requiring manual deselection inputs.
- **Hostile Focus-Fire Raycasting**: Aiming crosshair directly at a hostile mob up to 64 blocks away and right-clicking issues a squad-wide focus-fire ping, accompanied by war drum cadences and crit particles.
- **Direct Scepter Follow & Single-Click Station Toggle**: Right-clicking an owned minion while it is stationed (either via an RTS Waypoint Ping or close-up guard) immediately commands that individual unit to break guard stance and follow master on the very first click, unifying waypoint and close-up stationing.
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

| Interaction                     | Condition                       | Behavior                                                                                                    |
| :------------------------------ | :------------------------------ | :---------------------------------------------------------------------------------------------------------- |
| **Right-Click (Quick Tap)**     | Hostile Mob ($\le 64\text{D}$)  | Focus-fire attack order; squad units focus target with drum cadence.                                        |
| **Right-Click (Quick Tap)**     | Ground Block ($\le 64\text{D}$) | Drops ground waypoint; selected squad units sprint, form up, hold station, and **automatically deselect**.  |
| **Right-Click (Quick Tap)**     | Owned Minion ($\le 64\text{D}$) | Toggles unit selection (chime/hearts vs bass/smoke).                                                        |
| **Right-Click (Quick Tap)**     | Open Sky / Air                  | Broadcasts active directive (`FOLLOW`, `STAY`) to 64-block radius.                                          |
| **Hold Right-Click (>8 ticks)** | Hostiles in Sector              | Charges 90° forward sector; launches coordinated **Mass Attack** across enemy formation.                    |
| **Hold Right-Click (>8 ticks)** | Minions in Sector               | Charges 90° forward sector; rallies, selects, and transfigures enclosed minions into primed role.           |
| **Sneak + Right-Click**         | Aiming at Air                   | Cycles scepter command mode forward (`FOLLOW` → `STAY` → `MINE` → `BUILD` → `RECRUIT`).                     |
| **Sneak + Right-Click**         | Owned Minion                    | Opens interactive **Minion Management GUI** (`MinionScreen`).                                               |
| **Sneak + Left-Click**          | Non-`BUILD` Modes               | Deselects all active minions immediately with bass/smoke feedback.                                          |
| **Sneak + Left-Click**          | `BUILD` Mode                    | Cycles blueprint rotation (0° → 90° → 180° → 270°) with actionbar HUD.                                      |
| **Press [V] Key**               | In-Game Hotkey                  | Opens the **Command Hub GUI** (`CommandScepterScreen`).                                                     |
| **Press [R] Key**               | In-Game Hotkey                  | **Tactical Panic Retreat**: Sounds warning bell, clears minion targets, and recalls all units to formation. |

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
- **Warrior Combat Modality & Thrown Weapon Arsenal**: Warriors dynamically adapt combat styles based on their equipped arsenal:
  - **Frontline Melee**: Swords, axes, and maces trigger aggressive melee assault pathfinding.
  - **Ranged Archery**: Bows and crossbows trigger strafing ranged skirmishing AI (`MinionRangedAttackGoal`).
  - **Thrown Ordnance**: Frost Grenade Sticks and TNT Sticks allow warriors to lob tactical projectiles at enemy formations.
  - **Trident Duality**: Tridents operate as hybrid melee/ranged weapons, dynamically engaging in close-quarters melee thrusts when enemies are $\le 5.0\text{D}$ and seamlessly switching to ranged javelin throws when targets are $5.0\text{D} < d \le 20.0\text{D}$.
- **Warrior Ranged vs. Melee Suppression**: When equipped with pure ranged weapons (bow, crossbow, frost grenade stick), melee strike behaviors are suppressed, allowing them to strafe and skirmish. When equipped with melee weapons, close-range combat engages automatically.

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
- **In-Wall Suffocation Immunity**: Minions levitating, climbing, or standing within terrain obstacles, structures, or blocks are completely immune to `DamageTypes.IN_WALL` suffocation damage.

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

- **Unified Stationing & Single-Click Follow**:
  - Minions can be stationed up-close via empty-hand right-click or remotely up to 64m away via RTS Waypoint Pings. Both actions place the unit into the unified **Stationed** position (`isHoldingPosition()`).
  - Right-clicking any stationed minion with an empty hand immediately orders them to break station and follow the commander on the **very first click** (`setSitting(false)`, `setGuardAnchorPos(null)`, `setSelected(true)`).
  - Right-clicking a following minion with an empty hand stations them at their current position (`setSitting(true)`, `setGuardAnchorPos(pos)`, `setSelected(false)`).
  - Minion Screen status tooltips accurately show `§eStatus: §7Holding Position (Stationed)` for both forms of stationing.
- **Sneak + Right-Click**: Opens the **Minion Management GUI** (`MinionScreen`).

### Minion Spawn Egg (`MinionSpawnEggItem`)

- Summons minion thralls bound to the player in standby guard stance with level-up chime audio and heart particles.
- **Spawner Reconfiguration**: Right-clicking a vanilla mob spawner with the spawn egg reconfigures the spawner to produce Minion Thralls.

---

## 3. Tactical Army Architecture: Roles, Squads & Formations

### 3 Specialized Archetype Roles (`MinionRole`)

| Role           | Color        | Combat Profile                                                    | Primary Equipment                                                                                  | Behaviors                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| :------------- | :----------- | :---------------------------------------------------------------- | :------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`WARRIOR`**  | Red (`§c`)   | Frontline Melee, Ranged Skirmish **&** Tactical Procurement Hunts | Swords, Axes, Maces, Tridents **OR** Bows, Crossbows, Thrown Ordnance (Frost Grenades, TNT Sticks) | **Multi-Modality Combatant & Hunter**: Functions as a frontline melee striker, ranged archer, or thrown javelin specialist based on equipped loadout. Features **Trident Duality** ($\le 5\text{D}$ melee, $5\text{D}\text{--}20\text{D}$ ranged javelin) with **Server-Side Loyalty Return Recovery** (`TridentEntityMixin`). Automatically accepts **Squad Material Procurement Contracts** commissioned by Builder thralls to hunt mob resources (wool, bones, slime, leather, etc.), synthesize refined goods, and deliver drops via peer logistics. Leads formation frontline.                       |
| **`SENTINEL`** | Green (`§a`) | Perimeter Guard & Combat Medic                                    | Sword + Shield                                                                                     | Holds designated anchor; 8-block guard zone; 128-block leash freedom; prioritizes shields. **Aegis of Restoration**: Autonomously channels healing to the player commander and allied minions under 70% HP within 10 blocks (prioritizing the player commander if wounded), restoring 6.0 HP (3 hearts) and granting Regeneration II for 5s with heart VFX, chime audio, and an action bar confirmation message (6s cooldown; fallback self-heal below 40% HP).                                                                                                                                           |
| **`BUILDER`**  | Blue (`§9`)  | Construction, Deconstruction & Logistics Procurement              | Pickaxes, Axes, Shovels                                                                            | Master architect, resource excavator, and logistics specialist. Autonomously constructs blueprints, deconstructs areas, quarries natural stone, harvests timber via agro-forestry (using bone meal for instant growth), self-crafts tools, shares blocks via peer energy beams, deploys supply depot chests, and commissions **Squad Material Procurement** contracts to nearby Warrior thralls for mob-derived construction materials. Equipped with **Ceiling Clearance Raycasts**, **Doorway Traversal Fallbacks**, **Line-of-Sight Hover Stations**, and **Self-Intersection Entombment Prevention**. |

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
                       [B2]   [B0]     │     [B1]   [B3]      ◄── Builder Rearguard (-2.0 rear)
```

- **Straight Parallel Battle Ranks**: Every 4 units in a role share the exact same `forwardOffset`, eliminating curving arcs or wedges and presenting a disciplined military battle front.
- **Frontline Lines (Warriors)**: $+4.0\text{D}$ forward (stepping back $2.0\text{D}$ per rank line), intercepting head-on threats with both melee swordsmen and archers.
- **Midline Escort Lines (Sentinels)**: $+1.8\text{D}$ forward, providing an immediate defensive bulwark shielding the commander within an expanded 128-block leash.
- **Rearguard Support Lines (Builders)**: $-2.0\text{D}$ rear, staying tucked safely behind the commander.
- **Open Central Command Lane**: Inner units flank at $\pm 1.35\text{D}$ and outer units at $\pm 3.60\text{D}$, leaving an open central corridor for the commander to lead, aim scepters, and shoot without friendly obstruction.
- **Movement Hysteresis Yaw Anchoring**: Formation heading locks during stationary camera sweeps ($\le 0.04\text{ blocks}^2$ displacement), preventing minions from orbiting dizzyingly when the player looks around.
- **Formation Anti-Jitter & Mutual Push Suppression**: Minions suppress mutual physical collision shoving (`pushAwayFrom`) between allied minions when idle, guarding, or standing in formation, preventing units from jostling each other out of alignment. Consistent `walkableY` resolution and arrival velocity zeroing eliminate station-hunting oscillations.
- **Universal Arcane Levitation Traversal & Anti-Skyrocket Safety**: All minions possess 3D Arcane Levitation mobility for navigating vertical terrain, descending cliffs ($\Delta Y < -1.5\text{D}$), ascending bluffs ($\Delta Y > 1.25\text{D}$), and vaulting obstacle stalls. Upward lift is ceiling-restricted to above-target vectors ($\Delta Y > 0.5\text{D}$) or short obstacle hops, non-builder thralls are capped at 40 levitation ticks with auto-landing over solid ground, and melee warriors immediately ground themselves when engaging ground combat targets to prevent launching into the stratosphere.

### Automatic Waypoint Combat Formations & Auto-Deselect Flow

When the commander drops a ground waypoint ping, selected minions automatically deploy into their respective Ranked Army Lines facing the commander's line-of-sight yaw:

- **Automatic Selection Clearance**: Selected minions dispatched to the waypoint are immediately deselected (`setSelected(false)`). Their overhead star and squad glowing outlines extinguish as they lock into stationary guard attention at the destination coordinates.
- **Warriors**: Form straight battle lines ahead of the objective.
- **Sentinels**: Form protective bulwark lines behind the vanguard.
- **Builders**: Form disciplined support and logistics lines to the rear.
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
- **Semantic Color-Coded Ghost Wireframes**: Replaces messy particle clutter with crisp, high-visibility wireframe bounding boxes color-coded by architectural element:
  - **Doors**: 🟢 Emerald Green (`#00FF88`, full 2-block tall portal box).
  - **Illumination (Torches & Lanterns)**: 🟡 Amber Gold (`#FFCC00`).
  - **Utilities, POIs & Beds**: 🟣 Arcane Purple (`#9933FF`).
  - **Structural Walls, Columns & Foundations**: 🔵 Diamond Cyan (`#00D4FF`).
  - **Roof Trim, Stairs & Battlement Eaves**: ❄️ Ice Blue (`#70B8FF`).
- **Dynamic In-World Projection**:
  - `BUILD` Sessions: Color-coded blueprint wireframe with translucent ghost block rendering and floor grid orientation.
  - `DISMANTLE` Sessions: Fiery orange wireframe bounding box.
  - Dynamic Quadrant Rotation: Wireframe and ghost blocks dynamically rotate to match the active scepter rotation.
  - Remains visible in the world while minions construct, vanishing automatically once the last block is placed.
  - **Exact Procedural Size Variant Outlines**: Every category variant and procedural size (`barricade_small`, `barricade_grand`, `home_small`, `workshop_grand`, `depot_med`, etc.) is registered and mapped via category prefix resolution. Placing any structure (such as Barricades or Grand Manors) displays its exact, true wireframe geometry rather than falling back to default Watchtowers. Active construction wireframes also dynamically adapt foundation blocks via `DynamicBuildingResolver.resolve()` to match terrain.
  - **Real-Time Per-Block Ghost Dissolution**: As builder minions place each block in the world, the corresponding wireframe block dissolves immediately from the client hologram renderer. Once the final block is placed, the remaining ghost grid disappears, triumphant totem particles and chime sound play, and builders cleanly disengage levitation to return to their commander or assigned post.

### Arcane Build Flight & Tactical Build System (`BuildFlightManager`, `CommandScepterItem`)

- **Free Survival Build Flight & Physical Arcane Flight**: When holding the Command Scepter in `BUILD` mode, the commander is immediately granted physical Arcane Flight in both Survival and Creative modes. Unlike detached 3rd-person camera boom offsets which create cursor-to-world parallax, Arcane Build Flight physically controls the player's avatar while keeping the camera centered on the player's true perspective:
  - **Unconstrained 3D Vanilla Flight in Survival**: In Survival mode, players experience fluid, unconstrained 3D flight matching creative flight physics. Ascend with **`Space`**, descend with **`Shift`**, and glide in any direction with WASD without rigid hover altitude locking or velocity clamping.
  - **Water Flight Cancellation Safeguard**: If the player flies over water or enters a fluid column while in `BUILD` mode, `CommandScepterItem.isPlayerOverWater` automatically detects the liquid beneath them. The server immediately:
    1. Cancels all active construction sessions belonging to the commander (`cancelActiveSessionsForOwner`).
    2. Switches the scepter's operating mode to **`FOLLOW`** (`CommandMode.FOLLOW`).
    3. Revokes flight abilities (`allowFlying = false`, `flying = false`) and clears `ACTIVE_SERVER_BUILD_FLIERS`.
    4. Triggers an extinguishing sizzle audio cue (`SoundEvents.BLOCK_FIRE_EXTINGUISH`) and water splash particles (`ParticleTypes.SPLASH`).
    5. Displays an immediate actionbar warning: `§c⚠ Construction cancelled: Flying over water is prohibited in BUILD mode!§r`.
  - **Indoor & Cave Ceiling Clamping**: In enclosed areas, raycasts check overhead terrain and ensure safe headroom below solid ceilings.
  - **Adaptive Height Adjustments**: Cycling to a taller or shorter blueprint dynamically updates target hover height in real time.
  - **Graceful Descent & Zero Fall Damage**: When switching out of `BUILD` mode, stowing the scepter, or descending to ground, the player lands smoothly with complete fall damage immunity enforced by server-side tracking (`ACTIVE_SERVER_BUILD_FLIERS`).
- **Camera-Aligned Crosshair Raycasting (96m Reach)**: Raycasting initiates from the player's eye coordinates along the look vector through the center screen crosshair, eliminating parallax errors and aligning the 3D ghost preview 100% with the cursor.
- **One-Click Aerial Placement (`AnchorConstructionPayload`)**: Eliminates vanilla reach limits by transmitting targeted coordinates and face directions over custom C2S packets, allowing commanders to anchor construction and dismantle sessions from high in the air without descending.
- **Sky Tap Blueprint Cycling**: Right-clicking into empty air or open sky cycles through the blueprint catalog, cleanly separating placement from browsing.
- **Dedicated Zoom Controls & Hotbar Conflict Resolution**:
  - Pressing the **`H`** key cycles tactical camera zoom presets (`0.75x`, `1.0x` default, `1.5x`, `2.0x`).
  - Holding **`Ctrl` + Mouse Scroll** smoothly zooms in and out without cycling hotbar item slots.
- **Camera Hook Injection**: Integrated via `CameraMixin` and `CameraAccessor` into net.minecraft.client.render.Camera.

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
|  [⚔ Warrior]        [🛡 Sentinel]        [🔨 Builder]             |
+-------------------------------------------------------------------+
|  [Execute]     [Teleport All]     [§c✖ Destroy All]      [Close]   |
+-------------------------------------------------------------------+
```

- **Contextual Archetype vs. Architecture Style Bar Swap**:
  - **Standard Modes (`FOLLOW`, `STAY`, `MINE`, `RECRUIT`)**: Displays the 3-button Mass Archetype bar (`[⚔ Warrior]`, `[🛡 Sentinel]`, `[🔨 Builder]`). Clicking directly converts minions or primes the scepter for channeled rally transfigurations.
  - **No Preselected Default Archetype**: By default, no archetype is preselected (`selectedRole = null`). This protects players from unintentionally transfiguring their army when opening the Command Hub GUI or right-clicking without a deliberate selection.
  - **`BUILD` Mode Swap**: Seamlessly swaps the role buttons into the 4-button **Architecture Style** bar:
    - `[🌍 Biome Native]`: Vernacular architecture adapting to local biome palettes (plains, desert, taiga, swamp, cave, nether, end).
    - `[🏰 Fortress Stone]`: Heavy masonry, stone bricks, mossy/cracked stones, andesite, and iron fittings.
    - `[🌲 Frontier Timber]`: Rustic log framing, horizontal wood planks, stripped accents, fences, and lanterns.
    - `[🔮 Arcane Nether]`: Polished blackstone, basalt pillars, crimson/warped timbers, and soul fire.
- **Procedural Footprint Size Presets (`[ S ]` `[ M ]` `[ L ]` `[ 🎲 ]`)**:
  - Embedded between pagination arrows in the Blueprint Catalog section when in `BUILD` mode.
  - Controls procedural dimension scaling across all category blueprints (`Small 5x5`, `Medium 7x7`, `Grand 9x9`, `Random`).
  - Dynamically updates catalog card dimensions and block counts in real time.
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

### Procedural Blueprint Categories & Catalog (`BlueprintRegistry`, `BuildingCategory`)

1. **`🏡 Home` (Villager Residence)**: Fully furnished residential dwellings containing beds, front entrance doors, crafting stations, furnaces, and lanterns. Designed specifically for villager settlement integration — villagers naturally inhabit, sleep, and claim POIs within these structures.
2. **`🗼 Watchtower` (Tactical Observation Outpost)**: Elevated fortification with observation battlements, arrow slits, and wooden door entry.
3. **`🛡 Barricade` (Defensive Rampart)**: Heavy frontline barrier with log framing, stone wall embrasures, and palisade fencing.
4. **`⚒ Workshop` (Artisan Forge)**: Blacksmith facility complete with anvil, furnaces, crafting benches, and weapon racks.
5. **`📦 Supply Depot` (Logistics Warehouse)**: Storage warehouse with double chests, storage barrels, and material intake stations.
6. **`🔮 Obelisk` (Arcane Monument)**: Mystical spire featuring obsidian foundations, polished deepslate, gold core, and redstone lantern finials.

### Procedural Footprint Scaling (`BuildingCategory`)

Each functional category procedurally adapts across four footprint scales:

- **`SIZE_SMALL` (0)**: Compact $5 \times 5$ layout for tight terrain and outpost footholds.
- **`SIZE_MEDIUM` (1)**: Standard $7 \times 7$ layout balancing utility and footprint (default).
- **`SIZE_GRAND` (2)**: Grand $9 \times 9$ multi-room estate or heavy citadel bastion.
- **`SIZE_RANDOM` (3)**: Procedurally samples dimensions for organic town layouts.

### Dynamic Organic Architecture (Noise Weathering Engine) (`DynamicBuildingResolver`, `BiomePalette`)

- **Natural Block Variation**: Replaces uniform, sterile block patterns with natural spatial variations (similar to natural cobblestone and dirt patterns) using 3D coordinate hash noise ($[0.0, 1.0]$).
- **Material Degradation & Masonry Aging**:
  - Stone Bricks weather into cracked bricks, mossy bricks, polished andesite, and cobblestone.
  - Cobblestone weathers into mossy cobblestone and raw andesite.
  - Deepslate Bricks weather into cracked deepslate bricks and cobbled deepslate.
  - Blackstone weathers into cracked polished blackstone bricks in subterranean and Nether environments.
- **Vernacular Biome Palettes (`BiomePalette`)**: Translates structural blocks semantically according to local biome archetypes (`PLAINS_FOREST`, `DESERT_BADLANDS`, `TAIGA_SNOWY`, `JUNGLE_SWAMP`, `SUBTERRANEAN_CAVE`, `NETHER`, `END`).
- **Deterministic 1:1 Client-Server Visual Hashing**: Seeds noise with `anchorPos.asLong() ^ blueprintId.hashCode() ^ style.ordinal()`, guaranteeing that client holographic wireframes and server minion placement match 1:1 down to the exact block.

### Dynamic Foundation Slope Snapping (`DynamicBuildingResolver`)

- **Hillside & Cliff Retaining Walls**: Scans floor perimeter coordinates at the base layer. If the underlying terrain drops into air or uneven contours, automatically generates vertical stone/brick retaining pillars downward up to 8 blocks (`MAX_FOUNDATION_DEPTH = 8`) to eliminate floating buildings.
- **Water Stilt Foundations**: If the structure extends over rivers, oceans, or swamps, automatically generates wooden fence stilts (mangrove in swamps, oak elsewhere) down to the seabed.
- **Door Sill Ground Anchor**: Automatically guarantees that the block directly beneath every entrance door is solid ground, preventing doors from popping off upon placement.

### Dimension Bed Explosion Safeguard

- Automatically detects the Nether and End dimensions during blueprint resolution.
- Replaces standard `BedBlock` entries with `Blocks.RESPAWN_ANCHOR` in the Nether and `Blocks.PURPUR_BLOCK` in the End, completely eliminating fatal accidental explosions during multiblock construction.

### Server Construction Manager (`ConstructionManager` & `ConstructionSession`)

Server-authoritative engine orchestrating automated multiblock building and demolition:

- **Task Queue & Topological Ordering**: Breaks blueprints into discrete block placement tasks sorted bottom-up.
- **Task Leasing**: Minions claim leases on available tasks. If a builder runs out of materials or stalls, the lease releases for other workers.
- **Dual Economics**:
  - **Creative Mode**: Zero-cost instant block placement. Automatically pre-clears any existing blocks (grass, flowers, snow, dirt) with zero dropped items, eliminating all clutter.
  - **Survival Mode Multi-Stage Logistics**:
    1. _9-Slot Minion Backpack_: Consumes blocks currently carried in inventory.
    2. _Nearby Container Scavenging_: Searches chests, barrels, and shulkers within 12 blocks for missing materials.
    3. _Peer-to-Peer Allied Sharing (`MinionLogisticsHelper`)_: Scans allied minions within 24m, transferring required blocks via green energy beams and pickup audio.
    4. _Autonomous Quarrying & Agro-Forestry (`MinionHarvestingHelper`)_: Autonomously quarries natural stone, deepslate, and earth. For timber, fells trees or plants saplings and accelerates maturity with bone meal.
    5. _Squad Material Procurement & Mob Hunting Contracts (`MinionHarvestingHelper`)_: When organic or mob-derived materials are required (wool, bones, slime, leather, ink, prismarine, etc.), builders scan for target mobs within 32m and commission an available allied Warrior thrall. The warrior receives the contract via a weaponsmith audio cue and energy beam, hunts the target mob, synthesizes the required refined material, and delivers it directly to the builder. (Falls back to solo builder hunting if no warrior is nearby).
    6. _In-Inventory Resource Synthesis Engine_: Automatically synthesizes high-tier multiblock components from raw mob ingredients (e.g. 4 String $\to$ 1 Wool, 1 Bone $\to$ 3 Bone Meal, 9 Bone Meal $\to$ 1 Bone Block, 9 Slimeballs $\to$ 1 Slime Block, 4 Magma Cream $\to$ 1 Magma Block, 1 Blaze Rod $\to$ 2 Blaze Powder, 1 Ender Pearl + 1 Blaze Powder $\to$ 1 Eye of Ender, 4 Prismarine Shards $\to$ 1 Prismarine, 9 Shards $\to$ 1 Prismarine Bricks, 4 Shards + 5 Crystals $\to$ 1 Sea Lantern, 4 Rabbit Hide $\to$ 1 Leather, 9 String $\to$ 1 Cobweb).
    7. _Strict Safety & Build Protection_: Never hunts player pets, named mobs, villagers, iron golems, allays, or allied minions (`isSafeHuntTarget`). Never breaks player-placed blocks, active blueprint structures, processed materials (planks, bricks, slabs, glass), or blocks within 12m of player beds, chests, or respawn anchors.
    8. _Hazard Avoidance_: Inspects all 6 orthogonal directions and refuses to break blocks adjacent to lava.
    9. _Tool Self-Crafting_: Synthesizes wooden or stone tools (pickaxes, axes, shovels) from timber and stone when tools break or are missing.
    10. _Autonomous Supply Depots_: When bags are full of surplus non-blueprint materials, deposits excess into nearby chests or crafts an 8-plank Chest (pairing into a Double Chest if adjacent).

### Arcane Builder Levitation & Universal 3D Levitation Traversal (`MinionBuildGoal`, `MinionEntity`, `WaypointHoldGoal`, `MinionFormationFollowGoal`, `SentinelGuardGoal`)

- **Universal 3D Arcane Levitation Traversal & Safety Ceilings**: All minion thralls (Builders, Warriors, Sentinels) utilize 3D Arcane Levitation to seamlessly traverse extreme vertical terrain and multiblock structures:
  - **High Cliff & Rooftop Descent**: When stationed on high cliffs, rooftops, or newly completed buildings, minions smoothly glide off high structures ($\Delta Y < -1.5\text{D}$) down to the ground or their target post without taking fall damage or getting trapped on roofs.
  - **Elevated Cliff & Building Ascent**: When navigating towards elevated waypoints, cliffs, or commanders perched on structures ($\Delta Y > 1.25\text{D}$), minions automatically engage 3D levitation flight to ascend sheer vertical walls and land safely at their destination.
  - **Obstacle & Navigation Stall Recovery**: Responsive 2-tick obstacle stall sensitivity: if navigation stalls or hits horizontal obstructions for $\ge 2$ ticks while actively moving towards a goal, minions engage Arcane Levitation flight in 3D to bypass the barrier.
  - **Unconstrained Dynamic Obstacle Climbing**: Minions can scale barriers of any height (3, 5, 10, or 20+ blocks high) without artificial altitude limits or premature timers. Traversal raycasts ahead measure `obstacleTopClearanceY` to lift minions cleanly over obstacles with sustained upward velocity ($v_y \ge 0.30\text{D} \to 0.38\text{D}$) and full horizontal drive ($v_x, v_z$).
  - **Immediate Solid Ground Landing Rule**: The root cause of endless levitation (minions refusing to land because of elevation mismatch or wall collisions) is permanently solved: the instant a minion's feet reach solid ground (`isOnGround() || belowState.isSolidBlock()`) without a higher obstacle ahead, levitation deactivates immediately, restoring normal ground walking and step height (1.0625D). In mid-air, downward glide ($v_y \le -0.22\text{D}$) carries airborne minions smoothly to earth until solid ground is reached.
  - **Combat Grounding**: Minions fighting ground targets remain firmly grounded and never launch into the air from entity collisions. If elevated above a ground enemy, melee warriors immediately descend ($v_y = -0.35\text{D}$) to engage within weapon reach.
  - **Hierarchical Destination Resolution**: `resolveActiveTargetDestination()` dynamically reconciles explicit goal traversal stations, active combat targets, waypoint/guard anchors, commander positions, and navigation waypoints.
  - **Safe Arrival & Landing Fanfare**: Upon reaching their destination within tolerance (horizontal $\le 2\text{D}$, vertical $\le 1.5\text{D}$), minions float smoothly to solid ground, deactivate levitation, restore gravity, and burst into arcane purple and cyan runes (`PORTAL` and `ENCHANT`).

- **Arcane Builder Levitation**: Elevated multiblock construction completely transcends temporary scaffolding generation and block climbing. Builders maintain permanent 3D Arcane Levitation throughout construction:
  - **Dynamic 3D Hover Station**: The minion calculates an optimal 3D air station adjacent to the target block ($1.4\text{D} \to 1.8\text{D}$ horizontally away, with unobstructed headroom) and smoothly glides to it at $0.35\text{D}$ velocity.
  - **Arcane Runes & Safe Hovering**: Emits purple and cyan portal/enchant particles (`PORTAL` and `ENCHANT`) around the builder's boots while hovering. Gravity is suppressed and fall damage is 100% neutralized.
  - **Elevated Task Chaining**: Upon placing or dismantling a block, the builder immediately leases the next topological task in the sequence and glides directly to the next block station without descending to the ground.
  - **Smooth Earthward Landing & Roof Exit**: When all tasks are completed or work is paused, the builder's `activelyBuilding` state releases, allowing universal levitation to carry the worker smoothly off the roof to the master or ground post.
  - **Zero Scaffolding Clutter**: Permanently eliminates minion suffocation inside scaffolding blocks, wall-scraping friction, and leftover scaffolding clutter with 100% pure Universal Arcane Levitation.

### Builder Ceiling Clearance, Doorway Navigation & Entombment Safety Systems (`MinionBuildGoal`, `MinionEntity`)

To eliminate issues where builders get stuck inside completed rooms, ram upward into solid ceilings when targeting roof blocks, ignore doorways, or become entombed inside placed blocks, `MinionBuildGoal` incorporates a comprehensive 5-layer navigation safety suite:

```
                            [Builder Task Assigned]
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                     ▼
        [Ceiling / LOS Obstructed?]             [Clear Overhead Headroom]
        • hasCeilingAboveMinion(3) == true      • hasCeilingAboveMinion(3) == false
        • hasLineOfSightToStation() == false    • hasLineOfSightToStation() == true
                    │                                     │
                    ▼                                     ▼
       [Ground Navigation Forced]              [Arcane Levitation Enabled]
       • MinionNavigation (A*)                 • 3D Air Hover Station (0.35D vel)
       • Door Traversal (canPathThroughDoors)  • Clamped vy <= 0 if ceiling within 1.9D
       • Pathfinds through doorway             • 12-tick stall fallback to ground
                    │                                     │
                    └──────────────────┬──────────────────┘
                                       │
                                       ▼
                   [Pre-Placement Entombment Safeguard]
                   • minionBox.intersects(targetBox)?
                   • Lateral Nudge >= 1.2D / Open Step
                                       │
                                       ▼
                     [Block Placed Safely & Cleanly]
```

1. **Ceiling Clearance Raycast & Suppressed Levitation**:
   - Before engaging 3D flight, `hasCeilingAboveMinion(serverWorld, 3)` scans up to 3 blocks directly above the minion's head/feet ($Y+2 \to Y+5$).
   - If solid ceiling blocks are detected overhead (e.g. minion is inside a room and the target block is on the roof or upper floor), Arcane Levitation is strictly suppressed and ground navigation is forced (`groundNavigationForced = true`).
   - This forces the builder to use standard A\* ground pathfinding (`MinionNavigation`) with open door traversal enabled (`setCanPathThroughDoors(true)`, `setCanEnterOpenDoors(true)`), guiding the builder naturally out through doorways and hallways rather than blindly lifting into ceilings.

2. **Line-of-Sight (LOS) Hover Station Validation**:
   - When evaluating candidate hover positions ($1.4\text{D} \to 1.8\text{D}$ offset), `hasLineOfSightToStation(serverWorld, candidateStation)` casts a collider raycast from the minion's eye position to the hover point (`stationVec + 0.2D`).
   - If interior walls, partitions, or ceilings obstruct direct line of sight, the hover candidate is rejected and the minion remains in ground navigation mode until an unobstructed path through a door or window is traversed.

3. **12-Tick Collision & Stall Fallback**:
   - While levitating, `MinionBuildGoal` tracks consecutive stall and collision ticks (`stallCollisionTicks`).
   - If the minion hits horizontal obstacles or encounters unexpected ceiling resistance while attempting upward movement (`minion.horizontalCollision || (hasCeiling && delta.y > 0)`), `stallCollisionTicks` increments.
   - Upon reaching 12 consecutive stall ticks ($\ge 0.6\text{s}$), the goal automatically cancels levitation (`setArcaneLevitating(false)`), resets the hover vector, drops the minion to the ground, and falls back to ground A\* pathfinding (`groundNavigationForced = true`) targeting a walkable stand position (`findSafeStandPositionNear`) through doorways.

4. **Vertical Velocity Ceiling Clamping**:
   - In both `MinionBuildGoal` kinematic velocity calculations and `MinionEntity` core travel ticking, vertical velocity is ceiling-checked:
     - `MinionBuildGoal`: If `hasCeilingAboveMinion(serverWorld, 2)` is true and desired vertical velocity $v_y > 0.0\text{D}$, upward velocity is hard-clamped to $0.0\text{D}$ (`vel = new Vec3d(vel.x, 0.0D, vel.z)`), fully preserving horizontal navigation while eliminating ceiling ramming.
     - `MinionEntity`: If `solidCeilingDirectlyOverhead` is true within $1.9\text{D}$ overhead, vertical traversal impulses $v_y > 0$ are clamped to $0.0\text{D}$.

5. **Self-Intersection Block Entombment Prevention (`nudgeMinionAwayFromTargetBlock`)**:
   - Before executing `serverWorld.setBlockState()` for any single-block or double-block (door) structure component, the goal tests bounding box intersection (`minionBox.intersects(targetBox)`).
   - If the minion is standing on or inside the target block coordinates:
     1. Scans orthogonal cardinal directions (`NORTH`, `SOUTH`, `EAST`, `WEST`, `UP`) for adjacent open ground with clear headroom.
     2. If found, teleports/nudges the minion cleanly to the adjacent coordinate (`requestTeleport(escapeVec)`).
     3. If surrounding spaces are tight, applies a normalized lateral push vector $\ge 1.2\text{D}$ away from the block center, resetting velocity to zero.
   - For double-block doors, the check is applied to both lower and upper door blocks (`targetPos` and `targetPos.up()`), completely eliminating entity suffocation and physics locking inside placed blocks.

6. **Builder Block Phasing (`noClip = true`) & Freedom of Movement**:
   - **Autonomous Block Phasing**: Builder minions can pass through blocks (`this.noClip = true`) **strictly and only** while actively building (`activelyBuilding = true`) or evacuating a structure post-completion (`exitingBuilding = true`).
   - **Complete Elimination of Indoor Trapping**: When placing upper-floor blocks, roofs, or battlements, builders are no longer trapped indoors looking up at ceilings. They fly directly through floors, walls, and ceilings in 3D without encountering collision obstruction or having vertical velocity clamped by intermediate ceilings.
   - **Mutual Shoving Suppression**: While phasing, builders ignore entity shoving and collision physics (`pushAwayFrom`, `isPushable`), ensuring they fly smoothly without interference from allies.
   - **Client-Server Synchronization**: Phasing status is synchronized to the client via `PHASING_BLOCKS` tracked data, guaranteeing zero client-side rubberbanding or jitter.

7. **Post-Construction Structure Egress & Perimeter Waypoint Stationing**:
   - **Safe Exterior Evacuation Before Collision Restoration**: Builders never lose their block-phasing ability while still inside a structure. When construction completes (or if a session is cancelled), `startEgressFromStructure` is triggered. The builder remains in 3D levitation with `noClip = true` until it has physically moved outside the structure bounding box and reached clear ground with unobstructed headroom. Only then does `finishBuildingEgress` restore normal collision physics (`noClip = false`) and gravity.
   - **360° Angular Perimeter Waypoint Distribution (Guaranteed Full Flank Spread)**: Rather than clustering at the front door, perimeter waypoints are calculated along a continuous closed ring around all four sides of the structure (South/Front, East Flank, North/Back, West Flank). For $N$ builder minions, stations are distributed at uniform angular intervals ($360^\circ / N$) with unique deduplicated coordinates. Minions are teleported directly to their assigned flank station upon completion and safely exited from the structure, ensuring builder thralls completely encircle and defend the finished build rather than standing in the same spot, regardless of their assigned squad channels.
   - **Stationed Builder Formation**: Builder minions are dispatched and settled at their distinct perimeter waypoints and automatically placed in a stationed defensive stance (`guardAnchorPos` assigned, `isSitting() = true`, `isSelected() = false`), standing guard at attention with golden beacon beams (`END_ROD` + `GLOW`) and chime audio (`BLOCK_AMETHYST_BLOCK_CHIME`).
   - **Autonomous Mobilization from Stationed Position**: Stationed builders (whether holding a post from a previous build or remote waypoint) automatically wake up (`setSitting(false)`, `setGuardAnchorPos(null)`) and start constructing whenever a new blueprint is placed within 64 blocks. Commanders no longer need to walk over and manually re-select them.
   - **Construction Drift Elimination**: While actively building with block-phasing enabled, builders fly directly in 3D through walls and ceilings straight to their target hover station. They never fall back to ground-based doorway exit waypoints (`findStructureExitWaypoint`), preventing them from wandering outside away from the build.
   - **Unified Stationing & First-Click Selection**: Stationing via perimeter waypoints, remote scepter waypoint pings, or close-up right-clicks shares the unified stationing contract (`isHoldingPosition()`). Right-clicking any stationed builder immediately transitions them to active follow mode on the first click.

8. **Persistent Zero-Timeout Builder Execution & Arcane Phase-Shift**:
   - **Eradication of Premature Halting & Timeout Loops**: Previously, builders stalled near completion on complex structures (e.g., Workshops, Smithies, Cottages) when only interior ground details (anvils, grindstones, blast furnaces, chests, lanterns) remained. Because exterior walls and roofs are completed earlier, roof-standing builders had line-of-sight to ground tasks occluded and could not pathfind down high roofs. Under the legacy architecture, an artificial 400-tick (20s) navigation timeout released the task lease, paused `activelyBuilding`, and triggered a failure cooldown, leaving builders frozen in place until manually re-selected.
   - **Arcane Phase-Shift Resolution**: All timeout loops, task surrender counters, and failure penalties have been eliminated. When an active builder encounters navigation stalls or physical obstacle occlusions for $\ge 40$ ticks (~2.0s), the builder initiates an **Arcane Phase-Shift**:
     - Teleports directly to the calculated work hover station or safe adjacent ground (`minion.requestTeleport(...)`).
     - Emits magical `PORTAL` particles and triggers an arcane enderman warp audio cue (`SoundEvents.ENTITY_ENDERMAN_TELEPORT`).
     - Immediately resumes continuous block placement without abandoning tasks.
   - **Continuous Goal Execution**: `MinionBuildGoal.shouldContinue()` remains active for the entire duration of an active construction session (`session != null && session.isActive()`). When `currentTask == null`, the builder polls `session.claimNextTask(...)` every tick while maintaining its active building stance without dropping back to idle.
   - **Double-Block Multi-Part Synchronization**: When placing lower halves of multi-part components such as doors (`DoubleBlockHalf.LOWER`), the server session automatically marks the corresponding upper task (`DoubleBlockHalf.UPPER`) as completed, preventing task desynchronization and eliminating stalls where builders wait for already-occupied spaces.
   - **Hanging Block Fast-Path Validation**: Hanging fixtures (`LanternBlock.HANGING`) are marked ready as soon as their overhead supporting block exists in the world, preventing detail blocks from being blocked indefinitely.
   - **World-Space Rotated Structure Exit Navigation**: `findStructureExitWaypoint` utilizes true transformed world positions rather than raw unrotated blueprint offsets, ensuring ground pathfinding accurately targets open doorways when navigating structures in any quadrant rotation.

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

## 8. 100% Zero-Footprint Universal Arcane Levitation

All ephemeral scaffolding and combat sapper goals (`MinionSapperGoal`, `TraversalScaffoldingManager`) have been completely retired in favor of **100% Universal Arcane Levitation**:

- **Zero World Footprint**: Minions traverse across ravines, scale cliffs, descend structures, and navigate steep terrain using pure arcane flight kinematics with zero ephemeral block generation or block clutter.
- **Universal Role Support**: Warriors, Sentinels, Rangers, and Builders all share full 3D Arcane Levitation flight and 2-tick obstacle vaulting.
- **Dynamic 3D Hover & Glide**: Minions automatically glide up vertical obstacles, across open gaps, and off rooftop perimeters, maintaining safe descent velocities and zero fall damage.
- **Kinematic Safety Ceiling & Anti-Jitter**: Enforces smooth station positioning and collision-aware height ceilings to prevent skyrocketing or oscillation.

---

## 9. Advanced Mob Pathfinding Engine

### Scaffolding Pathfinding Evaluation (`MinionPathNodeMaker`)

Vanilla Minecraft's `LandPathNodeMaker` treats scaffolding as `PathNodeType.BLOCKED`, preventing mobs from climbing or traversing temporary platforms. The mod's custom path node maker re-evaluates vanilla scaffolding (`Blocks.SCAFFOLDING`):

- **Player-Placed Scaffolding Awareness**: Evaluates player-placed vanilla scaffolding as `PathNodeType.OPEN` (or `WALKABLE` when supported from below), allowing vertical ascent and horizontal span crossing across elevated platforms.
- **Elevated Platforms**: Evaluates standing on top of scaffolding as `PathNodeType.WALKABLE`, enabling fluid ground navigation across high-altitude platforms.

### Custom Navigation (`MinionNavigation`)

- Integrates `MinionPathNodeMaker` into a custom `MobNavigation` pipeline.
- Pre-configured with open and closed door traversal (`setCanPathThroughDoors(true)`, `setCanEnterOpenDoors(true)`).

### Interior Door & Portal Traversal

- Sets pathfinding penalties for `DOOR_OPEN`, `DOOR_WOOD_CLOSED`, `WALKABLE_DOOR`, and `TRAPDOOR` to `0.0F`.
- Leverages `LongDoorInteractGoal` to allow minions to open, walk through, and close doors smoothly without pathing stalls.

---

## 10. Tactical Ordnance & Warrior Thrown Weapon Arsenal

### Warrior Thrown Weapon Arsenal & Trident Duality

The `WARRIOR` archetype possesses advanced mastery over thrown weapons and projectile ordnance:

#### 1. Trident Duality (`TridentItem` & `TridentEntity`)

- **Dual-Stance Weapon Kinetics**: The Trident acts as both a heavy melee polearm and a long-range piercing javelin:
  - **Melee Zone ($\le 5.0\text{D}$, $\le 25.0\text{D}^2$)**: `MinionRangedAttackGoal` suppresses ranged attack triggers; `MeleeAttackGoal` takes priority, executing rapid thrusts at close range.
  - **Ranged Zone ($5.0\text{D} < d \le 20.0\text{D}$, $\le 400.0\text{D}^2$)**: `MinionRangedAttackGoal` activates; the warrior maintains spacing, adopts the `THROW_SPEAR` arm pose, charges for 20--40 ticks, and launches a server-authoritative `TridentEntity` with $1.6\text{F}$ velocity, accurate pitch/yaw orientation, and `ITEM_TRIDENT_THROW` audio.
  - **Disallowed Pickup Protection**: Minion-launched tridents initialize with `TridentEntity.PickupPermission.DISALLOWED`, preventing infinite arrow/trident duplication while ensuring seamless combat balance.
  - **Dual-Wielding Priority**: If a warrior carries a pure ranged weapon (Bow/Crossbow) in offhand, offhand ranged skirmishing is prioritized; otherwise, mainhand Trident duality governs combat.

#### Trident Loyalty Return Interception & Orbit Fix (`TridentEntityMixin`)

In vanilla Minecraft, tridents enchanted with **Loyalty** return to their thrower when hitting blocks or entities. While vanilla handles the return flight physics via `noClip = true` and acceleration vectors toward owner coordinates, vanilla **only** collects and despawns returning tridents inside `TridentEntity.onPlayerCollision(PlayerEntity)`.

Because `MinionEntity` is a `TameableEntity` (extending `PassiveEntity` and `PathAwareEntity`, not `PlayerEntity`), returning Loyalty tridents endlessly overshoot the minion's eye position, accelerate back and forth, and enter an infinite, buzzing orbit loop around the minion thrall.

To solve this, `TridentEntityMixin` intercepts returning Loyalty tridents server-side:

```
                  [TridentEntity Thrown by Minion]
                                 │
                     (Hits Mob or Terrain Block)
                                 │
                                 ▼
                     [Loyalty Return Engaged]
               • noClip == true
               • Vector acceleration toward owner
                                 │
                                 ▼
                   [TridentEntityMixin Intercept]
               • Method: tick() @ HEAD
               • Owner: MinionEntity (alive)
               • Distance: distSq <= 2.25D (1.5 blocks)
                                 │
                                 ▼
              ┌──────────────────┴──────────────────┐
              ▼                                     ▼
   [Mainhand Empty?]                     [Mainhand Occupied?]
   • Equip to Mainhand                   • Check Offhand
                                                    │
                                         ┌──────────┴──────────┐
                                         ▼                     ▼
                              [Offhand Empty?]        [Offhand Occupied?]
                              • Equip to Offhand      • Store in 9-Slot Backpack
                                                               │
                                                      ┌────────┴────────┐
                                                      ▼                 ▼
                                            [Backpack Has Room]  [Backpack Full]
                                            • Add to Inventory   • Drop at Feet
                                                      │                 │
                                                      └────────┬────────┘
                                                               │
                                                               ▼
                                               [Return SFX & VFX Triggered]
                                               • SoundEvents.ITEM_TRIDENT_RETURN
                                               • 8x PORTAL Particles
                                               • trident.discard()
```

- **Bytecode Mixin Target**: Injects into `tick()` at `HEAD` on `TridentEntity` (registered in `modid.mixins.json`).
- **Proximity Interception ($\le 1.5\text{D}$ / $2.25\text{D}^2$)**: When `isNoClip()` is active on the server and `getOwner()` is an alive `MinionEntity`, the mixin monitors squared distance to the minion. As soon as the trident reaches within $1.5$ blocks ($d^2 \le 2.25\text{D}$), proximity capture triggers.
- **Prioritized Equipment & Inventory Recovery**:
  1. **Mainhand (Primary)**: If the minion's mainhand is empty, the returning trident is immediately re-equipped in the mainhand (`EquipmentSlot.MAINHAND`), instantly priming the minion for subsequent melee or ranged strikes.
  2. **Offhand (Secondary)**: If the mainhand is occupied (e.g. sword or tool), the trident is equipped into the offhand (`EquipmentSlot.OFFHAND`).
  3. **9-Slot Internal Backpack (Tertiary)**: If both hands are occupied, the trident is safely deposited into the minion's 9-slot persistent internal inventory (`minion.getInventory().addStack(...)`).
  4. **Ground Drop Fallback**: If hands and all 9 backpack slots are full, the stack safely drops at the minion's feet (`minion.dropStack(...)`).
- **Audio-Visual Catch Feedback**:
  - Plays authentic trident return audio (`SoundEvents.ITEM_TRIDENT_RETURN`, `SoundCategory.NEUTRAL`, volume `1.0F`, pitch `1.0F`).
  - Spawns 8 purple `PORTAL` particles in the `ServerWorld` around the minion's catch coordinates.
- **Entity Cleanup**: Calls `this.discard()` to safely despawn the returning projectile entity, permanently eliminating infinite orbital loops.

#### 2. Frost Grenade Launching (`FrostGrenadeStickItem` & `FrostGrenadeEntity`)

- Equipped Warriors identify Frost Grenade Sticks as ranged tactical ordnance (`isThrownWeapon`).
- In combat, warriors maintain an 8--16 block pocket and lob cryogenic frost grenades via `shootAt(LivingEntity, float)`, creating instant snow transmutation zones, flash-freezing fluids, deploying powder snow rings, and inflicting freezing debuffs on enemy formations.

#### 3. TNT Stick Launching (`TntStickItem` & `TntProjectileEntity`)

- Warriors equipped with TNT Sticks launch explosive projectiles at hostile clusters, providing heavy artillery support.

---

### Handheld Tactical Ordnance Items

Commanders and minions alike can employ dedicated handheld ordnance:

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
- **In-Game Tooltip**: Color-coded tactical summary detailing block transmutation, fluid conversion, powder snow perimeter, and freezing debuffs.

### Frost Grenade Projectile (`FrostGrenadeEntity`)

- **Identifier**: `modid-mmcli-agent-modding:frost_projectile`
- **Flight Physics**: Arcing thrown item physics leaving client-side snowflake (`SNOWFLAKE`) and snowball (`ITEM_SNOWBALL`) particle trails.
- **Direct Impact (`onEntityHit`)**: Deals $3.0\text{F}$ direct cold/blunt damage on entity impact (+5.0 bonus damage against fire-elemental mobs like Blazes and Magma Cubes).
- **Zero Explosive Destruction**: Causes no destructive blast damage, safely transmuting the landscape without destroying blocks.
- **Block Transmutation to Snow ($r = 3.5\text{D}$)**:
  - **Solid Snow Block Transmutation**: Converts destructible solid blocks (grass blocks, dirt, stone, cobblestone, wood logs, planks, leaves, sand, gravel, terracotta, etc.) directly into solid Snow Blocks (`Blocks.SNOW_BLOCK`).
  - **Snow Layer Surface Coating**: Automatically blankets exposed ground, surfaces, and air/replaceable vegetation above solid blocks with Snow layers (`Blocks.SNOW`).
  - **Bedrock & Container Immunity**: Unbreakable blocks (bedrock, barrier) and block entities/containers (chests, furnaces, barrels, spawners) are strictly protected and never modified.
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

## 11. Data Components & Network Protocol Architecture

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

## 12. Survival vs. Creative Mode Economy & Mechanics

The commander's active game mode dynamically dictates minion logistics, resource consumption, and world interaction across building, mining, recruitment, and maintenance:

| Subsystem                           | Survival Mode (`/gamemode survival`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Creative Mode (`/gamemode creative`)                                                                                                                                                                                                                                                      |
| :---------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Structure Building (`BUILD`)**    | **Autonomous Logistics, Harvesting & Procurement**: Builders consume carried blocks, scavenge nearby chests within 12 blocks, transfer blocks via peer-to-peer beams from allies within 24m, autonomously quarry natural stone/deepslate, and harvest timber via bone-meal agro-forestry. When mob resources (wool, bones, slime, leather, etc.) are needed, builders commission nearby Warriors on tactical hunting contracts with automatic material synthesis. They self-craft replacement tools and deposit excess materials into newly crafted/placed supply depot chests. (Only pauses with alert if non-harvestable materials are missing). | **Zero-Cost Free Placement & Zero-Drop Pre-Clearing**: Builders place blocks freely and continuously without consuming items or requiring container inventories. Existing terrain (grass, flowers, snow, dirt) is automatically pre-cleared with zero dropped items, eliminating clutter. |
| **Area Mining (`MINE`)**            | **Full Resource Recovery**: Excavated blocks drop as collectible world item entities for player salvage.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | **Zero-Drop Demolition**: Blocks are cleared without entity drops, preventing world and inventory clutter during large excavations.                                                                                                                                                       |
| **Minion Taming**                   | Consumes **1 Gold Ingot** from player hand when binding an untamed minion.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Tames the minion instantly **without consuming** the held Gold Ingot.                                                                                                                                                                                                                     |
| **Feeding & Healing**               | Consumes **1 food or gold item** per healing interaction from the player's hand.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Restores health to full **without consuming** held food or gold items.                                                                                                                                                                                                                    |
| **Spawn Egg Usage**                 | Consumes **1 spawn egg** per mob spawned from the item stack.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Spawns minions infinitely **without depleting** the held spawn egg stack.                                                                                                                                                                                                                 |
| **Scepter Recruitment (`RECRUIT`)** | Transfigures target wild or enemy mobs into loyal minion thralls.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Transfigures target wild or enemy mobs into loyal minion thralls.                                                                                                                                                                                                                         |
| **Universal Arcane Traversal**      | **100% Zero-Footprint Arcane Levitation**: All minion roles (Warriors, Sentinels, Builders) traverse chasms, scale cliffs, and descend structures using zero-footprint 3D flight with zero ephemeral block generation.                                                                                                                                                                                                                                                                                                                                                                                                                             | **100% Zero-Footprint Arcane Levitation**: Zero ephemeral blocks generated; fluid 3D flight and obstacle vaulting across all roles.                                                                                                                                                       |
| **Arcane Build Flight**             | **Free 3D Survival Build Flight**: Unconstrained 3D vanilla flight mobility with Space (ascend), Shift (descend), and WASD. Water Flight Cancellation Safeguard automatically revokes flight, reverts mode to `FOLLOW`, and cancels construction if entering water.                                                                                                                                                                                                                                                                                                                                                                                | **Creative Flight & Scepter Sync**: Full creative flight with high-altitude aerial placement (96m crosshair reach) and water cancellation safeguard.                                                                                                                                      |

---

## 13. Builder Minion Stability, Anti-Oscillation & Placement Idempotency

### Single-Click Placement Guarantee & Cooldown Debouncing

- **Multi-Layer Placement Cooldown**: A 10-tick (0.5-second) debounce cooldown is applied to `CommandScepterItem` across the entire command pipeline:
  - **Client-Side**: `ExampleModClient.UseItemCallback` verifies `!isCoolingDown()` before dispatching `AnchorConstructionPayload`.
  - **Networking Gate**: `ModNetworking.handleAnchorConstruction` rejects packets if the commander's scepter is on cooldown.
  - **Server-Side Execution**: `CommandScepterItem.executeBuildPlacement` and `executeMinePlacement` verify `!isCoolingDown()` and immediately stamp a 10-tick cooldown upon activation.
  - **Vanilla Block Interaction**: `CommandScepterItem.useOnBlock` checks `!isCoolingDown()`, preventing duplicate executions between Fabric callbacks and vanilla reach raycasts.
- **Mouse Release Action Isolation**: Quick-tap release (`onStoppedUsing`) is strictly reserved for blueprint cycling when aimed into open air/sky. It never re-triggers `executeBuildPlacement` or `executeMinePlacement`, completely eliminating secondary placement on mouse release.
- **Construction Session Deduplication**: `ConstructionManager.startSession` scans existing active sessions owned by the player, automatically canceling and removing any session sharing the same anchor or intersecting the new structure's bounding box.

### Builder Anti-Oscillation & Positive Elevation Kinematics

- **Positive Target Elevation (`targetY = targetPos.getY() + 0.05D`)**:
  - Previously, `targetY` was calculated as `targetPos.getY() - 0.2D`, which placed the minion's target hovering coordinates $0.2\text{m}$ inside the solid floor or roof block beneath the target. The minion set downward velocity into the block, Minecraft's entity collision pushed it back up, and the AI pushed it back down, causing violent vertical oscillation ("sinking into the building and popping back up").
  - `findOptimalHoverStation` now anchors at `targetPos.getY() + 0.05D` and inspects solid blocks beneath candidate positions to ensure feet hover safely at `Math.max(targetY, candPos.getY() + 0.05D)`.
- **Upward-Biased Block Nudging (`nudgeMinionAwayFromTargetBlock`)**:
  - When placing blocks that intersect the minion's bounding box (e.g. roof tiles or floor blocks), the escape vector search prioritizes `Direction.UP`.
  - The minion steps safely onto the newly placed surface at `targetPos.getY() + 1.0D`.
  - Lateral fallback pushes now guarantee `safeY = Math.max(minion.getY(), targetPos.getY() + 1.0D)`, preventing builders from sinking into newly constructed surfaces.

### Builder Motion Continuity & Stall Tracking

- **Physical Collision Stall Detection**:
  - Previously, `stallCollisionTicks` incremented unconditionally whenever `hasCeilingObstruction && delta.y > 0.0D`. Any building with an upper floor or roof triggered 12 stall ticks within 0.6 seconds, dropping levitating minions to the ground and causing repeated stuttering and freezing.
  - Stall tracking now requires active physical collision (`horizontalCollision` or upward `verticalCollision`) combined with negligible velocity ($\|\mathbf{v}\|^2 < 0.005\text{D}$).
- **Headroom-Aware Line-of-Sight Fallback**:
  - Builders are no longer blocked from engaging levitation simply because an upper ceiling block exists overhead. If unobstructed line-of-sight to an optimal hover station exists and overhead velocity clamping prevents ramming, minions smoothly engage levitation to place ceilings, second-story walls, and roofs without pausing.
- **Distance-Dampened Station Velocity (Anti-Overshoot)**:
  - In `MinionBuildGoal`, hover velocity is dynamically calculated as $\text{speed} = \min(0.35\text{D}, \max(0.08\text{D}, \text{distToHover} \times 0.5\text{D}))$.
  - When within $0.35\text{m}$ of the hover station or within reach of the target block, velocity halts immediately to $(0, 0, 0)$, completely eliminating sub-block overshoot oscillations and perpetual purple portal particle trails under hovering builders.
- **Non-Structural Interior Furniture Isolation (`ConstructionSession.isBuildTaskReady`)**:
  - Detail and furniture blocks (`isNonStructuralDetail`: Anvils, Grindstones, Smithing Tables, Furnaces, Blast Furnaces, Smokers, Chests, Barrels, Ladders, Torches, Lanterns) are exempted from gating upper structural layers. If a lower-layer interior workstation cannot be placed immediately, builders smoothly proceed with upper walls and roofs instead of deadlocking.
- **Autonomous Workstation Synthesis (`MinionHarvestingHelper`)**:
  - Builders autonomously craft specialized workstations (Smithing Tables, Grindstones, Blast Furnaces, Smokers, Anvils, Lanterns, Chests) from harvested ingots, cobblestone, and timber.
- **Doorway Navigation (`MobNavigation`)**:
  - Minions configure `mobNav.setCanPathThroughDoors(true)` and `setCanEnterOpenDoors(true)` alongside `LongDoorInteractGoal`, enabling them to route through and operate doors to access interior workstation locations.
- **Intelligent Indoor/Outdoor Structure Egress & Doorway Traversal (`findStructureExitWaypoint`, `autoOpenNearbyDoors`)**:
  - **Player-Like Navigation Behavior**: Eliminates the issue where builder minions get stuck inside completed rooms staring up at the ceiling when a roof block needs placement:
    - _Indoor-to-Outdoor Transition_: When an indoor minion has no line-of-sight to an exterior/roof hover station and cannot pathfind directly on foot, it automatically locates the nearest exterior exit doorway or open-sky perimeter waypoint (`findStructureExitWaypoint`). It walks out the door on the ground, automatically opens closed doors in its path (`autoOpenNearbyDoors`), and immediately engages Arcane Levitation to fly up to the roof station as soon as it reaches the open air.
    - _Outdoor-to-Indoor Transition_: When hovering in the air or on the roof and assigned an interior task (furniture, bed, workstation), the minion smoothly glides down outside to the entrance doorstep, disengages levitation, and walks through the doorway into the room.
    - _Emergency Arcane Phase Egress_: If a minion becomes trapped inside a completely sealed room with no doors or openings for 35+ ticks, it executes an Arcane Phase teleport to the outside ground with portal particles and teleport sound, guaranteeing minions are never permanently entombed.

---

## 14. Multi-Modal Blueprint Rotation & Arcane Build Flight Altitude Controls

### 3 Intuitive Ways to Rotate Blueprints

- **Dedicated `R` Key Rotation**:
  - When holding the Command Scepter in **`BUILD`** mode, pressing **`R`** rotates the blueprint 90° clockwise (0° → 90° → 180° → 270° → 0°).
  - Emits note block chime audio feedback with pitch scaling per quadrant, action bar confirmation text (`🏗 Rotation: 90° (CLOCKWISE_90)`), and immediate server C2S packet synchronization (`UpdateScepterPayload`).
  - In all other command modes (`FOLLOW`, `STAY`, `MINE`, `RECRUIT`), `R` continues to trigger Panic Retreat / Regroup.
- **Direct Left-Click Rotation**:
  - When holding the Command Scepter in **`BUILD`** mode, any left-click (whether aimed at the sky, ground terrain, distant wireframes, or blocks, with or without `Shift`) immediately rotates the blueprint 90° clockwise.
  - Left-clicking only performs minion selection if the crosshair directly targets an owned `MinionEntity`.
- **Interactive Command Hub GUI Button (`[ ↻ Rotate: 90° ]`)**:
  - Inside the Command Hub screen (**`V`** or **`Shift + Right-Click`**), when in `BUILD` mode, a dedicated **`[ ↻ Rotate: X° (Direction) ]`** button appears in the mode footer box.
  - Clicking cycles the rotation orientation with audio feedback and syncs the held scepter before placement.

### Arcane Build Flight Space / Shift Altitude Control

- **Smooth Elevation Adjustment in Flight**:
  - In `BUILD` mode, Arcane Build Flight automatically elevates the player to the blueprint's build height.
  - Holding **`Space`** (Jump Key) smoothly ascends upward (safely clamped below any overhead ceilings via vertical raycasts).
  - Holding **`Shift`** (Sneak Key) smoothly descends downward toward the ground ($Y_{\text{ground}} + 1.5\text{m}$).
  - Releasing either key locks the hovering altitude at the current height with zero fall damage and full horizontal WASD panning.
- **Physical Sneak Key Compatibility**:
  - Vanilla Minecraft disables sneaking pose while flying. All command scepter checks now query `options.sneakKey.isPressed()`, ensuring that sneaking actions (such as opening the Command Hub GUI via Sneak + Right-Click) work flawlessly while airborne.

---

## 15. Builder Block Phasing, Post-Construction Structure Egress & Guaranteed Perimeter Flank Spread

### Builder Block Phasing (`noClip = true`) & Zero Drift

- **Phase-Through Physics**: Builder minions can pass through blocks (`this.noClip = true`, `setNoGravity(true)`) strictly and only while actively constructing (`activelyBuilding = true`) or evacuating a structure post-completion (`exitingBuilding = true`). Non-builder roles (Warriors, Sentinels) never receive `noClip`, preserving solid combat physics.
- **Client-Server Synchronization**: Phasing status is synchronized to the client via `PHASING_BLOCKS` tracked data, guaranteeing zero client-side rubberbanding or visual stutter.
- **Mutual Shoving Suppression**: While phasing, builders ignore entity shoving and collision physics (`pushAwayFrom`, `isPushable`), ensuring they fly smoothly in 3D without interference from allies.
- **Direct 3D Navigation**: Phasing builders fly directly through floors, walls, and ceilings straight to their target hover stations, bypassing intermediate ceilings and doorway navigation drift.

### Post-Construction Structure Egress Engine (`startEgressFromStructure`)

- **Safe Exterior Evacuation**: Builders never lose their block-phasing ability while still inside a structure. When construction finishes (or if a session is cancelled), `startEgressFromStructure` triggers. The builder remains in 3D levitation with `noClip = true` until it has physically moved outside the structure bounding box and reached clear ground with unobstructed headroom. Only then does `finishBuildingEgress` restore normal collision physics (`noClip = false`), gravity, and ground footing.

### Guaranteed 360° Perimeter Flank Spread (All Squad Channels)

- **Continuous Outer Perimeter Ring**: Rather than clustering at the front entrance doorway, perimeter stations are calculated along a continuous closed ring encircling all four flanks of the structure: South (Front), East flank, North (Back), and West flank.
- **Guaranteed Flank Stationing**: For $N$ builder minions, stations are distributed uniformly at angular intervals of $360^\circ / N$ along this ring with deduplicated coordinates. Minions are directly teleported (`minion.requestTeleport(wx, waypoint.getY(), wz)`) to their assigned perimeter stations upon build completion, ensuring minions surround the finished building regardless of their assigned squad channels.
- **Stationed Guard Posture**: Upon arriving at their perimeter stations, minions enter a defensive stationed stance (`guardAnchorPos` assigned, `isSitting() = true`, `isSelected() = false`), standing guard at attention with golden beacon beams (`END_ROD` + `GLOW`) and chime audio (`BLOCK_AMETHYST_BLOCK_CHIME`).
- **Autonomous Mobilization**: When a new blueprint is anchored within 64 blocks, stationed builders automatically wake up (`setSitting(false)`, `setGuardAnchorPos(null)`) and mobilize to build immediately without requiring manual re-selection.

---

## 16. Free Survival Build Flight & Water Flight Cancellation Safeguard

### Unconstrained 3D Survival Flight (`BuildFlightManager`)

- **Vanilla Flight Mechanics**: Holding the Command Scepter in `BUILD` mode grants the player true physical flight in Survival mode matching Creative flight freedom:
  - **Space (Jump)**: Ascend smoothly without altitude caps.
  - **Shift (Sneak)**: Descend smoothly toward the ground.
  - **WASD**: Complete, unconstrained horizontal navigation.
- **Elimination of Velocity Clamping**: Rigid hover altitude locking and vertical velocity zeroing have been eliminated, allowing the commander to survey, position, and inspect blueprints at any angle or altitude.
- **Graceful Descent & Zero Fall Damage**: Switching scepter modes, stowing the scepter, or touching down gently restores normal ground physics with complete fall damage immunity enforced server-side (`ACTIVE_SERVER_BUILD_FLIERS`).

### Water Flight Cancellation Safeguard (`CommandScepterItem`, `BuildFlightManager`, `ConstructionManager`)

- **Vertical Column Water Scanning**: `CommandScepterItem.isPlayerOverWater` continuously scans the vertical column beneath the player down to terrain or liquid level.
- **Immediate Cancellation on Water Entry**: If a player enters water or flies over water while in `BUILD` mode:
  1. **Construction Cancellation**: Automatically cancels all active construction sessions belonging to the player via `ConstructionManager.cancelActiveSessionsForOwner`.
  2. **Mode Reversion**: Switches the scepter's operating mode to **`FOLLOW`** (`CommandMode.FOLLOW`).
  3. **Flight Revocation**: Revokes flight abilities (`allowFlying = false`, `flying = false`) and clears `ACTIVE_SERVER_BUILD_FLIERS`.
  4. **Audiovisual Feedback**: Plays an extinguishing sizzle sound effect (`SoundEvents.BLOCK_FIRE_EXTINGUISH`) and spawns water splash particles (`ParticleTypes.SPLASH`).
  5. **Commander Alert**: Displays an immediate warning message on the actionbar: `§c⚠ Construction cancelled: Flying over water is prohibited in BUILD mode!§r`.
