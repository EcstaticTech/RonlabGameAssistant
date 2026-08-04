# Ronlab Game Assistant (RGA)

A comprehensive Minecraft server plugin for managing minigames, player progression, and multi-world environments on friend-run PaperMC servers.

## Overview

**What It Does**

Ronlab Game Assistant is an all-in-one game management system designed to orchestrate complex minigame experiences on small Minecraft servers. It provides seamless world management, party-based minigame lobbies, inventory sharing, advancement persistence, and automated game session lifecycle management.

**Why It Exists**

Friend servers often want to run multiple minigames and activities alongside survival gameplay. RGA solves the complexity of managing:
- Multiple isolated worlds for different game modes
- Player parties/groups joining and playing minigames together
- Inventory separation or sharing between worlds based on game logic
- Automatic world generation and cleanup for minigame instances
- Player state preservation (location, advancements) between sessions
- Guided navigation through available worlds and activities

**Who It's For**

Small friend servers (2-32 players) running PaperMC who want a structured minigame system without the complexity of multiple disparate plugins. The plugin emphasizes ease of configuration over out-of-the-box behavior.

---

## How It Works

### Architecture Overview

RGA uses a modular manager-based architecture where each subsystem handles a distinct responsibility:

```
RGA (Main Plugin)
├── ConfigManager          → Loads and caches YAML configurations
├── WorldManager           → Creates, loads, and manages world instances
├── MenuManager            → Builds and manages GUI menus
├── LocationTracker        → Persists player positions per world
├── InventoryManager       → Groups worlds to share inventories
├── AdvancementManager     → Saves/restores achievements during games
├── MinigameManager        → Defines and provides minigame configurations
├── PartyManager           → Manages player groups and game sessions
├── MenuListener           → Handles GUI interactions
├── CompassListener        → Handles navigator compass usage
└── SocialListener         → Handles party browsing and social features
```

### Core Systems

#### 1. **World Management**

The plugin manages both persistent and temporary worlds:

- **Persistent Worlds**: Hub, SMP (Overworld/Nether/End), Creative, Adventure
  - Defined in `worlds.yml`
  - Loaded at startup with configured settings (gamemode, difficulty, PvP, time-lock, weather-lock)
  - Player locations automatically tracked and restored when returning
  - Optional `first-visit-spawn` coordinates per world for players visiting without prior location data

- **Minigame Worlds**: Temporary instances created for game sessions
  - Generated on-demand with isolated copies of template worlds (for custom maps)
  - Or freshly generated vanilla instances (for procedural games)
  - Destroyed after concluding to save disk space
  - Full multiverse support (overworld, nether, end dimensions)

**Technical Implementation**: 
- Uses PaperMC's `WorldCreator` API
- Handles world environment setup (NORMAL, NETHER, THE_END)
- Applies per-world gamerules and settings
- Manages portal routing between dimensions

#### 2. **Party & Minigame System**

Parties are groups of 2-8 players who join a minigame together through a lobby interface:

**Party Lifecycle**:
1. Player joins minigame lobby → Creates or joins existing party
2. Party leader starts game → All players teleported to minigame world
3. Game runs with isolated inventory and advancement state
4. `/rga conclude` called → Game ends, players teleported back, state restored
5. Party disbanded or players return to lobby

**Key Features**:
- Configurable player count limits per minigame
- Lobby GUI shows party members and ready status
- Leader-only start button
- Automatic leader transfer on disconnect
- Automatic party cleanup if all members leave
- **Party Persistence Grace Period**: When party members visit the Hub temporarily, a grace-period timer prevents premature party disbanding during transient transitions
- **Minigame Queueing & Auto-Start**: Full minigames support queueing waiting parties, automatically launching the next party when a session concludes

#### 3. **Inventory Management**

Prevents inventory overlap when multiple worlds exist:

- **Grouped Worlds**: Worlds in the same inventory group share items (e.g., SMP overworld/nether/end)
- **Isolated Worlds**: Each world gets its own inventory container if not grouped
- **Hub Entry Behavior**: Configurable hub inventory-clearing confirmation and optional restore-on-return behavior (`config.yml`)
- **Minigames**: Isolated per-session inventory; restored after game ends

#### 4. **Advancement Persistence**

Prevents minigame achievements from polluting permanent advancement data:

- Saves all player advancements before minigame start
- Wipes advancements during the game (isolated experience)
- Restores original advancements when game concludes
- Optional per-minigame advancement reset on conclusion

#### 5. **Navigation & UI**

**Compass Item**:
- Right-click to open World Navigator menu
- Shows available worlds with configurable GUI layout
- Displays active player counts per world
- Click actions mapped to teleport or execute commands

