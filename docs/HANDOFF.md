# Ronlab Game Assistant (RGA) — Project Handoff & Architecture Specifications

> [!NOTE]
> **Single Source of Truth**: This document consolidates all historical design decisions, current architectural specifications, and the converged 3-sprint execution roadmap for Ronlab Game Assistant (RGA).

---

## 1. Executive Summary & Architecture Status

Ronlab Game Assistant (RGA) is an enterprise-grade Minecraft Paper 26.1 plugin and orchestration API for dynamic minigame instances, world lifecycle management, and party management.

### Key Architectural Pillars:
* **Paper 26.1 Plugin Platform**: Built using `paper-plugin.yml` with `api-version: '26.1'`, leverage Kyori Adventure API for native rich component messaging.
* **Nested Dimension Storage**: Fully compliant with Paper 26.1 dimension directory layout (`dimensions/minecraft/the_nether`, `dimensions/minecraft/the_end`), featuring template world cloning with datapack stripping.
* **Write-Ahead Session Persistence**: Persistent state engine (`SessionManager`) tracking active minigame worlds on disk with automatic orphan detection on startup and clean runtime recovery.
* **Thread-Safe Orchestration**: `WorldCopyManager` uses key-level template concurrency locks (`ConcurrentHashMap<String, ReentrantLock>`) preventing I/O collisions during parallel world copying.
* **Inventory & Hub Management**: World-group based inventory separation (`InventoryManager`), hub snapshots, and slot-guarded join item delivery (Navigator Compass at Index 8, Social Head at Index 7).
* **Programmatic Companion API**: Decoupled `rga-api` module exposing `RGAApi`, `SessionConcludeEvent`, and `requestSessionConclude` for external game plugins.

---

## 2. Decisions Log (Decisions 1–35 + Converged Architecture)

| Decision ID | Area | Summary & Technical Impact |
| :--- | :--- | :--- |
| **ADR-0001** | Platform | Adopted Paper 26.1 API & `paper-plugin.yml` native format. |
| **ADR-0002** | Storage | Datapack isolation & stripping during world copy via `SKIP_SUBTREE`. |
| **ADR-0003** | Persistence | Write-ahead session persistence logs written prior to world creation. |
| **ADR-0004** | Recovery | Startup recovery scans for orphaned session files; manual destruction via `/rga cleanupsession`. |
| **ADR-0005** | UI / Text | Replaced legacy Bukkit `ChatColor` with Kyori Adventure `Component` & MiniMessage. |
| **ADR-0006** | Material API | Replaced string-based material lookups with registry-safe `AdventureUtil.safeMaterial(...)`. |
| **ADR-0007** | Security | Input validation and strict regex filtering (`^[a-zA-Z0-9_-]+$`) for world and player names. |
| **ADR-0008** | Security | Granular permission nodes under `rga.admin.*` (`rga.admin.session`, `rga.admin.reload`, etc.). |
| **ADR-0009** | Concurrency | Template-level locking in `WorldCopyManager` to prevent parallel copy collisions. |
| **ADR-0010** | Security | Console command allowlist enforcement for `console:` lifecycle hooks in `minigames.yml`. |
| **ADR-0011-30** | Orchestration | Grace period timers, party invite timeouts, portal listener rerouting, and respawn routing. |
| **ADR-0031** | API | Introduced decoupled `rga-api` artifact for third-party minigame integration. |
| **ADR-0032** | API | Added `SessionConcludeEvent` allowing plugins to intercept session endings. |
| **ADR-0033** | Teleport | Multi-stage teleportation sequence ensuring target world chunks are loaded before player transfer. |
| **ADR-0034** | Hardening | World load failure boundary catching `WorldInitException` cleanly. |
| **ADR-0035** | Testing | Comprehensive mock-based testing harness and multi-client QA test suite protocol. |
| **Converged-1** | Log Hygiene | Elimination of `saveResource` log spam by enforcing explicit file existence pre-checks. |
| **Converged-2** | Slot Safety | Guaranteed non-destructive hub item slotting for compass (slot 8) and social head (slot 7). |
| **Converged-3** | Handoff | Single source-of-truth documentation consolidation (`HANDOFF.md` & `OPEN_QUESTIONS.md`). |

---

## 3. Converged 3-Sprint Roadmap

```mermaid
flowchart LR
    subgraph Sprint 1 [Sprint 1: Hygiene & QA]
        S1A["Config Spam Fix"] --> S1B["Hub Item Guards"]
        S1C["Docs Consolidation"] --> S1D["QA Suite Setup"]
    end
    subgraph Sprint 2 [Sprint 2: Hardening & Persistence]
        S2A["Nested Dimension Teleports"] --> S2B["Orphan Cleanup Rules"]
        S2B --> S2C["Grace Period Edge Cases"]
    end
    subgraph Sprint 3 [Sprint 3: Multi-Client & API]
        S3A["Multi-Client Harness"] --> S3B["Companion API Export"]
        S3B --> S3C["Session Telemetry"]
    end
    Sprint 1 --> Sprint 2 --> Sprint 3
```

### Sprint 1: Codebase Hygiene, Documentation & QA Pre-Flight
* [x] **Config Spam Remediation**: Wrap resource saving routines with explicit `file.exists()` checks to eradicate Bukkit reload warnings.
* [x] **Hub Item Slotting Verification**: Audit `HubListener` & `SocialItem` slotting rules, ensuring 5-tick post-join safety and slot collision protection.
* [x] **Documentation Consolidation**: Establish `docs/HANDOFF.md` and `docs/OPEN_QUESTIONS.md`.
* [x] **QA Pre-Flight Suite**: Generate multi-client verification checklist (`docs/qa/BLOCKSHUFFLE_QA_SUITE.md`).

### Sprint 2: Hardening & Persistence Refinement
* [ ] **Nested Dimension Safety**: Perform boundary testing on nether/end portals inside dynamic session worlds.
* [ ] **Orphan Session Diagnostics**: Refine `/rga status` detailed output for corrupted disk states.
* [ ] **Concurrency Stress Tests**: Verify concurrent party creation with identical map templates under lock contention.

### Sprint 3: Multi-Client Load Verification & Companion API
* [ ] **Multi-Client Execution**: Run 2-player and 4-player automated load tests across multiple minigame loops.
* [ ] **Companion API Expansion**: Finalize `rga-api` helper methods for programmatic session queries and score submissions.
* [ ] **Session Telemetry**: Add optional metric logging for session durations and teardown latency.
