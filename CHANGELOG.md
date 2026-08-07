# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.13.0] - 2026-08-04

### Added
- **Sprint 7 Stage 3 Persistence Layer (`rga-persistence` & `rga-api`):** Implemented embedded SQLite database engine localized at `/plugins/RonlabGameAssistant/data/rga.db` managed by single-threaded `dbExecutor` with bounded `ArrayBlockingQueue(2048)` and drop-and-log overflow policy.
- **Public Stats Provider API (`RGAStatsProvider` & `PlayerMinigameStats`):** Exposed non-blocking `RGAStatsProvider` interface and `PlayerMinigameStats` record in `rga-api:1.13.0` for cross-session stats recording (`recordMatchResult`) and querying (`getPlayerStats`, `getTopPlayers`).
- **Flyway Schema Migrations & SQLite WAL Mode:** Configured automated Flyway migration (`V1__init_stats_schema.sql`) for hybrid stats DDL (`player_stats` table with strict metrics + JSON metadata TEXT column and `idx_minigame_wins` index) with native `PRAGMA journal_mode=WAL;` and 3-try exponential backoff lock handling for `SQLITE_BUSY` errors.
- **Sprint 6 Core Engine Optimization (`WorldCopyManager`):** Implemented deterministic spawn injection (`applyDeterministicSpawn()`) in `WorldCopyManager.java`, overriding world spawn coordinates directly after template copy/generation. This completely eliminates synchronous main-thread heightmap scans (`PlayerSpawnFinder`) and resolves 15-second watchdog hangs during `Bukkit.createWorld()`.
- **CPMK Event Bus Standard (`rga-api` / `rga-plugin`):** Standardized event bus execution for companion plugins by strictly dispatching `MinigameStartEvent` and `MinigameConcludeEvent` with payload verification (checking mutable scores map, cancellation status, session IDs, and participant metadata).
- **PaperMC 26.2 Metadata Compliance:** Standardized `api-version: '26.2'` compliance in `paper-plugin.yml` and aligned dependency descriptors across `rga-parent`, `rga-api`, and `rga-plugin` POMs for Java 25 and PaperMC 26.2 runtime compatibility.
- **Session Occupancy Guard (`CorePlayerDeathListener`, Feature 3.1):** Created `CorePlayerDeathListener` executing at `EventPriority.LOWEST`. Yields 100% control of `PlayerDeathEvent` handling to companion plugin listeners whenever a player is in an active minigame session (`isPlayerInActiveSession(UUID)`).
- **Runtime Session Auditor Active Session Protection (Hotfix Bug 3.1):** Added `extractSessionId()` helper to strip `.wal`, `.yml`, and `.json` file extensions before memory lookups. Added a 60-second file age protection buffer (`GRACE_PERIOD_MS = 60_000L`) and Triple-Lock active state verification (`SessionManager`, `Bukkit` loaded worlds, `PartyManager` active sessions) to eliminate false-positive active session file deletions during live gameplay.
- **Portal Listener Priority & World Settings Hardening (Layer 2):** Elevated `PortalBlockListener` and `MinigameWorldListener` to `EventPriority.HIGHEST` with `ignoreCancelled = false`. Updated listeners to recognize `session_` world prefixes alongside `minigame_`. Added `allow-nether: false` and `allow-end: false` configuration fallback parsing in `WorldManager` and `MinigameManager`.
- **Default Resources & World Generator Settings:** Added CPMK companion minigame definitions (`deathrace`, `ultimate_tag`, `block_shuffle`, `turfwars`) to `minigames.yml` with clean `start-commands: []` and `conclude-commands: []` arrays. Created `paper-world-defaults.yml` resource and injected `paper-world.yml` (`unsupported-settings.suppress-feature-cross-chunk-loads: true`) into dynamic session world directories to suppress cross-chunk feature decoration console warnings.
- **Async Directory Deleter Engine (`AsyncDirectoryDeleter`, Sprint 1.1):** Implemented non-blocking background directory purges using Java `ScheduledExecutorService` and Java NIO (`Files.walkFileTree`) with exponential backoff retries (500 ms to 4s) to eliminate Windows OS `.mca` file lock TPS spikes.
- **Session Naming Isolation (Sprint 1.1):** Dynamic world folder generation in `WorldCopyManager` updated to format `session_[minigame]_[timestamp]_[shortUUID]` to prevent path collisions during concurrent session lifecycle operations.
- **Immutable Session Snapshot Model (`SessionSnapshot`, Sprint 1.2):** Introduced `SessionSnapshot` record and `SessionPhase` enum for point-in-time thread boundary safety without memory race conditions.
- **Write-Ahead Logging Engine (`SessionStateWALWriter`, Sprint 1.2):** Non-blocking WAL writer executing state transitions to `sessions/<session_id>.wal` asynchronously on a dedicated executor ($0\text{ ms}$ main thread tick overhead).
- **Runtime Session Auditor (`RuntimeSessionAuditor`, Sprint 1.2):** Periodic background auditing task running every 30 seconds (600 ticks) to detect phantom session directories on disk, offload purges to `AsyncDirectoryDeleter`, and auto-purge corrupted log folders older than 7 days from `/sessions/corrupted/`.
- **Power-Loss Recovery Handler (`PowerLossRecoveryHandler`, Sprint 1.2):** Startup recovery procedure in `onEnable()` that replays valid `.wal` entries and quarantines corrupted session directories into `/sessions/corrupted/` to ensure engine stability after abrupt server terminations.
- **JIT Spectator Management API (`RGASessionControl.setSpectator`):** Public programmatic interface for companion plugins to manage spectator mode JIT state (inventory capture/restore, advancement toggling, hub routing) without direct inventory management.
- **Typed Companion Bridge Protocol:** Fully supported companion bridges (e.g. `rga-turfwars`, `rga-announcer`, `Block-Shuffle`) using typed `RGASessionControl` interface contracts instead of Java reflection.
- **Data Folder & Resource Guards:** Strictly guarded default YAML extraction routines in `RGA.saveResourceIfNotExists` and `ConfigManager` to prevent console log warnings when configuration files exist.