**Social Item**:
- Browse open party lobbies
- Quick-join existing parties
- Return to current party if already in one

**Custom Menus** (via `menus.yml`):
- Fully configurable inventory GUIs
- Support for left/right-click actions
- Action commands execute with customizable behavior

#### 6. **Game Session Command Execution & Role-Based Context**

Minigames can define custom commands to run at specific lifecycle points with role-based execution contexts:

**Start Commands** (after all players teleported in):
- Support for role-based execution: `console:`, `player-each:`, `leader:`
- Placeholders: `%world%`, `%leader%`, `%players%`, `%player%`
- Examples:
  ```yaml
  start-commands:
    - "console: team assign runner %leader%"
    - "player-each: tell %player% Good luck!"
    - "leader: say The game starts now in %world%!"
  ```

**Conclude Commands** (before players teleported out):
- Same format as start commands
- Useful for stopping game plugins cleanly
- Example: `console: manhunt stop %world%`

#### 7. **Companion Plugin Event-Driven Integration API**

RGA exposes an event-driven Java API (`rga-api` module) under `com.ronlab.rga.api.event` for companion plugins (such as Block-Shuffle) to integrate natively without relying solely on console command hooks:

- **`MinigameStartEvent`**: Fired post world-load and pre-teleport. Cancellable by companion listeners to abort session launch cleanly.
- **`MinigameConcludeEvent`**: Fired when a game session concludes. Cancellable, and includes a **mutable scores map** (`getScores()`) allowing companion plugins to record winner scores and game stats.
- **`GameSessionRequestConcludeEvent`**: Allows companion plugins to trigger session conclusions directly via Java event dispatch with execution feedback.

For complete integration contracts, event ordering rules, and reload behavior, refer to [EVENT_API_KNOWN_LIMITATIONS.md](EVENT_API_KNOWN_LIMITATIONS.md).


---

## Technical Requirements

### Server & Java Requirements
- **Minecraft Version**: 26.1.2+ (tested on PaperMC)
- **Server Software**: PaperMC (Paper or compatible forks)
- **Java Version**: Java 25 or later

### Dependencies
- PaperMC API 26.1.2-R0.1-SNAPSHOT
- No external plugin dependencies

### Hardware Requirements (Recommended)
- **RAM**: 2GB per active minigame world (consider multiple simultaneous games)
- **Disk**: 500MB base + template world sizes (template copying can be I/O intensive)
- **CPU**: Minimal; world copying and teleportation are I/O bound

### File System
- Standard Minecraft server directory structure required
- Template worlds placed in server root alongside main `world/` folder
- Config files stored in `plugins/RonlabGameAssistant/` directory
- World data stored in standard Minecraft location

---

## Features & Capabilities

### Implemented Core Features ✓

- **Multi-world environment management** with persistent and temporary worlds
- **Party-based minigame lobbies** with player count limits
- **Inventory grouping system** for shared or isolated inventory containers
- **Location tracking** to restore player position across world visits
- **Advancement persistence** to save/restore player achievements
- **World copying** for template-based minigame maps
- **Vanilla world generation** for procedural minigames
- **Customizable GUI navigation system** with click actions
- **Social browsing** to find and join party lobbies
- **Automated game lifecycle** (start, conclude, cleanup)
- **Command execution system** with role-based and placeholder support
- **Configurable console command allowlist** for lifecycle hooks and menu actions
- **World gamerule enforcement** (time-lock, weather-lock, specific rules)
- **Portal rerouting** for minigame dimension management
- **Inventory isolation** per minigame session
- **Per-world difficulty, gamemode, and PvP settings**
- **Minigame queueing** with auto-start next party on game conclude
- **Responsive lobby updates** with real-time player count

### Configuration Resources

All plugin behavior is customizable through YAML files:

- **`config.yml`** - Hub world, SMP worlds, compass/social item settings, inventory groups
- **`worlds.yml`** - World definitions with environment, gamemode, difficulty, time/weather locks, and optional `first-visit-spawn` coordinates
- **`menus.yml`** - Custom GUI menus with items and click actions
- **`minigames.yml`** - Minigame definitions with world types, player limits, commands

---

## Technical Limitations & Known Constraints

### Current Limitations

1. **Single Server Only**
   - No cross-server party or world support
   - All minigame worlds must be on the same physical server

2. **Inventory Sharing**
   - Inventory groups are static; cannot be changed during gameplay
   - No partial inventory sync between worlds
   - Armor and equipped items treated same as inventory items

