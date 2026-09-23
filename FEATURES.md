# Sovereign: Arcane Strategy & Engineering — Feature Showcase & Technical Specifications

This document provides a comprehensive technical breakdown of all gameplay features, items, entities, blocks, AI behaviors, construction mechanics, client rendering pipelines, GUIs, pathfinding engines, and networking protocols implemented in **Sovereign: Arcane Strategy & Engineering** (`modid-mmcli-agent-modding`).

---

## Table of Contents

1. [Loki Command Scepter (`CommandScepterItem`)](#1-loki-command-scepter-commandscepteritem)
2. [Autonomous Minion Thralls (`MinionEntity`) & Spawn Egg](#2-autonomous-minion-thralls-minionentity--spawn-egg)
3. [Tactical Army Architecture: Roles, Squads & Formations](#3-tactical-army-architecture-roles-squads--formations)
4. [Client Visuals, Holograms & Overhead Crest Badges](#4-client-visuals-holograms--overhead-crest-badges)
5. [Interactive GUIs: Command Hub & Minion Management](#5-interactive-guis-command-hub--minion-management)
   - [5.1 Command Hub GUI (`CommandScepterScreen`)](#51-command-hub-gui-commandscepterscreen)
   - [5.2 Minion Management GUI (`MinionScreen`)](#52-minion-management-gui-minionscreen)
   - [5.3 Clean & Crisp In-Game GUI Overlays (Universal Background Blur Removal)](#53-clean--crisp-in-game-gui-overlays-universal-background-blur-removal)
6. [Multiblock Construction & Blueprint Engine](#6-multiblock-construction--blueprint-engine)
7. [Structure Deconstruction, Mining Area Clearance & Bedrock Immunity](#7-structure-deconstruction-mining-area-clearance--bedrock-immunity)
8. [Arcane Builder Levitation & Player-Like Ground Navigation](#8-arcane-builder-levitation--player-like-ground-navigation)
9. [Advanced Mob Pathfinding Engine](#9-advanced-mob-pathfinding-engine)
10. [Tactical Ordnance & Warrior Thrown Weapon Arsenal](#10-tactical-ordnance--warrior-thrown-weapon-arsenal)
11. [Data Components & Network Protocol Architecture](#11-data-components--network-protocol-architecture)
12. [Survival vs. Creative Mode Economy & Mechanics](#12-survival-vs-creative-mode-economy--mechanics)
13. [Builder Minion Stability, Anti-Oscillation & Placement Idempotency](#13-builder-minion-stability-anti-oscillation--placement-idempotency)
14. [Multi-Modal Blueprint Rotation & Arcane Build Flight Controls](#14-multi-modal-blueprint-rotation--arcane-build-flight-controls)
15. [Builder Block Phasing, Post-Construction Structure Egress & Guaranteed Perimeter Flank Spread](#15-builder-block-phasing-post-construction-structure-egress--guaranteed-perimeter-flank-spread)
16. [Free Survival Build Flight & Water Flight Cancellation Safeguard](#16-free-survival-build-flight--water-flight-cancellation-safeguard)
17. [Visual Pathway Patrol System & Minion Escort Hierarchy](#17-visual-pathway-patrol-system--minion-escort-hierarchy)
18. [Autonomous Agent System (`MinionRole.AUTO` Dynamic Evaluation)](#18-autonomous-agent-system-minionroleauto-dynamic-evaluation)
19. [Dual-Tier Panic Retreat & Emergency Citadel Call](#19-dual-tier-panic-retreat--emergency-citadel-call)
20. [Custom Blueprint Catalog Lifecycle & Decommissioning Safeguards](#20-custom-blueprint-catalog-lifecycle--decommissioning-safeguards)
21. [In-World Spatial Blueprint Capture ('DESIGN' Mode)](#21-in-world-spatial-blueprint-capture-design-mode)
22. [Unified Surface Anchoring Contract & Hollow Grid Mechanics](#22-unified-surface-anchoring-contract--hollow-grid-mechanics)

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

### 7 Operating Modes (`CommandMode`)

The scepter cycles through 7 distinct operational modes via **Sneak + Right-Click** (in air) or the Command Hub GUI:

1. **`FOLLOW`** (`0.8F` pitch, `§aFollow`): Directs matching squad thralls to break stationary posts, assemble into formation, and escort the commander.
2. **`STAY`** (`1.0F` pitch, `§eStay`): Directs matching squad thralls to halt movement and hold ground at attention.
3. **`MINE`** (`1.4F` pitch, `§6Mine`): Directs builders to begin top-down deconstruction of clicked multiblock structures or 3D terrain volumes. Supports dual sub-modes: **`DIRECT`** (instant point-and-click structure/ground dismantle up to 96m) and **`AREA`** (configurable 3D boundary mining with sequential corner selection `Pos1` ➔ `Pos2`, real-time height adjustment, and confirmation modal).
4. **`BUILD`** (`1.6F` pitch, `§bBuild`): Projects blueprint holographic wireframes and anchors new multiblock construction sessions.
5. **`DESIGN`** (`1.7F` pitch, `§dDesign`): Sequential left-click spatial corner selection (1st left-click sets `Pos1`, 2nd left-click sets `Pos2`, subsequent left-clicks restart a new selection cycle) with real-time in-world holographic ghost grid structure rendering (semantic color-coded block outlines for doors, lighting, containers, stairs, and masonry) to capture, normalize, and compile existing world structures into custom blueprints.
6. **`RECRUIT`** (`1.8F` pitch, `§dRecruit`): Targets wild or enemy mobs to transfigure them into loyal minion thralls.
7. **`PATHWAY`** (`2.0F` pitch, `§3Pathway`): Projects 3D block wireframe checkpoints and establishes autonomous looping patrol routes across 5 distinct color channels.

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
| **Left-Click**                  | `DESIGN` Mode (Air / Block / Entity)| Sets sequential corner (`Pos1` on 1st click, `Pos2` on 2nd click, cycle on next click) without breaking blocks or attacking. Authoritative click handling prevents duplicate triggers. |
| **Left-Click**                  | `MINE` Mode (`AREA` Sub-Mode)   | Sets sequential mining corner (`Pos1` on 1st click, `Pos2` on 2nd click, cycle on next click) with fiery orange/amber wireframes without breaking blocks. |
| **Sneak + Left-Click**          | `DESIGN` Mode (Air / Block / Entity)| Resets corners back to initial state (wipes `Pos1` & `Pos2`, plays bass tone, guarantees next click places `Pos1`). |
| **Sneak + Left-Click**          | `MINE` Mode (`AREA` Sub-Mode)   | Resets mining corners back to initial state (wipes `Pos1` & `Pos2`, plays bass tone, guarantees next click places `Pos1`). |
| **Right-Click (Quick Tap)**     | `DESIGN` Mode (with Pos1 & Pos2) | Opens **Capture Custom Blueprint** modal (`BlueprintCaptureModalScreen`) on ground or air.   |
| **Right-Click (Quick Tap)**     | `MINE` Mode (`AREA` with Pos1 & Pos2) | Opens **Mining Area Confirmation** modal (`MiningConfirmModalScreen`) on ground or air. |
| **Right-Click (Quick Tap)**     | `MINE` Mode (`DIRECT` Sub-Mode) | Anchors point/structure dismantle session immediately up to 96m away. |
| **Right-Click (Quick Tap)**     | Hostile Mob ($\le 64\text{D}$)  | Focus-fire attack order; squad units focus target with drum cadence.                                        |
| **Right-Click (Quick Tap)**     | Ground Block ($\le 64\text{D}$) | Drops ground waypoint; selected squad units sprint, form up, hold station, and **automatically deselect**.  |
| **Right-Click (Quick Tap)**     | Owned Minion ($\le 64\text{D}$) | Toggles unit selection (chime/hearts vs bass/smoke).                                                        |
| **Right-Click (Quick Tap)**     | Open Sky / Air                  | Broadcasts active directive (`FOLLOW`, `STAY`) to 64-block radius.                                          |
| **Hold Right-Click (>8 ticks)** | Hostiles in Sector              | Charges 90° forward sector; launches coordinated **Mass Attack** across enemy formation.                    |
| **Hold Right-Click (>8 ticks)** | Minions in Sector               | Charges 90° forward sector; rallies, selects, and transfigures enclosed minions into primed role.           |
| **Sneak + Right-Click**         | Aiming at Air                   | Cycles scepter command mode forward (`FOLLOW` → `STAY` → `MINE` → `BUILD` → `RECRUIT`).                     |
| **Sneak + Right-Click**         | Owned Minion                    | Opens interactive **Minion Management GUI** (`MinionScreen`).                                               |
| **Sneak + Left-Click**          | Non-`BUILD`/`DESIGN` Modes      | Deselects all active minions immediately with bass/smoke feedback.                                          |
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
- **Step Height**: `1.25D` base step height, allowing smooth traversal over slabs, stairs, and 1-block terrain steps without jumping.
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
- **Orderly Post-Combat Formation Return**: Only once the assault queue is completely empty (`hasAssaultTargets() == false`) does the minion increment `outOfCombatTicks` and execute `returnToOwnerPostCombat()`. If assigned a guard anchor or patrol, it returns to its station; if selected (`isSelected() == true`), it returns to its commander; unselected wandering minions remain in their area and resume normal wandering. Only selected minions ever follow the commander.

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
| **`SENTINEL`** | Green (`§a`) | Perimeter Guard & Combat Medic                                    | Sword + Shield                                                                                     | Holds designated anchor; 8-block guard zone; 128-block leash freedom; prioritizes shields. **Aegis of Restoration**: Autonomously channels healing to the player commander, allied minions, and non-hostile/allied **Iron Golems** under 70% HP within 10 blocks (prioritizing the wounded player commander, then most critical minion or Iron Golem ally), restoring 6.0 HP (3 hearts) and granting Regeneration II for 5s with heart VFX, amethyst chime audio, and an action bar confirmation message (6s cooldown; fallback self-heal below 40% HP). Recognized as friendly teammates alongside Iron Golems via `isTeammate()`. |
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
  - `DESIGN` Spatial Captures: Full 3D holographic bounding box and in-world ghost grid structure rendering around the ground and targeted building. Scans non-air blocks in real-time within the spatial volume and renders semantic color-coded 3D ghost grid block boxes (Emerald Green doors, Amber Gold lighting, Arcane Purple utilities/containers, Ice Blue stairs, and Vibrant Cyan-Magenta masonry).
  - Dynamic Quadrant Rotation: Wireframe and ghost blocks dynamically rotate to match the active scepter rotation.
  - Remains visible in the world while minions construct, vanishing automatically once the last block is placed.
  - **Exact Custom Blueprint Geometry**: Every captured blueprint preserves its exact captured dimensions, block coordinates, and orientations without falling back to defaults. Construction wireframes and ghost blocks reflect the exact blueprint structure 1:1 in real time.
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
|  [BUILD]   [RECRUIT]            |  | Village Manor (11x9)  [✕] |  |
|  [PATHWAY] [DESIGN]             |  +---------------------------+  |
|                                 |  | Guard Bastion (7x7)   [✕] |  |
|  FORMATIONS                     |  +---------------------------+  |
|  [WEDGE]   [LINE]  [BOX]        |  | Grand Gate (5x5)      [✕] |  |
|                                 |  +---------------------------+  |
|  [ ↻ Rotate: NORTH ]            |  [ ◀ ]     Page 1/2     [ ▶ ]   |
+---------------------------------+---------------------------------+
|  MASS ARCHETYPES:                                                 |
|  [⚔ Warrior]        [🛡 Sentinel]        [🔨 Builder]             |
+-------------------------------------------------------------------+
|  [Execute]     [Teleport All]     [§c✖ Destroy All]      [Close]   |
+-------------------------------------------------------------------+
```

- **Contextual Command Hub GUI Layout**:
  - **Standard Modes (`FOLLOW`, `STAY`, `MINE`, `RECRUIT`)**: Displays the 3-button Mass Archetype bar (`[⚔ Warrior]`, `[🛡 Sentinel]`, `[🔨 Builder]`). Clicking directly converts minions or primes the scepter for channeled rally transfigurations.
  - **No Preselected Default Archetype**: By default, no archetype is preselected (`selectedRole = null`). This protects players from unintentionally transfiguring their army when opening the Command Hub GUI or right-clicking without a deliberate selection.
  - **`BUILD` Mode Directives**: When `BUILD` mode is active, the lower-left section displays active construction directives confirming 100% exact captured block placement with zero procedural alteration, along with the **`[ ↻ Rotate ]`** button.
- **Custom Blueprint Catalog & Pagination**:
  - Embedded between pagination arrows in the Blueprint Catalog section with clean `Page X/Y` indicators.
  - Displays user-authored custom blueprints with dimensions, block counts, and deletion controls (`[✕]`).
  - When no custom blueprints exist, displays helpful guidance directing players to enter `DESIGN` mode.
- **Squad Filter Bar**: Selects target squad division for directives and mass assignments.
- **Directives & Formations**: Radio buttons for quick operational mode and marching shape toggles.
- **Paginated Blueprint Viewport**: Displays 3 cards per page with dynamic `◀` and `▶` controls, block counts, dimensions, and `[✕]` delete buttons.
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

### 5.3 Clean & Crisp In-Game GUI Overlays (Universal Background Blur Removal)

- **Elimination of Vanilla Background Blur**: In vanilla Minecraft 1.21 (Yarn mappings), standard `Screen.renderBackground(DrawContext, int, int, float)` calls automatically invoke `this.applyBlur(delta)`, which triggers the costly `GameRenderer.renderBlur()` post-processing shader pass. In a high-tempo tactical strategy mod, blurring the world background obscures nearby combat skirmishes, minion construction progress, pathway wireframes, and tactical positioning, while introducing unnecessary visual lag.
- **Universal No-Op Blur Bypass Across All Screens**: By overriding `protected void applyBlur(float delta)` with a clean no-op (`{}`) across all custom screens, background blurring is completely eliminated across the entire mod interface suite:
  1. **`CommandScepterScreen`** (`extends Screen`): Tactical Command Hub, squad assignments, directive toggles, mining mode toggle (`DIRECT` vs `AREA`), and blueprint catalog.
  2. **`MinionScreen`** (`extends HandledScreen<MinionScreenHandler>`): Minion backpack inventory, 6 equipment slots, hearts health display, and role cycle controls.
  3. **`BlueprintCaptureModalScreen`** (`extends Screen`): In-world spatial blueprint capture dialog, dimension inspection, and naming modal.
  4. **`MiningConfirmModalScreen`** (`extends Screen`): Mining area boundary inspection, dimension and volume verification, height fine-tuning steppers, and confirmation dialog.
- **Architectural & Aesthetic Advantages**:
  - **100% In-Game Situational Awareness**: Commanders maintain complete, crisp visual tracking of the 3D battlefield, active minion worker swarms, building wireframes, and approaching threats while managing inventories or dispatching squad orders.
  - **High-Contrast Thematic UI Styling**: Replaces shader blur with stylized dark translucent backdrops (`context.fill` slate plates `0xEE111822` / `0xEE101622`), polished gold/cyan borders, and crisp typography for maximum legibility.
  - **Zero Post-Processing Framebuffer Overhead**: Completely bypasses Minecraft's intermediate blur shader passes and render target copies, ensuring instant, zero-latency modal opening and buttery frame rates.

---

## 6. Multiblock Construction & Blueprint Engine

### 100% Exact Block Fidelity & Custom Blueprint Architecture (`BlueprintRegistry`, `StructureBlueprint`)

- **Player-Driven Custom Catalog**: Construction relies entirely on player-designed structures captured in **`DESIGN`** mode. Legacy procedural generator archetypes, biome palettes, noise weathering, and arbitrary size selectors have been completely retired to give players absolute creative fidelity.
- **100% Exact Block Preservation**: Minion builders place every block (stairs, slabs, glass, lanterns, chests, doors, masonry) in the exact position, orientation, and block type captured in the world. Zero biome-specific block swaps, noise weathering, or procedural foundation stilts are added.
- **Deterministic 3D Rotation**: Rotating blueprints (0°, 90°, 180°, 270°) transforms coordinates and rotates block-facing properties directly via Minecraft's `BlockRotation` mappings.
- **Autonomous Stationed Mobilization**: Placing a blueprint alerts and mobilizes stationed builder minions within an expanded 112-block radius, eliminating the need to manually re-select builders on large construction sites.
- **Safe Empty Catalog Fallback**: When no structures have been captured yet, the Command Hub GUI displays empty-catalog guidance directing players to capture their first structure in `DESIGN` mode, with safe fallback handling preventing null errors.

### Universal Block Type Fidelity Across Construction (`BUILD` Mode)

Builder minions construct every block type with 100% state, property, and orientation fidelity:
- **Solid Structural Masonry & Timber**: Full support for all standard solid blocks (stone, cobblestone, deepslate, planks, bricks, terracotta, concrete, and custom mod blocks).
- **Transparent, Translucent & Glass**: Seamless construction of glass, tinted glass, stained glass variants, and glass panes with correct connecting geometry.
- **Lighting & Illumination**: Accurate placement of torches, wall torches, lanterns, hanging lanterns, campfires, sea lanterns, glowstone, froglights, redstone lamps, and end rods.
- **Containers, Utility & Workstations**: Single chests, double chests, barrels, furnaces, blast furnaces, smokers, crafting tables, anvils, looms, smithing tables, brewing stands, hoppers, droppers, dispensers, and shulker boxes.
- **Multi-Part Synchronized Placement**:
  - **Doors (`DoorBlock`)**: Automatically pairs and sets lower (`DoubleBlockHalf.LOWER`) and upper (`DoubleBlockHalf.UPPER`) halves simultaneously, nudging entities away and preventing neighbor-update breaking.
  - **Beds (`BedBlock`)**: Places and pairs `BedPart.FOOT` and `BedPart.HEAD` states simultaneously aligned with facing direction, preventing bed pop-offs.
  - **Tall Plants (`TallPlantBlock`)**: Synchronizes lower and upper halves of tall grass, large ferns, peonies, sunflowers, and lilacs.
- **Architectural Fixtures**: Stairs, slabs, fences, fence gates, walls, iron bars, chains, banners, and signs with authentic directional facing and waterlogged states.
- **Redstone & Mechanics**: Redstone dust, repeaters, comparators, levers, buttons, pressure plates, pistons, and observers.

### Custom Blueprint Architecture & World Persistence (`CustomBlueprintManager` & `CustomBlueprintPersistentState`)

- **Server-Authoritative Blueprint Manager (`CustomBlueprintManager`)**:
  - Singleton orchestrator (`getInstance()`) managing the lifecycle of user-designed custom blueprints across server sessions.
  - Integrates with the Overworld `PersistentStateManager` on world load (`init(ServerWorld)`), reading or creating the `CustomBlueprintPersistentState` instance.
  - Dynamically registers loaded and player-authored blueprints into `BlueprintRegistry` (`registerCustomBlueprint`), making them instantly available in the Command Hub GUI blueprint catalog for all connected players.
  - Handles server stopping hooks (`onServerStopping()`), ensuring all in-memory blueprints are flushed and committed to disk before world shutdown.
  - Synchronizes the custom blueprint catalog across clients upon player join (`syncToPlayer`) or on mutation events (`syncToAll`) using `SyncCustomBlueprintsPayload`.

- **World-Saved Persistent State (`CustomBlueprintPersistentState`)**:
  - World-saved persistent state stored under key `"minion_custom_blueprints"` (`data/minion_custom_blueprints.dat`).
  - Serializes blueprint definitions into structured NBT compounds containing unique string `id`, display `name`, `description`, and a compact `CustomBlueprints` list of `blocks`.
  - Block entries serialize relative coordinates (`x`, `y`, `z`) and raw `BlockState` IDs (`Block.getRawIdFromState` / `Block.getStateFromRawId`), omitting air blocks to maximize storage efficiency.
  - Thread-safe `ConcurrentHashMap<String, StructureBlueprint>` storage with automated `markDirty()` triggering on addition and deletion.

### Server Construction Manager (`ConstructionManager` & `ConstructionSession`)

Server-authoritative engine orchestrating automated multiblock building and demolition:

- **Task Queue & Topological Ordering**: Breaks blueprints into discrete block placement tasks sorted bottom-up.
- **Task Leasing**: Minions claim leases on available tasks. If a builder runs out of materials or stalls, the lease releases for other workers.
- **Dual Economics**:
  - **Creative Mode (Zero-Drop Guarantee)**: Zero-cost instant block placement. Automatically pre-clears any existing blocks (grass, flowers, snow, dirt, double plants, beds, doors) with zero dropped items, eliminating all clutter. In Creative mode, builders never craft or deploy chests—they discard unneeded blocks from their 9-slot inventory immediately, keeping backpacks clean and free for any materials. Any loose item entities spawned during building or upon session completion are automatically purged within the bounding box.
  - **Survival Mode Multi-Stage Logistics (Direct Chest Storage)**:
    1. _9-Slot Minion Backpack_: Consumes blocks currently carried in inventory.
    2. _Direct Dismantle & Mine Collection_: When deconstructing structures or clearing areas in `MINE` mode, mined blocks enter the minion's backpack directly rather than scattering onto the ground.
    3. _Nearby Container Scavenging_: Searches chests, barrels, and shulkers within 24 blocks for missing materials.
    4. _Peer-to-Peer Allied Sharing (`MinionLogisticsHelper`)_: Scans allied minions within 24m, transferring required blocks via green energy beams and pickup audio.
     5. _Autonomous Quarrying & Agro-Forestry (`MinionHarvestingHelper`)_: Autonomously quarries natural stone, deepslate, and earth. For timber, fells trees or plants saplings and accelerates maturity with bone meal. **Loot-Table-Faithful Drops**: Both `fellLog` and `quarryNaturalStone` use `Block.getDroppedStacks()` for drop computation, ensuring silk-touch axes yield logs, fortune pickaxes yield extra ores/gravel/flint, and all block-specific loot tables are respected. A fallback guarantees at least one resource unit if the loot table yields empty drops.
    6. _Squad Material Procurement & Mob Hunting Contracts (`MinionHarvestingHelper`)_: When organic or mob-derived materials are required (wool, bones, slime, leather, ink, prismarine, etc.), builders scan for target mobs within 32m and commission an available allied Warrior thrall. The warrior receives the contract via a weaponsmith audio cue and energy beam, hunts the target mob, synthesizes the required refined material, and delivers it directly to the builder. (Falls back to solo builder hunting if no warrior is nearby).
    7. _In-Inventory Resource Synthesis Engine_: Automatically synthesizes high-tier multiblock components from raw mob ingredients (e.g. 4 String $\to$ 1 Wool, 1 Bone $\to$ 3 Bone Meal, 9 Bone Meal $\to$ 1 Bone Block, 9 Slimeballs $\to$ 1 Slime Block, 4 Magma Cream $\to$ 1 Magma Block, 1 Blaze Rod $\to$ 2 Blaze Powder, 1 Ender Pearl + 1 Blaze Powder $\to$ 1 Eye of Ender, 4 Prismarine Shards $\to$ 1 Prismarine, 9 Shards $\to$ 1 Prismarine Bricks, 4 Shards + 5 Crystals $\to$ 1 Sea Lantern, 4 Rabbit Hide $\to$ 1 Leather, 9 String $\to$ 1 Cobweb).
    8. _Strict Safety & Build Protection_: Never hunts player pets, named mobs, villagers, iron golems, allays, or allied minions (`isSafeHuntTarget`). Never breaks player-placed blocks, active blueprint structures, processed materials (planks, bricks, slabs, glass), or blocks within 12m of player beds, chests, or respawn anchors.
    9. _Hazard Avoidance_: Inspects all 6 orthogonal directions and refuses to break blocks adjacent to lava.
    10. _Tool Self-Crafting_: Synthesizes wooden or stone tools (pickaxes, axes, shovels) from timber and stone when tools break or are missing.
    11. _Autonomous Supply Depots & Post-Build Chest Storage_: When backpacks fill or sessions finish, builders deposit 100% of surplus materials into nearby chests or craft an 8-plank Chest (pairing into a Double Chest if adjacent). Stray ground items are gathered into containers upon completion. Items only drop if no chest exists and no wood is available.
- **Autonomous Blueprint Chaining & Vicinity Transition Engine (`ConstructionManager`, `MinionBuildGoal`)**:
  - _Seamless Multi-Blueprint Transitions_: When builder minions finish a construction or dismantle session, they immediately scan their 128-block vicinity for other pending active blueprints belonging to their master instead of halting.
  - _Zero Premature Stationing / Sleep Prevention_: `ConstructionManager.completeSession` checks for nearby active sessions ($\le 128$ blocks). If another blueprint is active, builders bypass perimeter stationing (`setSitting(false)`, `setGuardAnchorPos(null)`), remaining mobilized and ready to work.
  - _Multi-Blueprint Placement During Active Construction_: If the commander places down additional blueprints while builders are actively constructing an earlier structure, builders seamlessly queue the new blueprints and transition directly to the next nearest blueprint the moment their current build completes.
  - _Nearest-First Spatial Dispatch_: When multiple blueprints are placed simultaneously, builders evaluate Euclidean distance across all pending sessions in the world and automatically prioritize and dispatch to the closest blueprint session.
  - _Periodic 20-Tick Active Mobilization_: `ConstructionManager.tick` executes a 20-tick (1-second) mobilization pulse across a 128-block bounding box around active sessions, waking any stationed builders (`setSitting(false)`, `setGuardAnchorPos(null)`) so they never miss newly placed blueprints even if they were stationed prior to placement.
  - _Autonomous `MinionBuildGoal` Persistence & Re-Leasing_: `MinionBuildGoal.tryClaimTaskOrChainNextSession` re-queries `ConstructionManager` when a session's task queue is depleted, atomically claiming the next available blueprint's tasks and equipping the required tools/blocks without exiting the build goal.

### Arcane Builder Levitation & Player-Like Ground Navigation (`MinionBuildGoal`, `MinionEntity`, `WaypointHoldGoal`, `MinionFormationFollowGoal`, `SentinelGuardGoal`)

- **Pure Ground Navigation & Removal of Traversal Levitation**: Traversal levitation has been completely stripped from normal minion movement. Non-builder minions navigate on the ground using standard `MobNavigation` and enhanced player-like $1.25\text{D}$ step height:
  - **Player-Like Step Height ($1.25\text{D}$)**: With `GENERIC_STEP_HEIGHT = 1.25D`, minions walk smoothly over slabs (0.5D), stairs (0.5D/1.0D), carpets, trapdoors, and 1-block steps natively with zero physics hesitation, zero collision stall, and zero purple particles.
  - **Natural Barrier Pathfinding**: Minions pathfind *around* 2-block obstacles, walls, fences, and pits using standard ground navigation just like vanilla mobs, eliminating unwanted launches into the sky.
  - **Smart Companion Catch-Up Teleportation**: Follower minions (`MinionFormationFollowGoal` and `MinionFollowLeaderGoal`) feature companion catch-up teleportation:
    - If a minion falls into a deep chasm, pit, or gets trapped behind a high wall ($> 10$ blocks away from leader/owner) and remains navigationally stuck for $\ge 40$ consecutive ticks ($2.0\text{s}$), it automatically teleports directly to its commander or squad leader.
    - If distance exceeds 24 blocks ($576.0\text{D}^2$), the minion immediately teleports to catch up, ensuring squad cohesion without aerial launches.
  - **Combat Grounding**: Minions fighting ground targets remain firmly grounded and never launch into the air from entity collisions.
  - **Selected-Only GUI Teleportation**: The Command Scepter GUI "✦ Teleport" action exclusively recalls **selected minions** within the 64-block command radius. Unselected minions remain stationed at their posts or on their patrol routes. If no minions are selected, the button remains disabled with informative feedback.
  - **Patrol Route Detachment**: Selecting a patrolling minion to follow the player explicitly and permanently detaches it from its patrol route (`setPatrolRouteId(-1)`), allowing direct command without goal conflicts.
  - **Hierarchical Destination Resolution**: `resolveActiveTargetDestination()` dynamically reconciles explicit goal destinations, active combat targets, waypoint/guard anchors, commander positions, and navigation waypoints.

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

The deconstruction engine (`CommandMode.MINE`, `MiningMode`, `ConstructionManager`, `ConstructionSession`, `MiningConfirmModalScreen`, `ClientMiningCaptureTracker`) provides dual operational modes for structure demolition and terrain quarrying:

```
                                      [MINE Operating Mode]
                                                │
                 ┌──────────────────────────────┴──────────────────────────────┐
                 ▼                                                             ▼
         [DIRECT Sub-Mode]                                             [AREA Sub-Mode]
   • Instant point-and-click dismantle                           • Configurable 3D boundary volume
   • Anchors directly at clicked position                        • Sequential Left-Clicks: Pos1 ➔ Pos2
   • 96m crosshair reach                                         • Fiery orange/amber ghost grid wireframes
   • Dismantles structures or terrain                            • Real-time height adjustment (Ctrl + Scroll)
   • Zero boundary setup required                                • Dedicated Confirmation Modal before mining
```

### 7.1 Dual Operational Sub-Modes: DIRECT vs. AREA Mining

Commanders can toggle between operational sub-modes within `MINE` mode via the Command Hub GUI (**`V`** key) or via scepter controls:

1. **`AREA` Mode (`MiningMode.AREA`, "Custom Area", `§e`) — Default**:
   - The default operational mode for mining.
   - Allows commanders to establish exact, custom 3D mining boundaries in the world, working identically to `DESIGN` mode grid selections but to remove blocks top-to-bottom.
   - Employs sequential left-click corner selection (`Pos1` ➔ `Pos2`), in-world fiery orange/amber ghost grid wireframes, real-time height adjustment, and a dedicated confirmation modal (`MiningConfirmModalScreen`) before initiating minion excavation.
2. **`DIRECT` Mode (`MiningMode.DIRECT`, "Direct / Structure", `§6`)**:
   - Alternate point-and-click deconstruction mode.
   - Aiming crosshairs at any block or placed structure and right-clicking anchors a deconstruction session immediately without needing boundary setup.
   - Long-range crosshair reach extends up to 96 blocks away.

### 7.2 Configurable 3D Area Boundary & Sequential Corner Invariants

In **`AREA`** mining mode, commanders define the 3D excavation boundary using sequential left-clicks:

- **Unified Click Interception & Stepping (`ClientMiningCaptureTracker`)**:
  - Left-clicks in `MINE` mode (with `MiningMode.AREA` active) are intercepted via `AttackBlockCallback` and `attackKey` handlers, routing through authoritative corner-stepping logic.
  - **1st Left-Click (`Pos1`)**: Sets Corner 1 anchored on top of the targeted block surface adhering to the Unified Surface Anchoring Contract. Emits a vibrant fiery orange marker box (`0xFFFFA500`), sparkle particle burst, note block chime (`1.2F` pitch), and actionbar confirmation: `§6✦ Mining Pos1: §f[X, Y, Z]`.
  - **Live Candidate Aiming Preview**: Aiming around the world with `Pos1` set projects a live candidate bounding wireframe and real-time ghost grid block outlines spanning from `Pos1` to the targeted crosshair block.
  - **2nd Left-Click (`Pos2`)**: Sets Corner 2 on the opposite diagonal, completing the 3D volume footprint. Emits sparkle particles, resonant note block chime (`1.4F` pitch), and actionbar confirmation: `§6✦ Mining Pos2: §f[X, Y, Z]`.
  - **Subsequent Left-Clicks (Cycle)**: Left-clicking with both corners set starts a new selection cycle (`Pos1` set to clicked block, `Pos2` cleared).
  - **Shift + Left-Click (Reset)**: Instantly clears both `Pos1` and `Pos2` with a bass tone (`BLOCK_NOTE_BLOCK_BASS`), guaranteeing the next left-click begins anew at `Pos1`.

### 7.3 Real-Time In-World Height Adjustment

Commanders can dynamically adjust excavation depth and height without placing temporary scaffolding blocks:

- **Mouse Scroll Interception (`MouseMixin`)**:
  - Holding **`Ctrl` + Mouse Scroll** while holding the Scepter in `MINE` mode (with `MiningMode.AREA` active):
    - Scroll **Up**: Expands top boundary ($Y$-max) by +1 block.
    - Scroll **Down**: Lowers top boundary ($Y$-max) by -1 block.
    - Holding **`Shift` + `Ctrl` + Scroll**: Fast-steps height by $\pm 5$ blocks.
    - Cancels vanilla mouse scroll to prevent cycling hotbar item slots.
- **Keyboard Controls**:
  - Pressing **`]`** (Right Bracket) or **`Page Up`**: Increases boundary height (+5 with Shift).
  - Pressing **`[`** (Left Bracket) or **`Page Down`**: Decreases boundary height (-5 with Shift).
- **HUD & Audio Feedback**:
  - Actionbar displays live height updates: `§6✦ Mining Area Height: §fX blocks §8| §7Dimensions: WxHxD (Vb)`.
  - Emits crisp note block hat audio feedback (`SoundEvents.BLOCK_NOTE_BLOCK_HAT`).

### 7.4 In-World Ghost Grid Wireframes & Semantic Highlighting (`BlueprintHologramRenderer`)

When in `MINE` mode with `MiningMode.AREA` active, the client hologram renderer projects in-world visuals:

- **Fiery Orange/Amber 3D Bounding Box**: Renders a glowing, pulsing fiery orange/amber wireframe box (`1.0F, 0.45F, 0.05F`) enclosing the entire excavation volume.
- **8 Vertex Accent Cubes**: Vibrant gold accent cubes (`0xFFFFD700`) highlight all 8 corners of the 3D bounding box.
- **Corner Highlight Markers**: `Pos1` (Orange `0xFFFFA500`) and `Pos2` (Amber `0xFFFFD700`) corner markers clearly indicate selection endpoints.
- **Non-Air Block Ghost Grid Outlines**: Scans the enclosed 3D volume in real time and renders fiery wireframe block outlines around all solid, destructible blocks to be excavated.
- **Camera-Facing Dimension & Voxel Count Badge**:
  $$\text{Mining Area: } W \times H \times D \quad | \quad \text{Destructible: } N \text{ blocks}$$

### 7.5 Mining Confirmation Modal Screen (`MiningConfirmModalScreen`)

To prevent accidental excavation of large areas, establishing an `AREA` mining boundary requires confirmation:

- **Modal Launch Triggers**:
  - Standard **Right-Click** on ground or in air with the Scepter in `MINE` mode (`AREA` sub-mode) when corners (`Pos1` and `Pos2`) are established.
  - Clicking **`[ ⛏ Confirm Area ]`** in the Command Hub GUI (`CommandScepterScreen`).
- **Inspection Metrics**:
  - **Coordinates**: Displays exact `Pos1` and `Pos2` coordinates.
  - **Dimensions & Volume**: Displays Width $\times$ Height $\times$ Depth and total voxel volume.
  - **Destructible Block Counter**: Counts and displays the exact number of solid, destructible blocks queued for mining (excluding air and indestructible bedrock).
- **Dedicated Height Stepper Row**: Interactive **`[ -5 ]`**, **`[ -1 ]`**, **`[ +1 ]`**, and **`[ +5 ]`** buttons placed in a dedicated row cleanly separated from title and boundary descriptions, allowing fine-tuning boundary height directly within the modal without visual overlap.
- **Modal Action Buttons**:
  - **`[ ✔ Start Mining ]`**: Dispatches `StartMiningAreaPayload(pos1, pos2)` to the server, clears client corner selections, plays horn/chime audio, and initiates top-to-bottom excavation.
  - **`[ ⌫ Reset ]`**: Wipes `Pos1` and `Pos2` corners across tracker and scepter data components.
  - **`[ ✖ Cancel ]`**: Closes the modal safely without modifying selection corners.
- **Universal Blur Removal**: Overrides `applyBlur(float delta)` with a clean no-op (`{}`) to maintain 100% tactical situational awareness of the battlefield.

### 7.6 Reverse Topological Top-Down Execution & Zero Air-Mining Guarantee

- **Top-Down Reverse Demolition Sorting**: Scans the 3D bounding box and sorts tasks strictly from highest Y level down to lowest Y level ($Y_{\max} \to Y_{\min}$), ensuring structures and terrain are cleared safely top-to-bottom without collapsing overhead blocks.
- **Zero Air-Mining Guarantee**:
  - Air blocks are completely excluded from task generation.
  - When leasing tasks in `claimNextTask()` or ticking in `MinionBuildGoal`, any task position that is already air in the world is auto-completed immediately. Minions **never** pathfind to, hover at, or swing pickaxes at air blocks, and never play villager sounds at empty air.
- **Automatic Wireframe Dismissal & Celebration**:
  - The construction manager continuously monitors active dismantle sessions (`hasRemainingBlocksInWorld()`).
  - As soon as all destructible blocks in the selected volume are cleared, the session marks `COMPLETED`, broadcasts `EndConstructionSessionPayload`, plays challenge completion fanfare (`UI_TOAST_CHALLENGE_COMPLETE`), spawns celebratory particles (`HAPPY_VILLAGER`, `TOTEM_OF_UNDYING`), and **instantly dismisses the wireframe outline**.
- **Mining & Building Completion Symmetry (Perimeter Teleportation & Hold Position)**:
  - When mining operations conclude, minions receive identical post-completion treatment as builders:
    - **Perimeter Waypoints**: Waypoints are generated along the rim of the quarry ($Y_{\max} + 1$) or excavated ground footprint ($Y_{\min}$), ensuring stations are safely grounded above pits and caverns.
    - **Perimeter Teleportation**: Minions are distributed evenly along the perimeter and teleported directly to their assigned stations (`requestTeleport(wx, waypoint.getY(), wz)`).
    - **Beacon Fanfare**: Golden beacon particle beams (`END_ROD` + `GLOW`) and chime audio (`BLOCK_AMETHYST_BLOCK_CHIME`) erupt at every perimeter waypoint.
    - **Hold Position Defense**: Minions are automatically placed into defensive hold position (`isSitting() = true`, `setGuardAnchorPos(waypoint)`, `isSelected() = false`), standing guard at attention encircling the cleared quarry/site without wandering into pits or following uncommanded.
- **Anti-Bobbing Mining Flight & Uniform Hover Kinematics**:
  - Minions share the exact same 3D Arcane Levitation flight as builders during mining operations.
  - Hover stations are calculated at a uniform elevated altitude `targetPos.getY() + 1.25D` directly above the mined block (or `+0.05D` under tight ceilings), eliminating the vertical oscillation between trench floor and elevated air.
  - Mining swings freeze velocity completely (`setVelocity(0, 0, 0)`) during work ticks.
  - Zero gravity is maintained throughout the operation, preventing miners from falling when blocks beneath them are broken.
- **Dynamic Tool Equipping**: Automatically equips pickaxes for stone/brick/ore, shovels for dirt/gravel, and axes for wood.
- **Survival Direct Collection & Chest Logistics vs. Creative Zero-Drop Demolition**: In Survival mode, broken blocks are collected directly into minion inventories via `Block.getDroppedStacks` with `drop = false` rather than scattering across the ground. When inventory is full, minions deposit materials into nearby chests (up to 24 blocks) or craft and deploy autonomous supply depot chests. In Creative mode, blocks are cleared cleanly without spawning entity drops or deploying chests, preventing world and inventory clutter during large excavations.

### 7.7 Universal Block Type Fidelity Across Demolition (`MINE` Mode)

Area deconstruction and mining clear every block type within the selected 3D volume:
- **Solid Terrain & Masonry**: Excavates stone, dirt, sand, gravel, deepslate, ores, wood, and brick structures from top to bottom.
- **Transparent & Decorative**: Clears glass, panes, stairs, slabs, fences, gates, banners, signs, and decorative items.
- **Lighting & Fixtures**: Demolishes torches, lanterns, campfires, and overhead lights cleanly.
- **Containers & Storage**: Empties and recovers containers (chests, barrels, furnaces, hoppers) into builder backpacks and storage.
- **Multi-Part Component Clearance**: Breaks doors, beds, and tall double plants across both halves cleanly, preventing orphaned floating blocks or desynchronized world states.
- **Fluid & Liquid Demolition**: Drains standing water and lava pools cleanly (`setBlockState(Blocks.AIR)`), eliminating residual source blocks and preventing endless water flooding during excavations.
- **Loot-Table-Faithful Drop Collection**: All block breaks — both direct `MINE` dismantle tasks and autonomous quarrying/timber felling — use `Block.getDroppedStacks()` to compute drops against Minecraft loot tables. Silk-touch pickaxes yield ice/glass/ore, fortune pickaxes multiply gravel/flint/coal/diamond yields, and enchanted axes produce extra logs — exactly as the player expects.
- **4-Tier Bedrock Immunity**: Bedrock, barrier blocks, and unbreakable structures are 100% immune and preserved.

### 7.8 4-Tier Bedrock & Indestructible Immunity Safeguards

Guarantees minions never break bedrock, barrier blocks, or world boundaries:

1. **Session Generation Filter**: Omits blocks with hardness $< 0.0\text{F}$ or `Blocks.BEDROCK` during initial task queue generation.
2. **Task Readiness Check**: Re-verifies indestructible criteria before assigning tasks to minions.
3. **Execution Guard**: In `executeDismantleWork()`, if an indestructible block is encountered, block destruction is bypassed, an anvil clank SFX (`BLOCK_ANVIL_HIT`) plays with smoke particles, and the task marks complete safely.
4. **Zero-Scaffolding Architecture**: With builders utilizing 3D Arcane Levitation, temporary scaffolding generation and cleanup are completely retired, preventing any unintended block modifications or ground corruption.

---

## 8. Arcane Builder Levitation & Player-Like Ground Navigation

All ephemeral scaffolding and combat sapper goals (`MinionSapperGoal`, `TraversalScaffoldingManager`) have been completely retired. Minions utilize a clean dual-navigation model:

- **Builder Arcane Levitation**: Builders exclusively possess 3D Arcane Levitation flight and block phasing (`noClip`) during active construction and structure egress (`MinionBuildGoal`). They smoothly fly, hover at work stations, phase through ceilings/walls, and exit completed structures without scaffolding clutter.
- **Pure Ground Navigation for Non-Builders**: All other minions (Warriors, Sentinels, Rangers, Minion Followers) navigate exclusively on the ground via standard `MobNavigation`:
  - **Player-Like Step Height ($1.25\text{D}$)**: Enhanced step height allows minions to step over slabs, stairs, carpets, trapdoors, and 1-block steps without jumping or levitating.
  - **Natural Barrier Pathfinding**: Minions pathfind around 2-block obstacles, walls, and pits rather than attempting flight.
  - **Companion Catch-Up Teleportation**: If separated from their owner or leader and navigationally stuck in deep holes or behind walls for $\ge 40$ ticks ($2.0\text{s}$), followers automatically catch up via teleportation, preventing estrangement without aerial launches into the sky.
- **Universal Indoor Navigation Safeguards & Door Auto-Opening**:
  - Closed wooden doors and gates in a minion's path automatically open (`autoOpenNearbyDoors`), allowing fluid movement through doorways.
- **Builder & Miner Kinematics Isolation**:
  - `MinionEntity.tickUniversalArcaneLevitation()` returns immediately for all minions matching `MinionRole.BUILDER` (including `AUTO` minions adapting to construction/mining). Builders and miners are governed 100% exclusively by `MinionBuildGoal`, completely preventing universal obstacle clearance or hole/pit escape logic from launching workers into the sky toward the player.
- **Quarry Miner Fly-Away Elimination**:
  - **Inter-Task State Preservation**: When waiting for the next block lease in an active session, minions maintain `activelyBuilding = true`, freeze their velocity to `(0.0D, 0.0D, 0.0D)`, and hover in place rather than dropping state or triggering formation follow.
  - **Dismantle Kinematics**: Wall scrapes during deconstruction never impart upward rocket velocity; velocity dampening guides miners directly to elevated hover stations (`targetPos.getY() + 1.25D`).
  - **Quarry Stall Phase-Shift**: Collision stalls in pits immediately phase-shift directly to the hover station instead of falling back to ground navigation or searching for non-existent structure exit doors.
  - **Construction Proximity Gating**: Formation follow, catch-up teleports, leader follow, and patrol goals check `isMinionEngagedInConstruction(this.minion)` (within 48 blocks of an active session), preventing minions from abandoning active quarries.
- **Complete Fall Damage Immunity & Safe Descents**:
  - **Zero Fall Damage**: Minions working atop buildings or mining elevated ledges never take fall damage or play hurt sounds upon descending or falling (`handleFallDamage` and `damage(DamageTypes.FALL)` are completely negated during building, mining, levitating, exiting, or within a 100-tick grace window of construction activity).
  - **Controlled Landing Glide**: Airborne minions descending from completed work or heights execute `tickGentleDescent(serverWorld)` with an anti-gravity glide straight to solid ground with zero accumulated fall distance.

---

## 9. Advanced Mob Pathfinding Engine

### Scaffolding Pathfinding Evaluation (`MinionPathNodeMaker`)

Vanilla Minecraft's `LandPathNodeMaker` treats scaffolding as `PathNodeType.BLOCKED`, preventing mobs from climbing or traversing temporary platforms. The mod's custom path node maker re-evaluates vanilla scaffolding (`Blocks.SCAFFOLDING`):

- **Player-Placed Scaffolding Awareness**: Evaluates player-placed vanilla scaffolding as `PathNodeType.OPEN` (or `WALKABLE` when supported from below), allowing vertical ascent and horizontal span crossing across elevated platforms.
- **Elevated Platforms**: Evaluates standing on top of scaffolding as `PathNodeType.WALKABLE`, enabling fluid ground navigation across high-altitude platforms.

### Custom Navigation (`MinionNavigation`) & Universal Door Traversal

- Integrates `MinionPathNodeMaker` into a custom `MobNavigation` pipeline.
- Pre-configured with open and closed door traversal (`setCanPathThroughDoors(true)`, `setCanEnterOpenDoors(true)`).
- **Universal Door Traversal & Auto-Opening (`autoOpenNearbyDoors`)**:
  - Integrated directly into `MinionEntity.tick()` movement loops and `MinionBuildGoal`.
  - As minions walk or navigate towards targets, closed non-iron wooden doors, gates, and trapdoors within a 1.5-block bounding box are automatically opened (`DoorBlock.setOpen`), allowing smooth, uninhibited passage through doorway portals and residential entryways without pathing halts.
  - Complemented by `MinionBuildGoal.autoOpenNearbyDoors()` for builders moving materials through doorways.

### Interior Door & Portal Traversal

- Sets pathfinding penalties for `DOOR_OPEN`, `DOOR_WOOD_CLOSED`, `WALKABLE_DOOR`, and `TRAPDOOR` to `0.0F`.
- Leverages `LongDoorInteractGoal` and dynamic auto-opening to allow minions to open, walk through, and navigate doors smoothly without pathing stalls.

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

- `COMMAND_MODE`: Encodes active operational mode (`FOLLOW`, `STAY`, `MINE`, `BUILD`, `RECRUIT`, `PATHWAY`, `DESIGN`).
- `MINING_MODE`: Encodes the active mining sub-mode (`MiningMode.DIRECT` or `MiningMode.AREA`).
- `ACTIVE_BLUEPRINT`: Encodes active blueprint identifier string.
- `TARGET_SQUAD`: Encodes active squad channel filter (`ALL`, `ALPHA`, `BRAVO`, `CHARLIE`, `DELTA`).
- `STRUCTURE_ROTATION`: Encodes integer quadrant rotation index ($0 = 0^\circ, 1 = 90^\circ, 2 = 180^\circ, 3 = 270^\circ$).
- `TARGET_ROLE`: Encodes the optional target `MinionRole` archetype for channeled rally ring transfiguration.
- `DESIGN_POS1` & `DESIGN_POS2`: Encodes active spatial capture corner coordinates (`BlockPos`).
- `MINE_POS1` & `MINE_POS2`: Encodes active area mining boundary corner coordinates (`BlockPos`).
- `LEGACY_ARCHITECTURE_STYLE` & `LEGACY_BUILDING_SIZE`: Migration data components ensuring backward compatibility with older save files and player inventories, preventing Minecraft 1.21 item deserialization error logs.

### Client-to-Server (C2S) Payloads

1. **`UpdateScepterPayload`**: Synchronizes active command mode, selected blueprint, target squad channel, rotation index, optional target archetype role, and directive dispatch flags.
2. **`StartMiningAreaPayload`**: Dispatches 3D boundary mining area coordinates (`pos1`, `pos2`) to the server to initiate full top-down area excavation in `ConstructionManager`.
3. **`UpdateMinionConfigPayload`**: Updates an individual minion's role archetype and squad assignment.
4. **`MassRolePayload`**: Batch-assigns a role archetype to all selected minions (or all matching squad minions) within a 64-block radius.
5. **`TeleportMinionPayload`**: Recalls an individual minion or broadcast-teleports all squad minions to the player.
6. **`DismissMinionPayload`**: Decommissions an individual minion (`targetMinionId > 0`), all selected squad minions (`TARGET_SELECTED = -2`), or all owned minions (`TARGET_ALL = -1`) within 64 blocks, guarded by modal confirmation dialogs.
7. **`DeselectMinionsPayload`**: Clears active unit selection for an individual minion or the entire army.
8. **`RetreatPayload`**: Dispatches instant tactical panic retreat (`Keybind R`), resetting combat targets, canceling guard posts, and recalling all squad minions to formation at sprint speed.
9. **`AnchorConstructionPayload`**: Dispatches player-anchored construction or deconstruction requests with position (`BlockPos`), blueprint ID string, rotation index ($0\text{--}3$), and session mode (`BUILD` / `DISMANTLE`).
10. **`ModifyPatrolRoutePayload`**: Dispatches patrol route modifications (`Action: CREATE, ADD_WAYPOINT, REMOVE_WAYPOINT, CLEAR, SET_LOOP`), channel ID ($0\text{--}4$), and waypoint coordinate parameters.
11. **`ConfigurePatrolRoutePayload`**: Dispatches custom patrol route configuration updates (Action: `SAVE` / `DELETE`), route ID, custom display name, 24-bit RGB color, and `PatrolMode` (`LOOP` / `PING_PONG`).
12. **`CreateCustomBlueprintPayload`**: Transmits player-authored blueprints (`id`, `name`, `description`, `List<BlueprintBlock> blocks` with relative offsets and block states) captured in **`DESIGN`** mode to the server for validation, dynamic registry injection, world persistence, and multi-client broadcast sync.
13. **`DeleteCustomBlueprintPayload`**: Requests the deletion and unregistration of a custom blueprint by its unique identifier (`String blueprintId`), removing it from `CustomBlueprintManager`, `CustomBlueprintPersistentState`, and `BlueprintRegistry`, followed by immediate client synchronization.

### Server-to-Client (S2C) Payloads

1. **`SyncConstructionSessionPayload`**: Broadcasts the start, anchor coordinate, blueprint ID, rotation index, dismantle mode, and 3D bounding box dimensions (`sizeX`, `sizeY`, `sizeZ`) of an active construction session to tracking clients for persistent 3D holographic wireframe and ghost grid rendering.
2. **`EndConstructionSessionPayload`**: Broadcasts the termination (completion or cancellation) of a construction session so client wireframe renderers clear holographic geometry.
3. **`SyncPatrolRoutesPayload`**: Broadcasts the full synchronized set of world patrol routes across all dynamic color-coded channels to connecting and active players.
4. **`SyncCustomBlueprintsPayload`**: Transmits the complete catalog of saved custom `StructureBlueprint` records (`List<StructureBlueprint>`) using `BLUEPRINT_CODEC` to clients on world join, blueprint creation, or blueprint deletion, ensuring all player Command Hubs display up-to-date custom blueprints.

---

## 12. Survival vs. Creative Mode Economy & Mechanics

The commander's active game mode dynamically dictates minion logistics, resource consumption, and world interaction across building, mining, recruitment, and maintenance:

| Subsystem                           | Survival Mode (`/gamemode survival`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Creative Mode (`/gamemode creative`)                                                                                                                                                                                                                                                      |
| :---------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Structure Building (`BUILD`)**    | **Autonomous Logistics, Harvesting & Procurement**: Builders consume carried blocks, scavenge nearby chests within 12 blocks, transfer blocks via peer-to-peer beams from allies within 24m, autonomously quarry natural stone/deepslate, and harvest timber via bone-meal agro-forestry. When mob resources (wool, bones, slime, leather, etc.) are needed, builders commission nearby Warriors on tactical hunting contracts with automatic material synthesis. They self-craft replacement tools, collect deconstruction drops directly into inventory, and deposit 100% of excess materials into nearby or autonomous chests upon completion, leaving zero dropped items. (Only pauses with alert if non-harvestable materials are missing). | **Zero-Cost Free Placement & Zero-Drop Guarantee**: Builders place blocks freely and continuously without consuming items or requiring container inventories. Unneeded blocks are discarded directly from inventory slots (`inv.setStack(slot, ItemStack.EMPTY)`). Multi-part blocks (tall grass, flowers, beds, doors) are cleanly pre-cleared with zero dropped items, and post-session sweeps purge stray item entities within the bounding box, leaving zero dropped items on the ground. |
| **Area Mining (`MINE`)**            | **Direct Backpack Collection & Chest Logistics**: Excavated blocks are gathered directly into the builder's inventory (`drop = false`) instead of scattering onto the ground. When backpacks fill or sessions finish, builders offload into nearby containers or craft and deploy autonomous chests, gathering any loose ground items into storage. Items only drop on the ground as an absolute fallback if no chests exist and no wood is available. | **Zero-Drop Demolition**: Blocks are cleared without entity drops or chests, preventing world and inventory clutter during large excavations. |
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
- **Autonomous Mobilization**: When a new blueprint is anchored within 112 blocks horizontally and vertically, stationed builders automatically wake up (`setSitting(false)`, `setGuardAnchorPos(null)`) and mobilize to build immediately without requiring manual re-selection, providing comprehensive coverage for mega structures.

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

---

## 17. Visual Pathway Patrol System & Minion Escort Hierarchy

### Autonomous Waypoint Patrol Engine (`MinionPatrolGoal`, `PatrolRouteManager`, `PatrolRoute`)

- **Dynamic Custom Hex Colors & Route Channels**: Supports customizable dynamic patrol routes with arbitrary 24-bit hex colors (`#FFD700`, `#00E5FF`, `#00FF66`, `#B300FF`, `#FF2244`, or custom hex codes parsed via `PatrolRoute.parseHexColor`). Opening or selecting `PATHWAY` mode in the Command Hub GUI explicitly defaults the active route dashboard to **Route 1 (🟡 Gold `#FFD700`)** and synchronizes with the held scepter:
  - **Route 1**: 🟡 **Gold / Amber** (`0xFFD700`) — Citadel gates & primary courtyard (Default Route).
  - **Route 2**: 🔵 **Azure / Cyan** (`0x00E5FF`) — Castle ramparts & battlement parapets.
  - **Route 3**: 🟢 **Emerald Green** (`0x00FF66`) — Village perimeter & agricultural farms.
  - **Route 4**: 🟣 **Arcane Purple** (`0xB300FF`) — Nether portal hubs & subterranean quarry entries.
  - **Route 5**: 🔴 **Crimson Red** (`0xFF2244`) — Forward trenches, barbicans & perimeter killzones.
  - **Custom Routes**: Players can create, rename, recolor, and delete custom routes on the fly.
- **Dedicated Route Edit Modal Screen (`PatrolRouteEditModalScreen`)**:
  - Accessible directly by clicking the **`Edit`** button on any patrol route card in the Command Hub GUI.
  - Includes a text input field for route naming, a live color swatch preview square, a custom hex color code text input (`#RRGGBB` / `0xRRGGBB`), and 8 quick-select preset color palette swatch buttons (`#FFD700` Gold, `#00E5FF` Cyan, `#00FF66` Emerald, `#B300FF` Purple, `#FF2244` Crimson, `#FF8800` Blaze Orange, `#3366FF` Royal Blue, `#FF3399` Hot Pink) rendering their vibrant swatch colors with active selection highlight borders.
  - Modal action buttons: **`[ ✔ Save Route ]`**, **`[ ✖ Delete Route ]`**, and **`[ Cancel ]`**.
  - Deleting a route safely detaches all patrolling minions assigned to that route (`setPatrolRouteId(-1)`), sets them into a stationed holding position (`setSitting(true)`), and cleanses persistent world state.
- **Paginated Pathway Dashboard in Command Hub (`CommandScepterScreen`)**:
  - Features paginated route management (`PATHWAY_PAGE_SIZE = 4`) with **`[ ◀ Prev ]`** and **`[ Next ▶ ]`** navigation buttons and **`[ + New Route ]`** creation.
  - Per-route control row: **Select Route** (channel select), **Mode Toggle** (`Loop` vs `Ping`), **Edit** (opens configuration modal), **Clear** (empties waypoints), and **Del** (deletes custom route).
- **In-World 3D Holographic Wireframes & Vector Trajectories (`PathwayHologramRenderer`)**:
  - Clicking blocks while in `PATHWAY` mode anchors an exact bounding wireframe resting cleanly on top of each waypoint surface.
  - Floating, camera-aligned billboarding badges hover above each block displaying sequential numbered checkpoint tags: `[ 1 ]`, `[ 2 ]`, `[ 3 ]`, etc.
  - Continuous glowing directional 3D laser tether lines float directly on top of the block surfaces, connecting consecutive waypoints in order ($1 \to 2 \to 3 \to \dots \to 1$).
  - **Selective Scepter Illumination**: Outlines and trajectory lines render exclusively while holding the Command Scepter in **`PATHWAY`** mode, keeping the world clutter-free during regular gameplay. The active channel pulses with an energetic alpha glow.
  - **Tactile Waypoint Deletion**: Punching/attacking an existing waypoint with the Scepter in `PATHWAY` mode instantly removes it with smoke particles and a bass note. Right-clicking an existing waypoint toggles it off. If deleting the last waypoint empties the route, all minions assigned to that route are automatically unassigned and halted.
  - **World Save Persistence (`PatrolRoutePersistentState` & `PatrolRouteManager`)**:
    - **Native NBT World Storage**: All 5 route channels and waypoints are stored in native world save data (`data/minion_patrol_routes.dat`) and reload seamlessly when joining or reloading a save.
    - **Root Data Unwrap & Multi-Session Resilience**: Deserialization safely unpacks the outer `"data"` compound used by Minecraft's `PersistentStateManager`, ensuring routes are never wiped or lost across game restarts.
    - **Immediate Disk Flushing**: Placing, removing, clearing, or modifying waypoints triggers an immediate `stateManager.save()` flush to disk rather than waiting for server shutdown.
    - **Lifecycle & Join Synchronization**: Guaranteed initialization via `ServerLifecycleEvents.SERVER_STARTED` and `ServerWorldEvents.LOAD` (`.equals(World.OVERWORLD)`), coupled with automatic fail-safe initialization in `syncToPlayer` on connection.
  - **Fail-Safe Route & Stance Guards**:
    - **Empty Route Rejection**: Assigning minions to routes with 0 waypoints via GUI, direct right-click, or Banner of Courage is cleanly rejected with an informative chat alert.
    - **Autonomous Ghost Route Detachment**: If a route is cleared while minions are assigned, `MinionPatrolGoal` automatically self-detaches (`setPatrolRouteId(-1)`), preventing minions from ever freezing in an unpatrollable state.
    - **Stationed Post Precedence**: Stationed minions holding an anchor post or ground ping yield in `MinionPatrolGoal` to ensure they maintain their defensive posts.
    - **Direct Command Precedence**: Toggling stance with an empty hand (following or stationed) immediately clears active patrol routes and escort links.
    - **Circular Escort Prevention**: Self-escort and reciprocal loops (A escorts B while B escorts A) are automatically rejected and broken.
    - **Zero-Overlap Protection & Universal Cross-Channel Removal**: Waypoints are mutually exclusive across all 5 channels—a block coordinate can only belong to one route at a time. Right-clicking or punching an existing waypoint from ANY channel removes/undoes it from whichever route it belongs to without adding duplicates on top.
  - **Surface Block Type Fidelity**:
    - Waypoints anchor cleanly on **any** surface block type (full solid blocks, slabs, stairs, carpets, fences, walls, glass roofs, leaves, and iron bars).
    - Elevated surface holograms (`y + 0.15D`) and vector laser tethers float cleanly atop the exact surface profile without clipping or appearing buried.
- **Patrol Kinematics & Vigilant Checkpoint Scanning**:
  - **Sequential Order Traversal**: Minions follow each pathway tile placed by players from first placed `[ 1 ]` to last `[ N ]` in sequence.
  - **Smooth Traversal (No Intermediate Stopping)**: Minions smoothly advance from checkpoint to checkpoint without stopping along the trail.
  - **End-Only Sentry Linger**: Upon reaching the **end** of a pathway (at the final waypoint in `LOOP` mode, or at the terminal endpoints before reversing in `PING_PONG` mode), minions pause for **200–300 ticks (10 to 15 seconds)**, performing periodic head-turn scanning across horizontal angles to detect threats like vigilant perimeter guards. If waypoints are removed or modified while lingering, linger ticks immediately reset to transition smoothly.
  - **Closed Loop vs. Linear Ping-Pong Patrol (`PatrolMode`)**:
    - **`PatrolMode.LOOP`**: Connects sequential waypoints continuously, cycling from the final checkpoint straight back to point 1 ($1 \to 2 \to 3 \to 1$).
    - **`PatrolMode.PING_PONG`**: Traverses forward, and upon reaching the final checkpoint, reverses traversal direction back to the origin ($1 \to 2 \to 3 \to 2 \to 1$), omitting the return line across base territory.
  - **Patrol Breach Alarm & Sentry Mobilization (`MinionPatrolGoal.triggerBreachAlarm`)**:
    - When a patrolling sentry detects hostile targets crossing its route, it activates an emergency sentry breach protocol:
      - Operates completely silently with zero audio sounds (no horn, bell, or chime sounds when breaking from routes).
      - Bursts angry villager particles (`ParticleTypes.ANGRY_VILLAGER`) above the sentry's head.
      - Scans a 16-block radius and alerts nearby idle allied minions, instantly mobilizing them to engage the threat.
  - **Smart Combat & Task Resumption**: If a hostile mob enters detection range, a blueprint is placed nearby, or an ally requires healing, the minion yields patrol duties silently (Warriors fight, Builders construct, Sentinels heal) without any sound effects. Once the combat or task resolves, the minion snaps to the nearest waypoint along its route and resumes the patrol cycle seamlessly. Patrolling minions strictly follow their routes and never return to or follow the commander unless explicitly selected (which cleanly detaches them from their route).
  - **Unconstrained Terrain Scaling & Anti-Stall Ground Navigation**:
    - **Normal Terrain Descents (1–3 Blocks Deep)**: Minions traverse down steps, ledges, and slopes using normal Minecraft gravity without triggering Arcane Levitation or emitting purple portal particles.
    - **Multi-Point Footprint Contact (`hasSolidGroundBeneath`)**: When vaulting or ascending over obstacles > 1 block high (2-block ledges, fences, walls), the minion's entire base footprint (`x ± 0.25, z ± 0.25`) is evaluated, instantly restoring gravity and planting the minion firmly on solid ground as soon as it clears the ledge lip.
    - **Anti-Oscillation Forward Thrust**: Upward clearance maintains level forward propulsion across obstacle crests rather than dragging downward into the block edge.
    - **Watchdog Cooldown Protection**: 15–20 tick cooldown prevents infinite levitation re-trigger loops if forward progress is blocked.

### Minion Escort Hierarchy & Squad Leaders (`MinionFollowLeaderGoal`)

- **Commander Delegation & Bodyguards**: Minions can be assigned to follow and bodyguard other minions instead of the player commander.
- **Method 1: Shift-Punch to Designate Squad Leader (Instant Mass Escort Assignment)**:
  - Select one or more minions using the Command Scepter (individual punch or 90° cone mass sweep, even while in **PATHWAY** mode).
  - With the Command Scepter in hand, **Shift + Left-Click (Shift-Punch)** an owned minion:
    - The punched minion becomes the **Squad Leader**!
    - All selected minions are instantly assigned to escort the punched minion as their designated Squad Leader!
    - **Self-Follow Immunity**: A minion cannot follow itself; if the punched minion was already selected and following the player commander, it remains selected as the point-man squad leader following the commander, while all other selected minions become its bodyguards.
    - **Automatic Deselection & Mutual Exclusivity**: All assigned escort minions are automatically deselected from the player commander (`setSelected(false)`). They strictly follow their squad leader and will never follow the player and the minion simultaneously.
    - **Automatic Detachment Upon Following Master**: Whenever a minion is ordered to follow the player (via normal punch, 90° cone sweep, or empty-hand right-click), any active escort bond is automatically severed (`clearLeader()`), returning it immediately to direct player following.
    - **Goal Conflict Resolution**: `SentinelGuardGoal` and `MinionFormationFollowGoal` strictly yield whenever a minion has an active leader (`hasLeader()`), preventing bodyguards from rubberbanding back to the player's anchor position.
    - **Loud Resonant Chime Feedback**: Plays a loud dual-chime sound effect (`BLOCK_NOTE_BLOCK_CHIME` + `BLOCK_AMETHYST_BLOCK_RESONATE` at 1.8F volume) at the player's position along with a burst of emerald happy villager particles.
  - **Shift + Left-Click (Shift-Punch)** an escort minion with 0 minions selected to dissolve its escort bond directly, returning it to direct player command.
- **Method 2: Tactile Scepter Escort Priming (Single-Target Pairing)**:
  - With 0 minions selected, **Sneak + Left-Click** an owned minion with the Scepter to prime it as an escort (plays amethyst chime).
  - **Right-Click** another owned minion to bind the primed escort to that leader with a happy villager particle burst, loud chime, and automatic player deselection. Reciprocal escort loops are automatically prevented.
  - **Shift + Left-Click** again or right-click the same minion to dissolve the escort bond and return to player following.
- **Escort Arcane Tether Beam (`PathwayHologramRenderer`)**:
  - When holding the Command Scepter, an arcane particle vector beam connects each escort to its squad leader.
  - Features high-energy cyan-to-magenta gradient particles streaming along the tether vector, allowing commanders to visualize intricate command chains in real time.
- **Dynamic Multi-Unit Army Formation Stations (Any Escort Squad Size)**:
  - **No Matter the Squad Size**: Supports any number of escort minions (from 1 up to 20+ units). Rather than clustering or sharing the same offset, every escort unit calculates a distinct, non-overlapping military rank offset around the squad leader using `MinionFormationFollowGoal.resolveRank` and `calculateFormationStation`.
  - **Tactical Formation Offsets**:
    - **Sentinels**: Assume flanking bodyguard ranks on lateral shoulders and outer wings ($\pm 1.35\text{D}$ to $\pm 3.60\text{D}$, $+1.8\text{D}$ forward), staying in optimal range to cast *Aegis of Restoration* whenever their leader drops below 70% HP.
    - **Warriors**: Advance in straight frontline battle rows ($+4.0\text{D}$ forward, $-2.0\text{D}$ per subsequent rank), actively screening the leader in combat.
    - **Builders**: Trail in the protected rearguard support column ($-2.0\text{D}$ rearward).
  - **Surface Elevation & Heading Hysteresis**: Uses `resolveWalkableY` to anchor stations to valid walking surfaces on hills, stairs, and ledges. Anchors formation yaw relative to the leader to eliminate disorienting station spinning during small rotational adjustments.
  - **Emergency Teleportation**: If an escort is separated from their squad leader by >64 blocks, they immediately teleport to the leader with ender portal particles and SFX.
- **Dedicated Squad Leader Combat AI & Leash Enforcement**:
  - **Leader Target Sharing & Retaliation**: Escorts defend their squad leader directly: they target whatever strikes their leader (`TrackLeaderAttackerGoal`) and engage whichever enemy their leader attacks (`AttackWithLeaderGoal`), ignoring distant commander skirmishes.
  - **16-Block Combat Leash Override**: If an enemy lures an escort >16 blocks away from the squad leader (`COMBAT_LEASH_OVERRIDE_SQ = 256.0D`), combat aggro is instantly broken and the escort sprints back to formation.
  - **Post-Combat Return to Leader**: After clearing combat encounters, escorts sprint directly back to their squad leader (`returnToOwnerPostCombat`), never running to the commander.
  - **Wander AI Suppression**: `WanderAroundFarGoal` and stationary waypoint holding (`WaypointHoldGoal`) are strictly suppressed while a minion has an active squad leader.
- **Squad Patrol Synergy**:
  - Assigning a **Squad Leader** to a patrol route causes all follower bodyguards to march together along the route in full military escort formation, coordinating attacks and defense as a unified fireteam!

### Overhead Badge Status Indicators (`MinionOverheadBadgeFeatureRenderer`)

Minions project dynamic billboarding badges hovering above their nameplates:
- **Route Channel Badge**: Displays `[ 🟡 Route 1 ]`, `[ 🔵 Route 2 ]`, etc., tinted to the exact hex color of the assigned channel (only rendered when the assigned route contains active waypoints).
- **Escort Badge**: Displays `[ 🛡 Escort ]` when bound as a dedicated follower to another squad leader.
- **Auto Role Badge**: Displays `[ ⚙ AUTO: <Role> ]` in yellow, dynamically reflecting the autonomous agent's currently adapted role.

---

## 18. Autonomous Agent System (`MinionRole.AUTO` Dynamic Evaluation)

### Default Archetype & Dynamic Role Morphing Engine (`evaluateAutoRole`)

- **Default Role on Creation & Recruitment**: All minions dynamically spawned, summoned via egg/command, or recruited into thrall service via the Command Scepter (`RECRUIT` mode) now default to **`MinionRole.AUTO`** (`id = 3`, icon `⚙`, color `§e`).
- **Autonomous Tactical Adaptation**: Minions configured in Auto mode act as intelligent autonomous agents, dynamically re-evaluating their tactical environment every 20 ticks (1 second) and adapting into the archetype best suited for current conditions:

1. **Medical Emergency Assessment (Priority 1 -> Sentinel)**:
   - Evaluates the health of the player commander (within 16m) and all allied minions (within 16m).
   - If any friendly unit is wounded below 70% maximum health, the autonomous agent adapts into a **Sentinel**, prioritizing restorative healing via *Aegis of Restoration*.
2. **Combat Threat Assessment (Priority 2 -> Warrior)**:
   - Scans a 16-block radius for aggressive hostile entities (monsters, raiders, enemy targets) or engaged combat targets.
   - If threats are detected, the minion adapts into a **Warrior**, drawing weapons and executing coordinated melee grounding or ranged attacks.
3. **Civil Engineering & Blueprint Logistics (Priority 3 -> Builder)**:
   - If no immediate medical or combat threats exist, the minion inspects active construction or deconstruction sessions belonging to the commander within 128 blocks via `ConstructionManager.findNearestSessionForMinion`, or active block phasing.
   - If blueprint sessions exist, the agent adapts into a **Builder**, taking flight with Arcane Levitation to construct structures, quarry materials, harvest timber, or manage supply depots.
4. **Position Holding / Standing Guard (Priority 4 -> Sentinel)**:
   - If stationed on standing guard (`guardAnchorPos != null`) or sitting, the minion assumes a **Sentinel** defensive guard posture.
5. **Peacetime Escort & Marching Formation (Priority 5 -> Warrior)**:
   - When accompanying the commander peacefully with no active construction or immediate threats, the minion adapts into a **Warrior** escort, marching proudly in frontline battle ranks.

### Universal Equipment & Goal Compatibility

- **Adaptive Role Querying (`getEffectiveRole()` & `matchesRole()`)**: All AI goals (`MinionActiveTargetGoal`, `MinionRangedAttackGoal`, `SentinelHealAllyGoal`, `MinionBuildGoal`, `ConstructionManager`, `MinionFormationFollowGoal`) query `matchesRole()` and `getEffectiveRole()`. This allows `AUTO` minions to seamlessly satisfy role requirements, rank properly in military formation ranks, phase through blocks during egress, and participate in squad tasks.
- **Universal Auto-Equip**: Auto minions intelligently accept weapons and tools of all disciplines (swords, bows, crossbows, tridents, pickaxes, axes, shovels) while rejecting non-weapon items in the main hand. Auto minions never disarm valid weapons.

---

## 19. Dual-Tier Panic Retreat & Emergency Citadel Call

The tactical retreat system (`RetreatPayload`, `ExampleModClient`, `CommandScepterItem`) provides two distinct tiers of emergency recall:

### Tier 1: Tactical Squad Retreat (Quick `R`)
- **Execution**: Press **`R`** while holding the Command Scepter.
- **Scope**: Affects units belonging to the active squad within **64 blocks** (strictly preserving units assigned to patrol routes).
- **Action**:
  - Clears hostile combat targets (`setTarget(null)`).
  - Dismisses 3D holographic wireframes (`ClientConstructionTracker.clear()`).
  - Cancels active construction sessions for the commander.
  - Recalls units directly into their **structured formation stations** (Warriors front, Sentinels mid, Builders rear) calculated via `calculateFormationStation` rather than crowding onto a single block.
- **Feedback**: Sounds a warning retreat bell (`SoundEvents.BLOCK_BELL_USE`) and displays action bar notification.

### Tier 2: Emergency Citadel Call (`Shift + R`)
- **Execution**: Press **`Shift + R`** while holding the Command Scepter.
- **Scope**: Fortress-wide emergency muster covering all owned minions within **128 blocks**.
- **Action**:
  - Unbinds **all minions from patrol duty** (`patrolRouteId = -1`).
  - Overrides all stationary hold postures and sitting positions.
  - Clears all combat targets and breaks worker focus across all squads.
  - Recalls every thrall on the map back to the player's immediate defense.
- **Feedback**: Sounds a resounding **Raid Horn** blast accompanied by a clanging **Iron Bell** (`SoundEvents.ITEM_GOAT_HORN_SOUND_0` and `SoundEvents.BLOCK_BELL_USE`), bursts smoke and portal particles, and displays urgent banner: `§c🚨 CITADEL CALL! All 128m units abandoning posts and rallying to commander!§r`.

---

## 20. Custom Blueprint Catalog Lifecycle & Decommissioning Safeguards

### 20.1 Pure Custom Blueprint Catalog Architecture
The construction system relies 100% on player-authored custom blueprints captured in **`DESIGN`** mode:
- **`BlueprintRegistry.getAll()` & `getActiveCatalog()`**: Returns exclusively custom blueprints from `CUSTOM_REGISTRY`. All legacy hardcoded presets have been completely retired from the catalog.
- **Immediate Catalog Sync**: Newly captured blueprints appear immediately in the Command Hub catalog across all connected players.
- **Empty State Guidance**: On fresh worlds without custom blueprints, the catalog renders a clean guidance banner directing players to switch to **`DESIGN`** mode to capture their first structure.

### 20.2 Custom Blueprint Deletion & Elevated Confirmation Modal
- **Catalog Delete Action (`[✕]`)**: Every custom blueprint card renders a distinct red `[✕]` delete button on the right side of its slot card.
- **In-Screen Confirmation Dialog & Elevated Z-Plane Layering**:
  - Clicking `[✕]` prompts a confirmation dialog (`ConfirmationType.DELETE_BLUEPRINT`) displaying the target blueprint's name.
  - **Elevated Z-Plane Layering (`Z = 400.0F`)**: To eliminate DrawContext text batching bleed-through where background card labels or button text render in front of dark overlay rectangles, the confirmation modal is isolated within `context.getMatrices().push(); context.getMatrices().translate(0, 0, 400.0F); ... context.getMatrices().pop();`. This guarantees the modal backdrop, warning typography, and action buttons render strictly on top of all underlying GUI widgets.
  - The commander can click **Confirm Delete** to proceed or **Cancel** (or press **`ESC`**) to dismiss the modal without action.
- **Two-Way Server Synchronization**:
  - Clicking confirm sends `DeleteCustomBlueprintPayload` via client networking.
  - The server unregisters the blueprint from `BlueprintRegistry` and `CustomBlueprintPersistentState`, persists changes to `data/minion_custom_blueprints.dat`, and broadcasts `SyncCustomBlueprintsPayload` to all clients.

### 20.3 Minion Decommissioning Safeguards (Selected vs. All & Modal Layering)
- **Accident Prevention**: In previous iterations, clicking the Command Hub dismiss button immediately dismissed minions. Now, clicking **Dismiss / Destroy** opens a confirmation modal (`ConfirmationType.DISMISS_MINIONS`) rendered on the elevated `Z = 400.0F` matrix layer.
- **Contextual Scope Selection**:
  - **Minions Selected**: If 1 or more minions are highlighted, the modal offers **Destroy Selected** (only decommissions the highlighted units) and **Destroy All** (destroys all owned minions in range).
  - **No Minions Selected**: The modal offers **Destroy All** with an explicit warning and a **Cancel** button.
- **Minion Screen 2-Step Confirmation**: In `MinionScreen`, the **Destroy Minion** button requires a two-step confirmation (`confirmingDestroy` flag). The first click turns the button amber with text `Confirm Destroy?`; a second click within the same view executes the dismissal.
- **Networking Protocol Expansion**:
  - `DismissMinionPayload` supports `TARGET_SELECTED = -2` and `TARGET_ALL = -1`.
  - When `TARGET_SELECTED` is received, `ModNetworking.handleDismissMinion` filters owned alive minions by `isUnitSelected()`, ensuring unselected units remain active and unaffected.

### 20.4 Responsive Mode Description Banner & Auto-Fitting Box Bounds
- **Adaptive Text Layout & Box Bounds**: In `CommandScepterScreen`, the mode description banner renders within a bounded 144px tray ($startX + 14$ to $startX + 158$). In modes with bottom controls (`BUILD` and `DESIGN`), the single-line banner (`§fMode §8| §7Description`) calculates raw text width and applies proportional dynamic scaling (`fittedScale`) whenever text exceeds the 136px interior limit, ensuring description strings never clip or overflow outside the border.
- **Concise DESIGN Mode Formatting**: Features tailored, compact banner text (`§dDesign §8| §7Capture spatial volume.`) and accurate sub-header instructions (`§8[L-Click: Set Pos1/Pos2 | R-Click: Capture]`), eliminating line wrapping artifacts and maintaining clean visual separation from active catalog cards and corner management buttons.

---

## 21. In-World Spatial Blueprint Capture ('DESIGN' Mode)

The **In-World Spatial Blueprint Capture** system (`CommandMode.DESIGN`, `BlueprintCaptureModalScreen`, `CaptureSpatialBlueprintPayload`, `ClientDesignCaptureTracker`) enables commanders to turn any existing player-built house, fortification, watchtower, or decorative monument directly into an autonomous minion construction blueprint without leaving the Minecraft world or recreating voxels manually in a separate editor.

```
                              [In-World Target Structure]
                                           │
                                  [First Left-Click]
                                           ▼
                              [Set Corner Pos1 (p1)]
                              • Vibrant Magenta Anchor Cube
                              • Chime SFX + Sparkle Particle Burst
                              • Actionbar: §d✦ Design Pos1: [X, Y, Z]
                                           │
                                           ▼
                         [Aiming & Raycasting Candidate Target]
                         • Live Ghost Bounding Box from Pos1 to Raycast Hit
                         • Real-Time Holographic Ghost Grid Block Outlines
                                           │
                                 [Second Left-Click]
                                           ▼
                              [Set Corner Pos2 (p2)]
                              • Completes Spatial Volume
                              • Chime SFX + Sparkle Particle Burst
                              • Actionbar: §d✦ Design Pos2: [X, Y, Z]
                                           │
                                           ▼
                 [3D Real-Time Holographic Ghost Grid & Bounding Box]
                 • Translucent Magenta Pulsing Wireframe Bounding Prism
                 • Semantic Color-Coded In-World Ghost Grid Block Outlines:
                   - 🚪 Doors: Bright Emerald Green (2-block portal box)
                   - 🏮 Lighting: Warm Amber Gold (torches, lanterns)
                   - 📦 Utilities/Containers: Arcane Purple (beds, chests, tables)
                   - 🏠 Roofing/Stairs: Soft Ice Blue (stairs, trim)
                   - 🧱 Structural Walls/Ground: Vibrant Cyan-Magenta Grid
                 • Camera-Facing Dimension Badge (W x H x D | Voxel Count)
                 • Corner Marker Cubes at Vertices
                                           │
                                           ▼
                  [Right-Click / Sneak + Right-Click: Capture Modal]
                  • Opens BlueprintCaptureModalScreen GUI
                  • Blueprint Name & Description Fields
                  • Real-Time Solid Block Counter & Height Steppers
                                           │
                                           ▼
                 [Server-Side Non-Destructive Ingestion Engine]
                 • Samples BlockView without destroying terrain
                 • Filters out air/void voxels
                 • Normalizes coordinates: Min Corner (minX, minY, minZ) -> (0, 0, 0)
                 • Deterministic Bottom-Up Topological Sorting
                 • Volume Limits Safeguard: Max 64x64x64 & 65,536 blocks
                                           │
                                           ▼
                 [Global Sync & Native World Save Persistence]
                 • Saves to data/minion_custom_blueprints.dat
                 • Broadcasts SyncCustomBlueprintsPayload to all clients
                 • Immediately selectable in BUILD mode & Command Hub!
```

### 21.1 Sequential Left-Click Corner Selection & Invariance

Commanders select the 3D spatial boundary volume using streamlined, single-button sequential left-clicks in **`DESIGN`** mode:

- **Streamlined Left-Click Corner Progression & Unified Authoritative Click Interception**:
  - **Single Authoritative Left-Click Registration**: Left-click events in `DESIGN` mode are intercepted via `AttackBlockCallback` (for targeted blocks), `AttackEntityCallback` (for targeted entities), and `attackKey` raycasting in `ExampleModClient` (for extended reach up to 96 blocks). All triggers funnel through `CommandScepterItem.handleDesignClick`.
  - **Double-Stepping Elimination & Key Queue Draining (`DESIGN_CLICK_CONSUMER`)**:
    - *Prior Flaw*: Previously, left-clicking block $B$ to set `Pos2` fired `AttackBlockCallback` (which completed selection $A \to B$) followed immediately by `client.options.attackKey.wasPressed()` in client tick within the same frame. The second event saw a completed selection and instantly began a new selection cycle with $pos1 = B, pos2 = null$, which immediately collapsed the preview to a candidate box between $B$ and $B$ ($1 \times 1 \times 1$).
    - *Unified Fix*: Whenever `AttackBlockCallback` handles a design click, it immediately calls `CommandScepterItem.DESIGN_CLICK_CONSUMER.run()`. This consumes all pending GLFW `attackKey.wasPressed()` queue events, records `lastDesignClickTick`, and sets a 150ms timestamp debounce window (`lastDesignClickTime`). The client tick handler checks these guards and drains any leftover events, ensuring exactly **one** `stepCorner()` invocation per physical click.
  - **1st Left-Click (Corner 1 / `Pos1`) & Ground Offset Protection**: Left-clicking any block sets `Pos1`. When clicking the top face of a ground block, `Pos1` is automatically anchored on top of the clicked block (`pos.offset(direction)` / `pos.up()`). This ensures the bounding box begins directly at floor level, cleanly excluding the dirt/grass terrain beneath the structure. A vibrant magenta anchor box (`0xFFFF20D8`) marks the coordinate, sparkle particles (`ParticleTypes.END_ROD`) erupt, a note block chime sounds (`SoundEvents.BLOCK_NOTE_BLOCK_CHIME` at 1.2F pitch), and the action bar confirms: `§d✦ Design Pos1: §f[X, Y, Z]`.
  - **Aiming Candidate Volume Preview**: As the player aims their crosshair around the world with `Pos1` set, the engine dynamically renders a candidate bounding box and live holographic ghost grid blocks spanning from `Pos1` to the targeted block (offset by hit face).
  - **2nd Left-Click (Corner 2 / `Pos2`)**: A second left-click on the opposite diagonal block automatically sets `Pos2`, completing the horizontal footprint and spatial volume. Sparkle particles erupt, a resonant note block chime sounds (`1.4F` pitch), and the action bar confirms: `§d✦ Design Pos2: §f[X, Y, Z]`.
  - **Subsequent Left-Clicks (Cycle / Reset)**: If both `Pos1` and `Pos2` are already defined, the next left-click automatically starts a new selection cycle: it sets `Pos1` to the clicked block and clears `Pos2`, enabling rapid re-selection without needing a separate reset action.
- **Direct Right-Click Capture Trigger**:
  - Once corners (`Pos1` and `Pos2`) are established, a standard **Right-Click** on the ground (or in the air) without needing Shift immediately launches the `BlueprintCaptureModalScreen` design modal to name, describe, and save the blueprint into the active catalog.
  - **Shift + Right-Click** also acts as a modal launcher when corners are active, or opens the Command Hub when no corners are selected.
- **Corner Invariance & Diagonal Independence**:
  - The bounding region is calculated analytically via $X \in [\min(x_1, x_2), \max(x_1, x_2)]$, $Y \in [\min(y_1, y_2), \max(y_1, y_2)]$, and $Z \in [\min(z_1, z_2), \max(z_1, z_2)]$.
  - Setting `Pos1` and `Pos2` in any diagonal permutation (e.g. top-north-west to bottom-south-east, or bottom-north-east to top-south-west) produces an identical normalized bounding volume.
- **Bidirectional Corner Clearance & Pos1 Reset Guarantee**:
  - **Shift + Left-Click**: Punching or clicking with Shift held in `DESIGN` mode (targeting a block or open air) executes `CommandScepterItem.handleDesignReset()`, immediately wiping both `Pos1` and `Pos2`, playing `BLOCK_NOTE_BLOCK_BASS`, and sending actionbar confirmation.
  - **GUI Reset Actions**: Clicking **`⌫ Reset`** in `CommandScepterScreen` or **Discard** in `BlueprintCaptureModalScreen` synchronizes the reset across both `ClientDesignCaptureTracker` and the held scepter item stack data components (`ModDataComponents.DESIGN_POS1` and `DESIGN_POS2`).
  - **Clean Cycle Guarantee**: Clearing corners resets the stepper state so that the very next left-click is mathematically guaranteed to register as `Pos1` (Step 1) rather than skipping or retaining previous corner coordinates.

### 21.2 Real-Time 3D Holographic Volume Preview & Ghost Grid Rendering (`BlueprintHologramRenderer`)

Whenever one or both corners are defined and the player holds the Command Scepter in `DESIGN` mode, the client rendering engine projects an in-world preview with full ghost grid structure rendering:

- **In-World Ghost Grid Structure Rendering (`renderDesignGhostGrid`)**:
  - Scans all non-air blocks within the spatial volume in real time and projects semantic color-coded 3D ghost grid block boxes around the targeted structure and ground:
    - **🚪 Doors & Entrances**: Bright Emerald Green (`0.0F, 1.0F, 0.53F`) full 2-block tall portal boxes.
    - **🏮 Lighting & Illumination**: Warm Amber Gold (`1.0F, 0.80F, 0.0F`) around lanterns, torches, wall torches, campfires, glowstone, sea lanterns, froglights, redstone lamps, and end rods.
    - **📦 Containers, Beds & Workstations**: Arcane Purple (`0.70F, 0.25F, 1.0F`) around chests, trapped chests, barrels, furnaces, blast furnaces, smokers, crafting tables, anvils, smithing tables, enchanting tables, brewing stands, ender chests, hoppers, dispensers, droppers, shulker boxes, and beds.
    - **🏠 Roofing, Stairs & Slabs**: Soft Ice Blue (`0.45F, 0.75F, 1.0F`) around stair blocks and slab trim.
    - **🛡 Defenses, Walls, Fences & Gates**: Arcane Orange-Gold (`1.0F, 0.60F, 0.15F`) around stone walls, fences, fence gates, and iron bars.
    - **🪟 Windows & Glass**: Crystal Cyan (`0.30F, 0.95F, 0.90F`) around glass blocks, stained glass, glass panes, and tinted glass.
    - **🧱 General Walls, Ground & Foundation**: Vibrant Arcane Cyan-Magenta Grid (`0.85F, 0.35F, 1.0F`) around all structural masonry, solid blocks, and terrain foundation blocks.
  - Ghost grid outlines render both during live candidate aiming (semi-transparent preview with vertex accent boxes stretching from `Pos1` to crosshair block) and when the completed volume (`Pos1` + `Pos2`) is confirmed.
- **Dynamic Candidate Bounding Box & Aiming Guides**:
  - While aiming with `Pos1` set, the engine renders an expanding 3D candidate bounding box with 8 gold vertex corner accent cubes (`0xFFFFD700`), a cyan crosshair target box (`0x33E6FF`), and live candidate ghost grid outlines scaled with 65% opacity.
- **Active Corner Highlight Markers**:
  - Selected `Pos1` (Magenta `0xFFFF20D8`) and `Pos2` (Cyan `0x33E6FF`) corner blocks are enclosed in vibrant, glowing wireframe anchor boxes with 8 gold vertex corner accent cubes.
- **Translucent Pulsing Bounding Box**:
  - When both corners are established, the engine renders a full 3D wireframe prism enclosing the entire volume with a pulsing magenta/purple arcane tint (`0xFFD044D0`).
- **In-World Dimension & Voxel Count Badge**:
  - Renders a camera-facing billboarding text badge above the center of the bounding volume displaying:
    $$\text{Dimension: } W \times H \times D \quad | \quad \text{Volume: } V \text{ voxels}$$
  - Turns warning yellow if dimensions approach limits, and red if bounds exceed the maximum spatial threshold.

### 21.3 In-World Real-Time Height Adjustment (No Blocks Required)

To eliminate the frustration of having to pillar up with temporary dirt or scaffolding blocks just to click an air block at the roof level, `DESIGN` mode features real-time in-world height adjustment:

- **Mouse Scroll Interceptor (`MouseMixin`)**:
  - Injected directly into `net.minecraft.client.Mouse.onMouseScroll`.
  - When holding the Command Scepter in `DESIGN` mode with **`Ctrl`** held:
    - Scrolling **Up** raises the top boundary ($Y$-max) by +1 block.
    - Scrolling **Down** lowers the top boundary ($Y$-max) by -1 block.
    - Holding **`Shift` + `Ctrl` + Scroll** fast-steps the height by $\pm 5$ blocks.
    - Consumes the scroll event (`ci.cancel()`), preventing hotbar item cycling so the player remains holding the Scepter.
- **Dynamic Clamping & Feedback**:
  - Clamped safely between minimum 1 block and maximum 96 blocks (`ClientDesignCaptureTracker.MAX_HEIGHT`).
  - Actionbar HUD displays live height updates: `§d✦ Design Box Height: §fX blocks §8(Y: minY → maxY) §8| §7Dimensions: WxHxD (Vb)`.
  - Emits crisp note block chime audio feedback (`SoundEvents.BLOCK_NOTE_BLOCK_HAT`).
- **Keyboard Fallback for Trackpad Users**:
  - Handled in `ClientTickEvents.END_CLIENT_TICK`:
  - Pressing **`]`** (Right Bracket) or **`Page Up`**: Raises height by +1 block (+5 with Shift).
  - Pressing **`[`** (Left Bracket) or **`Page Down`**: Lowers height by -1 block (-5 with Shift).
- **Modal Stepper Fine-Tuning**:
  - Inside `BlueprintCaptureModalScreen`, interactive **`[ -5 ]`**, **`[ -1 ]`**, **`[ +1 ]`**, and **`[ +5 ]`** stepper buttons allow commanders to fine-tune the structure height directly inside the capture dialog before saving.

### 21.4 In-World Blueprint Capture Modal (`BlueprintCaptureModalScreen`)

Performing **Right-Click** (or **Sneak + Right-Click**) on the ground or in the air with the Scepter in `DESIGN` mode when corners are set opens the **Capture Custom Blueprint** modal interface:

- **Modal Controls**:
  - **Blueprint Name Field**: Interactive text input for the user-facing blueprint title (defaults to `Custom Blueprint`).
  - **Description Field**: Optional text field (empty by default; no pre-filled boilerplate text to delete; leaving empty never drops blocks or affects compilation).
  - **Real-Time Solid Block Counter**: Dynamic detection displaying the exact count of solid, non-air blocks within the selection, with instant alerts if all selected voxels are air.
  - **Dedicated Height Stepper Row**: Interactive `[ -5 ]`, `[ -1 ]`, `[ +1 ]`, `[ +5 ]` stepper buttons placed in a dedicated row cleanly isolated from modal title, coordinates, and volume descriptions, preventing any visual overlap.
  - **Capture & Save Button**: Compiles the bounding volume, validates spatial limits, and dispatches `CaptureSpatialBlueprintPayload` to the server.
  - **Clear Corners Button**: Clears active corner coordinates without leaving the screen.

### 21.4 Coordinate Normalization & Air Block Exclusion Engine

When compiling captured blocks into a `StructureBlueprint`, the engine applies rigorous coordinate transformations:

1. **Origin Shifting (Coordinate Normalization)**:
   - Evaluates the minimum corner coordinate $\mathbf{p}_{\min} = (\min X, \min Y, \min Z)$.
   - Translates every captured block position $\mathbf{p} = (x, y, z)$ into local blueprint space:
     $$\mathbf{p}_{\text{offset}} = (x - \min X, \ y - \min Y, \ z - \min Z)$$
   - The lowest foundation block is guaranteed to align precisely with local origin $(0, 0, 0)$, enabling flawless placement across any world location, altitude, or negative coordinate space ($Y < 0$ in the deepslate layer).
2. **Air & Void Block Filtering**:
   - Iterates through the 3D volume and queries `BlockState.isAir()`.
   - All air, cave air, and void spaces are filtered out entirely, preventing minions from spending time attempting to place air blocks during construction.

### 21.5 Deterministic Bottom-Up & Reverse Top-Down Topological Sorting

Captured structures are topologically sorted via `BlueprintBlock.compareTo` to guarantee structural buildability and dismantling safety:

1. **Bottom-Up Construction Sorting (`SessionMode.BUILD`)**:
   - **Effective Vertical Layer ($Y$)**: Lower foundation blocks ($Y = 0$) are placed before walls ($Y = 1, 2$), which are placed before ceiling and roof structures ($Y = 3, 4$).
   - **Hanging Block Dependencies**: Blocks with hanging states (lanterns, chains, weeping vines) have their effective sorting layer shifted upward ($Y_{\text{eff}} = Y + 1$), guaranteeing their supporting ceiling blocks are constructed before the hanging fixtures are placed.
   - **Radial Manhattan Distance Core Ordering**: For blocks on the same vertical layer, blocks closest to the structural center ($|X| + |Z|$) are placed before outer walls and exterior buttresses, ensuring strong structural core stabilization.
2. **Top-Down Reverse Demolition Sorting (`SessionMode.DISMANTLE`)**:
   - In dismantling mode, task queues are reversed topologically ($Y_{\text{apex}}$ first, foundations last).
   - Hanging fixtures and upper roofs are dismantled before supporting walls or foundation pillars are broken, preventing floating block collapses.

### 21.6 Spatial Safeguards & Limits

- **Maximum Dimension Clamp (`MAX_SPATIAL_DIMENSION = 64`, `MAX_SPATIAL_HEIGHT = 96`)**:
  - Horizontal footprint length and width ($X$ and $Z$) cannot exceed 64 blocks ($64 \times 64$).
  - Vertical structural height ($Y$) can reach up to **96 blocks tall**, enabling capture of towering cathedrals, grand watchtowers, and mega structures without clipping.
- **Maximum Voxel Volume Limit (`MAX_SPATIAL_VOLUME = 393,216`)**:
  - Total bounding volume $(sizeX \times sizeY \times sizeZ)$ accommodates up to 393,216 voxels (supporting a full $64 \times 96 \times 64$ mega build), safeguarding server tick performance and network packet buffers.
- **Safe Out-of-Bounds Rejection**: Attempts to capture regions exceeding these thresholds display informative warning alerts on the client action bar without server desynchronization.

### 21.7 Server Synchronization & World Save Persistence

- **Networking Protocol**:
  - Dispatches `CaptureSpatialBlueprintPayload` (or `CreateCustomBlueprintPayload`) over Fabric C2S networking.
  - Server verifies ownership, samples world blocks non-destructively, registers the blueprint into `CustomBlueprintManager`, and saves it to `data/minion_custom_blueprints.dat`.
- **Global Broadcast**:
  - Synchronizes the new blueprint to all connected players via `SyncCustomBlueprintsPayload`.
  - The captured blueprint instantly appears in the Command Hub's **Blueprint Catalog** and is ready for immediate deployment in `BUILD` mode!

---

## 22. Unified Surface Anchoring Contract & Hollow Grid Mechanics

A fundamental design principle of **Sovereign: Arcane Strategy & Engineering** is the **Unified Surface Anchoring Contract** governing how hollow grid borders, wireframe holograms, spatial corner markers, and tactical beacons interact with terrain and block surfaces across all operational modes (`BUILD`, `MINE`, `DESIGN`, `PATHWAY`, and RTS Waypoint Pings).

### 22.1 The Core Surface Anchoring Formula

Across all client raycasting, tactical camera targeting, item interactions, and server-side packet handlers, target coordinates are computed using the canonical surface anchoring formula:

```java
BlockState state = world.getBlockState(hitPos);
BlockPos anchorPos = state.isReplaceable() ? hitPos : hitPos.offset(hitSide);
```

```
                                  [Raycast Hit on Target]
                                             │
                                  (Inspect BlockState)
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
             [state.isReplaceable()]                     [!state.isReplaceable()]
             • Air / Cave Air                            • Solid Terrain (Stone/Dirt)
             • Short / Tall Grass, Flowers               • Architectural Masonry & Bricks
             • Snow Layers, Ferns, Vines                 • Wooden Planks, Logs & Pillars
             • Water, Lava & Fluids                      • Slabs, Stairs, Glass & Walls
                       │                                           │
                       ▼                                           ▼
             [anchorPos = hitPos]                     [anchorPos = hitPos.offset(hitSide)]
             • Occupies target voxel                  • Sits cleanly on target surface
             • Replaces vegetation/fluids             • Never embeds inside solid block
             • Zero skyward floating                  • Starts precisely at floor plane
```

### 22.2 Mode-by-Mode Architectural Invariants

| Operating Mode / Feature | Solid Block Surface (`!isReplaceable()`) | Replaceable Block (`isReplaceable()`) | Visual & Kinematic Invariant |
| :--- | :--- | :--- | :--- |
| **`BUILD` Mode Placement & Preview** | Offsets along targeted face normal (`pos.offset(side)`). Blueprint foundation sits cleanly atop the floor surface. | Anchors directly at `pos`. Foundation replaces grass, snow layers, or water directly without floating 1 block in the air. | Neon cyan wireframe hologram and ghost blocks align 100% with the cursor and terrain surface. |
| **`MINE` Mode Area Clearance** | **DIRECT**: Anchors directly at `clickedPos` volume. **AREA**: Corner selections `Pos1`/`Pos2` anchor on target face normal (`pos.offset(side)`). | **DIRECT**: Anchors at `clickedPos`. **AREA**: Corner anchors at `pos` replacing surface vegetation. | Fiery orange/amber wireframe encloses the actual structure or 3D bounding volume to excavate downward cleanly without floating gaps. |
| **`DESIGN` Mode Spatial Capture** | `Pos1` / `Pos2` corner selections and candidate aiming offset by hit face (`pos.offset(direction)` or `pos.up()`), starting directly at the bottom floor level. | Anchors directly at `pos`, replacing surface foliage without jumping upward. | Ground terrain (dirt, stone) beneath floorboards is cleanly excluded from the captured custom blueprint bounding box. |
| **`PATHWAY` Patrol Waypoints** | Waypoint box rests on top of the surface (`pos.offset(side)` or `y + 0.15D`), billboarding badge hovers above, and vector lasers connect at surface level. | Anchors at `pos`. Replaceable vegetation (tall grass, flowers, water) does not push waypoint wireframe or laser an extra block into the sky. | Luminous 3D laser tether lines float directly on top of walkable surfaces without clipping or burying. |
| **RTS Waypoint Pings** | Marker beacon (`END_ROD` + `GLOW`) drops on top of solid surface; minions station upright at surface elevation. | Marker beacon drops at `hitPos`; minions step cleanly onto ground level. | Units auto-deselect upon arrival and hold station in the unified Stationed position (`holdingPosition == true`). |

### 22.3 Cross-Cutting Integrity & Quality Invariants

- **Zero Parallax Drift**: Raycasting initiates from the player's true eye coordinates along the camera look vector through the center-screen crosshair, matching server-side hit results up to 96 blocks away.
- **Fluid & Vegetation Transparency**: Non-solid replaceable blocks (water, lava, tall grass, large ferns, peonies, snow layers) never cause hollow grid bounding boxes or tactical waypoints to lift into the air.
- **Zero Embedding**: Solid surfaces never cause blueprints, waypoints, or spatial corners to embed inside solid stone, ensuring all multiblock structures and tactical routes maintain crisp surface-level fidelity.




