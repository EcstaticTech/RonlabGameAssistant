# RGA Event API — Integration & Known Limitations

This document provides developer guidance for integrating companion minigame plugins with the **Ronlab Game Assistant (RGA) Event API** (`com.ronlab:rga-api`).

---

## 1. Maven Dependency & Distribution

Companion plugins should depend on `rga-api` via Maven or JitPack:

```xml
<dependency>
    <groupId>com.ronlab</groupId>
    <artifactId>rga-api</artifactId>
    <version>1.11.0</version>
    <scope>provided</scope>
</dependency>
```

### Dependency Declaration (`paper-plugin.yml`)
Companion plugins are strongly recommended to declare a **`softdepend`** on RGA to ensure safe standalone operation:

```yaml
name: Block-Shuffle
version: '1.0.0'
main: com.ronlab.blockshuffle.BlockShuffle
api-version: '26.1'
softdepend:
  - RonlabGameAssistant
```

### Runtime Presence Guard
In your companion plugin's `onEnable()`, guard RGA event listeners and API calls:

```java
if (Bukkit.getPluginManager().isPluginEnabled("RonlabGameAssistant")) {
    Bukkit.getPluginManager().registerEvents(new RgaEventListener(), this);
}
```

---

## 2. Event API Reference

All events reside under the package `com.ronlab.rga.api.event`.

### `MinigameStartEvent`
- **Fired**: After arena world creation/load succeeds, immediately before party state finalization and countdown teleport.
- **Cancellable**: Yes. If `setCancelled(true)` is called, RGA cleanly aborts game start, cleans up temporary arena world files and inventory groups, and returns party members to the lobby.
- **Fields**: `minigameId`, `minigameName`, `worldName`, `playerUuids`.

### `RGAGameRequestConcludeEvent`
- **Fired**: When a programmatic conclusion request is initiated via `RGA.requestSessionConclude(...)`.
- **Cancellable**: Yes. Companion plugins can inspect reason/scores and call `setCancelled(true)` to veto the conclusion request.
- **Listen-Only Interface**: Firing `RGAGameRequestConcludeEvent` manually does NOT trigger session teardown. Companion plugins must invoke `RGA.requestSessionConclude(...)`.
- **Fields**: `minigameId`, `minigameName`, `worldName`, `playerUuids`, `reason`, `scores` (`Map<UUID, Number>`).

### `MinigameConcludeEvent`
- **Fired**: On entry to `PartyManager.concludeGame()`.
- **Cancellable**: Yes. Calling `setCancelled(true)` prevents session conclusion.
- **Fields**: `minigameId`, `minigameName`, `worldName`, `playerUuids`, `scores` (`Map<UUID, Number>`).
- **Mutable Scores**: Listeners can read and modify the `scores` map (`event.getScores().put(uuid, score)`) prior to final cleanup.

---

## 3. Migration Guide & Programmatic Conclusion API

### Programmatic Conclude Interface (`requestSessionConclude`)
Companion plugins can programmatically conclude active minigame sessions by calling `RGA.requestSessionConclude(worldName, reason, scores)` on the main server thread.

```java
ConcludeResult result = RGA.getInstance().requestSessionConclude(
    "minigame_tag_123",
    "Game finished cleanly",
    Map.of(playerUuid, 100)
);
```

### Legacy Pattern (Deprecated)
Minigames previously dispatched raw console commands such as:
`console: team assign runner %leader%` or `/rga conclude <world>`

### Recommended Event-Driven Pattern
Register a standard Paper Bukkit Listener for lifecycle events:

```java
package com.ronlab.blockshuffle.listener;

import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.MinigameStartEvent;
import com.ronlab.rga.api.event.RGAGameRequestConcludeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class RgaEventListener implements Listener {

    @EventHandler
    public void onRequestConclude(RGAGameRequestConcludeEvent event) {
        if (!"block_shuffle".equals(event.getMinigameId())) return;
        // Veto or prepare for programmatic conclude request
    }

    @EventHandler
    public void onMinigameStart(MinigameStartEvent event) {
        if (!"block_shuffle".equals(event.getMinigameId())) return;
        // Initialize game logic for arena world: event.getWorldName()
    }

    @EventHandler
    public void onMinigameConclude(MinigameConcludeEvent event) {
        if (!"block_shuffle".equals(event.getMinigameId())) return;
        // Read final scores or perform companion cleanup
    }
}
```