3. **Template World Copying**
   - Template worlds must be placed as top-level folders in the server directory
   - Large template worlds (>100MB) may cause noticeable I/O lag when copied
   - Template worlds are copied fresh each game; modifications between sessions are lost

4. **Advancement System**
   - Only saves/restores advancement state (not progress within achievements)
   - Cannot cherry-pick specific advancements to preserve
   - All-or-nothing: either save all or wipe all

5. **World Environment Isolation**
   - Cannot have overworld+nether+end in same party inventory group
   - Dimension suffixes hardcoded (e.g., `_nether`, `_the_end`)
   - Limited cross-dimension communication

6. **Party Management**
   - No persistent parties (disbands on all-players-leave)
   - No party chat or messaging system
   - Leader cannot delegate or split decision-making

7. **Minigame Coupling**
   - Requires custom start/conclude commands to interact with external minigame plugins
   - No built-in score tracking or win conditions
   - Game state management delegated to external plugins

### Architectural Constraints

- **Configuration Reload**: `/rga reloadconfig` reloads configuration files, GUI menus, and world definitions (alias: `/rga reload`). Note: Changes apply to future sessions; active minigames will continue using their original settings until they conclude.
- **World Deletion**: Old minigame worlds must be manually cleaned if deletion fails
- **Session Persistence**: Write-ahead session snapshots (`session_recovery.yml`) survive server restarts. Crashed/restarted servers automatically detect active persistence states on boot, flagging missing companion hooks as `ORPHANED` while keeping explicit deletion triggered by `/rga cleanupsession <world>`.
- **Asynchronous World Operations**: Template world copying is offloaded asynchronously with active progress countdown feedback before teleportation.
- **Role-Based Command Execution**: Support for role-specific execution scopes (`console:`, `player-each:`, `leader:`) with interpolation placeholders (`%world%`, `%leader%`, `%players%`, `%player%`).


### Performance Considerations

- Multiple simultaneous minigame instances increase RAM and I/O load
- Large template worlds impact startup time when copied
- Advancement save/restore is CPU intensive for many players
- Menu GUI creation happens synchronously; many concurrent menu opens may lag

---

## Commands

### Admin Commands
```
/rga reloadconfig       - Reloads configuration files, GUI menus, and world definitions. Note: Changes apply to future sessions; active minigames will continue using their original settings until they conclude. (alias: /rga reload)
/rga tp <world>         - Teleport to a world
/rga conclude <world>   - Conclude a minigame session in a world
/rga createworld <name> - Create a new world
/rga compass            - Get the navigation compass item
/rga listworlds         - List all loaded worlds
/rga importworld <name> - Import an existing world folder
/rga loadworld <name>   - Load an existing world
/rga unloadworld <name> - Unload a world
/rga deleteworld <name> - Delete a world permanently
/rga setspawn [world]   - Set spawn point
/rga setworldgamemode <world> <mode>
/rga setworldpvp <world> <true|false>
/rga setworlddifficulty <world> <difficulty>
/rga setworldtime <world> <day|noon|night|midnight|ticks|-1>
/rga setworldweather <world> <true|false>
/rga setworldalias <world> <alias>
/rga setworldtemplate <world> <true|false>
/rga gamerule <world> <rule> <value>
/rga concludeall        - Conclude all active minigames
/rga cleanupsession <worldname> - Delete orphaned session and world data
/rga queue              - Show minigame queue status
/rga sessions list      - List active and orphaned sessions
```

### Permission Nodes

| Node | Grants | Default |
|---|---|---|
| `rga.admin` | Wildcard — all nodes below | `op` |
| `rga.reload` | `/rga reloadconfig`, `/rga reload` | `op` |
| `rga.world.teleport` | `/rga tp`, `/rga compass`, `/rga listworlds` | `op` |
| `rga.world.manage` | `createworld`, `importworld`, `loadworld`, `unloadworld`, `deleteworld` | `op` |
| `rga.world.configure` | `setspawn`, `setworldgamemode/pvp/difficulty/time/weather/alias/template`, `gamerule` | `op` |
| `rga.session.conclude` | `/rga conclude`, `/rga concludeall` | `op` |
| `rga.session.status` | `/rga sessions list`, `/rga queue` | `op` |
| `rga.session.cleanup` | `/rga cleanupsession <worldname>` | `op` |
| `rga.hub` | `/hub` | `true` (all players) |

> [!NOTE]
> Existing server configs granting `rga.admin` continue to work unchanged — it remains the wildcard parent.

### Player Commands
```
/hub                    - Return to the Hub world
```

**Permission**: `rga.hub` (default: true for all players)

