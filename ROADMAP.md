# Ronlab Game Assistant (rga-core) Roadmap

This roadmap documents the framework evolution, sprint history, and milestone targets for the `rga-core` engine and `rga-api` ecosystem under the Micro-Companion Architecture (CPMK standard).

---

## Executive Status

- **Framework Version**: `1.13.1`
- **Target Platform**: Java 25 | PaperMC 26.2 (`api-version: '26.2'`)
- **Architecture**: Micro-Companion Architecture (CPMK)
- **Active Target**: **Sprint 1.13.1: Template Discovery, Dynamic GUI & Central Command Routing**

---

## Sprint Progression & Milestones

### Sprint 1 through 5: Core Architecture & Session Recovery
- [x] **Sprint 1: Concurrency & Async Engine** — Non-blocking directory purging (`AsyncDirectoryDeleter`), dynamic session isolation (`session_[minigame]_[timestamp]_[uuid]`), lock-free template operations.
- [x] **Sprint 2: Persistence & Recovery Layer** — Write-Ahead Logging (`SessionStateWALWriter`), `SessionSnapshot` immutability, `PowerLossRecoveryHandler` and boot-time session auditor.
- [x] **Sprint 3: Spectator & Bridge API** — JIT spectator inventory/advancement isolation, typed `RGASessionControl` interface contracts for companion plugins.
- [x] **Sprint 4: Hub & Grace Period Polish** — Hub inventory snapshots, party persistence grace period, first-visit spawn vectors.
- [x] **Sprint 5: PaperMC 26.2 & Event Model** — Paper 26.2 descriptor migration (`api-version: '26.2'`), `MinigameStartEvent` / `MinigameConcludeEvent` cancellable lifecycle bus.

---

### Sprint 6: Core Engine Stabilization & CPMK Alignment
**Status**: [x] **100% COMPLETE**

- [x] **Deterministic Spawn Injection (`WorldCopyManager`)**: Integrated `applyDeterministicSpawn()` in `WorldCopyManager.java` to set world spawn coordinates directly upon world load/copying, overriding saved vectors or defaulting gracefully to `(0.5, 100.0, 0.5)`. Eliminates synchronous main-thread `PlayerSpawnFinder` heightmap scans and resolves the 15-second watchdog server hang during `Bukkit.createWorld()`.
- [x] **CPMK Event Bus Standardization**: Verified payload integrity, cancellation rules, and score map synchronization across `MinigameStartEvent` and `MinigameConcludeEvent` for companion plugins (`rga-sumo`, `rga-bingo`, `rga-deathrace`, `rga-turfwars`).
- [x] **PaperMC 26.2 Metadata Compliance**: Standardized `api-version: '26.2'` descriptor across `paper-plugin.yml` and aligned multi-module Maven dependency trees (`rga-parent`, `rga-api`, `rga-plugin`).
- [x] **Baseline Documentation & Default Config**: Updated `README.md`, `CHANGELOG.md`, `ROADMAP.md`, and `config.yml` with comprehensive architectural documentation, administrative command guides, and inline configuration specs.

---

### Sprint 7: Stage 3 Persistence Layer (Embedded SQLite in rga-persistence)
**Status**: [x] **100% COMPLETE**

- [x] **Embedded Database Engine (`rga-persistence`)**: Integrated `sqlite-jdbc` and `flyway-core` into dedicated `rga-persistence` module with `ServicesResourceTransformer` shaded SPI declarations.
- [x] **Public API Contract (`RGAStatsProvider` & `PlayerMinigameStats`)**: Exposed non-blocking `RGAStatsProvider` interface and `PlayerMinigameStats` record in `rga-api:1.13.0` with `CompletableFuture<T>` read queries and non-blocking fire-and-queue write tasks.
- [x] **Bounded Worker & Backpressure (`dbExecutor`)**: Implemented single-threaded database worker backed by `ArrayBlockingQueue(2048)` and non-blocking drop-and-log backpressure handler.
- [x] **Exponential Backoff Lock Handling**: Wrapped SQL operations in 3-try exponential backoff logic targeting `SQLITE_BUSY` errors.
- [x] **Automated Schema Migration & Hybrid DDL**: Integrated `V1__init_stats_schema.sql` Flyway migration script, hybrid DDL schema (`player_stats` table with strict core metrics + metadata JSON TEXT), and leaderboard index `idx_minigame_wins`.
- [x] **SQLite WAL Engine Initialization**: Automatic `PRAGMA journal_mode=WAL;`, `PRAGMA busy_timeout=5000;`, and `PRAGMA synchronous=NORMAL;` execution at `/plugins/RonlabGameAssistant/data/rga.db`.

