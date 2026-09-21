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
| **Hold Right-Click** _(≥ 8 ticks)_ | All Modes *(incl. PATHWAY)* | **Banner of Courage (90° Forward Sector)**: Real-time unit highlight; on release rallies and selects all enclosed minions into your squad. Any escorts caught in the sweep automatically detach and follow you! |
| **Shift + Hold Right-Click**       | In **PATHWAY** Mode      | **Sector Pathway Dispatch (90° Forward Sector)**: On release dispatches all enclosed minions (or active selection) directly to the active patrol route! |
| **Shift + Right-Click**            | In Air / On Block        | **Cycle Command Mode**: Cycles `FOLLOW` → `STAY` → `MINE` → `BUILD` → `RECRUIT` → `PATHWAY`.                                  |
| **Keybind `R`**                    | In **BUILD** Mode        | **Rotate Blueprint**: Rotates hologram 90° clockwise (North → East → South → West).                                            |
| **Keybind `R`**                    | Any Other Mode           | **Tactical Squad Retreat**: Recalls active squad members within 64m, cancels combat, and resumes formation ranks.              |
| **Shift + Keybind `R`**            | Anywhere                 | **Emergency Citadel Call**: Fortress-wide emergency muster across 128m! Unbinds all patrol routes, sounds raid horn & bell, and sprints all units home. |
| **Left-Click**                     | In **BUILD** Mode        | **Rotate Blueprint**: Rotates hologram 90° clockwise (on air, terrain, or block, without requiring Shift).                     |
| **Left-Click**                     | Owned Minion             | **Toggle Selection**: Select/deselect minion without friendly-fire damage. If the minion was escorting another minion, selecting it **automatically detaches** it from its squad leader to follow you! |
| **Left-Click**                     | Waypoint Block in **PATHWAY** | **Delete Waypoint Tile**: Punches/removes the targeted waypoint tile cleanly with sound and smoke particles across any of the 5 route channels without breaking the block! |
| **Shift + Left-Click**             | Owned Minion             | **Designate Squad Leader / Untether**: With 1+ minions selected, designates this minion as the **Squad Leader** that all selected minions will escort! With 0 minions selected on an escort, dissolves its escort bond so it follows you. |
| **Right-Click** _(Quick Tap)_      | Block in **PATHWAY**     | **Add / Remove Waypoint (Zero-Overlap)**: Places a waypoint on top of the surface. If *any* route channel (Color 1–5) already has a waypoint at that position, clicking removes/undoes it from that route instead of creating an overlap! |
| **Shift + Right-Click**            | Block in **PATHWAY**     | **Remove Waypoint**: Removes clicked waypoint across all route channels (bypasses Command Hub GUI).                           |
| **Right-Click Air**                | In **PATHWAY** Mode      | **Cycle Route Channel**: Cycles active patrol route (Route 1 🟡 Gold → Route 2 🔵 Cyan → Route 3 🟢 Emerald → Route 4 🟣 Purple → Route 5 🔴 Crimson). |
| **Shift + Right-Click Air**        | In **PATHWAY** Mode      | **Clear Route**: Clears all waypoints from active patrol route channel.                                                         |
| **Right-Click Minion**             | In **PATHWAY** Mode      | **Assign Minion to Route**: If minions are selected, assigns all selected minions to the active route. If none are selected, toggles minion patrol duty on the active route channel (shows `[ 🟡 Route # ]` badge). |
| **Shift + Left-Click**             | In **BUILD** Mode        | **Rotate Blueprint**: Rotates hologram 90° clockwise.                                                                           |
| **Shift + Left-Click**             | Any Other Mode (Air/Ground) | **Deselect All**: Instantly clears selection on all minions when clicking air or terrain.                                       |
| **Keybind `H`**                    | In **BUILD** Mode        | **Tactical Zoom**: Cycles tactical camera zoom presets (0.75x, 1.0x default, 1.5x, 2.0x).                                      |
| **Arcane Build Flight**             | In **BUILD** Mode        | **Free Survival Build Flight**: Enjoy unconstrained 3D vanilla flight in Survival mode while holding the Scepter in **BUILD** mode (Space to ascend, Shift to descend, full WASD mobility). Flying over water automatically cancels construction and revokes flight! |
| **Ctrl + Scroll**                  | In **BUILD** Mode        | **Smooth Camera Zoom**: Smoothly zooms tactical view in and out without hotbar cycling conflicts.                                 |
| **Keybind `V`**                    | Anywhere (with Scepter)  | **Command Hub GUI**: Tactical screen for squads, modes, blueprints, 4-role archetypes (`AUTO`), **Architecture Style**, **Size**, and **Patrol Route Dashboard**. |
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
| **`PATHWAY`** | 🔷 Deep Aqua     | Place waypoint outlines and assign minions to autonomous patrol routes with 5 unique color channels.       |

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

