# 📜 Overlord's Command Guide

A comprehensive, quick-reference manual for all minion commands, controls, squad directives, and scepter abilities.

---

## ⚡ Quick Controls Cheat Sheet

| Input                              | Target / Context         | Action                                                                                                                                                          |
| :--------------------------------- | :----------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Right-Click** _(Quick Tap)_      | Hostile Mob              | **Focus-Fire Attack Ping**: Selected minions charge and attack this entity.                                                                                     |
| **Right-Click** _(Quick Tap)_      | Ground Block (up to 64m) | **RTS Waypoint Ping**: Deploys minions into ranked army battle lines and automatically puts them into a **Stationed** position (`holdingPosition`), auto-deselecting them. (In **BUILD** mode: anchors construction; in **MINE** mode: anchors dismantle session). |
| **Right-Click** _(Quick Tap)_      | Owned Minion             | **Individual Follow**: Toggles follow order on this specific minion. If stationed (via waypoint or manual sit/guard), a **single click** immediately orders them to follow. |
| **Right-Click** _(Quick Tap)_      | Ground in **BUILD** Mode | **Anchor Construction**: Places the selected blueprint at the targeted ground block (seamless from elevated tactical camera or ground up to 96m away). Builders construct persistently with zero timeout halts until 100% complete. |
| **Right-Click** _(Quick Tap)_      | Sky in **BUILD** Mode    | **Cycle Blueprint**: Cycles to next structure preset (Watchtower, Cottage, etc.) when looking into open air or sky.                                            |
| **Right-Click** _(Quick Tap)_      | Ground in **MINE** Mode  | **Anchor Dismantle**: Initiates structure deconstruction or area clearance at the targeted position (up to 96m away).                                          |
| **Right-Click** _(Quick Tap)_      | Open Air / Sky           | **Broadcast Directive**: Broadcasts current mode directive to active squad.                                                                                     |
| **Hold Right-Click** _(≥ 8 ticks)_ | Any Direction            | **Banner of Courage (90° Forward Sector)**: Real-time unit highlight; on release rallies/transfigures minions and launches **Mass Attack** on enclosed enemies. |
| **Shift + Right-Click**            | In Air / On Block        | **Cycle Command Mode**: Cycles `FOLLOW` → `STAY` → `MINE` → `BUILD` → `RECRUIT`.                                                                                |
| **Keybind `R`**                    | In **BUILD** Mode        | **Rotate Blueprint**: Rotates hologram 90° clockwise (North → East → South → West).                                            |
| **Keybind `R`**                    | Any Other Mode           | **Panic Retreat / Regroup**: Disengages all minions from combat, sounds a warning bell, and recalls them into army lines.       |
| **Left-Click**                     | In **BUILD** Mode        | **Rotate Blueprint**: Rotates hologram 90° clockwise (on air, terrain, or block, without requiring Shift).                     |
| **Left-Click**                     | Owned Minion             | **Toggle Selection**: Select/deselect minion without friendly-fire damage.                                                      |
| **Shift + Left-Click**             | In **BUILD** Mode        | **Rotate Blueprint**: Rotates hologram 90° clockwise.                                                                           |
| **Shift + Left-Click**             | Any Other Mode           | **Deselect All**: Instantly clears selection on all minions.                                                                    |
| **Keybind `H`**                    | In **BUILD** Mode        | **Tactical Zoom**: Cycles tactical camera zoom presets (0.75x, 1.0x default, 1.5x, 2.0x).                                      |
| **Arcane Build Flight**             | In **BUILD** Mode        | **Free Survival Build Flight**: Enjoy unconstrained 3D vanilla flight in Survival mode while holding the Scepter in **BUILD** mode (Space to ascend, Shift to descend, full WASD mobility). Flying over water automatically cancels construction and revokes flight! |
| **Ctrl + Scroll**                  | In **BUILD** Mode        | **Smooth Camera Zoom**: Smoothly zooms tactical view in and out without hotbar cycling conflicts.                                 |
| **Keybind `V`**                    | Anywhere (with Scepter)  | **Command Hub GUI**: Tactical screen for squads, modes, blueprints, roles, **Architecture Style**, **Size**, and **[↻ Rotate]**. |
| **Shift + Right-Click**            | In Air / On Block        | **Open Command Hub GUI**: Instant access to Command Hub GUI (works while walking or in Arcane Build Flight).                    |
| **Shift + Right-Click**            | Owned Minion             | **Open Minion GUI**: Access minion's 9-slot backpack and equipment slots.                                                       |
| **Empty Hand Right-Click**         | Owned Minion             | **Unified Station / Follow**: Toggles minion stationing. If stationed (via waypoint or up-close guard), a **single click** immediately orders them to follow. If following, stations them in place. |
| **Gold Ingot Right-Click**         | Untamed Minion           | **Bind / Tame**: Binds the minion permanently to your will (consumes 1 Gold Ingot in Survival).                                                                 |
| **Food / Gold Right-Click**        | Wounded Minion           | **Heal**: Restores minion health (consumes food/gold in Survival; infinite in Creative).                                                                        |
| **Right-Click** _(TNT Stick)_      | Open Air / Blocks        | **Throw Explosive Stick**: Launches projectile detonating on impact with 4.0F blast (5-tick cooldown).                                                          |
| **Right-Click** _(Frost Grenade)_  | Open Air / Blocks        | **Throw Frost Grenade**: Launches cryogenic grenade turning blocks into snow, flash-freezing fluids, placing powder snow ring, and freezing enemies (10-tick cooldown). |

