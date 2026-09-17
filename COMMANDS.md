# 📜 Overlord's Command Guide

A comprehensive, quick-reference manual for all minion commands, controls, squad directives, and scepter abilities.

---

## ⚡ Quick Controls Cheat Sheet

| Input                              | Target / Context         | Action                                                                                                                                                          |
| :--------------------------------- | :----------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Right-Click** _(Quick Tap)_      | Hostile Mob              | **Focus-Fire Attack Ping**: Selected minions charge and attack this entity.                                                                                     |
| **Right-Click** _(Quick Tap)_      | Ground Block (up to 64m) | **RTS Waypoint Ping**: Deploys minions into ranked army battle lines and auto-deselects them.                                   |
| **Right-Click** _(Quick Tap)_      | Owned Minion             | **Individual Follow**: Toggles follow order on this specific minion (follow / stop following).                                                                  |
| **Right-Click** _(Quick Tap)_      | In **BUILD** Mode        | **Cycle Blueprint**: Cycles to next structure preset (Watchtower, Cottage, etc.).                                                                               |
| **Right-Click** _(Quick Tap)_      | Open Air / Sky           | **Broadcast Directive**: Broadcasts current mode directive to active squad.                                                                                     |
| **Hold Right-Click** _(≥ 8 ticks)_ | Any Direction            | **Banner of Courage (90° Forward Sector)**: Real-time unit highlight; on release rallies/transfigures minions and launches **Mass Attack** on enclosed enemies. |
| **Shift + Right-Click**            | In Air / On Block        | **Cycle Command Mode**: Cycles `FOLLOW` → `STAY` → `MINE` → `BUILD` → `RECRUIT`.                                                                                |
| **Shift + Right-Click**            | Owned Minion             | **Open Minion GUI**: Access minion's 9-slot backpack and equipment slots.                                                                                       |
| **Left-Click**                     | Owned Minion             | **Toggle Selection**: Select/deselect minion without friendly-fire damage.                                                                                      |
| **Shift + Left-Click**             | In **BUILD** Mode        | **Cycle Blueprint Rotation**: Rotates hologram 90° (North → East → South → West).                                                                               |
| **Shift + Left-Click**             | Any Other Mode           | **Deselect All**: Instantly clears selection on all minions.                                                                                                    |
| **Keybind `V`**                    | Anywhere (with Scepter)  | **Command Hub GUI**: Full tactical screen for squads, modes, blueprints, and roles.                                                                             |
| **Keybind `R`**                    | Anywhere (with Scepter)  | **Panic Retreat / Regroup**: Disengages all minions from combat, sounds a warning bell, and recalls them into army lines.                                       |
| **Empty Hand Right-Click**         | Owned Minion             | **Sit / Guard**: Toggles minion sitting / stationary guard anchor.                                                                                              |
| **Gold Ingot Right-Click**         | Untamed Minion           | **Bind / Tame**: Binds the minion permanently to your will (consumes 1 Gold Ingot in Survival).                                                                 |
| **Food / Gold Right-Click**        | Wounded Minion           | **Heal**: Restores minion health (consumes food/gold in Survival; infinite in Creative).                                                                        |
| **Right-Click** _(TNT Stick)_      | Open Air / Blocks        | **Throw Explosive Stick**: Launches projectile detonating on impact with 4.0F blast (5-tick cooldown).                                                          |
| **Right-Click** _(Frost Grenade)_  | Open Air / Blocks        | **Throw Frost Grenade**: Launches cryogenic grenade turning blocks into snow, flash-freezing fluids, placing powder snow ring, and freezing enemies (10-tick cooldown). |

---

## 🗡️ 1. The Loki Command Scepter

The **Command Scepter** is your primary instrument of tactical command. It operates in 3 distinct click profiles:

### A. Quick Tap (Right-Click < 8 ticks)

- **Targeting an Enemy**: Commands all selected squad members to focus-fire that target. Plays a war drum sound and spawns angry villager & crit particles.
- **Targeting the Ground (up to 64 blocks)**: Drops an RTS waypoint marker with a golden beacon beam. Minions march, levitate across cliffs/gaps if needed, and form up in **Ranked Army Lines** facing the objective, automatically deselecting so you can issue fresh commands without re-clicking.
- **Targeting an Owned Minion**: Orders that individual minion to follow you immediately or stop following.
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
2. Look at the ground to see the **Neon Cyan 3D Hologram Preview**.
3. **Right-Click** to cycle the active blueprint.
4. **Shift + Left-Click** to rotate the structure (0°, 90°, 180°, 270°).
5. **Right-Click** on the ground block to anchor the construction session.
6. Assigned **Builder** minions will activate **3D Arcane Levitation**, flying up to each layer and completing the structure bottom-to-top:
   - **Creative Mode**: Builders place blocks freely at zero material cost. Any existing blocks (grass, flowers, snow, dirt) are automatically pre-cleared with zero dropped items, eliminating all clutter.
   - **Survival Mode**: Builders resolve construction materials through a multi-stage logistics pipeline:
     1. *9-Slot Backpack*: Consumes blocks already carried.
     2. *Nearby Containers*: Scavenges chests, barrels, and shulkers within 12 blocks.
     3. *Peer-to-Peer Allied Sharing*: Transmits required materials from nearby allied minions within 24m via green energy particle beams and pickup audio.
     4. *Autonomous Quarrying & Agro-Forestry*: Quarries natural stone, deepslate, and earth. For timber, fells trees or plants saplings and rapidly accelerates maturity with bone meal.
     5. *Squad Material Procurement & Mob Hunting Contracts*: When mob-derived materials are required (wool, bones, slime, leather, ink, prismarine, etc.), builders scan for target mobs within 32m and commission an available allied Warrior thrall. The warrior receives the contract with a weaponsmith sound and energy beam, slays the target, synthesizes refined items (e.g. 4 String $\to$ 1 Wool, 1 Bone $\to$ 3 Bone Meal $\to$ Bone Block, 9 Slimeballs $\to$ Slime Block, 4 Prismarine Shards $\to$ Prismarine), and delivers them directly. (Falls back to solo builder hunt if no warrior is available).
     6. *Strict Safety & Build Protection*: Strictly protects player pets, named mobs, villagers, iron golems, allays, and allied minions (`isSafeHuntTarget`). Never harvests player-placed blocks, active blueprint structures, processed materials (planks, bricks, slabs, glass), or blocks within 12m of player beds, chests, or respawn anchors.
     7. *Hazard Avoidance*: Checks all 6 directions and strictly refuses to break blocks adjacent to lava.
     8. *Tool Self-Crafting*: Synthesizes wooden or stone pickaxes, axes, and shovels on demand from harvested timber and stone.
     9. *Autonomous Supply Depots*: When bags are full of surplus materials, deposits excess into nearby chests, or crafts an 8-plank Chest (pairing into a Double Chest if adjacent) on site.

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