## 🛡️ 4. Visual Pathway Patrol & Minion Escort Hierarchy

### A. 5 Color-Coded Route Channels
Patrol pathways are organized into 5 independent, color-coded channels:
- **Route 1**: 🟡 **Gold / Amber** (`0xFFD700`) — Base perimeter & main gates.
- **Route 2**: 🔵 **Azure / Cyan** (`0x00E5FF`) — Castle battlements & ramparts.
- **Route 3**: 🟢 **Emerald Green** (`0x00FF66`) — Farms, village borders & gardens.
- **Route 4**: 🟣 **Arcane Purple** (`0xB300FF`) — Nether portals & mine shafts.
- **Route 5**: 🔴 **Crimson Red** (`0xFF2244`) — Forward defensive trenches & killzones.

### B. Designing a Patrol Route in PATHWAY Mode
1. Switch to **`PATHWAY`** mode (**Shift + Right-Click** or in the Command Hub **`V`**).
2. **Right-Click Air** to cycle to your desired route channel (e.g. Route 1 Gold).
3. **Right-Click Ground Blocks** along your perimeter:
   - Each clicked block places a waypoint tile resting cleanly **on top of the surface** with a 3D holographic bounding wireframe.
   - An animated numbered billboarding badge hovers above the tile: `[ 1 ]`, `[ 2 ]`, `[ 3 ]`, etc.
   - Luminous 3D laser tether lines float directly on top of the block surface, connecting consecutive waypoints in order.
   - **Zero-Overlap Protection**: A waypoint position can only belong to **one** route channel. You can never accidentally place two route colors on the same block.
   - **Selective Visibility**: Pathway holograms and laser tethers are visible **only** when holding the Command Scepter in **`PATHWAY`** mode. Inactive channels remain vividly colored at 65% opacity, while the active channel pulses brightly with an inner core.
4. **Universal Deleting & Undo**:
   - **Left-Click (Punch) with Scepter**: Attack any placed waypoint block with the Scepter in `PATHWAY` mode to instantly remove it from whichever route channel it belongs to (with smoke particles and bass note sound without breaking the block!).
   - **Right-Click Undo / Removal**: Right-clicking an existing waypoint tile removes it from its route channel, even if your held Scepter is set to a different color channel! It will never place a duplicate waypoint on top.
   - **Sneak + Right-Click**: Sneak-right-clicking an existing waypoint deletes it without popping up the Command Hub GUI screen.
5. Want to start over? **Sneak + Right-Click Air** or click **Clear Route** in the Command Hub GUI (`V`) to clear all waypoints from the active route channel.
6. **Persistent Across Game Reloads**: All routes are automatically stored in world save data (`data/minion_patrol_routes.dat`) and reload seamlessly when joining the world!

### C. Assigning Minions to a Patrol Route
You have three intuitive ways to deploy minions onto a pathway:

1. **Direct Right-Click (Individual Assignment)**:
   - Hold the Command Scepter in **`PATHWAY`** mode.
   - Verify that your active route channel matches the one with waypoints (cycle with **Right-Click Air** or check the tooltip/GUI).
   - **Right-Click directly on an owned minion** (or aim at it with crosshairs up to 64m away).
   - The minion salutes, displays an overhead badge `[ 🟡 Route 1 ]`, and immediately begins marching to Waypoint `[ 1 ]`.
   - *(Right-clicking the minion again unassigns it from patrol duty).*

2. **90° Forward Sector Sweep (Mass Group Assignment)**:
   - Hold the Command Scepter in **`PATHWAY`** mode.
   - **Hold Right-Click** for $\ge 0.4\text{s}$ (8 ticks) aiming at your group of minions.
   - An expanding 90° forward arcane sector appears in front of you (growing from 3m to 16m), highlighting all minions inside the arc.
   - **Release Right-Click**: All minions inside the 90° sector are assigned to the active route, burst with celebration particles, and march out together!
   - *(Tip: If you aim into open space with no new minions in the cone, this will assign all minions you currently have **selected** to the route!)*
   - *(Note: Assigning to an empty route with 0 waypoints is prevented with an informative warning).*
   - *(Direct Command Precedence: Interacting with a minion using an empty hand to follow or hold position will immediately unbind it from patrol routes and escort chains).*