---

## 🗡️ 1. The Loki Command Scepter

The **Command Scepter** is your primary instrument of tactical command. It operates in 3 distinct click profiles:

### A. Quick Tap (Right-Click < 8 ticks)

- **Targeting an Enemy**: Commands all selected squad members to focus-fire that target. Plays a war drum sound and spawns angry villager & crit particles.
- **Targeting the Ground (up to 64 blocks)**: Drops an RTS waypoint marker with a golden beacon beam. Minions march, levitate across cliffs/gaps if needed, form up in **Ranked Army Lines** facing the objective, and enter the **Unified Stationed** position (`guardAnchorPos` set, `holdingPosition == true`), automatically deselecting so you can issue fresh commands without re-clicking.
- **Targeting an Owned Minion**: Orders that individual minion to toggle between stationed and following. If the minion was stationed (via waypoint ping or close-up right-click), a **single click** immediately orders them to follow without any extra clicks!
- **Aiming into Open Air**: Broadcasts your current mode directive to your entire active squad.
- **In BUILD Mode**: Cycles through your blueprint catalog (Watchtower, Cottage, Barracks, Workshop, etc.).

### B. Channeled Banner of Courage (Hold Right-Click ≥ 8 ticks)

Channeling projects an expanding **90° forward conical sector** (from 3.0 up to 16.0 blocks) with flame and portal boundary rays:

- **Real-Time Targeting Preview**:
  - Owned minions inside the cone glow with an outline and sparkle with enchant dust.
  - Hostile mobs inside the cone are marked with angry villager and crit target cues.
- **On Release**:
  - **Mass Attack**: If enemy mobs are in the cone, minions automatically distribute targets across the enemy group and charge in a coordinated assault!
  - **Rally & Transfigure**: Minions inside the cone are gathered into your active squad and transfigured into your primed archetype (if selected in the Command Hub).
  - Sounds a deep war horn (`SoundEvents.EVENT_RAID_HORN` / goat horn) and war drum blast.

### C. Shift Modifiers (Sneak + Click)

- **Shift + Right-Click**:
  - Aimed at air/blocks: Cycles your active command mode.
  - Aimed at an owned minion: Opens their **9-slot Inventory and Equipment Screen**.
- **Shift + Left-Click**:
  - In `BUILD` mode: Cycles the structure hologram rotation (0° → 90° → 180° → 270°).
  - In any other mode: Instantly deselects all minions.

---

## 🎛️ 2. Command Modes

Cycle through operating modes using **Shift + Right-Click** or by pressing **`V`** to open the Command Hub:

| Mode          | Visual Theme     | Description & Behavior                                                                                     |
| :------------ | :--------------- | :--------------------------------------------------------------------------------------------------------- |
| **`FOLLOW`**  | 🟢 Emerald Green | Minions march in disciplined **Ranked Army Lines** behind you.                                             |
| **`STAY`**    | 🟡 Gold Yellow   | Minions hold position at their current location and guard the immediate perimeter.                         |
| **`MINE`**    | 🟠 Blaze Orange  | Anchors full 3D area mining & deconstruction. Minions clear all blocks top-to-bottom with zero air-mining. |
| **`BUILD`**   | 🔵 Diamond Cyan  | Activates 3D neon cyan blueprint holograms. Builders construct multiblocks using Arcane Levitation flight. |
| **`RECRUIT`** | 🟣 Arcane Purple | Quick-tap living mobs to transfigure them into loyal minions.                                              |

