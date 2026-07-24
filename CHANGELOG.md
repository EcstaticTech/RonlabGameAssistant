## Changelog

### v1.12.0

- Implemented programmatic session conclusion API & event (resolves #38 and #27).
  - Created `RGAGameRequestConcludeEvent` in `com.ronlab.rga.api.event` extending `MinigameEvent` and implementing `Cancellable` with isolated static `HandlerList`.
  - Expanded `ConcludeResult` enum with operational failure modes: `SUCCESS`, `CANCELLED`, `NOT_FOUND`, `ALREADY_CONCLUDING`, and `ERROR`.
  - Added `CONCLUDING` state to `Party.State` enum and audited state handling across `PartyManager` and `RGACommand`.
  - Implemented `RGA.requestSessionConclude(worldName, reason, scores)` and `PartyManager.requestSessionConclude(...)` with main-thread safety guard (`Bukkit.isPrimaryThread()`).
  - Added safe score conversion routine with overflow clamping (`Integer.MIN_VALUE`..`Integer.MAX_VALUE`) and numeric truncation logging.
  - Published companion plugin integration guide in `docs/companion-integration.md` and updated `EVENT_API_KNOWN_LIMITATIONS.md`.
  - Added unit test suite `SessionConcludeApiTest.java` verifying successful conclusions, event cancellations, thread safety guards, state guards, and score conversion safeguards.

- Hardened YAML configuration parsing, defensive enum parsers & dimension toggles (resolves #30).
  - Enforced strict `isSet()` precedence logic for `disable-nether` and `disable-end` dimension toggles (`world-settings (if set)` > `minigame root (if set)` > `false`).
  - Added absent/null-valued command list warnings using `!section.isSet(cmdKey)` for `start-commands` and `conclude-commands`.
  - Implemented `Object`-accepting, case-insensitive (`Locale.ROOT`), and numeric-mapped enum parsers in `WorldManager` (`parseEnvironment`, `parseDifficulty`, `parseGameMode`).
  - Refactored `WorldCopyManager` template copying to skip disabled dimension subfolders (`_nether`, `_the_end`, `the_nether`, `the_end`, `DIM-1`, `DIM1`) while keeping source templates read-only.
  - Implemented specific fallback warning contracts in `ConfigManager` (`getHubWorld()` -> `"world"`, `getMessage()` -> raw key string) and `MenuManager` (`parseMaterial()` -> `STONE`, `parseTitle()` -> `"Unnamed Menu"`).
  - Documented Bukkit nether portal limitation note: enabling `disable-nether`/`disable-end` stops dimension folder creation but does not block Bukkit portal generation if `allow-nether=true` in `server.properties`.
- Implemented companion plugin event-driven integration API (resolves #24 umbrella and #31–#37).
  - Extracted standalone `com.ronlab:rga-api:1.11.0` Maven submodule (`rga-api/pom.xml`) for companion plugin artifact distribution (#36).
  - Created base `MinigameEvent` and concrete event API package `com.ronlab.rga.api.event` (#32).
  - Implemented `MinigameStartEvent` fired in `PartyManager.startGame()` after arena world creation/load succeeds, honoring listener cancellation to cleanly abort game start (#33).
  - Implemented `MinigameConcludeEvent` fired in `PartyManager.concludeGame()` with a mutable `scores` map (`Map<UUID, Number>`) and updated `concludeGame()` signature to return `boolean` (#34).
  - Documented breaking API surface changes: introduced `ConcludeResult` return type for structured conclusion status and updated `MinigameConcludeEvent` scores contract (`Map<UUID, Number>`).
  - Reordered world load validation before state mutation in `PartyManager.startGame()` and extracted shared `abortGameStart()` helper to clean up inventory groups, session snapshots, and temporary worlds on failure (#31).
  - Established unit testing suite `MinigameEventTest` verifying event firing, field contents, scores map mutation, and cancellation handling (#35).
  - Published comprehensive developer integration and migration guide in `EVENT_API_KNOWN_LIMITATIONS.md` (#37).
  - Tracked follow-up issue #38 for companion-initiated conclude request API.
- Audited `/reload` command behavior and renamed primary subcommand to `/rga reloadconfig` (resolves #25).
  - Audited `RGA.reload()` behavior: confirmed configuration reload is isolated to RGA plugin scope and does not trigger server-level `Bukkit.reload()`. Identified and documented that `worldManager.loadConfiguredWorlds()` initializes and loads any newly configured `load-on-startup: true` worlds in `worlds.yml`.
  - Renamed primary subcommand to `/rga reloadconfig` and retained `/rga reload` as a backwards-compatible alias with notification guidance.
  - Maintained single permission node `rga.reload` covering both `/rga reloadconfig` and `/rga reload` to ensure backwards compatibility.
  - Updated command usage strings in `paper-plugin.yml`, `RGACommand` help text, tab completions, and `README.md`.
  - Enforced centralized world name validation across all entry points with a structured severity taxonomy (resolves #26).
  - Implemented `WorldNameValidator` and `ValidationResult` in `com.ronlab.rga.util` separating Hard Security Violations (path traversal `..`, OS-illegal characters) from Format Tier checks.
  - Added soft migration toggle `strict-world-name-validation: false` (default `false` in v1.11.x) to `config.yml` with getter in `ConfigManager`.
  - Hardened untrusted command boundary (`RGACommand.java`): dispatches check `WorldNameValidator.validate(worldName, isStrict)` and return appropriate security vs format error messages.
  - Hardened trusted config parsing boundary (`WorldManager.java`, `MinigameManager.java`): security violations trigger `SEVERE` errors and skip the entry, while cosmetic warnings (spaces, leading dots) log `WARNING` and load the world/minigame normally to prevent data loss.
  - Ensured generator conformity in `WorldCopyManager.java` by sanitizing minigame IDs when constructing session world names.
  - Added comprehensive unit test suite `WorldNameValidatorTest.java` verifying path traversal, OS characters, cosmetic warnings, strict mode, length boundaries, and filesystem sanitization.

### v1.10.0


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