3. **Command Hub Mass Squad Dispatch (`V` Menu)**:
   - Press **`V`** to open the Command Hub.
   - Select **`Pathway`** mode.
   - Select your desired **Target Squad Channel** (e.g. `Alpha`, `Bravo`, or `ALL`).
   - Click the **Route Channel** you want to assign them to (e.g. Route 1).
   - Click **Execute Directive** (bottom-left button).
   - All minions in the chosen squad within range are immediately assigned to that route and march out together!

- **Autonomous AI (`MinionPatrolGoal`)**:
  - Minions march sequentially through each tile from first placed `[ 1 ]` to last `[ N ]`.
  - **Smooth Traversal (No Intermediate Stopping)**: Minions continuously march through intermediate pathway tiles without stopping.
  - **End-Only Vigilant Linger**: When reaching the **end** of a pathway (terminal waypoint before reversing in `PING_PONG`, or final waypoint before looping back in `LOOP`), minions halt and perform a **10s–15s (200–300 ticks)** vigilant sentry wait, turning to scan for threats like a watchman.
  - Over cliffs or walls, minions automatically engage Arcane Levitation / Obstacle Vaulting to never get stuck.
  - **Smart Combat Resume**: If a hostile approaches, the minion breaks off patrol to engage. Once the threat is eliminated, the minion snaps to the nearest waypoint and seamlessly resumes the patrol cycle!

### D. Linear Ping-Pong vs. Closed Loop Patrol (`[ 🔁 / 🏓 ]`)
- **Closed Loop (`LOOP`)**: Checkpoints connect continuously ($1 \to 2 \to 3 \to 1$). Ideal for perimeter walls, moat boundaries, and circular courtyard patrols.
- **Linear Ping-Pong (`PING_PONG`)**: Checkpoints traverse sequentially forward and reverse upon reaching terminal ends ($1 \to 2 \to 3 \to 2 \to 1$). Ideal for linear hallways, trench battle lines, bridge checkpoints, and battlement walks without minions cutting through the middle of the base!
- **Toggling**: Switch mode via the Command Hub (`V` key) Route Dashboard using the `[ 🔁 / 🏓 ]` button.

### E. Patrol Breach Alarm & Mobilization
- When a patrolling sentry detects a hostile mob crossing its route:
  - **Audible Sentry Alarm**: Sounds a deep goat horn blast and resonant raid bell (`ITEM_GOAT_HORN_SOUND_0` / `BLOCK_BELL_USE`).
  - **Visual Alarm**: Emits angry villager particles above the sentry's head.
  - **Allied Mobilization**: Instantly alerts all nearby allied minions within a **16-block radius**, calling them to break idling and converge on the breach to eliminate the intruder!

### F. Minion Escort Hierarchy (Squad Leaders & Bodyguards)
You can command minions to escort and protect another minion in two seamless ways:

#### Method 1: Shift-Punch to Designate Squad Leader (Instant Escort Assignment)
1. **Select Minions**: Left-click (punch without Shift) or use the 90° cone sweep (`Hold Right-Click`) to select one or more minions (even in **PATHWAY** mode) so they are selected and following you (e.g. two Sentinels).
2. **Shift + Punch the Leader Minion**: While holding the Command Scepter, **Shift + Left-Click (Shift + Punch)** the minion you want them to escort (e.g. a frontline Warrior).
3. **Automatic Deselection & Mutual Exclusivity**:
   - The punched minion becomes the **Squad Leader**!
   - All selected minions are immediately tethered to escort that leader.
   - All assigned escort minions are **automatically deselected from you** (`setSelected(false)`). They cannot follow you and will exclusively follow their squad leader.
   - **Self-Follow Safeguard**: The leader minion cannot follow itself. If it was already following you, it remains following you as point-man while the other minions fall into escort formation around it.
   - A resonant chime sounds (`BLOCK_NOTE_BLOCK_CHIME` & `BLOCK_AMETHYST_BLOCK_RESONATE`) and celebratory particles burst!

#### Method 2: Scepter Escort Priming
1. Hold the Command Scepter with 0 minions selected and **Shift + Left-Click** an unassigned minion.
   - HUD: *"🛡 Sentinel primed as Escort! Right-click another minion to assign as Leader."*
2. **Right-Click** the leader minion.
   - The follower is automatically deselected from you and tethers to the leader!