> [!TIP]
> **No Need for an Attack Mode!**
> Combat is entirely contextual: tap an enemy to focus-fire, or channel the 90° sector to launch a mass coordinated attack on an entire enemy formation!

---

## 🎖️ 3. Squad Channel Management

You can divide your army into **5 distinct tactical channels**:

- **`ALL`** (White / Wildcard): Commands every minion you own regardless of assignment.
- **`ALPHA`** (Red): Primary vanguard strike force.
- **`BRAVO`** (Blue): Flankers and archers.
- **`CHARLIE`** (Green): Logistics, builders, and resource harvesters.
- **`DELTA`** (Purple): Heavy bulwark defenders and sentinels.

To switch squads:

1. Open the Command Hub (**`V`**).
2. Click the squad tab along the top header.
3. Any scepter directive or waypoint ping now applies strictly to minions assigned to that squad!

---

## 🖥️ 4. The Command Hub Screen (Keybind `V`)

Press **`V`** with a scepter anywhere in your inventory to open the tactical command screen:

- **Squad Tabs**: Filter orders by `ALL`, `ALPHA`, `BRAVO`, `CHARLIE`, or `DELTA`.
- **Mode Bar**: Direct buttons for `FOLLOW`, `STAY`, `MINE`, `BUILD`, and `RECRUIT`.
- **Archetype Toggles**: Select an active role (`WARRIOR`, `SENTINEL`, `BUILDER`). When primed, your next **Banner of Courage** rally transfigures all gathered minions into this archetype!
- **Blueprint Browser**: Browse multiblock structure blueprints, view required resources, and project 3D wireframe holograms.
- **Deconstruction Mode**: Toggle between construction and reverse top-down deconstruction.

---

## 🛡️ 5. Minion Archetypes & Ranks

| Archetype      | Preferred Weapon / Gear                                                        | Formation Position | Tactical Role                                                                                            |
| :------------- | :----------------------------------------------------------------------------- | :----------------- | :------------------------------------------------------------------------------------------------------- |
| **`WARRIOR`**  | Swords, Axes, Maces, Tridents **OR** Bows, Crossbows, Thrown Sticks (Frost/TNT) | Frontline Rank 1   | **Versatile Combatant & Hunter**: Frontline melee swordsman, ranged archer, or thrown javelin specialist. Features **Trident Duality** ($\le 5\text{D}$ melee thrust, $5\text{D}\text{--}20\text{D}$ thrown spear) and executes **Mob Procurement Contracts** for builders. |
| **`SENTINEL`** | Shield, Mace + Heavy Armor                                                     | Bulwark Rank 2     | **Defensive Guardian & Combat Medic**: Absorbs damage, holds fortified posts within an expanded 128-block leash, and channels the **Aegis of Restoration** to heal wounded players (commander priority) and allied minions under 70% HP. |
| **`BUILDER`**  | Pickaxes, Axes, Shovels + Toolset                                              | Rearguard Rank 3   | **Architect, Excavator & Supply Specialist**: 3D levitation flight to construct or dismantle multiblocks at any height. Autonomously quarries natural stone, harvests timber via agro-forestry (using bone meal for rapid growth), self-crafts tools, shares blocks via peer energy beams, deploys supply depot chests, and commissions **Squad Material Procurement** contracts to nearby Warriors for mob-derived resources. |

---

## 🏛️ 6. Building Structures (`BUILD` Mode)

1. Select **`BUILD`** mode (**Shift + Right-Click** or press **`V`**).
2. **Arcane Build Flight (Free Survival Flight & Water Safety)**:
   - When in `BUILD` mode, you automatically enter physical Arcane Flight in both Survival and Creative modes.
   - **Free 3D Survival Flight**: Enjoy unconstrained, responsive 3D vanilla flight mechanics in Survival mode. Ascend with **`Space`**, descend with **`Shift`**, and glide freely in any direction with WASD without rigid altitude clamping or velocity locks.
   - **Water Flight Cancellation Safeguard**: If you fly over water or submerge while in `BUILD` mode, the scepter instantly cancels all active construction sessions, switches mode automatically to **`FOLLOW`**, revokes flight abilities, plays an extinguishing hiss, emits splash particles, and displays a warning (`§c⚠ Construction cancelled: Flying over water is prohibited in BUILD mode!§r`).
   - **Cave & Indoor Clearance**: Upward raycasts automatically detect overhead cavern or room ceilings to prevent clipping into low terrain.
   - **Safe Descent**: Exiting `BUILD` mode, switching hotbar items, or descending to land gently restores normal ground physics with complete fall damage immunity.
   - **Tactical Zoom**: Press **`H`** to cycle zoom presets (`0.75x`, `1.0x`, `1.5x`, `2.0x`) or hold **`Ctrl` + Scroll** for fine zooming without hotbar conflicts.
