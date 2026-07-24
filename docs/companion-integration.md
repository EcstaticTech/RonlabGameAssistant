# Companion Plugin Integration Guide — Session Conclusion API & Events

This guide documents the programmatic session conclusion interface introduced in Ronlab Game Assistant (RGA) v1.11.0 (Issue #38).

## Overview

Companion plugins can programmatically trigger session conclusions and listen to conclusion request events.

```
                    ┌───────────────────────────────────────────┐
                    │        MinigameEvent (Abstract)           │
                    │  (minigameId, minigameName, worldName)    │
                    └─────────────────────┬─────────────────────┘
                                          │
            ┌─────────────────────────────┼─────────────────────────────┐
            ▼                             ▼                             ▼
┌───────────────────────┐     ┌───────────────────────┐     ┌───────────────────────────────┐
│  MinigameStartEvent   │     │ MinigameConcludeEvent │     │  RGAGameRequestConcludeEvent  │
│  (Start Notification) │     │ (Conclude Teardown)   │     │  (Programmatic Veto/Intercept)│
└───────────────────────┘     └───────────────────────┘     └───────────────┬───────────────┘
                                                                            │
                                                                            └─ Implements Cancellable
```

## 1. Direct Execution Interface (`requestSessionConclude`)

To request session conclusion programmatically, companion plugins **must** invoke `RGA.requestSessionConclude(...)`:

```java
RGA rga = RGA.getInstance();
Map<UUID, Number> scores = Map.of(
    playerUuid, 1500.5 // Double/Long scores will be safely clamped and truncated
);

ConcludeResult result = rga.requestSessionConclude("minigame_tag_123", "Victory threshold reached", scores);

switch (result) {
    case SUCCESS -> getLogger().info("Session concluded successfully.");
    case CANCELLED -> getLogger().info("Conclusion request vetoed by an event listener.");
    case NOT_FOUND -> getLogger().warning("No active RGA session found for target world.");
    case ALREADY_CONCLUDING -> getLogger().warning("Session is already in teardown phase.");
    case ERROR -> getLogger().severe("Internal error occurred during teardown.");
}
```

### Constraints & Guarantees
- **Main Thread Required**: `requestSessionConclude` must be called on Bukkit's main server thread. Calling from an async context throws an `IllegalStateException`.
- **Score Conversion**: Companion plugins may pass `Number` scores (e.g. `Double` or `Long`). RGA converts values into `Integer` scores using an overflow-clamped conversion routine (`Integer.MIN_VALUE`..`Integer.MAX_VALUE`).
- **Teardown State Guard**: When teardown begins, party state transitions to `Party.State.CONCLUDING`. Subsequent conclude requests return `ConcludeResult.ALREADY_CONCLUDING`.

## 2. Event Listener Veto Interface (`RGAGameRequestConcludeEvent`)

Companion plugins can register a listener for `RGAGameRequestConcludeEvent` to inspect or veto programmatic conclude requests before teardown begins:

```java
@EventHandler
public void onRequestConclude(RGAGameRequestConcludeEvent event) {
    if ("minigame_tag_123".equals(event.getWorldName())) {
        if (someCustomConditionUnmet()) {
            event.setCancelled(true); // Vetoes the conclude request
        }
    }
}
```

> [!IMPORTANT]
> **Listen-Only Event Rule**: `RGAGameRequestConcludeEvent` is a read/veto-only event. Firing `RGAGameRequestConcludeEvent` manually via `Bukkit.getPluginManager().callEvent(...)` does **NOT** trigger RGA's internal session teardown logic. Companion plugins must call `RGA.requestSessionConclude(...)` directly.

> [!NOTE]
> **Admin Command Bypass**: `/rga conclude` and `/rga concludeall` are administrative commands that bypass `RGAGameRequestConcludeEvent` and proceed directly to `MinigameConcludeEvent`. If your companion plugin needs to react to all conclusions (including admin commands), listen to `MinigameConcludeEvent`.

## Summary of `ConcludeResult` Status Codes

| Code | Description |
| --- | --- |
| `SUCCESS` | Session was successfully concluded and cleaned up. |
| `CANCELLED` | Conclusion request was vetoed by an event listener. |
| `NOT_FOUND` | Target world name does not match an active RGA session. |
| `ALREADY_CONCLUDING` | Session is already undergoing teardown (`Party.State.CONCLUDING`). |
| `ERROR` | An internal exception occurred during teardown. |