#### Escort Formations & Dedicated Leader Defense:
- **Escort Arcane Tether Beam**: Holding the Command Scepter renders a luminous cyan-to-magenta particle vector beam streaming from the escort to their squad leader.
- **Dynamic Army Formation Ranks (Any Squad Size)**:
  - **No Matter the Squad Size**: Whether 1, 2, 5, 10, or 20 minions escort a squad leader, every escort dynamically computes a unique, non-overlapping battle rank station.
  - **Role-Based Tactical Layout**:
    - **Sentinels**: Flank on lateral shoulders and outer wings ($\pm 1.35\text{D}$ to $\pm 3.60\text{D}$, $+1.8\text{D}$ forward), casting *Aegis of Restoration* whenever their leader drops below 70% HP.
    - **Warriors**: Form frontline shock lines ($+4.0\text{D}$ forward, $-2.0\text{D}$ per subsequent row), screening their leader in battle.
    - **Builders & Miners**: Guard the rear support column ($-2.0\text{D}$ rearward).
  - **Surface & Obstacle Vaulting**: Automatically resolves walkable surface elevation (`resolveWalkableY`) and engages Arcane Levitation flight to scale obstacles and ledges smoothly.
  - **Emergency Teleport**: If an escort is separated from their squad leader by >64 blocks, they immediately teleport to the leader with ender portal particles.
- **Dedicated Squad Leader Combat AI**:
  - **Defends the Leader, Not the Master**: Escorts automatically attack whoever damages their squad leader (`TrackLeaderAttackerGoal`) and coordinate attacks on whatever their leader attacks (`AttackWithLeaderGoal`), completely ignoring distant player skirmishes.
  - **16-Block Combat Leash**: If an enemy tries to lure an escort more than 16 blocks away from the squad leader, the escort breaks aggro and sprints back to formation.
  - **Post-Combat Regrouping**: Upon clearing combat targets, escorts sprint directly back to their squad leader at $1.35\text{D}$ speed, never returning to the player.
  - **Wander & Stray Suppression**: Idle wandering (`WanderAroundFarGoal`) and stationary waypoint holding are suppressed while escorting.
- **Automatic Detachment Upon Following You**:
  - If a minion is escorting another minion and you order it to follow you (via **normal punch / Left-Click**, **90° Cone Sweep**, or **empty-hand right-click**), it **automatically detaches** from that minion (`clearLeader()`) and joins your following squad!
  - You can also **Shift + Left-Click (Shift-Punch)** an escort minion with 0 minions selected to dissolve its escort bond directly (`Escort cleared; minion follows master.`).
  - Mass squad follow commands and normal panic retreats (`R`) preserve escort bonds; only **Emergency Citadel Call (`Shift + R`)** unbinds all escorts fortress-wide.

### G. Overhead Badge Status Indicators
Minions display real-time tactical overhead badges above their heads:
- `[ 🟡 Route 1 ]` / `[ 🔵 Route 2 ]`: Indicates active patrol channel assignment (only visible when the assigned route has active waypoints).
- `[ 🛡 Escort ]`: Indicates the minion is tethered to a squad leader as a dedicated bodyguard.
- `[ ⚙ AUTO: <Role> ]`: Indicates an autonomous agent displaying its currently adapted dynamic role (`Warrior`, `Sentinel`, or `Builder`).

---

## 🖥️ 4. The Command Hub Screen (Keybind `V`)

Press **`V`** with a scepter anywhere in your inventory to open the tactical command screen:

- **Squad Tabs**: Filter orders by `ALL`, `ALPHA`, `BRAVO`, `CHARLIE`, or `DELTA`.
- **Mode Bar**: Direct buttons for `FOLLOW`, `STAY`, `MINE`, `BUILD`, `RECRUIT`, and `PATHWAY`.
- **4-Role Archetype Bar**: Select an active role (`⚔ Warrior`, `🛡 Sentinel`, `🏗 Builder`, `⚙ AUTO`). When primed, your next **Banner of Courage** rally transfigures all gathered minions into this archetype!
- **Patrol Route Dashboard (in `PATHWAY` Mode)**:
  - Displays 5 route channel selection rows (`Route 1` through `Route 5`) with live waypoint counters (`[ 4 pts ]`).
  - Active channel is marked with `§e✦`.
  - **`[ 🔁 / 🏓 ]` Patrol Mode Toggle**: Instant one-click switching between **Closed Loop** ($1 \to 2 \to 3 \to 1$) and **Linear Ping-Pong** ($1 \to 2 \to 3 \to 2 \to 1$).
  - **`[ ✕ ]` Quick Clear**: Instantly purges all waypoints from the chosen channel.