3. **Real-Time Per-Block Ghost Dissolution & Wireframe Hologram**:
   - As builder minions place each block into the structure, that specific wireframe box dissolves immediately from the hologram view.
   - When the final block is placed, the ghost grid clears completely, triumphant fanfare particles play, and minions disengage flight to return to their commander or hold their post.
   - Semantic color coding highlights components:
     - **Doors**: 🟢 Emerald Green (`#00FF88`, full 2-block portal outline).
     - **Lights & Torches**: 🟡 Amber Gold (`#FFCC00`).
     - **Utilities & Beds**: 🟣 Arcane Purple (`#9933FF`).
     - **Walls & Columns**: 🔵 Diamond Cyan (`#00D4FF`).
     - **Roof Trim & Eaves**: ❄️ Ice Blue (`#70B8FF`).
4. **Command Hub GUI Bar Swap (`V` Key)**:
   - When **`BUILD`** mode is active, the 3-button Minion Archetype bar dynamically transforms into the **Architecture Style** bar:
     - **`[🌍 Biome Native]`**: Vernacular construction adapting to indigenous materials (oak/birch for plains/forest, sandstone for deserts/badlands, spruce for taigas, mangrove/mud for swamps, deepslate for subterranean caverns, blackstone for nether, purpur/end stone for end).
     - **`[🏰 Fortress Stone]`**: Heavy stone brick masonry, mossy & cracked cobblestone, polished andesite accents, deepslate, and iron fittings.
     - **`[🌲 Frontier Timber]`**: Rustic log framing, horizontal plank walls, stripped wood corners, fences, and lanterns.
     - **`[🔮 Arcane Nether]`**: Polished blackstone, basalt pillars, crimson/warped timbers, and soul fire illumination.
   - **Footprint Size Selectors**:
     - **`[ S ]` Small**: Compact 5x5 footprint.
     - **`[ M ]` Medium**: Balanced 7x7 footprint (default).
     - **`[ L ]` Grand**: Expansive 9x9 multi-room estate or fortified outpost.
     - **`[ 🎲 ]` Random**: Dynamically rolls a randomized footprint size.
5. **Procedural Building Categories**:
   - **`🏡 Home`**: Villager-ready residence with POIs (beds, entry door, crafting table, furnace, lantern) for natural villager habitation.
   - **`🗼 Watchtower`**: Elevated observation post with parapets and arrow slits.
   - **`🛡 Barricade`**: Fortified defensive rampart with firing steps.
   - **`⚒ Workshop`**: Blacksmith forge with furnaces, anvils, and tool stations.
   - **`📦 Supply Depot`**: Storage warehouse with chests and logistics barrels.
   - **`🔮 Obelisk`**: Mystical monument focusing arcane energy.
6. **Organic Noise Weathering Engine**:
   - Eliminates sterile, repetitive block patterns using 3D spatial coordinate noise, blending cracked bricks, mossy stones, andesite, and stripped logs organically (resembling natural cobblestone/dirt variance).
   - Deterministic hashing guarantees that client holographic wireframes and server minion placement match 1:1 down to the individual block.
7. **Dynamic Foundation Slope Snapping**:
   - Automatically scans perimeter and floor blocks at the base level. If placing on a hillside, cliff, or over water, automatically extends stone retaining pillars or wooden stilts downward up to 8 blocks (`MAX_FOUNDATION_DEPTH`) until solid ground is reached, eliminating floating structures!
8. **Dimension Bed Explosion Safeguard**:
   - In the Nether, beds automatically convert to `Blocks.RESPAWN_ANCHOR`. In the End, beds convert to `Blocks.PURPUR_BLOCK`, completely preventing accidental or intentional bed explosions!
