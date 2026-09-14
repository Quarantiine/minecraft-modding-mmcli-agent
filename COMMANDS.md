# 📜 Overlord's Command Guide

A comprehensive, quick-reference manual for all minion commands, controls, squad directives, and scepter abilities.

---

## ⚡ Quick Controls Cheat Sheet

| Input                              | Target / Context         | Action                                                                                                                                                          |
| :--------------------------------- | :----------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Right-Click** _(Quick Tap)_      | Hostile Mob              | **Focus-Fire Attack Ping**: Selected minions charge and attack this entity.                                                                                     |
| **Right-Click** _(Quick Tap)_      | Ground Block (up to 32m) | **RTS Waypoint Ping**: Deploys minions into ranked army battle lines and auto-deselects them.                                   |
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

---

## 🗡️ 1. The Loki Command Scepter

The **Command Scepter** is your primary instrument of tactical command. It operates in 3 distinct click profiles:

### A. Quick Tap (Right-Click < 8 ticks)

- **Targeting an Enemy**: Commands all selected squad members to focus-fire that target. Plays a war drum sound and spawns angry villager & crit particles.
- **Targeting the Ground (up to 32 blocks)**: Drops an RTS waypoint marker with a golden beacon beam. Minions march, levitate across cliffs/gaps if needed, and form up in **Ranked Army Lines** facing the objective, automatically deselecting so you can issue fresh commands without re-clicking.
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
- **`CHARLIE`** (Green): Logistics, builders, and miners.
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
- **Archetype Toggles**: Select an active role (`WARRIOR`, `SENTINEL`, `BUILDER`, `MINER`). When primed, your next **Banner of Courage** rally transfigures all gathered minions into this archetype!
- **Blueprint Browser**: Browse multiblock structure blueprints, view required resources, and project 3D wireframe holograms.
- **Deconstruction Mode**: Toggle between construction and reverse top-down deconstruction.

---

## 🛡️ 5. Minion Archetypes & Ranks

| Archetype      | Preferred Weapon / Gear                    | Formation Position | Tactical Role                                                                                            |
| :------------- | :----------------------------------------- | :----------------- | :------------------------------------------------------------------------------------------------------- |
| **`WARRIOR`**  | Swords, Axes, Maces **OR** Bows, Crossbows | Frontline Rank 1   | **Versatile Combatant**: Fights as a frontline swordsman or as a ranged archer based on equipped weapon. |
| **`SENTINEL`** | Shield, Mace + Heavy Armor                 | Bulwark Rank 2     | **Defensive Guardian**: High durability, absorbs damage, and holds fortified defense posts.              |
| **`BUILDER`**  | Pickaxe, Hammer + Toolset                  | Support Rank 3     | **Arcane Engineer**: 3D levitation flight to construct or dismantle multiblocks at any height.           |
| **`MINER`**    | Pickaxe + Torch                            | Support Rank 3     | **Resource Gatherer**: Autonomous excavation, tunneling, and quarry operations.                          |

---

## 🏛️ 6. Building Structures (`BUILD` Mode)

1. Select **`BUILD`** mode (**Shift + Right-Click** or press **`V`**).
2. Look at the ground to see the **Neon Cyan 3D Hologram Preview**.
3. **Right-Click** to cycle the active blueprint.
4. **Shift + Left-Click** to rotate the structure (0°, 90°, 180°, 270°).
5. **Right-Click** on the ground block to anchor the construction session.
6. Assigned **Builder** minions will activate **Arcane Levitation**, flying up to each layer and completing the structure bottom-to-top:
   - **Creative Mode**: Builders place blocks freely at zero material cost. Construction proceeds continuously with zero resource limitations.
   - **Survival Mode**: Builders consume blocks from their 9-slot backpack. If empty, they autonomously scavenge nearby chests/containers within 12 blocks. If materials are missing, they pause with an actionbar alert (`"Minion needs [item] to continue building!"`).

---

## ⛏️ 7. Mining & Area Clearance (`MINE` Mode)

1. Select **`MINE`** mode (**Shift + Right-Click** or press **`V`**).
2. Look at the block or ground area you want to clear to see a **Fiery Orange/Red 3D Wireframe Preview** anchored directly on the clicked block.
3. **Right-Click** to initiate the mining/deconstruction session.
4. Assigned **Miner** (and Builder) minions immediately mobilize:
   - **Full Selected Area Clearance**: Miners mine **every** solid, destructible block within the selected volume from the highest Y level down to the lowest Y level.
   - **Zero Air-Mining Guarantee**: Miners strictly never claim, navigate to, or swing pickaxes at air blocks. If a block was destroyed or already air, minions instantly advance to the next real block without swinging or vocalizing.
   - **Bedrock & Indestructible Immunity**: Bedrock and indestructible blocks (negative hardness) are strictly protected and never targeted.
   - **Survival Drops vs. Creative Demolition**: In Survival mode, broken blocks drop as collectible items for full resource recovery. In Creative mode, blocks are cleared cleanly without entity drops to avoid clutter.
   - **Automatic Wireframe Dismissal**: The moment the entire selected area is cleared (all blocks in the volume become air or indestructible), the session finishes with celebratory particles and sound, and the highlighted wireframe **immediately disappears**!

---

## 🧙‍♂️ 8. Arcane Levitation & Obstacle Vaulting

- **Builders**: Enjoy permanent 3D flight during construction tasks, hovering adjacent to work blocks at any height without scaffolding.
- **All Minion Types (Warriors, Sentinels, Miners)**:
  - When following or charging, if minions encounter walls, fences, cliffs, or ledges they cannot walk over:
  - They automatically perform an **Arcane Obstacle Vault**, levitating smoothly over the barrier with purple particle trails, and landing immediately on the other side!

---

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
| **Structure Construction (`BUILD`)**   | **Resource-Constrained**: Builders consume blocks from their 9-slot backpack. If empty, they automatically scavenge nearby chests/containers within 12 blocks. If materials are exhausted, building pauses with an actionbar alert (`"Minion needs [item]"`). | **Zero-Cost Free Placement**: Builders construct instantly and infinitely at zero material cost without needing blocks in their inventory or nearby chests. Building never pauses for missing items. |
| **Area Mining & Dismantling (`MINE`)** | **Resource Harvesting**: Broken blocks drop as real collectible items in the world for full resource recovery.                                                                                                                                                | **Zero-Drop Demolition**: Blocks are cleared cleanly without spawning entity drops, preventing world and inventory clutter during large excavations.                                                 |
| **Minion Taming**                      | Consumes **1 Gold Ingot** from player hand when binding an untamed minion.                                                                                                                                                                                    | Tames the minion instantly **without consuming** the Gold Ingot.                                                                                                                                     |
| **Minion Feeding & Healing**           | Consumes **1 food or gold item** per healing interaction.                                                                                                                                                                                                     | Restores minion health **without consuming** any items from the player's inventory.                                                                                                                  |
| **Minion Spawn Egg**                   | Consumes **1 spawn egg** per mob spawned.                                                                                                                                                                                                                     | Spawns minions infinitely **without depleting** the held egg stack.                                                                                                                                  |
| **Scepter Recruitment (`RECRUIT`)**    | Transfigures wild mobs into minion thralls.                                                                                                                                                                                                                   | Transfigures wild mobs into minion thralls.                                                                                                                                                          |
| **Sapper Bridges & Construction Blocks**  | Ephemeral `ConstructionBlock` placed during combat sapper bridging/scaling has zero drops in both modes to prevent debris. Builders levitate and do not use scaffolding. | Zero drops in both modes.                                                                                                                                                                            |