- **Blueprint Browser (in `BUILD` Mode)**: Browse multiblock structure blueprints, view required resources, and project 3D wireframe holograms.
- **Deconstruction Mode (in `MINE` Mode)**: Toggle between construction and reverse top-down deconstruction.

---

## 🛡️ 5. Minion Archetypes & Ranks

| Archetype      | Preferred Weapon / Gear                                                        | Formation Position | Tactical Role                                                                                            |
| :------------- | :----------------------------------------------------------------------------- | :----------------- | :------------------------------------------------------------------------------------------------------- |
| **`WARRIOR`**  | Swords, Axes, Maces, Tridents **OR** Bows, Crossbows, Thrown Sticks (Frost/TNT) | Frontline Rank 1   | **Versatile Combatant & Hunter**: Frontline melee swordsman, ranged archer, or thrown javelin specialist. Features **Trident Duality** ($\le 5\text{D}$ melee thrust, $5\text{D}\text{--}20\text{D}$ thrown spear) and executes **Mob Procurement Contracts** for builders. |
| **`SENTINEL`** | Shield, Mace + Heavy Armor                                                     | Bulwark Rank 2     | **Defensive Guardian & Combat Medic**: Absorbs damage, holds fortified posts within an expanded 128-block leash, and channels the **Aegis of Restoration** to heal wounded players (commander priority) and allied minions under 70% HP. |
| **`BUILDER`**  | Pickaxes, Axes, Shovels + Toolset                                              | Rearguard Rank 3   | **Architect, Excavator & Supply Specialist**: 3D levitation flight to construct or dismantle multiblocks at any height. Autonomously quarries natural stone, harvests timber via agro-forestry (using bone meal for rapid growth), self-crafts tools, shares blocks via peer energy beams, deploys supply depot chests, and commissions **Squad Material Procurement** contracts to nearby Warriors for mob-derived resources. |
| **`AUTO`**     | Any Weapon / Tool (Universal Auto-Equip)                                        | Dynamic Rank       | **Autonomous Agent**: Continuously evaluates current tactical context. Morphs dynamically into **Sentinel** (if any ally < 70% HP), **Warrior** (if hostiles < 16m), or **Builder** (if blueprints active or peaceful). Shows live adapted role on overhead badge (`[ ⚙ AUTO: <Role> ]`). |

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

## 🔔 9. Dual-Tier Panic Retreat & Emergency Citadel Call (Keybind `R`)

The mod provides two tiers of tactical emergency recall:

### A. Quick `R`: Tactical Squad Retreat
Press **`R`** at any time while holding the Command Scepter (or with it in your inventory):
- **Range**: Recalls active squad thralls within **64 blocks**.
- **Combat Disengage**: Immediately clears combat targets on all active minions (`setTarget(null)`).
- **Cancel Holding/Guarding**: Cancels stationary guard or waypoint hold positions.
- **Wireframe Dismissal**: Instantly dismisses all 3D holographic wireframes and cancels active construction sessions for the commander.
- **Sprint Regroup**: Minions sprint back at 1.35x speed and reassemble into **Ranked Army Lines** behind you.
- **Audio/Visual**: Sounds a warning retreat bell (`SoundEvents.BLOCK_BELL_USE`), bursts cloud particles, and displays action bar confirmation: `§e🔔 RETREAT! [Squad] disengaging and falling back!§r`.

### B. `Shift + R`: Emergency Citadel Call (Fortress Muster)
Press **`Shift + R`** when an all-out emergency threatens your citadel:
- **Expanded Range**: Fortress-wide recall encompassing all owned minions within **128 blocks**!
- **Patrol & Escort Unbinding**: Unbinds **all minions from patrol duty** (`patrolRouteId = -1`) and **unlinks all escort bodyguards** (`clearLeader()`).
- **Stationing Override**: Overrides any sitting or holding postures regardless of squad channel.
- **All-Out Sentry Alert**: Sounds a piercing **Raid Horn** and clanging **Iron Bell** (`ITEM_GOAT_HORN_SOUND_0` / `BLOCK_BELL_USE`).
- **Dramatic Visuals**: Emits large bursts of smoke and purple nether portal particles.
- **Full Fortress Rally**: Every thrall drops what they are doing and sprints to the commander's defense!
- **HUD Alert**: Displays an urgent action bar banner: `§c🚨 CITADEL CALL! All 128m units abandoning posts and rallying to commander!§r`.

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

