# Open Questions & Sprint 3 Technical Backlog

> [!NOTE]
> This document tracks unresolved technical decisions, edge-case questions, and companion API design items targeted for evaluation prior to or during **Sprint 3**.

---

## 1. Multi-Client QA & Performance Scale

### Q1.1: Automated Multi-Client Harness Integration
* **Question**: Should multi-client session testing rely solely on BukkitMock unit tests + human QA checklists, or introduce an automated headless bot runner (e.g. Mineflayer / MockBukkit multi-player simulator)?
* **Current Status**: MockBukkit unit tests handle event propagation; human multi-client checklist defined in `docs/qa/BLOCKSHUFFLE_QA_SUITE.md`.
* **Target Resolution**: Sprint 3.

### Q1.2: Dimension Pre-generation for Vanilla Minigames
* **Question**: For `VANILLA` minigame types, pre-generating Nether/End chunk regions during session start causes a 1-2 second async generation delay. Should we add a configurable chunk pre-gen radius flag in `minigames.yml`?
* **Current Status**: Default Paper 26.1 lazy chunk generation handles nether/end portals on demand.
* **Target Resolution**: Sprint 3.

---

## 2. Companion API & Third-Party Integration

### Q2.1: Granular Score Reporting Contract
* **Question**: Should `rga-api` support multi-team or key-value metric payloads in `requestSessionConclude(worldName, reason, metricsMap)` instead of `Map<UUID, ? extends Number>`?
* **Current Status**: `Map<UUID, ? extends Number>` covers player-based leaderboard minigames (e.g., BlockShuffle, Manhunt).
* **Target Resolution**: Sprint 3.

### Q2.2: Cross-Server / Velocity Proxy Hooks
* **Question**: Will RGA remain a single-instance Paper plugin orchestrating local world instances, or need Redis/Bungee/Velocity messaging for proxy-wide party routing?
* **Current Status**: Architecture is optimized for single-instance Paper 26.1 servers with multi-world session isolation.
* **Target Resolution**: Post-Sprint 3 evaluation.

---

## 3. Companion Plugin Alignment & CPMK Migration Audit (August 2026)

### Q3.1: Version Constraint Standardization Across Companions
* **Question**: Should companion plugin `pom.xml` dependencies be locked to explicit release version `<version>1.13.0</version>` (matching `rga-turfwars`, `rga-announcer`, `rgaParkour`) or maintain version ranges like `[1.12.0, 1.14.0-SNAPSHOT)` (as in `Block-Shuffle`)?
* **Current Status**: Core RGA locked at `1.13.0`. Companion `rga-turfwars` uses typed `RGASessionControl` API bridge.
* **Delegation Note**: Recommend standardizing all companion `pom.xml` dependencies to `1.13.0` during their next maintenance cycle.

### Q3.2: Legacy Direct Reflection Audit
* **Question**: Are any unmigrated companion plugins still attempting direct Java reflection calls (`getMethod("requestSessionConclude", ...)` or `saveResource("config.yml", false)`)?
* **Current Status**: `rga-turfwars` refactored to typed `RGASessionControl` interface in `RgaBridge.java`. Core RGA log hygiene enforced.
* **Delegation Note**: Recommend verifying other legacy plugins (`Block-Shuffle`, `InfectedManhunt`, `DeathRace`, `heatWave`) to ensure no lingering reflection methods exist.

---

## 4. Resolved Sprint 1.13.1 Architectural Decisions (August 2026)

### Q4.1: Template Pathing Resolution
* **Decision**: `TemplateDiscoveryService` scans `templates/` folder at server root first (`<server_root>/templates`). If missing/invalid, falls back to plugin data folder (`plugins/RonlabGameAssistant/templates`).

### Q4.2: Category GUI Routing Resolution
* **Decision**: Category navigator options (e.g. Parkour, Minigames) dispatch `rga:open_category <name>` and open dynamic 54-slot `PaginatedMapMenu` instances filtered by matching category string.

### Q4.3: Permission Nodes & Admin Bypass Resolution
* **Decision**: Central command router enforces permission node `rga.user.join` for minigame dispatches and `rga.tp` for world teleports. Permission `rga.admin` bypasses all permission checks.

### Q4.4: World Difficulty & Repository Template Layout
* **Decision**: `map.yml` difficulty setting represents standard Bukkit `Difficulty` enum (`PEACEFUL`, `EASY`, `NORMAL`, `HARD`). All parkour maps use `PEACEFUL` (peaceful adventure maps). Repository retains `templates/` in git tracking with clean example layout (`rga-companion-skeleton/` and example `minigames/sumo/map.yml`), while full production descriptors are deployed to server staging ground (`rga26servercopy`).