---

## 4. Known Limitations & Failure Path Contracts

1. **Score Mutation (Last-Write-Wins Rules & EventPriority Precedence)**:
   - The `scores` map in `MinigameConcludeEvent` is mutable (`Map<UUID, Number>`).
   - If multiple listeners alter the score for a player, standard Bukkit `EventPriority` ordering applies (`LOWEST` -> `LOW` -> `NORMAL` -> `HIGH` -> `HIGHEST` -> `MONITOR`).
   - A listener executing at a higher priority runs later and overwrites values set by earlier listeners (Last-Write-Wins).

2. **Hot-Swapping & Server Reloads**:
   - Registered `rga-api` listeners should unregister or handle plugin disable gracefully during `/reload`.
   - Active session state is protected by write-ahead session persistence (`SessionManager`), but companion plugins must ensure custom in-memory event handlers are re-registered upon plugin re-enable.

3. **In-Process Conclude API Feedback & `ConcludeResult` Enum**:
   - Companion plugins invoking `RGA.requestSessionConclude(...)` or `PartyManager.concludeGame(...)` receive synchronous feedback via the [ConcludeResult](file:///m:/projects/RonlabGameAssistant/rga-api/src/main/java/com/ronlab/rga/api/event/ConcludeResult.java) enum (`com.ronlab.rga.api.event.ConcludeResult`).
   - Possible enum return values:
     - `SUCCESS`: The session was successfully concluded and cleaned up.
     - `CANCELLED`: Conclusion was cancelled by an event listener (and active online party members were remaining). Note: If zero online members remain, RGA overrides cancellation to prevent abandoned sessions.
     - `NOT_FOUND`: No active party/session was found matching the specified world name.
     - `ALREADY_CONCLUDING`: Session is already in the teardown phase (`Party.State.CONCLUDING`).
     - `ERROR`: An internal exception occurred during teardown.

4. **No Event Fired on World Creation Failure**:
   - `MinigameStartEvent` is **only** fired after arena world creation and template loading succeed.
   - If Bukkit world loading or template duplication fails (e.g. IO error, missing world folder), `MinigameStartEvent` will **NOT** fire.
   - In this failure scenario, RGA executes internal `abortGameStart()` cleanup:
     - Reverts party state to `LOBBY` and clears active world association.
     - Cleans up any registered temporary inventory groups (`InventoryManager`).
     - Triggers `WorldCopyManager.cleanupWorld()` to purge residual temporary world directories.
     - Deletes orphaned session persistence records (`SessionManager`).
     - Restores party members safely to the lobby.

6. **Datapack Isolation & Global Registries (ADR-0002)**:
   - Bukkit/Paper loads datapacks into global server registries. Sub-folder `/datapacks/` directories inside minigame session worlds are ignored by Paper and stripped by `WorldCopyManager`.
   - Companion plugins must implement minigame mechanics via Java event listeners rather than `.mcfunction` datapacks.
   - See complete Architectural Decision Record in [adr-0002-datapack-isolation-strategy.md](file:///m:/projects/RonlabGameAssistant/docs/adr-0002-datapack-isolation-strategy.md).

---

## Related Documentation

- [adr-0002-datapack-isolation-strategy.md](file:///m:/projects/RonlabGameAssistant/docs/adr-0002-datapack-isolation-strategy.md)
- [companion-integration.md](file:///m:/projects/RonlabGameAssistant/docs/companion-integration.md)
- [DECISION_35_TESTING_FRAMEWORK.md](file:///m:/projects/RonlabGameAssistant/DECISION_35_TESTING_FRAMEWORK.md)
- [ConcludeResult.java](file:///m:/projects/RonlabGameAssistant/rga-api/src/main/java/com/ronlab/rga/api/event/ConcludeResult.java)