### GUI-Based Interaction
- **Compass**: Right-click to open World Navigator menu
- **Social Item**: Right-click to browse open parties or return to current party
- **Minigame Lobby**: Interact with items to join/leave/start games

---

## Configuration Quick Start

### 1. Add a New World
Edit `worlds.yml`:
```yaml
worlds:
  MyNewWorld:
    load-on-startup: true
    environment: NORMAL
    gamemode: ADVENTURE
    pvp: false
    difficulty: NORMAL
    time-lock: 6000
    weather-lock: true
    # Optional: where players without a tracked location spawn for the first time
    # first-visit-spawn: {x: 0, y: 64, z: 0, yaw: 0, pitch: 0}
```

### 2. Add a New Minigame
Edit `minigames.yml`:
```yaml
minigames:
  parkour:
    name: "Parkour Challenge"
    display-item: IRON_BOOTS
    max-players: 4
    min-players: 1
    world-type: TEMPLATE
    template-world: ParkourMap
    start-commands:
      - "console: say Welcome to %world%!"
```

### 3. Add a Custom Menu Item
Edit `menus.yml`:
```yaml
menus:
  navigator:
    items:
      custom_item:
        material: CHEST
        name: "&9My Custom World"
        slot: 12
        left_click:
          - "rga:tp MyNewWorld"
```

### 4. Create Inventory Groups
Edit `config.yml`:
```yaml
inventory-groups:
  my-group:
    worlds:
      - "world1"
      - "world2"
      - "world3"
```

---

## Development Status

### Current Version: 1.13.0

**Core Functionality**: ✓ Complete and usable
- All major systems functional and tested
- Suitable for production use on friend servers

**Areas for Future Development**:

1. **Enhanced Party Features**
   - Persistent party storage (database integration)
   - Party invitations and accept/decline workflow
   - Party chat and messaging
   - Party-specific settings (custom gamerules per party)

2. **Better Minigame Integration**
   - Built-in score tracking and leaderboards
   - Win condition detection and automatic conclude
   - Replay/highlight system

3. **World Improvements**
   - Incremental world backups instead of full copies
   - World preloading/caching


4. **Inventory System Enhancements**
   - Partial inventory sync between worlds
   - Custom loadout system per minigame
   - Armor preservation across worlds

5. **Performance Optimizations**
   - Configuration file hot-reload support
   - Caching improvements for menu rendering
   - Batch advancement operations

6. **Cross-Server Support** (Long-term)
   - Redis-backed party synchronization
   - BungeeCord/Velocity integration
   - Shared leaderboards across servers

7. **Administrative Tools**
   - Web dashboard for configuration
   - Real-time monitoring of active parties/worlds
   - Advanced logging and analytics

---

## Installation

1. Download the compiled JAR (or build from source)
2. Place in `plugins/` directory
3. Restart server (or use `/reload`)
4. Customize `config.yml`, `worlds.yml`, `menus.yml`, `minigames.yml` in `plugins/RonlabGameAssistant/`
5. Place template world folders in server root if using template-based minigames
6. Restart server to load new worlds

---

---

## Building from Source

**Requirements**: Java 25+, Maven 3.9+

RGA uses a multi-module Maven build structure:
- `rga-api` — Companion plugin integration API (`com.ronlab:rga-api`)
- `rga-plugin` — PaperMC server plugin (`com.ronlab:rga-plugin`)

To build all artifacts from the repository root:
```bash
mvn clean package
```

Artifacts produced:
- **API JAR**: `rga-api/target/rga-api-1.13.0.jar` (standalone Maven dependency artifact for companion plugin developers)
- **Plugin JAR**: `rga-plugin/target/RonlabGameAssistant-1.13.0.jar` (shaded plugin bundle including `rga-api`; deploy directly to server `plugins/` directory)


---

## Architectural Documentation

For in-depth architectural design decisions, event contracts, and system constraints:

- **[docs/EVENT_API_KNOWN_LIMITATIONS.md](docs/EVENT_API_KNOWN_LIMITATIONS.md)**: Details event dispatch ordering, state mutation contracts, failure path behaviors, and companion plugin migration guides.
- **[ADR-0002 — Datapack Isolation Strategy](docs/adr/ADR-0002-datapack-isolation.md)**: Architectural decision record evaluating isolated minigame instance datapacks vs global server datapacks.

---

## Support & Development

Developed for small friend servers running PaperMC. The plugin prioritizes configurability and extensibility through YAML files, event listeners, and command hooks. External minigame plugins integrate via the `rga-api` event model or start/conclude command execution.

For issues, enhancements, or questions about the plugin architecture, refer to the source code comments, architectural docs, and configuration examples.

---

## License

See LICENSE file in repository.

