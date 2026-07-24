# Companion Plugin Migration Kit (CPMK) — Architecture Guide

> [!NOTE]
> This guide outlines the event-driven lifecycle and architecture for building lightweight companion minigames powered by Ronlab Game Assistant (RGA).

---

## 1. Architectural Overview

Companion plugins delegate heavy server infrastructure responsibilities to RGA:

```mermaid
sequenceDiagram
    participant User as Player / Admin
    participant RGA as Ronlab Game Assistant
    participant Comp as Companion Plugin (e.g. DeathRace)

    User->>RGA: Select game in Hub / /party start
    RGA->>RGA: Clone map template / generate world
    RGA->>RGA: Teleport players & apply gamerules
    RGA->>Comp: Dispatch MinigameStartEvent(world, players)
    Comp->>Comp: Run game logic & win condition loops
    Comp->>RGA: Call requestSessionConclude(world, reason, scores)
    RGA->>User: Teleport to Hub & restore inventories
    RGA->>Comp: Dispatch MinigameConcludeEvent(world)
    RGA->>RGA: Teardown & delete session world folder
```

---

## 2. Shared Responsibilities Matrix

| Functionality | Handled By | RGA Mechanism |
| :--- | :--- | :--- |
| World Generation / Cloning | **RGA** | `WorldCopyManager` async template copy |
| Inventory Clear / Save / Restore | **RGA** | `InventoryManager` world groups & Hub snapshots |
| Player Teleportation to/from Hub | **RGA** | `HubListener` & `SessionManager` |
| Disconnect & Orphan Recovery | **RGA** | `SessionManager` write-ahead state logs |
| Game Rules & Win Conditions | **Companion Plugin** | Custom listeners & task runnables |
| Leaderboards / Victory Messages | **Companion Plugin** | Adventure API text components |

---

## 3. Best Practices & Safety Guidelines

1. **Decoupled API Imports**: Import `com.ronlab.rga.api.*` for event structures and key models.
2. **Pure Model Identifiers**: Use `MinigameId.of("yourgame")` for lightweight, offline-testable key identifiers.
3. **Graceful Degradation**: Always verify `RGA.getInstance() != null` during plugin enable.
