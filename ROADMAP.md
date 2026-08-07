# Ronlab Game Assistant (rga-core) Roadmap

This roadmap documents the framework evolution, sprint history, and milestone targets for the `rga-core` engine and `rga-api` ecosystem under the Micro-Companion Architecture (CPMK standard).

---

## Executive Status

- **Framework Version**: `1.13.0-SNAPSHOT` / `2.0.0`
- **Target Platform**: Java 25 | PaperMC 26.2 (`api-version: '26.2'`)
- **Architecture**: Micro-Companion Architecture (CPMK)
- **Active Target**: **Sprint 7: Stage 3 Persistence Layer (Embedded SQLite/H2 in `rga-api`)**

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