9. **Rotate Blueprint (3 Ways)**:
   - **Press `R`**: Directly rotates the hologram 90° clockwise (0° → 90° → 180° → 270°).
   - **Left-Click** *(with Scepter in `BUILD` Mode)*: Directly rotates the hologram 90° clockwise (works aiming at air, terrain, or blocks, with or without Shift).
   - **Command Hub GUI (`V`)**: Click the **`[ ↻ Rotate ]`** button inside the Command Hub screen.
   - **Flight Altitude Control**: While hovering in Arcane Build Flight, hold **`Space`** to ascend or **`Shift`** to descend to your ideal vantage height.
10. **Right-Click** on any ground block to anchor the construction session:
    - **100% Pixel-Perfect Crosshair Alignment**: A camera-aligned raycast ensures the hologram and placement anchor match exactly where your screen crosshair points on the terrain, even from high-altitude bird's-eye views (up to 96m reach).
    - **One-Click Aerial Placement**: Instantly transmits placement packets to the server so you can drop foundations without descending to the ground.
    - **Sky Tap to Cycle**: Right-clicking into empty air or open sky cycles to the next blueprint in your catalog.
11. Assigned **Builder** minions will activate **3D Arcane Levitation**, flying up to each layer and completing the structure bottom-to-top:
    - **Builder Block Phasing (`noClip = true`) & Zero Drift**: Builders can pass through blocks **strictly and only** while actively building or evacuating a finished structure. They fly directly through floors, walls, and ceilings in 3D straight to their work stations without getting trapped indoors or drifting towards exterior exits.
    - **Persistent Zero-Timeout Execution**: Builders never halt, give up, or freeze near the end of a build. The artificial 400-tick timeout loop and failure pauses have been completely eliminated. Builders poll tasks continuously and work without interruption until 100% of the structure is finished.
    - **Arcane Phase-Shift Resolution**: If interior detail blocks (like anvils, grindstones, blast furnaces, chests, or hanging lanterns) are enclosed by newly constructed walls or ceilings, builders do not get stuck. After 40 ticks (~2 seconds) of obstacle obstruction, they perform an **Arcane Phase-Shift**—teleporting directly to their work station with purple portal runes and SFX to place the block cleanly.
    - **Post-Construction Structure Egress**: Upon finishing a building, builders do NOT get trapped inside. They retain block phasing (`noClip = true`) and fly smoothly out of the building to the exterior perimeter. Normal collision physics are only restored once the builder is safely outside with clear headroom.
    - **360° Perimeter Waypoints & Guaranteed Flank Spread**: When construction completes, golden beacon beams (`END_ROD` + `GLOW`) and chime audio rise around the finished build, distributed evenly across all flanks (South/Front, East Flank, North/Back, West Flank) so minions completely encircle the structure instead of stacking in one spot. Builders are teleported directly to their assigned flank station, safely positioned outside the structure, and stationed on guard at attention (`isSitting = true`, `guardAnchor` set) regardless of squad assignment.
    - **Autonomous Stationed Mobilization**: When you place a new blueprint down nearby (within 64 blocks), stationed builder minions automatically wake up (`setSitting = false`) and mobilize to build immediately without requiring you to walk over and re-select them! A single right-click on any stationed builder still commands them to follow if desired.
    - **Creative Mode**: Builders place blocks freely at zero material cost. Any existing blocks (grass, flowers, snow, dirt) are automatically pre-cleared with zero dropped items, eliminating all clutter.
    - **Survival Mode**: Builders resolve construction materials through a multi-stage logistics pipeline (Backpack → Local Containers → Peer Sharing → Autonomous Quarrying/Timber → Mob Hunting Contracts).

---

## ⛏️ 7. Mining & Area Clearance (`MINE` Mode)

1. Select **`MINE`** mode (**Shift + Right-Click** or press **`V`**).
2. Look at the block or ground area you want to clear to see a **Fiery Orange/Red 3D Wireframe Preview** anchored directly on the clicked block.
3. **Right-Click** to initiate the mining/deconstruction session.
4. Assigned **Builder** minions immediately mobilize:
   - **Full Selected Area Clearance**: Builders mine **every** solid, destructible block within the selected volume from the highest Y level down to the lowest Y level.
   - **Zero Air-Mining Guarantee**: Builders strictly never claim, navigate to, or swing pickaxes at air blocks. If a block was destroyed or already air, minions instantly advance to the next real block without swinging or vocalizing.
   - **Bedrock & Indestructible Immunity**: Bedrock and indestructible blocks (negative hardness) are strictly protected and never targeted.
   - **Survival Drops vs. Creative Demolition**: In Survival mode, broken blocks drop as collectible items for full resource recovery. In Creative mode, blocks are cleared cleanly without entity drops to avoid clutter.
   - **Automatic Wireframe Dismissal**: The moment the entire selected area is cleared (all blocks in the volume become air or indestructible), the session finishes with celebratory particles and sound, and the highlighted wireframe **immediately disappears**!

