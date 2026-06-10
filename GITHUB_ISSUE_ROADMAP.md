# RGA GitHub Issue Roadmap

This document converts the RGA maintenance plan into an actionable GitHub issue checklist.
Use it to create issues, assign labels, and track milestone progress.

## Phase 1 — Foundation

- [ ] `issue: Migrate to paper-plugin.yml`
  - Label: `sustainability`, `phase-1`
  - Milestone: `RGA 26.1 rebuild`
  - Description: Add `paper-plugin.yml`, update plugin bootstrap if needed, declare `api-version: '26.1'`, and move permissions/commands from `plugin.yml`.

- [ ] `issue: Add startup version guard`
  - Label: `sustainability`, `phase-1`
  - Description: Validate server version in `onEnable()` and disable plugin cleanly on unsupported Paper builds.

- [ ] `issue: Harden input validation and placeholder sanitization`
  - Label: `security`, `phase-1`
  - Description: Enforce safe player/world name patterns and sanitize placeholders used in command interpolation.

- [ ] `issue: Define granular permission nodes`
  - Label: `security`, `phase-1`
  - Description: Expand `rga.admin` into fine-grained nodes and update `plugin.yml`/`paper-plugin.yml`.

## Phase 2 — Compatibility and persistence

- [ ] `issue: Rewrite world handling for Paper 26.1 storage`
  - Label: `blocker`, `phase-2`
  - Description: Update world copy, discovery, and deletion logic to support Paper 26.1's nested dimensions structure.

- [ ] `issue: Implement write-ahead session persistence and recovery`
  - Label: `blocker`, `phase-2`
  - Description: Persist active game session state to disk, recover orphaned sessions after restart, and remove state on normal conclude.

- [ ] `issue: Add graceful world load failure handling`
  - Label: `blocker`, `phase-2`
  - Description: Catch world migration/load runtime exceptions and continue startup while logging actionable diagnostics.

- [ ] `issue: Replace legacy ChatColor and material parsing`
  - Label: `sustainability`, `phase-2`
  - Description: Convert chat output to Adventure API components and use registry-safe material lookup for config values.

## Phase 3 — Safe orchestration

- [ ] `issue: Convert world copy I/O to async operations`
  - Label: `sustainability`, `phase-3`
  - Description: Offload template world copying to async tasks while keeping Bukkit world registration on the main thread.

- [ ] `issue: Implement progress feedback for world copy`
  - Label: `feature`, `phase-3`
  - Description: Notify players while async copy is in progress and countdown before teleport.

- [ ] `issue: Add console command allowlist support`
  - Label: `security`, `phase-3`
  - Description: Add config-driven allowlist enforcement for `console:` command lifecycle hooks.

- [ ] `issue: Add /rga status command`
  - Label: `feature`, `phase-3`
  - Description: Provide runtime visibility into active sessions, loaded worlds, and recovery state.

- [ ] `issue: Implement graceful onDisable() cleanup and recovery`
  - Label: `sustainability`, `phase-3`
  - Description: Clean up active sessions, persist recovery data when necessary, and detect orphaned state on next startup.

## Phase 4 — Gameplay polish

- [ ] `issue: Add minigame queueing and auto-start behavior`
  - Label: `feature`, `phase-4`
  - Description: Support queueing for full minigames and auto-start the next waiting party when a session concludes.

- [ ] `issue: Add spectator support for minigames`
  - Label: `feature`, `phase-4`
  - Description: Allow spectators to join active games in spectator mode and preserve separate inventory/advancement state.

- [ ] `issue: Add first-visit spawn configuration`
  - Label: `feature`, `phase-4`
  - Description: Add `first-visit-spawn` support to world config and use it when a player lacks a tracked location.

- [ ] `issue: Add configurable hub entry behavior`
  - Label: `feature`, `phase-4`
  - Description: Add hub inventory-clearing confirmation and optional restore-on-return behavior.

- [ ] `issue: Build companion plugin event-driven integration API`
  - Label: `architectural`, `phase-4`
  - Description: Define and fire custom Bukkit events for start/conclude lifecycle transitions instead of raw command dispatch.

## Cross-phase tasks

- [ ] `issue: Audit /reload command behavior and rename if needed`
  - Label: `blocker`, `cross-phase`
  - Description: Confirm whether `/rga reload` uses server reloads, and rename to `/rga reloadconfig` if it only refreshes plugin config.

- [ ] `issue: Add world name validation to all entry points`
  - Label: `security`, `cross-phase`
  - Description: Enforce safe world names in config, commands, and file operations.

- [ ] `issue: Document companion integration best practices`
  - Label: `documentation`, `cross-phase`
  - Description: Document the recommended event-driven API plus safe command-hook patterns.

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
