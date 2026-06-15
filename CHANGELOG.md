## Changelog

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