### Changed
- **System.gc() Elimination (Sprint 1.1):** Removed all `System.gc()` invocations from `FileUtils.deleteDirectoryWithRetry()` and delegated disk teardowns to `AsyncDirectoryDeleter`.
- **Orphan Log Management (Sprint 1.1):** Updated `WorldManager.purgeOrphanedSessionFolders()` to clear and truncate `orphaned_sessions.log` to 0 bytes after reading and purging orphaned paths.
- **Version Lock (`1.13.0`):** Kept version `1.13.0` locked across `rga-parent`, `rga-api`, `rga-plugin` POMs, and `paper-plugin.yml` while companion plugins target development.
- **Companion Ecosystem Compatibility:** Aligned companion plugins (`rga-turfwars`, `rga-announcer`, `rgaParkour`, `Block-Shuffle`) against the `1.13.0` API release baseline.

---

## [1.12.1] - 2026-07-24

### Changed
- **Repository Structure:** Relocated internal design documents, ADRs, and migration checklists into `docs/` using `git mv` to preserve history.
- **Dependency Alignment:** Locked Paper target to `26.2` and Java to `25` across all POMs and companion templates.

### Fixed
- **Config Log Spam (#M1):** Wrapped `saveResource()` calls in `ConfigManager` with file-existence guards to eliminate startup log warnings.
- **Windows File Handle Leaks:** Documented explicit `deleteDirectoryWithRetry()` GC routines releasing `.mca` region locks on world teardown.

---

## [1.12.0] - 2026-07-24

### Added
- **Orphan Session Recovery (#43):** Write-ahead persistence logging and boot-time session purging for crash recovery.
- **Concurrency Safeguards (#40):** `WorldCopyManager` thread safety using `ReentrantLock` maps per world template.
- **Config Immutability (#42):** Immutable wrapper list/map protection for loaded minigame configurations.
- **Module Shading:** Configured `maven-shade-plugin` in `rga-plugin/pom.xml` to shade `rga-api` into the primary plugin artifact (`RonlabGameAssistant-1.12.0.jar`).
- **Programmatic Conclude API:** Implemented programmatic session conclusion API & event (`RGAGameRequestConcludeEvent`, `requestSessionConclude`).

### Fixed
- **Datapack Stripping (#39):** Async world copy logic now strips `/datapacks/` directories on world creation.
- **Yaml Config Hardening (#30):** Enforced strict `isSet()` precedence logic for `disable-nether` and `disable-end` dimension toggles.

---

### [1.11.0] - 2026-07-24


- Implemented party persistence grace period for hub visits (resolves #29).
  - Added configurable grace period timer system allowing players to temporarily leave their party to visit the hub without immediately disbanding.
  - Configuration options: `grace-period.enabled` (default `true`), `grace-period.duration-seconds` (default `60`), and `grace-period.allow-in-game` (default `true`).
  - When a player enters the hub, they are marked as "away" and a BukkitTask timer is started; if the player returns before timeout, the grace period is canceled and the player rejoins the party.
  - If the grace period expires, the player is removed from the party; if this leaves the party empty in LOBBY state, the party is disbanded; if in IN_GAME state, the game concludes cleanly.
  - Ready-up button is blocked while any party member is in grace period (via `Party.hasAwayPlayers()` check).
  - Event handlers: `onPlayerChangedWorld()` triggers grace period on hub entry, `onPlayerJoin()` cancels on return, `onPlayerQuit()` handles disconnections during grace period.
  - Duplicate timer guard prevents multiple timers for the same player.
  - Manual testing guide (`GRACE_PERIOD_MANUAL_TEST.md`) documents 10 verification scenarios covering hub visits, timeouts, disconnections, configuration options, in-game state handling, and queue interactions.

### v1.9.0

- Added configurable hub entry and restore-on-return inventory behavior (resolves #23).
  - Config flags `hub-entry.clear-inventory-on-entry` (default `true`) and `hub-entry.restore-inventory-on-return` (default `false`) added to `config.yml`.
  - In-memory `HubSnapshot` records pre-hub inventory and origin world group when entering Hub with `restore-inventory-on-return: true`.
  - Exiting Hub back to the same origin world group restores the snapshot instantly without disk I/O; entering a different world group discards the snapshot to preserve world group isolation boundaries.
  - Snapshot cleaned up on `PlayerQuitEvent` to prevent memory leaks for offline players.
  - `HubListener.onJoin` respects `clear-inventory-on-entry` to gate initial join wipes.
  - New test suite `InventoryManagerHubEntryTest` verifies clear, preserve, group-scoped restore, group switch invalidation, and quit cleanup behavior.

### v1.8.0

- Added `first-visit-spawn` configuration support to `worlds.yml` (resolves #22).
  - New optional `first-visit-spawn` key per world entry accepts coordinates in Map (`{x, y, z, yaw, pitch}`), String (`"x, y, z"` — yaw/pitch optional, default `0`), or List (`[x, y, z]`) format.
  - `LocationTracker` upgraded to per-world location tracking: player locations are now stored under `<uuid>.worlds.<worldName>` in `player-data.yml`.
  - Veteran players with legacy flat `<uuid>.x/y/z` data are seamlessly migrated on first access; no player is treated as a first-time visitor after the update.
  - Players with no tracked location for a world are teleported to the configured `first-visit-spawn`; falls back to the standard world spawn if not configured.
  - `WorldManager.teleportToWorld()` now checks for a per-world tracked location first (veteran behaviour), then falls back to `first-visit-spawn` or world spawn.
  - New `FirstVisitSpawn` data class and `WorldSettings.getSpawnLocation(World)` helper encapsulate the fallback hierarchy.
  - 6 new unit tests in `WorldSettingsTest` covering all parsing formats and fallback behaviour.

### v1.7.0

- Finalized minigame spectator support implementation (resolves spectator task in Phase 4 roadmap).
  - Added per-minigame spectator configuration options `allow-spectators` (default `true`) and `max-spectators` (default `4`) under `minigames.yml`.
  - Updated `PartyManager.joinAsSpectator()` to enforce `allow-spectators` toggles and max spectator limits before allowing entry.
  - Added `%spectators%` command placeholder replacement support in command execution logic for start and conclude lifecycle hooks.

### v1.6.0

- Added minigame queueing and auto-start behavior (resolves the queueing issue in Phase 4 roadmap).
  - When a minigame is `IN_GAME`, new players are placed in a FIFO queue instead of being rejected.
  - Queued players see a queue status GUI showing their position and party size.
  - When a game concludes, the next queued party is auto-promoted to the lobby (standard ready-up flow) if `auto-start: true` in `minigames.yml`.
  - `/rga queue` command shows all active queues with position and member details.
  - Full disconnect/leave handling for queued parties: leader transfer, queue position updates, disband notification.
  - Per-minigame config toggles: `queue-enabled` and `auto-start` in `minigames.yml`.

### v1.5.0

- Implemented graceful shutdown cleanup and recovery (resolves #19).
  - Added robust `onDisable()` hooks to cleanup all active party sessions.
  - Teleports online players to hub and clears temporary inventory groups during shutdown to protect player state.
  - Preserves session data files upon shutdown as recovery files.
  - Scans for and restores orphaned session data on next startup, cleanly recovering player inventories, advancements, and locations when they next connect.
  - Reports recovery file status on plugin enable/disable.

### v1.4.1

- Added `/rga status` for runtime visibility into active sessions, loaded managed worlds, and pending recovery state (resolves #18).
  - The new command reports session world names and player counts, loaded world state/gamemode/PvP details, and whether orphaned recovery data is pending.
  - It uses the existing session and world managers so status reflects the live runtime state of the plugin.

### v1.4.0

- Added configurable console command allowlist support for lifecycle hooks and menu actions (resolves #17).
  - `config.yml` now supports `console-command-allowlist` for restricting `console:` commands.
  - Commands outside the allowlist are blocked and logged as warnings.
  - The allowlist is read from config at runtime, so it can be reloaded without restarting the server.

### v1.3.0

- Added progress feedback and countdown before minigame teleport (resolves #16).
  - Party members now receive an in-progress action-bar message when template-world copy begins.
  - A configurable 3-second countdown displays Adventure titles and optional sound cues before the party is teleported into the arena.
  - Countdown logic validates party membership and online presence each tick, aborting safely if the party becomes unavailable or all players disconnect.

### v1.2.0

- Converted template world copy I/O to async operations (resolves #15).
  - `WorldCopyManager.copyTemplateWorld()` now offloads folder copying to `CompletableFuture.supplyAsync()` to prevent main-thread I/O lag.
  - Bukkit world registration still occurs on the main thread via `runTask()` to preserve thread safety.
  - `PartyManager.startGame()` properly chains async copy with `thenAccept()` for template-based minigames.

### v1.1.2

- Replaced legacy ChatColor and material parsing (resolves #14).
  - All `ChatColor.translateAlternateColorCodes()` and `ChatColor.RED`/`YELLOW`/`GRAY` usages replaced with Adventure API `Component` objects.
  - All hardcoded `§` section color codes in command feedback, party messages, and GUI items migrated to Adventure Component builder pattern.
  - All `Material.matchMaterial()` calls replaced with registry-safe `Registry.MATERIAL.get(NamespacedKey)` via new `AdventureUtil.safeMaterial()` utility.
  - New `util/AdventureUtil.java` provides `color(String)` → `Component` and `safeMaterial(String, Material)` → `Material` helpers.
  - Config YAML files continue to work with existing `&` color codes unchanged (backward-compatible via `LegacyComponentSerializer.legacyAmpersand()`).
  - `ConfigManager.getMessage()` return type changed from `String` to `Component`; all callers updated.

### v1.1.1

- Added graceful world load failure handling (resolves #13).
  - `loadConfiguredWorlds()` now wraps each world in a per-world try-catch, so one bad world doesn't block all others.
  - `loadWorld()`, `loadExistingWorld()`, `importWorld()`, `createWorld()` each have method-level exception protection with actionable SEVERE-level logging (exception type, message, stack trace).
  - `upgradeLegacyLayout()` broadened from `IOException` to `Exception` catch to also handle `RuntimeException` during file operations.
  - Failed worlds are cleanly skipped; all successfully loaded worlds continue to function normally.

### v1.1.0

- Implemented write-ahead session persistence and crash recovery (resolves #12).
  - New `SessionManager` persists active game state to `plugins/RonlabGameAssistant/sessions/<worldName>.yml`.
  - Write-ahead snapshot written before world creation; closes durability gap by moving `party.setState(IN_GAME)` inside the creation task.
  - Orphaned sessions auto-detected on startup with actionable admin logging.
  - Crash-recovered players bypass Hub reset on login; inventory, advancements, and location restored silently.
- Added advancement snapshot + revoke-first restore with convergence loop.
  - Pre-game advancement criteria captured before wipe at game start.
  - Normal game conclusions now restore pre-game advancements (fixes permanent advancement wipe during regular play).
- Added `/rga cleanupsession <worldname>` command (`rga.session.cleanup`) for manual orphan cleanup.
- Added `/rga sessions list` command (`rga.session.status`) to view active and orphaned sessions.
- Updated `rga.admin` permission tree to include `rga.session.cleanup` as a child node.

### v1.0.4

- Implemented Paper 26.1 Nested World Handling, out-of-place upgrades, verification, and timestamped legacy backups under `_legacy_backups/` (resolves #11).
- Updated `WorldNameValidator` with a broader, safe regex pattern to accept dots and hyphens, preventing path traversal, and integrated name checks into command entry points (closes WorldManager portion of #26).
- Excluded the backup directory from tab completions.

### v1.0.3

- Expanded `rga.admin` into granular permission nodes (`rga.reload`, `rga.world.teleport`, `rga.world.manage`, `rga.world.configure`, `rga.session.conclude`, `rga.session.status`).
- Per-subcommand permission enforcement in `RGACommand.java`.
- Tab completion now filters to only commands the sender has permission to use.

### v1.0.2

- Hardened input validation for world names at config load and command execution.
- Added placeholder sanitization and safety pre-dispatch checks for GUI actions and lifecycle commands.

### v1.0.1

- Migrated metadata and commands to `paper-plugin.yml` (Paper API 26.1).
- Implemented a startup version guard checking for Paper API 26.1.2+.
- Upgraded target compilation to Java 25.

### v0.19.0

- Moved targets to paper-26.1.2 and java-25

### v0.18.0

- Added social item for easier access to parties.

### v0.17.5

- Patch for spawn preferences.

### v0.17.4

- Patch for spawn preferences.

### v0.17.3

- Patch for spawn preferences.

### v0.17.2

- Patch for spawn preferences.

### v0.17.1

- Patch for spawn preferences.

### v0.17.0

- Patch for spawn preferences.

### v0.16.1

- Added gamerule on startup enforcement for specific worlds.

### v0.16.0

- Added minigame configuration.

### v0.15.0

- Added world option to disable the nether.

### v0.14.0

- Changed advancement system to only wipe on minigame conclusion.

### v0.13.1

- Patch for cutom world system for "template" minigames.

### v0.13.0

- Patch for cutom world system for "template" minigames.

### v0.12.1

- Added conclude command in minigame configuration.

### v0.12.0

- Added autofill for conclusion command.
- Added conclude all command.

### v0.11.0

- Patch for spawn preferences in "vanilla" minigames where spawns can be set with beds and respawn anchors.

### v0.10.0

- Added advancement system to save, wipe, and restore when minigames start and finish.

### v0.9.1

- Added delay to conclusion command execution.
- Patch for world spawning preferences.

### v0.9.0

- Patch for spawning preferences.

### v0.8.3

- Added integration for conclude command to communicate with integration in mingame plugins.

### v0.8.2

- Added starting commands options for player positions in party.

### v0.8.1

- Changed dimenion suffixes in world manager.

### v0.8.0

- Added portal reroutes for use in "vanilla" minigames.
- Added feature so respawns happen in correct worlds within minigames.
- Added inventory groups for minigame worlds.
- Changed how the world generation works for "vanilla" minigames.

### v0.7.2