---

### Sprint 1.13.1: Template Discovery, Dynamic GUI & Central Command Routing
**Status**: [x] **100% COMPLETE**

- [x] **Immutable Record Contract (`MapTemplateMetadata`, Task API-1)**: Defined `MapTemplateMetadata` record in `rga-api` with compact validation, defensive copies, and builder pattern.
- [x] **Central Command Router API (`RGACommandRouter`, Task API-2)**: Defined `RGACommandRouter` contract in `rga-api` for action sanitization, permission enforcement (`rga.user.join`, `rga.tp`, `rga.admin`), and spectator party state validation.
- [x] **Async Template Discovery Engine (`TemplateDiscoveryService`, Task CORE-1)**: Implemented `Files.walkFileTree` NIO walker scanning server root `/templates` (with plugin data folder fallback), populating dynamic concurrent registry, and registering fallback `Material.BARRIER` dummy items.
- [x] **Default Template Settings Applicator (`WorldConfigManager`, Task CORE-2)**: Parsed `default-template` node from `config.yml` and integrated with `WorldCopyManager` to enforce peaceful difficulty, weather-locks, time-locks, and default gamerules on template world creation.
- [x] **Dynamic Paginated Inventory GUI (`PaginatedMapMenu`, Task CORE-3)**: Implemented 54-slot dynamic paginated menu provider with border injection, inner grid mapping (21 items/page), title parser `parseTitle`, and bottom-row navigation.
- [x] **Administrative Template Staging Engine (`TemplateStagingManager`, Task CORE-ADMIN-1)**: Implemented `/rga template <load|tp|save|unload|list>` workflow with automatic player evacuation to Hub, operator disconnect auto-unloading, and `EDITING` concurrency lock enforcement in `WorldCopyManager` and `PartyManager`.
- [x] **Environment Clock & Weather Guards (`WorldConfigManager` & `WorldManager`, Task CORE-GUARD-1)**: Hardened daylight cycle and weather mutations against non-`NORMAL` environments (`Environment.NETHER`, `Environment.THE_END`) to eliminate runtime `IllegalArgumentException` faults on multi-world initialization.
- [x] **Navigator & Category Menu Normalization (Task CORE-4)**: Implemented `DefaultRGACommandRouter` and aligned `menus.yml` root navigator router across centered slots 11–15 (SMP, Creative, Adventure, Parkour, Minigames), routing category entries directly to dynamic paginated category views.
- [x] **Dynamic Map Key Persistence (`VARCHAR(64)` Schema, Task PERSIST-1)**: Added `V2__expand_minigame_id_length.sql` and updated `player_stats` DDL column definition to `VARCHAR(64)` for untruncated dynamic map key persistence.
- [x] **Map Metadata Deployment (`map.yml`, Task MAPS-1)**: Deployed production `map.yml` descriptors with Minecraft world difficulties (`PEACEFUL` for parkour maps as peaceful adventure maps) to server staging ground (`projects/rga26servercopy/templates/`). Retained clean example template layout (`templates/minigames/sumo/map.yml` and `rga-companion-skeleton/`) in codebase repository.
- [x] **Config & Navigator Menu Alignment (Task MAPS-2)**: Deprecated hardcoded static map filler lists in `config.yml`, retaining core toggles, persistent inventory groups, and `default-template` configuration block. Updated production `menus.yml` category options to dispatch `rga:open_category`.

---

### Future Sprint Targets (Sprint 8+)

#### Sprint 8: Companion Ecosystem Standard Expansion
- Standardize companion plugin bootstrap template (`rga-companion-template`) adhering to CPMK 26.2 standards.
- Real-time event broadcasting bridge for companion chat announcers and match observers.

#### Sprint 9: Web Telemetry & Server Management Interface
- Lightweight HTTP REST API / WebSocket telemetry exporter in `rga-core`.
- Real-time session monitoring dashboard for server operators.

---

## Architectural Principles

1. **Non-Blocking Main Thread**: File I/O, template copying, WAL flushes, and database writes must execute off the Bukkit main thread.
2. **Deterministic State**: Ephemeral session worlds must boot with explicit deterministic coordinates to avoid costly engine searches.
3. **Decoupled Companions**: Companion plugins interface exclusively through typed `rga-api` events and interfaces, never mutating core private state.
