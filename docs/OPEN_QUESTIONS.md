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
