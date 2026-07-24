# RGA GitHub Issue Roadmap

This document converts the RGA maintenance plan into an actionable GitHub issue checklist.
Use it to create issues, assign labels, and track milestone progress.

## Phase 1 — Foundation

- [x] `issue: Migrate to paper-plugin.yml`
  - Label: `sustainability`, `phase-1`
  - Milestone: `RGA 26.1 rebuild`
  - Description: Add `paper-plugin.yml`, update plugin bootstrap if needed, declare `api-version: '26.1'`, and move permissions/commands from `plugin.yml`.

- [x] `issue: Add startup version guard`
  - Label: `sustainability`, `phase-1`
  - Description: Validate server version in `onEnable()` and disable plugin cleanly on unsupported Paper builds.

- [x] `issue: Harden input validation and placeholder sanitization`
  - Label: `security`, `phase-1`
  - Description: Enforce safe player/world name patterns and sanitize placeholders used in command interpolation.

- [x] `issue: Define granular permission nodes`
  - Label: `security`, `phase-1`
  - Description: Expand `rga.admin` into fine-grained nodes and update `plugin.yml`/`paper-plugin.yml`.

## Phase 2 — Compatibility and persistence

- [x] `issue: Rewrite world handling for Paper 26.1 storage`
  - Label: `blocker`, `phase-2`
  - Description: Update world copy, discovery, and deletion logic to support Paper 26.1's nested dimensions structure. **Expanded scope:**
    1. **In-Session Spawn & Respawn Verification (Gap 1):** Validate bed/anchor respawns and cross-dimension handling under Paper 26.1 dimension paths. Ensure dead-player routing in `PartyManager.concludeGame()` correctly handles respawn within nested `dimensions/minecraft/the_nether` and `dimensions/minecraft/the_end` folders.
    2. **Portal Reroute Linkage Audit (Gap 2):** Audit `PortalBlockListener` logic against new Paper 26.1 world folder/dimension naming conventions. Verify that `WorldSettings` lookups resolve correctly when portal causes reference dimension-aware world names rather than legacy sibling folders.
    3. **Startup Gamerule Enforcement (Gap 3):** Ensure `worlds.yml` per-world gamerules explicitly fire post-world-load event hooks during session initialization. Confirm `WorldManager.applySettings()` persists gamerules across dimension sub-folders under Paper 26.1 lazy resolution.

- [x] `issue: Implement write-ahead session persistence and recovery`
  - Label: `blocker`, `phase-2`
  - Description: Persist active game session state to disk, recover orphaned sessions after restart, and remove state on normal conclude.

- [x] `issue: Add graceful world load failure handling`
  - Label: `blocker`, `phase-2`
  - Description: Catch world migration/load runtime exceptions and continue startup while logging actionable diagnostics.

- [x] `issue: Replace legacy ChatColor and material parsing`
  - Label: `sustainability`, `phase-2`
  - Description: Convert chat output to Adventure API components and use registry-safe material lookup for config values.

- [x] `#43` `issue: Standardize orphan session startup detection and status reporting`
  - Label: `sustainability`, `phase-2`
  - Milestone: `RGA persistence and recovery`
  - Scope: On startup, identify active persistence states missing companion hooks, flag them as ORPHANED in memory, output a distinct console warning, and surface them in `/rga sessions` list.
  - Acceptance Criteria: Orphaned worlds are clearly identified on boot without auto-deletion; `/rga cleanupsession <world>` remains the explicit destruction trigger.
  - **Resolved: v1.12.0**



## Phase 3 — Safe orchestration

- [x] `issue: Convert world copy I/O to async operations`
  - Label: `sustainability`, `phase-3`
  - Description: Offload template world copying to async tasks while keeping Bukkit world registration on the main thread.

- [x] `issue: Implement progress feedback for world copy`
  - Label: `feature`, `phase-3`
  - Description: Notify players while async copy is in progress and countdown before teleport.

- [x] `issue: Add console command allowlist support`
  - Label: `security`, `phase-3`
  - Description: Add config-driven allowlist enforcement for `console:` command lifecycle hooks.

- [x] `issue: Add /rga status command`
  - Label: `feature`, `phase-3`
  - Description: Provide runtime visibility into active sessions, loaded worlds, and recovery state.

- [x] `issue: Implement graceful onDisable() cleanup and recovery`
  - Label: `sustainability`, `phase-3`
  - Description: Clean up active sessions, persist recovery data when necessary, and detect orphaned state on next startup.

