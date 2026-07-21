## Changelog

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
