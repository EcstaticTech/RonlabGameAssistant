RGA Fork Maintenance Plan
=========================

Source Repository: [piccoshi2002/RonlabGameAssistant](https://github.com/piccoshi2002/RonlabGameAssistant)

Fork Target: Personal maintainable fork

Plugin Version: 1.10.1

Target Platform: PaperMC 26.1.x (Minecraft 26.1.2+)

Java Version: 21+

Companion Plugin: [piccoshi2002/Block-Shuffle](https://github.com/piccoshi2002/Block-Shuffle) — integration should move from raw console dispatch to custom Bukkit events.

* * *

Overview
--------

This document defines the maintenance and rebuild roadmap for the RGA fork. It prioritizes critical compatibility and security fixes for Paper 26.1, adds robust runtime persistence, and lays out a long-term architecture for safer companion integration and datapack handling.

The plan is arranged in the following sections:

* Blockers — immediate fixes required to keep RGA safe and functional on Paper 26.1.
* Security — input hardening, permissions, and command execution safety.
* Sustainability — long-term compatibility, persistence, and modern API alignment.
* Features — quality-of-life and gameplay improvements.
* Architectural evaluation — datapack isolation approaches and the recommended path for this fork.

Each item includes:

* Category: `Blocker` | `Security` | `Sustainability` | `Feature` | `Architectural`
* Effort: `Low` | `Medium` | `High`
* Risk if skipped: 🔴 Critical / 🟡 Moderate / 🟢 Low

* * *

Priority Order at a Glance
--------------------------

# | Item | Category | Effort | Risk
--- | --- | --- | --- | ---
1 | Paper 26.1 world path rewrite | Blocker | Medium | 🔴
2 | Write-ahead session persistence and recovery | Blocker | High | 🔴
3 | Command injection via placeholders | Security | Low | 🔴
4 | World name validation / path traversal protection | Security | Low | 🟡
5 | Console command allowlist | Security | Low | 🟡
6 | Migrate to `paper-plugin.yml` + granular permissions | Sustainability | Medium | 🟡
7 | Startup version guard | Sustainability | Low | 🟢
8 | Graceful `onDisable()` cleanup and recovery | Sustainability | Medium | 🟡
9 | Async world copy with progress feedback | Sustainability | High | 🟡
10 | `/reload` deprecation handling | Blocker | Low | 🟡
11 | Migration failure graceful handling | Blocker | Low | 🟡
12 | Adventure API alignment | Sustainability | Medium | 🟡
13 | Material/registry-safe config parsing | Sustainability | Low | 🟡
14 | `/rga status` command | Feature | Low | 🟢
15 | Minigame queue system | Feature | Medium | 🟢
16 | Spectator support | Feature | Medium | 🟢
17 | First-visit spawn configuration | Feature | Low | 🟢
18 | Configurable hub entry behavior | Feature | Low | 🟢
19 | Companion plugin event-driven API | Architectural | Medium | 🟡
20 | Datapack isolation strategy evaluation | Architectural | Variable | 🟡

* * *

Section 1 — Blockers
--------------------

These items affect RGA on Paper 26.1 right now and should be patched before any gaming session on the new version.

### 1. Paper 26.1 world path rewrite

Category: Blocker

Effort: Medium

Risk if skipped: 🔴 All template world copy operations and dimension management can fail silently or corrupt worlds.

Paper 26.1 changed world storage to a Vanilla-aligned nested structure. RGA currently assumes legacy folder names such as `world_nether` and `world_the_end`, and it treats template worlds as top-level folders only.

What needs to change:

* Audit every `File`/`Path` operation in `WorldManager`, `WorldCopyManager`, and any world utilities.
* Resolve `paper-world.yml` and dimension folders from the new path: `world/ dimensions/minecraft/overworld`, `.../the_nether`, `.../the_end`.
* Avoid hardcoded `_<dimension>` suffixes when discovering or deleting worlds.
* Ensure copied template worlds are compatible with Paper 26.1 migration behavior and do not carry legacy dimension folders that cause `RuntimeException: Refusing to overwrite existing migrated file`.

### 2. Write-ahead session persistence and recovery

Category: Blocker

Effort: High

Risk if skipped: 🔴 Active minigame sessions and player state are lost on server crash or restart.

RGA currently stores active party state, inventory snapshots, and advancement state only in memory. That is unsafe for a survival server where crashes or restarts are normal.

What needs to change:

* Serialize active session state to disk immediately when a minigame starts.
* Store per-session recovery files under `plugins/RonlabGameAssistant/sessions/` or `recovery/`.
* On normal conclude, restore state and delete recovery files.
* On startup, detect orphaned session files and log a clear recovery prompt for admins.
* Integrate recovery into player login handling so offline players can be restored when they next connect.

### 3. Command injection via placeholders

Category: Security

Effort: Low

Risk if skipped: 🔴 A crafted player name or world name can inject arbitrary console commands, including privileged operations.

The `start-commands` and `conclude-commands` system currently substitutes `%player%`, `%leader%`, `%world%`, and other placeholders directly into console command strings.

What needs to change:

* Add strict sanitization for every substituted value.
* Allow only safe characters for usernames and world names: `[A-Za-z0-9_\-]`.
* Reject or escape newline, semicolon, and control characters.
* Apply validation at command interpolation time and whenever config values are loaded.

### 4. World name validation / path traversal protection

Category: Security

Effort: Low

Risk if skipped: 🟡 A crafted world name can escape the server root and access arbitrary file paths.

All world names from `worlds.yml`, `minigames.yml`, and `/rga createworld` are currently used in filesystem operations.

What needs to change:

* Enforce a strict world-name allowlist pattern at all entry points.
* Reject names containing `../`, backslashes, spaces, or invalid characters.
* Apply validation in config loading, command parsing, and world manager file operations.
* Provide clear admin-facing error messages when a name is rejected.

### 5. Console command allowlist

Category: Security

Effort: Low

Risk if skipped: 🟡 Misconfigured `console:` commands can execute destructive privilege actions.

When RGA dispatches console commands on behalf of configs, it has full operator power with no guardrail.

What needs to change:

* Add optional allowlist support in `config.yml`.
* Default to permissive mode for compatibility, but document the security setting strongly.
* When enabled, verify the first token of `console:` commands against `allowed-console-prefixes`.
* Block and log any command outside the allowlist.

### 6. `/reload` deprecation handling

Category: Blocker

Effort: Low

Risk if skipped: 🟡 `/rga reload` may break on future Paper builds once `/reload` is removed.

RGA currently exposes a reload command that can be misleading and may depend on legacy server reload behavior.

What needs to change:

* Audit whether `/rga reload` wraps `Bukkit.reload()`.
* Rename the command to `/rga reloadconfig` if it only reloads plugin config and menus.
* Do not rely on `/reload` for world or session state management.
* Document the command precisely: config-only reload, not world reload.

### 7. Migration failure graceful handling

Category: Blocker

Effort: Low

Risk if skipped: 🟡 A single world migration conflict can crash the plugin startup or leave worlds half-loaded.

Paper 26.1 can throw runtime exceptions when legacy and new-format dimension files collide.

What needs to change:

* Wrap world load and world discovery code in failure-safe try/catch blocks.
* Log actionable diagnostics when a world fails to load.
* Continue loading remaining worlds instead of disabling the entire plugin.
* Add a startup warning if legacy `world_nether` / `world_the_end` folders are detected alongside new-format worlds.

* * *

Section 2 — Security
--------------------

These items should be addressed immediately after the blockers, before the plugin is used in any configuration where player-provided input reaches command execution.

### 8. Granular permission nodes

Category: Security

Effort: Low

Risk if skipped: 🟢 All admin actions are currently bundled under `rga.admin`, so delegation is impossible.

RGA should expose fine-grained admin permission nodes and make `rga.admin` the wildcard parent.

Recommended structure:

* `rga.admin` — wildcard for all admin actions
* `rga.admin.reload` — `/rga reloadconfig`
* `rga.admin.tp` — `/rga tp`
* `rga.admin.conclude` — `/rga conclude`
* `rga.admin.createworld` — `/rga createworld`
* `rga.admin.importworld` — `/rga importworld`
* `rga.admin.loadworld` — `/rga loadworld`
* `rga.admin.unloadworld` — `/rga unloadworld`
* `rga.admin.deleteworld` — `/rga deleteworld`
* `rga.session.status` — `/rga status`
* `rga.hub` — `/hub`

Update `plugin.yml` / `paper-plugin.yml` with these nodes and proper child relationships.

### 9. Console command allowlist

Category: Security

Effort: Low

Risk if skipped: 🟡 A badly written `console:` hook can dispatch a privileged command such as `op` or `stop`.

This is particularly important because the current lifecycle hook system executes raw console strings after world creation.

What needs to change:

* Implement a command prefix allowlist in `config.yml`.
* When enforcement is enabled, only permit safe prefixes such as `team`, `scoreboard`, `function`, `datapack`, `blockshuffle`, `manhunt`, `rga`.
* Block and warn on any disallowed command.

### 10. Input sanitization for placeholders

Category: Security

Effort: Low

Risk if skipped: 🔴 Placeholder substitution remains the most dangerous attack surface.

The current interpolation system must be hardened.

What needs to change:

* Sanitize `%player%`, `%leader%`, `%world%`, `%players%`, and all position placeholders.
* Reject invalid names before building command strings.
* Prefer typed event-based APIs for companion integrations wherever possible, so raw text substitution is minimized.

* * *

Section 3 — Sustainability
--------------------------

These items improve long-term compatibility, maintainability, and the plugin’s ability to evolve with Paper.

### 11. Migrate to `paper-plugin.yml`

Category: Sustainability

Effort: Medium

Risk if skipped: 🟡 Legacy `plugin.yml` prevents access to Paper bootstrap hooks and lifecycle events that are needed for datapack and world integration.

What needs to change:

* Add `paper-plugin.yml` alongside or instead of `plugin.yml`.
* Implement a bootstrapper class if necessary.
* Declare `api-version: '26.1'`.
* Move permissions and commands into `paper-plugin.yml`.

### 12. Startup version guard

Category: Sustainability

Effort: Low

Risk if skipped: 🟢 RGA may load on unsupported server versions and fail later in obscure ways.

What needs to change:

* Check `Bukkit.getServer().getBukkitVersion()` during `onEnable()`.
* Require PaperMC 26.1.2+.
* Disable the plugin cleanly if the version is unsupported.
* Log the detected server version and plugin version at startup.

### 13. Graceful `onDisable()` cleanup and recovery

Category: Sustainability

Effort: Medium

Risk if skipped: 🟡 Active sessions leave players with lost inventories, revoked advancements, or stranded state after a restart.

The current `onDisable()` only saves tracked locations.

What needs to change:

* Detect active parties and in-progress minigame sessions.
* Persist recovery state for players who cannot be restored immediately.
* On next startup, detect recovery files and notify admins.
* Integrate with the new write-ahead session persistence system.

### 14. Async world copy with progress feedback

Category: Sustainability

Effort: High

Risk if skipped: 🟡 Large template world copies block the main thread and cause TPS lag.

What needs to change:

* Offload file-copy I/O to an asynchronous scheduler task.
* Keep world registration and Bukkit API calls on the main server thread.
* Provide user-facing progress messages while the copy is in progress.
* Prevent duplicate world creation or partial copies by locking the template during copy.

### 15. Adventure API alignment

Category: Sustainability

Effort: Medium

Risk if skipped: 🟡 ChatColor and legacy text APIs are deprecated and may be removed from future Paper builds.

What needs to change:

* Replace `ChatColor` strings in messages with Adventure `Component` text.
* Use Adventure in inventory/gui titles and command feedback.
* Standardize message formatting across all managers and listeners.

### 16. Material / enum registry safety

Category: Sustainability

Effort: Low

Risk if skipped: 🟡 `Material.valueOf()` and enum reliance may break if Paper converts `Material` to registry-backed classes.

What needs to change:

* Replace `Material.matchMaterial()` and `Material.valueOf()` with registry lookups whenever possible.
* Resolve config item types using `Registry.MATERIAL.get(NamespacedKey.minecraft(...))`.
* Provide safe fallback defaults when materials are unknown.

* * *

Section 4 — Features
--------------------

These improvements are not required for compatibility, but they significantly improve the player/admin experience.

### 17. `/rga status` command

Category: Feature

Effort: Low

Risk if skipped: 🟢 Admins have no easy runtime visibility into active sessions.

Suggested output:

* Active sessions and world names
* Party leaders and member counts
* Lobby state and queued parties
* Number of loaded worlds
* Persistence/recovery status

Permission: `rga.admin.status`

### 18. Minigame queue system

Category: Feature

Effort: Medium

Risk if skipped: 🟢 Full minigames reject new parties instead of offering a waitlist.

What needs to change:

* Add queue support to `minigames.yml`.
* Track queued parties when sessions are full or template worlds are busy.
* Notify players of queue position.
* Auto-start the next party when a session concludes.

### 19. Spectator support

Category: Feature

Effort: Medium

Risk if skipped: 🟢 Observers currently have no structured way to watch games safely.

What needs to change:

* Add optional spectator support per minigame.
* Teleport spectators in `SPECTATOR` mode and keep them out of participant counts.
* Save/restore spectator inventories and advancements separately.
* Exclude spectators from `%players%` and add `%spectators%` if needed.

### 20. First-visit spawn configuration

Category: Feature

Effort: Low

Risk if skipped: 🟢 Fresh template worlds or new-world visitors may spawn at an unintended location.

What needs to change:

* Add `first-visit-spawn` support to `worlds.yml`.
* Use it when a player has no tracked location in that world.
* Apply it to template worlds and hub entry behavior.

### 21. Configurable hub entry behavior

Category: Feature

Effort: Low

Risk if skipped: 🟢 The current hub entry behavior may clear survival inventory with no confirmation.

What needs to change:

* Add hub config options for inventory clearing and confirmation.
* Support optional restore-on-return behavior.
* Prompt players before clearing if they have items.
* Preserve safety for SMP gear.

* * *

Section 5 — Companion Integration and Event API
-----------------------------------------------

This fork can improve companion-plugin integration by moving away from raw command dispatch.

### 22. Companion plugin event-driven API

Category: Architectural

Effort: Medium

Risk if skipped: 🟡 Raw console commands remain a fragile and insecure integration surface.

The current design dispatches console commands such as `/rga conclude <world>`. That creates security, injection, and maintenance risk.

What needs to change:

* Define custom Bukkit events for lifecycle transitions:
  * `GameSessionStartEvent`
  * `GameSessionRequestConcludeEvent`
  * `GameSessionConcludeEvent`
* Fire `GameSessionStartEvent` after world copy finishes and player state is prepared.
* Allow companion plugins to request conclusion via `GameSessionRequestConcludeEvent`.
* Fire `GameSessionConcludeEvent` during cleanup so plugins can save scores or persist state.
* Keep the current command-hook system as a fallback for pure config-driven integrations.

Benefits:

* Eliminates command injection from companion hooks.
* Provides a typed, version-safe integration surface.
* Makes RGA a safer orchestrator for Block-Shuffle and future plugins.

* * *

Section 6 — Datapack Isolation Strategy
---------------------------------------

Paper does not support true per-world datapacks. This section evaluates viable approaches for RGA’s server model.

### Plan A — Proxy / multi-server architecture

Pros:

* True datapack isolation per backend.
* Clean separation of game modes.

Cons:

* Infrastructure overkill for a friend server.
* Requires cross-server party sync and shared persistence.
* Massive rewrite and maintenance burden.

Verdict: Correct for large networks, but not the right path for this fork today.

### Plan B — Dynamic datapack enable/disable hooks

Pros:

* Uses existing start/conclude command system.
* No proxy infrastructure required.
* Works for function/loot/advancement packs.

Cons:

* Affects the entire server globally.
* Conflicts if two datapack-dependent games run concurrently.
* Does not solve feature-flag world-gen packs.

Verdict: Pragmatic for single-session, single-activity setups with an exclusive-game flag.

### Plan C — Unified master datapack with world-scoped logic

Pros:

* One always-enabled datapack.
* Logic gates by world/dimension.
* Eliminates pack toggling race conditions.

Cons:

* Still global for recipes and loot tables.
* Requires careful authoring and namespace management.

Verdict: Best fit for function-driven game logic when combined with template worlds.

### Plan D — Template worlds with embedded datapacks

Pros:

* Appears clean by ownership.

Cons:

* Paper ignores datapacks in non-default worlds today.
* No native API exists for per-world packs.

Verdict: Not viable today, but worth preserving if Paper later exposes per-world pack loading.

### Plan E — Paper fork / custom per-world datapack API

Pros:

* The cleanest long-term technical solution.
* Could become an upstream contribution.

Cons:

* Very high effort and NMS-level patching.
* Requires ongoing patch maintenance across Paper versions.

Verdict: A future possibility for a custom Paper build, not the primary fork path for now.

### Recommended datapack strategy for this fork

Use a hybrid approach:

1. Bake world generation and dimension behavior into template worlds.
2. Use a single always-enabled master datapack for function and game logic where possible.
3. Reserve dynamic enable/disable hooks for strictly companion-managed behavior packs.
4. Keep world template datapacks as a staging concept for future Paper improvements.

* * *

Section 7 — Implementation Phases
---------------------------------

This plan is designed to minimize rewrites and ensure each layer is stable before the next is built.

### Phase 1 — Foundation

* Migrate to `paper-plugin.yml`.
* Add startup version guard.
* Harden input validation and placeholder sanitization.
* Introduce granular permissions.

### Phase 2 — Compatibility and persistence

* Rewrite world handling for Paper 26.1 storage.
* Implement write-ahead session persistence and recovery.
* Add graceful world load failure handling.
* Replace legacy ChatColor and material parsing with modern APIs.

### Phase 3 — Safe orchestration

* Add async world copying with progress feedback.
* Implement `onDisable()` recovery and cleanup.
* Add console command allowlist.
* Add `/rga status` and improved admin visibility.

### Phase 4 — Gameplay polish

* Add minigame queues and spectator support.
* Add first-visit spawn and hub entry configuration.
* Implement companion event-driven integration API.
* Finalize the datapack isolation strategy and document the chosen path.

* * *

Section 8 — Task Checklist
--------------------------

Use this checklist to track individual work items as issues, PRs, or commits.

Blockers

* [ ] Rewrite world copy and discovery logic for Paper 26.1 path structure.
* [ ] Add write-ahead session persistence and disk-based recovery files.
* [ ] Harden placeholder interpolation and sanitize all substituted values.
* [ ] Validate all world names and reject unsafe filesystem inputs.
* [ ] Implement console command allowlist support in config.
* [ ] Audit `/rga reload` behavior and rename to `/rga reloadconfig` if appropriate.
* [ ] Add graceful world load failure handling with actionable logging.

Security

* [ ] Define fine-grained permission nodes and update plugin descriptors.
* [ ] Add config-driven command prefix allowlist enforcement.
* [ ] Sanitize player, leader, world, and party placeholders.
* [ ] Document safe command hook patterns and disallowed commands.

Sustainability

* [ ] Migrate to `paper-plugin.yml` and Paper 26.1 bootstrap conventions.
* [ ] Add server version guard during `onEnable()`.
* [x] Implement robust `onDisable()` cleanup and recovery detection.
* [ ] Convert world copy I/O to async file operations with main-thread world registration.
* [ ] Replace legacy ChatColor messaging with Adventure API components.
* [ ] Use registry-safe material lookups for config item parsing.

Features

* [ ] Add `/rga status` for runtime visibility.
* [ ] Implement minigame queueing and auto-start behavior.
* [ ] Add spectator support for supported minigames.
* [x] Add first-visit spawn support to world config.
* [x] Add configurable hub entry behavior and inventory confirmation.
* [ ] Build a companion event-driven integration API for start/conclude events.

Section 9 — Converting this plan into a GitHub issue roadmap
------------------------------------------------------------

To convert this plan into a GitHub issue roadmap, create issues for each checklist item and group them by phase and label.

Recommended workflow:

* Create one issue per task or logical sub-task, using the checklist text as the issue title.
* Add labels: `blocker`, `security`, `sustainability`, `feature`, `architecture`.
* Use milestones such as `RGA 26.1 rebuild`, `Phase 1`, `Phase 2`, etc.
* Create parent/epic issues for each phase:
  * `Phase 1 — Foundation`
  * `Phase 2 — Compatibility and persistence`
  * `Phase 3 — Safe orchestration`
  * `Phase 4 — Gameplay polish`
* Include acceptance criteria in each issue body, such as:
  * expected config changes
  * expected runtime behavior
  * rollback/recovery conditions
* Track progress by checking off completed items and closing issues as they are merged.

Benefits of this approach:

* Makes the roadmap actionable and easy to share with collaborators.
* Provides a clear trace from plan to implementation.
* Helps prioritize work by issue severity and milestone.

Section 10 — Notes
------------------

* This fork should treat the current Block-Shuffle integration as a temporary bridge.
* Long-term stability depends on reducing raw console command dispatch and moving companion plugins to event-based coordination.
* The `paper-plugin.yml` migration is the critical enabler for modern Paper lifecycle hooks and future datapack work.

Last updated: June 2026 — based on Paper 26.1.2, RGA 1.0.0.


Risk if skipped: 🔴 All template world copy operations and dimension management are broken on 26.1

Paper 26.1 changed the world directory structure from the decade-old Bukkit layout to align with Vanilla. Dimensions are no longer stored as top-level sibling folders with hardcoded suffixes. Every `File` operation in `WorldManager` that references `world_nether` or `world_the_end` as server-root-level directories will fail silently or crash.

Old structure (1.21.x Bukkit-style):

server-root/

├── MyWorld/

├── MyWorld\_nether/        ← top-level, \_nether suffix

└── MyWorld\_the\_end/       ← top-level, \_the\_end suffix

New structure (26.1 Vanilla-aligned):

server-root/

└── MyWorld/

    ├── datapacks/

    ├── dimensions/

    │   └── minecraft/

    │       ├── overworld/

    │       │   ├── region/

    │       │   ├── entities/

    │       │   ├── poi/

    │       │   └── paper-world.yml   ← moved here

    │       ├── the\_nether/

    │       │   └── ...

    │       └── the\_end/

    │           └── ...

    └── level.dat

What needs to change:

*   All file path construction in `WorldManager` using `_nether` / `_the_end` string concatenation must be updated to the new `dimensions/minecraft/the_nether/` and `dimensions/minecraft/the_end/` paths

*   Template world folders shipped on disk must be in the new nested format

*   Any code that injects or reads `paper-world.yml` must resolve it from inside the dimension subfolder, not the world root

*   `WorldCreator` API calls themselves should be fine — Paper handles registration correctly — but any manual `File` operations need auditing

Template world authors note: If template worlds were created on 1.21.x, Paper 26.1 will attempt to auto-migrate them on first load. There is a known bug where this migration throws `RuntimeException: Refusing to overwrite existing migrated file` if any dimension data exists in both old and new paths. Template worlds should be tested on 26.1 before shipping in the fork.

* * *

### 2\. /reload Deprecation Handling

Category: Blocker

Effort: Low

Risk if skipped: 🟡 RGA's reload command will break on a future Paper build when `/reload` is fully removed

Paper has deprecated the `/reload` command for removal. Their documentation explicitly states: "You should instead restart your server... reload is known for causing issues with plugins." The `/rga reload` command likely depends on this mechanism or reinitializes managers in a way that assumes a clean plugin load state.

What needs to change:

*   Audit exactly what `/rga reload` does — if it wraps `Bukkit.reload()`, replace it

*   For config-only reloads, use `/paper reload` as the underlying mechanism where applicable

*   Document clearly in the command reference what `/rga reload` actually reloads (ConfigManager only, not world or party state)

*   Consider renaming to `/rga reloadconfig` to set accurate expectations

* * *

### 3\. Migration Failure Graceful Handling

Category: Blocker

Effort: Low

Risk if skipped: 🟡 Server crashes silently during world load with no actionable error message for the admin

A confirmed bug in Paper 26.1.2 causes a hard `RuntimeException` during world storage migration when dimension files already exist in both old and new paths. This can occur if a server was partially upgraded or if template worlds were copied between versions.

What needs to change:

*   Wrap `WorldManager` startup world loading in try-catch blocks that catch `RuntimeException` during world load

*   On failure, log a clear, actionable error message:

\[RGA\] FATAL: World 'MyWorld' failed to load. This may be a 26.1 migration conflict.

\[RGA\] Check that MyWorld/ does not contain both legacy DIM-1/ folders and new dimensions/ folders.

\[RGA\] Server will continue loading remaining worlds.

*   Do not let a single world failure crash the entire plugin enable sequence

*   Add a startup warning if legacy-style `world_nether` or `world_the_end` folders are detected in the server root alongside new-format worlds

* * *

Section 2 — Security
--------------------

These items should be addressed immediately after blockers, before the plugin is used in any configuration where player-provided input reaches command dispatch.

* * *

### 4\. Command Injection via Placeholders

Category: Security

Effort: Low

Risk if skipped: 🔴 A player with a crafted username can execute arbitrary console commands including `/op`

This is the most critical security issue in the current architecture. The `start-commands` / `conclude-commands` system performs direct string interpolation into `Bukkit.dispatchCommand()`. The placeholders `%player%`, `%leader%`, and `%world%` are substituted from live player state before dispatch.

Attack vector:

A player whose username is `runner; op BadActor` or contains newline characters causes the interpolated string to become a multi-command injection when dispatched through console with full operator permissions.

Example:

start-commands:

  - "console: team assign runner %leader%"

\# If leader's name is "Steve; op BadActor"

\# Dispatches: "team assign runner Steve; op BadActor"

Fix — sanitize all placeholder values before substitution:

private String sanitizePlaceholder(String value) {

    // Allow only characters valid in Minecraft usernames and world names

    return value.replaceAll("\[^a-zA-Z0-9\_\\\\-\]", "");

}

Apply `sanitizePlaceholder()` to every value substituted into `%player%`, `%leader%`, and `%world%` before the command string is built.

Additional note: The Block-Shuffle companion plugin calls `/rga conclude <world>` on game end — the world name argument is also a substitution surface and should be validated against the known loaded world registry before dispatch.

* * *

### 5\. Path Traversal in World Names

Category: Security

Effort: Low

Risk if skipped: 🟡 A crafted world name in YAML config or via `/rga createworld` could escape the server root in file operations

All world names sourced from `minigames.yml`, `worlds.yml`, or the `/rga createworld <name>` command are used directly in `File` construction for template copy and world management. A name like `../../plugins/RonlabGameAssistant/config` would resolve outside the server root.

Fix — validate world names against a strict allowlist pattern:

private void validateWorldName(String name) {

    if (!name.matches("^\[a-zA-Z0-9\_\\\\-\]{1,64}$")) {

        throw new IllegalArgumentException(

            "\[RGA\] Unsafe world name rejected: '" + name + "'. " +

            "World names must be alphanumeric with underscores/hyphens only."

        );

    }

}

Call this validation at:

*   Config load time for all world names in `worlds.yml` and `minigames.yml`

*   Before any `File` operation in `WorldManager`

*   At the entry point of `/rga createworld`

* * *

### 6\. Granular Permission Nodes

Category: Security

Effort: Low

Risk if skipped: 🟢 Currently all admin actions share `rga.admin`; no delegation is possible

The current `rga.admin` permission covers all administrative operations. This prevents giving trusted non-op players limited access (e.g., a friend who can conclude sessions but shouldn't create worlds).

Proposed permission tree:

Permission Node

Action

Default

`rga.admin`

Wildcard for all admin nodes

op

`rga.admin.reload`

`/rga reload`

op

`rga.admin.tp`

`/rga tp <world>`

op

`rga.admin.conclude`

`/rga conclude <world>`

op

`rga.admin.createworld`

`/rga createworld <name>`

op

`rga.admin.compass`

`/rga compass`

op

`rga.session.status`

`/rga status` (new)

op

`rga.hub`

`/hub`

true

Update `plugin.yml` (or `paper-plugin.yml` after item #9) with the full permission tree and child relationships so `rga.admin` implies all sub-nodes.

* * *

### 7\. Console Command Allowlist

Category: Security

Effort: Low

Risk if skipped: 🟡 A misconfigured `minigames.yml` could dispatch unintended privileged commands with no guardrail

All `console:` prefixed commands in `start-commands` and `conclude-commands` dispatch with full operator-level permissions and no attribution to the plugin. There is currently no mechanism to prevent a config mistake from dispatching something destructive (e.g., `console: stop`, `console: op someuser`).

Fix — add an optional allowlist in `config.yml`:

\# config.yml

command-security:

  enforce-allowlist: true

  allowed-console-prefixes:

    - "team"

    - "scoreboard"

    - "function"

    - "datapack"

    - "blockshuffle"

    - "manhunt"

    - "rga"

When `enforce-allowlist: true`, any `console:` command whose first token is not in `allowed-console-prefixes` is blocked and logged as a warning:

\[RGA\] BLOCKED console command (not in allowlist): "op someuser" — edit allowed-console-prefixes to permit this.

Set `enforce-allowlist: false` by default so existing configs are not broken on upgrade, but recommend enabling it in documentation.

* * *

Section 3 — Sustainability
--------------------------

These items address long-term maintainability, API compatibility, and server performance as Paper continues to evolve.

* * *

### 8\. Async World Copy

Category: Sustainability

Effort: High

Risk if skipped: 🟡 Large template worlds block the main thread during copy, causing TPS lag and timeout for all players on the server

RGA's own documentation flags this as a known limitation: "Synchronous World Operations: World copying blocks the main thread (I/O intensive)." For template worlds over ~50MB this is a noticeable freeze. The fix requires splitting the copy into two phases — async file I/O followed by a sync world registration step.

Implementation pattern:

public void copyAndLoadWorld(String templateName, String instanceName, Player requester) {

    // Phase 1: File copy — safe to run off main thread

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

        try {

            Path templatePath = serverRoot.resolve(templateName);

            Path instancePath = serverRoot.resolve(instanceName);

            copyDirectory(templatePath, instancePath); // pure File I/O

            // Phase 2: WorldCreator MUST be on main thread

            Bukkit.getScheduler().runTask(plugin, () -> {

                WorldCreator creator = new WorldCreator(instanceName);

                World world = creator.createWorld();

                // proceed with teleport, inventory save, etc.

            });

        } catch (IOException e) {

            // Report failure back to main thread

            Bukkit.getScheduler().runTask(plugin, () -> {

                requester.sendMessage("\[RGA\] World copy failed: " + e.getMessage());

            });

        }

    });

}

Important constraints:

*   `WorldCreator.createWorld()` must remain on the main thread — the Bukkit API is not thread-safe for world registration

*   Only file copy operations are safe to offload

*   Player feedback (see item #15) is required because the async gap creates a silent wait period

* * *

### 9\. Migrate to paper-plugin.yml

Category: Sustainability

Effort: Medium

Risk if skipped: 🟡 Blocks access to the datapack lifecycle API, bootstrap hooks, and proper classloader isolation; legacy `plugin.yml` is not being removed but is the less capable path forward

RGA currently uses the legacy `plugin.yml`. Migrating to `paper-plugin.yml` unlocks the Paper-specific plugin bootstrap system, which is a prerequisite for the `DATAPACK_DISCOVERY` lifecycle API needed for any datapack isolation strategy (see item #20).

What changes:

*   Add `paper-plugin.yml` alongside or replacing `plugin.yml`

*   Implement a `PluginBootstrap` class for pre-server-load initialization

*   Gain access to `LifecycleEvents.DATAPACK_DISCOVERY` for programmatic datapack management

*   Improved classloader isolation from other plugins (reduces conflicts with Block-Shuffle and future companion plugins)

Minimum `paper-plugin.yml`:

name: RonlabGameAssistant

version: '1.1.0'

main: com.piccoshi2002.ronlabgameassistant.RGA

bootstrapper: com.piccoshi2002.ronlabgameassistant.RGABootstrap

api-version: '26.1'

permissions:

  rga.admin:

    description: Full RGA admin access

    default: op

    children:

      rga.admin.reload: true

      rga.admin.tp: true

      rga.admin.conclude: true

      rga.admin.createworld: true

      rga.admin.compass: true

      rga.admin.status: true

  rga.hub:

    description: Access to /hub command

    default: true

* * *

### 10\. Material/Enum Registry Safety

Category: Sustainability

Effort: Low

Risk if skipped: 🟡 Paper is moving `Material` and similar enums toward registry-backed classes; `Material.valueOf()` calls will throw when that migration completes

RGA uses `Material` values from `minigames.yml` for `display-item` fields (e.g., `IRON_BOOTS`, `CHEST`). The Paper API documentation explicitly warns that enums implementing `Keyed` "are liable to conversion to regular classes" and that switch statements and `EnumSet` usage should be avoided.

Fix — replace `Material.valueOf()` with registry lookup:

// Fragile — will throw when Material becomes registry-backed:

Material mat = Material.valueOf(configString.toUpperCase());

// Resilient — works now and after the registry migration:

Material mat = Registry.MATERIAL.get(

    NamespacedKey.minecraft(configString.toLowerCase())

);

if (mat == null) {

    plugin.getLogger().warning("\[RGA\] Unknown material in config: " + configString);

    mat = Material.STONE; // safe fallback

}

Audit all YAML-to-Material deserialization points in `MenuManager`, `MinigameManager`, and any `ItemStack` construction that reads from config.

* * *

### 11\. Version Guard at Startup

Category: Sustainability

Effort: Low

Risk if skipped: 🟢 Without a version check, RGA loads silently on incompatible Paper builds and produces confusing errors later

RGA should check the running Paper version at `onEnable()` and warn or abort if below the minimum tested build. Given that 26.1 introduced a hard migration that can corrupt world data, failing early is much safer than failing mid-session.

Implementation:

@Override

public void onEnable() {

    String version = Bukkit.getServer().getBukkitVersion();

    // Parse and compare against minimum supported build

    if (!isVersionSupported(version)) {

        getLogger().severe("\[RGA\] Unsupported server version: " + version);

        getLogger().severe("\[RGA\] RGA requires PaperMC 26.1.2 or later. Disabling.");

        Bukkit.getPluginManager().disablePlugin(this);

        return;

    }

    // ... rest of onEnable

}

Also log the detected version and RGA version at startup INFO level for easier support debugging.

* * *

### 12\. Graceful onDisable() Session Cleanup

Category: Sustainability

Effort: Medium

Risk if skipped: 🟡 A `/stop` or server crash during an active minigame leaves players with wiped advancements and wrong inventories permanently — data loss with no recovery path

RGA's documentation acknowledges: "No Database: All state in-memory; server restart loses active parties/sessions." Currently `onDisable()` does not appear to run conclude logic for active sessions, meaning a server restart mid-game causes permanent advancement and inventory corruption for affected players.

What `onDisable()` must do:

3.  Detect all active minigame sessions via `PartyManager`

6.  For each active session: a. Restore player inventories from the pre-game saved state b. Restore player advancements from the pre-game saved state c. Teleport players back to their pre-game world and location d. Log which world was active and which players were affected

9.  If any step fails (e.g., player is offline), write their saved state to disk as a recovery file:

plugins/RonlabGameAssistant/recovery/<uuid>\_<timestamp>.yml

3.  On next `onEnable()`, check for recovery files and prompt admins to restore them

* * *

### 13\. Adventure API Alignment

Category: Sustainability

Effort: Medium

Risk if skipped: 🟡 Deprecated `ChatColor` and `Bukkit.broadcastMessage()` usage will be removed in a future Paper build; the companion Block-Shuffle plugin has already completed this migration

The Block-Shuffle companion plugin has already migrated to Paper's Adventure API (`Component`, `NamedTextColor`, `MiniMessage`). RGA should align to the same API surface to:

*   Avoid deprecation warnings and eventual compile failures

*   Maintain consistent message formatting across the plugin ecosystem

*   Enable rich text features (hover events, click events in chat) for future use

Key replacements:

// Deprecated:

player.sendMessage(ChatColor.GREEN + "Welcome to " + worldName);

Bukkit.broadcastMessage(ChatColor.RED + "Game over!");

// Modern Adventure API:

player.sendMessage(Component.text("Welcome to " + worldName, NamedTextColor.GREEN));

Bukkit.broadcast(Component.text("Game over!", NamedTextColor.RED));

Inventory/GUI titles also require `Component` on modern Paper — audit all `Bukkit.createInventory()` calls with string titles.

* * *

Section 4 — Features
--------------------

Quality-of-life and gameplay improvements. None are blockers but several significantly improve the player experience on a friend server.

* * *

### 14\. /rga status Command

Category: Feature

Effort: Low

Risk if skipped: 🟢 No runtime visibility into plugin state; debugging active sessions requires log diving

An admin-facing status command that dumps current plugin state to chat in a readable format.

Example output:

\[RGA\] ── Active Sessions (2) ──────────────────

\[RGA\]   manhunt\_abc123 | Steve, Alex | Leader: Steve | 4m 32s

\[RGA\]   parkour\_def456 | Notch       | Leader: Notch | 1m 05s

\[RGA\] ── Open Lobbies (1) ──────────────────────

\[RGA\]   parkour | 2/4 players waiting

\[RGA\] ── Loaded Worlds (6) ─────────────────────

\[RGA\]   hub, smp, creative, adventure, manhunt\_abc123, parkour\_def456

\[RGA\] ── Memory ────────────────────────────────

\[RGA\]   Parties: 3 | Tracked Locations: 12 | Saved Inventories: 4

Permission: `rga.admin.status`

* * *

### 15\. World Copy Progress Feedback

Category: Feature

Effort: Low

Risk if skipped: 🟢 Without feedback, players see nothing after clicking "start" until the async copy finishes — the server appears frozen

Once async world copy (item #8) is implemented, there is a visible gap between the player clicking "start" and the game beginning. Progress messages fill this gap.

Proposed message sequence:

\[RGA\] Preparing your world... (copying template)

\[RGA\] World ready. Teleporting in 3...

\[RGA\] World ready. Teleporting in 2...

\[RGA\] World ready. Teleporting in 1...

Implement as a `BukkitRunnable` countdown on the main thread after the async copy completes. Broadcast to all party members, not just the leader.

* * *

### 16\. Minigame Queue System

Category: Feature

Effort: Medium

Risk if skipped: 🟢 Currently a full minigame simply rejects new parties; no waitlist exists

When a minigame is at capacity or a `template`\-type world copy is still in progress, new parties are silently rejected. A queue gives players a place to wait and auto-teleports them when a slot opens.

Config addition in `minigames.yml`:

minigames:

  parkour:

    max-players: 4

    queue-enabled: true

    max-queue-size: 8

Behavior:

*   Party receives: `[RGA] parkour is full. You are #2 in queue.`

*   When a session concludes, the next queued party is automatically started

*   Queue position is visible in the Social browsing GUI

*   If a queued party leader disconnects, leadership transfers and queue position is preserved

* * *

### 17\. Spectator Support

Category: Feature

Effort: Medium

Risk if skipped: 🟢 Players who want to observe a game currently have no structured way to join without affecting the session

Allow players to join an active or lobby minigame session as a spectator. Spectators are in `SPECTATOR` gamemode, are not counted against `max-players`, and do not affect game logic.

Config addition in `minigames.yml`:

minigames:

  manhunt:

    allow-spectators: true

    max-spectators: 4

Behavior:

*   Spectators receive an isolated inventory save/restore (same as participants)

*   Advancements are handled the same as participants

*   Spectators are teleported out on conclude along with the party

*   `%players%` placeholder in commands does not include spectators

*   A separate `%spectators%` placeholder is available for commands that need to address them

* * *

### 18\. First-Visit Spawn Configuration

Category: Feature

Effort: Low

Risk if skipped: 🟢 New players visiting a world for the first time (or a recreated world) fall back to raw world spawn, which may be unfinished or unintended

`LocationTracker` currently restores the last known position or falls back to world spawn when no tracked location exists. For new players or freshly-copied template worlds, this fallback may place players in unexpected areas.

Config addition in `worlds.yml`:

worlds:

  Creative:

    first-visit-spawn:

      x: 0.5

      y: 64.0

      z: 0.5

      yaw: 0.0

      pitch: 0.0

When a player has no tracked location for a world (first visit or cleared location), teleport to `first-visit-spawn` if defined, otherwise fall back to world spawn. Also apply this to minigame template worlds — useful for placing all players at a defined start point regardless of template world spawn.

* * *

### 19\. Configurable Hub Entry Behavior

Category: Feature

Effort: Low

Risk if skipped: 🟢 Current hub always clears inventory on entry — a `/hub` misclick permanently deletes SMP gear with no recovery

Hub currently clears inventory unconditionally on entry to prevent gear pollution. This is a footgun for survival players. The behavior should be configurable.

Config addition in `config.yml`:

hub:

  clear-inventory: true

  inventory-clear-confirm: true      # prompt if player has items

  confirm-timeout-seconds: 10        # auto-cancel if no response

  restore-on-return: false           # restore inventory when leaving hub

Behavior with `inventory-clear-confirm: true`:

\[RGA\] You have items in your inventory. Entering the hub will clear them.

\[RGA\] Type /hub confirm to proceed, or do nothing to cancel (10s).

The existing `InventoryManager` save/restore logic already handles this for minigames — `restore-on-return` would apply the same mechanism when a player leaves the hub back to an inventory-grouped world.

* * *

Section 5 — Datapack Isolation Strategy
---------------------------------------

Category: Architectural

Effort: Variable

Risk if skipped: 🟡 Game modes requiring datapack-driven behavior (custom loot, recipes, function logic) interfere with each other and with the main SMP world

### The Core Problem

Paper does not support per-world datapacks. Datapacks are bound to the server globally — only the default world's `datapacks/` folder is read. A Paper maintainer confirmed in GitHub discussion #10518: "It's still global state... this is generally not something that we can support."

Additionally, feature flags can only be enabled via datapack at world creation time. Any datapack that requires a feature flag (most world generation packs — terrain generators, biome packs, dimension modifiers) cannot be toggled at runtime with `/datapack enable/disable`. This is a hard Minecraft engine constraint, not a Paper limitation.

The following plans are evaluated in order of implementation complexity.

* * *

### Plan A — Proxy Server (Velocity / BungeeCord)

Each game type runs on a separate backend Paper server. The proxy routes players between servers. Each backend has its own completely independent datapacks.

✅ True isolation

Each server's datapacks are fully independent

✅ Clean architecture

Correct long-term design for a server network

✅ Already on RGA roadmap

Redis-backed party sync and Velocity integration are listed future goals

❌ Infrastructure overkill

Each backend is a full JVM process — for 2–8 friends you're running 5–6 JVMs

❌ Massive RGA rewrite

Party sync, inventory state, advancement persistence all need cross-server redesign via Redis or a shared database

❌ Wrong scale now

Designed for 50–500+ player networks

❌ Operational burden

Each backend server needs independent updates, configs, backups, and monitoring

Verdict: Correct long-term architecture for a network. Wrong solution for the current friend server context. Retain as the future roadmap item it already is.

* * *

### Plan B — Dynamic Enable/Disable via Lifecycle Hooks

Place all game datapacks in the default world's `datapacks/` folder. Use RGA's existing `start-commands` / `conclude-commands` lifecycle hooks to toggle them at game boundaries.

minigames:

  manhunt:

    exclusive-datapack: true

    start-commands:

      - "console: datapack enable \\"file/manhunt-loot\\""

      - "console: function manhunt:init"

    conclude-commands:

      - "console: function manhunt:teardown"

      - "console: datapack disable \\"file/manhunt-loot\\""

✅ Zero RGA architecture changes

Plugs directly into existing command hook system

✅ No `/reload` required

Enable/disable takes effect immediately

✅ Fits the server context

One activity at a time = no concurrent conflict

❌ Server-wide side effects

Disabling a pack affects ALL loaded worlds simultaneously — SMP players are impacted

❌ Race condition risk

Two games with conflicting packs running concurrently fight over the same enabled/disabled state

❌ Feature flag packs hard-blocked

World generation datapacks with feature flags cannot be toggled post-creation — only function, loot table, recipe, and advancement packs work

❌ No automatic teardown

Disabling a pack does not clean up in-flight effects; the pack must include its own teardown function

❌ Residual state on crash

If `conclude` fails or a player crashes, the pack stays enabled

Mitigation: Add `exclusive-datapack: true` flag to `minigames.yml`. When set, `PartyManager` blocks new game starts of any other datapack-dependent minigame until the current session concludes. This eliminates the race condition.

Verdict: Pragmatic and viable for a single-activity friend server. The feature flag limitation means this only works for behavioral packs, not world generation packs. World gen must be baked into template worlds at creation time.

* * *

### Plan C — World-Scoped Logic in a Unified Master Datapack

Instead of toggling packs, author a single always-enabled datapack where each game's logic is gated by dimension checks using `execute in`.

\# rga\_master:tick  (always enabled, runs every tick)

execute in rga:manhunt\_world  run function rga\_master:manhunt/tick

execute in rga:parkour\_world  run function rga\_master:parkour/tick

execute in rga:smp\_world      run function rga\_master:smp/ambient

✅ No toggling at all

One pack, always on; logic fires only in the correct world namespace

✅ No race conditions

Each world executes independently within the same pack

✅ No residual state

Pack is always enabled; no cleanup timing issues

✅ Maintainer-endorsed approach

Paper maintainer electronicboy confirmed: "you would need to rename the entries inside of the data pack to apply to the dimension you want" — this is exactly that

✅ Pairs naturally with RGA

World names are known via `%world%` placeholder; pack namespaces can mirror world names

❌ Requires unified datapack authoring

You maintain one growing pack instead of isolated per-game packs

❌ Loot tables and recipes are still global

These apply server-wide by definition — a loot table change cannot be dimension-scoped

❌ Pack grows with game count

More game modes = larger, more complex master pack

Verdict: Best approach for function-driven game logic. Does not help with loot tables, recipes, or world generation. Works well in combination with Plan B.

* * *

### Plan D — Template Worlds with Embedded Datapacks

Place datapacks inside each template world's `datapacks/` subfolder and copy them with the world when an instance is created.

🔶 Conceptually clean

Datapacks live alongside their world — logical ownership

❌ Does not work today

Paper only reads datapacks from the default world's `datapacks/` folder; all other worlds' datapack folders are silently ignored

❌ No API exists

Paper maintainers confirmed there is no mechanism to load per-world packs without reworking the entire registry system

🔮 Forward-looking potential

The 26.1 storage refactor brings Paper closer to Vanilla's dimension model; if Paper ever adds native per-world pack support, RGA's template copy approach would support it with zero additional changes

Verdict: Dead end today. Keep an eye on Paper's development — this becomes the cleanest solution if native per-world pack support is ever added.

* * *

### Plan E — Paper Fork: Custom Per-World Datapack API

Fork Paper using the `paperweight-patcher` Gradle toolchain and implement a `WORLD_DATAPACK_DISCOVERY` lifecycle event that re-triggers the client configuration phase on world change.

The technical mechanism exists: the Minecraft client configuration phase (where registries and datapacks are synced) can theoretically be re-entered mid-session. A Paper collaborator confirmed in 2024: "This is slightly more feasible with the introduction of the configuration stage... It's possible, but not tomorrow and probably not the next day."

Files that would need patching:

File

Change

`ServerPlayer.java`

Detect world change, flag reconfiguration needed

`ServerConfigurationPacketListenerImpl.java`

Accept re-entry from an in-game player

`PlayerChunkSender.java`

Handle chunk state during reconfiguration

`CommonListenerCookie.java`

Carry per-world pack state through the handoff

Registry loading

Scope active packs per dimension context

New API surface

`LifecycleEvents.WORLD_DATAPACK_DISCOVERY` event

✅ Clean plugin API

Solve the problem at the right layer; no duct tape

✅ Structured tooling

`paperweight-patcher` is the same workflow used by Purpur, Pufferfish, Folia

✅ Contribution path

A successful implementation could be submitted upstream as a PR

✅ Precedent exists

Paper already ships experimental `DATAPACK_DISCOVERY` API — this extends it

❌ NMS expertise required

Patching `ServerPlayer.java` and packet listener internals is advanced work

❌ Upstream maintenance burden

Every Paper update requires rebasing patches; conflict resolution on NMS files is painful

❌ Plugin compatibility risk

Other plugins hooking player connection events may break during mid-session reconfiguration

❌ Feature flags still hard-blocked

Even in the fork, world-gen packs with feature flags cannot be toggled post-creation

❌ Bus factor

You become the sole maintainer of server software; security patches require rebase before applying

Recommended framing: If pursued, frame this as a contribution to Paper upstream, not a maintenance burden for the friend server. A successful PR means the feature is maintained by the Paper team going forward.

* * *

### Recommended Approach: Plan B + C Hybrid (Near Term)

Given the current context (friend server, 2–8 players, typically one activity at a time), the practical path is:

3.  Fix item #1 (26.1 paths) first — prerequisite for everything else

6.  Migrate to `paper-plugin.yml` (item #9) — prerequisite for datapack lifecycle API access

9.  Implement Plan B for loot table and recipe packs — use `start-commands`/`conclude-commands` with `exclusive-datapack: true` enforcement in `PartyManager`

12.  Implement Plan C for function-driven game logic — author datapacks with `execute in <world>` gates so they are always-on but world-scoped

15.  Bake world generation into template worlds at creation time — this is not a limitation of RGA, it is a hard engine constraint

18.  Watch Paper upstream for progress on per-world datapack support (Plan D becoming viable) or the configuration phase API (Plan E becoming submittable)

Datapack type routing summary:

Datapack Content Type

Recommended Plan

Notes

Custom function logic, scoreboards, mob behavior

Plan C — world-gated unified pack

Always-on, no race conditions

Loot tables, recipes, advancements

Plan B — enable/disable on lifecycle

Only option; acceptable for single-activity server

World generation / terrain / biomes

Bake into template world

Must be set at world creation; cannot be toggled

Feature-flag packs

Bake into template world

Hard engine constraint; not fixable at plugin level

* * *

Appendix — External Integration Notes
-------------------------------------

### Block-Shuffle Companion Plugin

The [piccoshi2002/Block-Shuffle](https://github.com/piccoshi2002/Block-Shuffle) plugin integrates with RGA via `/rga conclude <world>`. Relevant notes for the fork:

*   Block-Shuffle has already migrated to the Adventure API (`Component`, `NamedTextColor`) — RGA should align (item #13)

*   Block-Shuffle calls `/rga conclude` on both `/blockshuffle stop` and natural game end — the world name argument is a command injection surface and should be validated (item #4)

*   Block-Shuffle is already targeting Paper 26.1.2 — it will break if RGA does not complete the 26.1 path rewrite (item #1)

*   Future companion plugins should follow the same integration pattern: call `/rga conclude <world>` on game end and rely on RGA for world lifecycle management

### Future Companion Plugin Recommendations

As more game mode plugins are added to the ecosystem:

*   All should call `/rga conclude <world>` on natural game end, not manage world teardown themselves

*   All should use Adventure API from the start

*   All should be listed in the console command allowlist (item #7) to avoid being blocked

*   Consider a lightweight RGA Plugin API interface that companion plugins can depend on for type-safe integration rather than string command dispatch

* * *

Last updated: June 2026 — based on Paper 26.1.2, RGA 1.0.0