- [x] `#40` `issue: Implement template-level concurrency locks in WorldCopyManager`
  - Label: `sustainability`, `phase-3`
  - Milestone: `RGA safe orchestration`
  - Scope: Add a key-based locking mechanism (e.g., `ConcurrentHashMap<String, ReentrantLock>`) on source template names in `WorldCopyManager`.
  - Acceptance Criteria: Concurrent async session starts referencing the same template block gracefully without file access collisions; lock releases reliably in a `finally` block even on I/O failure.
  - **Resolved: v1.12.0**



## Phase 4 — Gameplay polish

- [x] `#39` `issue: Implement datapack stripping in WorldCopyManager`
  - Label: `sustainability`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Scope: Skip `/datapacks/` subdirectories during `copyTemplateWorld` in `WorldCopyManager` using `FileVisitResult.SKIP_SUBTREE` in `preVisitDirectory`.
  - Acceptance Criteria: Datapacks directories are stripped from destination session world folders during copying; Paper 26.1 nested dimension folders remain intact.
  - **Resolved: v1.12.0**

- [x] `issue: Add minigame queueing and auto-start behavior`

  - Label: `feature`, `phase-4`
  - Description: Support queueing for full minigames and auto-start the next waiting party when a session concludes.

- [x] `issue: Add spectator support for minigames`
  - Label: `feature`, `phase-4`
  - Description: Allow spectators to join active games in spectator mode and preserve separate inventory/advancement state.

- [x] `#22` `issue: Add first-visit spawn configuration`
  - Label: `feature`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Add `first-visit-spawn` support to world config and use it when a player lacks a tracked location.
  - **Resolved: v1.8.0**

- [x] `#23` `issue: Add configurable hub entry behavior`
  - Label: `feature`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Add hub inventory-clearing confirmation and optional restore-on-return behavior.
  - **Resolved: v1.9.0**

- [x] `#29` `issue: Define party persistence grace period for hub visits`
  - Label: `architectural`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Define explicit party state handling when individual members visit the hub temporarily, ensuring parties do not disband prematurely during transient transitions. Implement a grace-period timer prior to disbanding.
  - **Resolved: v1.10.0**

- [x] `#24` `issue: Build companion plugin event-driven integration API`
  - Label: `architectural`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Define and fire custom Bukkit events for start/conclude
    lifecycle transitions instead of raw command dispatch. Umbrella issue
    for #31–#37 below.
  - **Resolved: v1.11.0**

- [x] `#31` `issue: Fix event-ordering / state-commit bug in PartyManager.startGame()`
  - Label: `blocker`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Reorder world-load-failure check ahead of state mutation in
    both VANILLA and TEMPLATE branches; extract shared cleanup helper;
    prerequisite for #33.
  - **Resolved: v1.11.0**

- [x] `#32` `issue: Define RGA event API package and conventions`
  - Label: `architectural`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Per-subclass HandlerList, Cancellable conventions in `com.ronlab.rga.api.event`,
    EventPriority documentation template, concrete scores map type decision.
  - **Resolved: v1.11.0**

- [x] `#33` `issue: Implement MinigameStartEvent`
  - Label: `feature`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Cancellable event (uniformly honored) fired after
    world-load success, before state commit, in PartyManager.startGame().
    Tested via in-repo test listener.
  - **Resolved: v1.11.0**

- [x] `#34` `issue: Implement MinigameConcludeEvent`
  - Label: `feature`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Cancellable event (uniformly honored) with mutable scores
    map, fired on entry to PartyManager.concludeGame(). concludeGame()
    gains a boolean/enum return value so callers can detect a cancelled conclude.
    Tested via in-repo test listener.
  - **Resolved: v1.11.0**

- [x] `#35` `issue: Testing framework decision — MockBukkit vs. existing mock pattern`
  - Label: `sustainability`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Time-boxed spike on MockBukkit + Paper 26.1.2 / Java 25
    compatibility before writing event dispatch tests (Gate 0 prerequisite).
  - **Resolved: v1.11.0**

- [x] `#36` `issue: Companion artifact distribution strategy`
  - Label: `architectural`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: JitPack, building from a tagged release of an extracted
    rga-api Maven module (`com.ronlab:rga-api`). Softdepend + plugin presence guard
    adopted for EcstaticTech641/Block-Shuffle fork. Multi-module extraction
    and JitPack validation is a hard blocker before Gate 1 work begins.
  - **Resolved: v1.11.0**

- [x] `#37` `issue: Known-limitations documentation for the event API`
  - Label: `documentation`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Document failure-path gaps, reload/hot-swap behavior,
    companion plugin migration guide off console commands, and scores contract.
  - **Resolved: v1.11.0**