---

## 🧙‍♂️ 8. 100% Universal Arcane Levitation & Obstacle Vaulting

- **100% Zero-Footprint Traversal Across All Roles**: Warriors, Sentinels, and Builders traverse ravines, scale cliffs, navigate vertical terrain, and descend structures using zero-footprint 3D Arcane Levitation with zero ephemeral block generation.
- **Builders**: Maintain permanent 3D flight throughout construction and deconstruction tasks, hovering adjacent to work blocks at any height without scaffolding or temporary blocks.
- **All Minion Types (Warriors, Sentinels, Builders)**:
  - Responsive 2-tick obstacle stall sensitivity: when encountering walls, fences, cliffs, or ledges they cannot walk over, they instantly perform an **Arcane Obstacle Vault**, gliding smoothly over barriers with purple portal trails and landing safely on the far side.
- **Dynamic Obstacle Clearance & Solid Ground Landing**: Minions can scale obstacles of any height (3, 5, 10, or 20+ blocks high) without artificial altitude caps or premature timers. Upward lift carries minions cleanly over barriers, and the instant a minion's feet reach solid ground without a taller obstacle ahead (e.g. stepping atop a 3-block ledge or reaching the other side), levitation deactivates immediately and restores normal ground walking and step height. In mid-air, downward glide carries minions to earth. Melee warriors in ground combat never launch into the air.
- **Formation Follow Anti-Jitter**: Allied minions suppress mutual physical collision shoving (`pushAwayFrom`) when idle, guarding, or standing in formation ranks. Consistent walkable ground elevation checks and arrival velocity zeroing eliminate endless station-hunting jitter.
- **Operational Leash Freedom**: Stationed and held minions enjoy an expanded **128-block leash**, while unselected free workers have simulation-chunk freedom without snap-teleporting to players while working on active construction sessions.
- **Panic Retreat (`R` key)**: Instantly dismisses all 3D holographic wireframes, cancels active construction sessions for the commander, clears combat targets, and recalls all minions to formation.

## 🔔 9. Tactical Panic Retreat (Keybind `R`)

Press **`R`** at any time while holding the Command Scepter (or with it in your inventory):

- Sounds a warning retreat bell (`SoundEvents.BLOCK_BELL_USE` / `BLOCK_NOTE_BLOCK_BELL`).
- Immediately clears combat targets on all active minions (`setTarget(null)`).
- Cancels stationary guard or waypoint hold positions.
- All minions disengage and sprint back to you at 1.35x speed, assembling into **Ranked Army Lines** behind you.
- Displays an action bar confirmation: `§e🔔 RETREAT! [Squad] disengaging and falling back!§r`.

---

## 🎮 10. Survival vs. Creative Mode Mechanics

The commander's active game mode directly affects how minions handle resources, construction, demolition, and interactions:

| Gameplay Mechanic                      | Survival Mode (`/gamemode survival`)                                                                                                                                                                                                                          | Creative Mode (`/gamemode creative`)                                                                                                                                                                 |
| :------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Structure Construction (`BUILD`)**   | **Autonomous Logistics, Harvesting & Procurement**: Builders consume carried blocks, scavenge nearby chests within 12 blocks, transfer blocks via peer-to-peer beams from allies within 24m, autonomously quarry natural stone/deepslate, and harvest timber via bone-meal agro-forestry. When mob resources (wool, bones, slime, leather, etc.) are needed, builders commission nearby Warriors on tactical hunting contracts with automatic material synthesis. They self-craft replacement tools and deposit excess materials into newly crafted/placed supply depot chests. (Only pauses with alert if non-harvestable materials are missing). | **Zero-Cost Free Placement & Zero-Drop Pre-Clearing**: Builders construct instantly and infinitely at zero material cost without needing blocks in their inventory or nearby chests. Existing terrain (grass, flowers, snow, dirt) is automatically pre-cleared with zero dropped items, eliminating clutter. |
| **Area Mining & Dismantling (`MINE`)** | **Resource Harvesting**: Broken blocks drop as real collectible items in the world for full resource recovery.                                                                                                                                                | **Zero-Drop Demolition**: Blocks are cleared cleanly without spawning entity drops, preventing world and inventory clutter during large excavations.                                                 |
| **Minion Taming**                      | Consumes **1 Gold Ingot** from player hand when binding an untamed minion.                                                                                                                                                                                    | Tames the minion instantly **without consuming** the Gold Ingot.                                                                                                                                     |
| **Minion Feeding & Healing**           | Consumes **1 food or gold item** per healing interaction.                                                                                                                                                                                                     | Restores minion health **without consuming** any items from the player's inventory.                                                                                                                  |
| **Minion Spawn Egg**                   | Consumes **1 spawn egg** per mob spawned.                                                                                                                                                                                                                     | Spawns minions infinitely **without depleting** the held egg stack.                                                                                                                                  |
| **Scepter Recruitment (`RECRUIT`)**    | Transfigures wild mobs into minion thralls.                                                                                                                                                                                                                   | Transfigures wild mobs into minion thralls.                                                                                                                                                          |
| **Universal Arcane Traversal**        | 100% zero-footprint 3D Arcane Levitation across all roles. Zero ephemeral block clutter.                                                                                                                                                                     | 100% zero-footprint 3D Arcane Levitation across all roles. Zero ephemeral block clutter.                                                                                                             |

---

## 💥 11. Tactical Ordnance & Warrior Thrown Weapon Arsenal

In addition to squad command, commanders and warrior thralls have access to throwable tactical ordnance and thrown weapon mechanics:

### Warrior Thrown Weapon Mastery & Trident Duality
- **Trident Duality**: Warriors equipped with Tridents dynamically switch between melee thrusts ($\le 5\text{D}$) and long-range thrown javelins ($5\text{D}\text{--}20\text{D}$) with `DISALLOWED` pickup protection.
- **Thrown Ordnance**: Warriors can equip and throw Frost Grenade Sticks and TNT Sticks, launching cryogenic or explosive artillery at enemy lines.

### TNT Stick (`modid-mmcli-agent-modding:tnt_stick`)
- **Type**: Single-stack throwable explosive stick (`Rarity.EPIC`).
- **Cooldown**: 5 ticks (0.25s) anti-spam delay.
- **Flight & Blast**: Launches at 1.5 velocity leaving smoke and flame particles; detonates on server collision with a **4.0F explosion**.

### Frost Grenade Stick (`modid-mmcli-agent-modding:frost_grenade_stick`)
- **Type**: Single-stack throwable cryogenic stick (`Rarity.RARE`).
- **Cooldown**: 10 ticks (0.5s) anti-spam delay.
- **Flight & VFX**: Leaves trailing snowflakes and snowball debris in flight.
- **Zero Explosive Destruction**: Causes zero destructive block breakage while transmuting the environment into a winter wonderland.
- **Block Transmutation to Snow ($r = 3.5\text{D}$)**:
  - **Snow Block Conversion**: Transmutes destructible solid blocks (dirt, grass, stone, cobblestone, wood, leaves, sand, etc.) directly into solid Snow Blocks (`Blocks.SNOW_BLOCK`).
  - **Snow Layer Coating**: Coats exposed ground and surfaces with delicate snow layers (`Blocks.SNOW`).
  - **Indestructible & Container Protection**: Unbreakable blocks (bedrock, barrier) and block entities/containers (chests, furnaces, barrels) are strictly protected.
- **Fluid & Fire Conversion ($r = 3.5\text{D}$)**:
  - **Water Flash-Freeze**: Converts still and flowing water into solid ice (`Blocks.ICE`).
  - **Lava Crystallization**: Converts still lava into obsidian (`Blocks.OBSIDIAN`) and flowing lava into cobblestone (`Blocks.COBBLESTONE`).
  - **Fire Quenching**: Extinguishes normal fire, soul fire, and lit campfires with steam particles.
- **Powder Snow Ring ($r \in [2.0\text{D}, 3.5\text{D}]$)**: Summons a perimeter ring of powder snow (`Blocks.POWDER_SNOW`) around the impact center on solid ground.
- **Freezing Debuffs ($r = 5.0\text{D}$)**: Inflicts **360 freezing ticks** (full frost vignette + shivering damage) and **Slowness III** (8.0s) on caught entities, while quenching any burning targets.