- [x] `#38` `issue: Implement companion-initiated session conclude API / event`
  - Label: `feature`, `phase-4`
  - Milestone: `RGA gameplay polish`
  - Description: Expose direct Java API or GameSessionRequestConcludeEvent for companion-initiated
    concludes with execution feedback. Tracked follow-up post #31–#37.
  - **Resolved: v1.12.0**

## Cross-phase tasks

- [x] `#25` `issue: Audit /reload command behavior and rename if needed`
  - Label: `blocker`, `cross-phase`
  - Milestone: `RGA security hardening`
  - Description: Confirm whether `/rga reload` uses server reloads, and rename to `/rga reloadconfig` if it only refreshes plugin config.
  - **Resolved: v1.11.0**

- [x] `#26` `issue: Add world name validation to all entry points`
  - Label: `security`, `cross-phase`
  - Milestone: `RGA security hardening`
  - Description: Enforce safe world names in config, commands, and file operations.
  - **Resolved: v1.11.0**

- [x] `#30` `issue: Harden config parsing for absent keys and nether toggles`
  - Label: `sustainability`, `cross-phase`
  - Milestone: `RGA security hardening`
  - Description: Implement null-safe defaults for missing fields (e.g., `conclude-commands` in minigames.yml), and add explicit `disable-nether: true|false` support per minigame to `worlds.yml`/`MinigameConfig`. Ensure all `ConfigurationSection` lookups handle absent sections gracefully to prevent runtime `NullPointerException`.
  - **Resolved: v1.11.0**

- [x] `#27` `issue: Document companion integration best practices`
  - Label: `documentation`, `cross-phase`
  - Milestone: `RGA gameplay polish`
  - Description: Document the recommended event-driven API plus safe command-hook patterns.
  - **Resolved: v1.12.0**

- [x] `#28` `issue: Evaluate datapack isolation strategy`
  - Label: `architectural`, `cross-phase`
  - Milestone: `RGA 26.1 rebuild`
  - Description: Evaluate datapack isolation strategies and performance implications for isolated minigame instances vs global server datapacks.
  - **Resolved: v1.12.0 (ADR-0002)**

- [ ] `#39` `issue: Implement datapack stripping in WorldCopyManager`
  - Label: `sustainability`, `phase-5`
  - Milestone: `RGA gameplay polish`
  - Description: Update WorldCopyManager.java to skip /datapacks/ subdirectories during copyTemplateWorld() using a unified exclusion set pattern (coordinating with #30 dimension filtering).
  - Tracked follow-up post ADR-0002.

- [x] `#41` `issue: Reconcile README.md with v1.12.0 state and surface architectural docs`
  - Label: `documentation`, `cross-phase`
  - Milestone: `RGA gameplay polish`
  - Scope: Update public-facing README to reflect v1.12.0, fix multi-module Maven build instructions, fix table formatting, and link out to `EVENT_API_KNOWN_LIMITATIONS.md` and `ADR-0002`.
  - Acceptance Criteria: README matches exact current codebase behavior with no version mismatches or stale limitation claims.
  - **Resolved: v1.12.0**

- [x] `#42` `issue: Enforce config snapshot immutability across /rga reloadconfig`
  - Label: `sustainability`, `cross-phase`
  - Milestone: `RGA security hardening`
  - Scope: Verify active `GameSession` instances reference immutable configuration snapshots created at start time. Update `/rga reloadconfig` feedback text to explicitly state live games remain unaffected.
  - Acceptance Criteria: Re-executing `/rga reloadconfig` mid-game changes settings for future sessions without mutating active game properties or throwing `ConcurrentModificationException`.
  - **Resolved: v1.12.0**



## Recommended labels

- `blocker`
- `security`
- `sustainability`
- `feature`
- `architectural`
- `documentation`
- `phase-1`
- `phase-2`
- `phase-3`
- `phase-4`
- `cross-phase`

## Recommended milestones

- `RGA 26.1 rebuild`
- `RGA security hardening`
- `RGA persistence and recovery`
- `RGA gameplay polish`

## Notes

Use this document as the source of truth when creating GitHub issues. Each issue should include:

* A short title matching the checklist item.
* A clear description of expected behavior.
* Acceptance criteria or testable success conditions.
* Associated labels and milestone.
* Links back to `plan.md` for implementation context.

`#30`/`#26` may be merged in the same release batch as `#24` provided each has
passed independent code review beforehand and does not share test dependencies
or overlapping change surfaces with the `#24` work.