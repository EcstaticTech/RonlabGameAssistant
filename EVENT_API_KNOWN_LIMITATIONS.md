# RGA Event API — Integration & Known Limitations

This document provides developer guidance for integrating companion minigame plugins with the **Ronlab Game Assistant (RGA) Event API** (`com.ronlab:rga-api`).

---

## 1. Maven Dependency & Distribution

Companion plugins should depend on `rga-api` via Maven or JitPack:

```xml
<dependency>
    <groupId>com.ronlab</groupId>
    <artifactId>rga-api</artifactId>
    <version>1.10.0</version>
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

### `MinigameConcludeEvent`
- **Fired**: On entry to `PartyManager.concludeGame()`.
- **Cancellable**: Yes. Calling `setCancelled(true)` prevents session conclusion.
- **Fields**: `minigameId`, `minigameName`, `worldName`, `playerUuids`, `scores` (`Map<UUID, Number>`).
- **Mutable Scores**: Listeners can read and modify the `scores` map (`event.getScores().put(uuid, score)`) prior to final cleanup.

---

## 3. Migration Guide from Legacy Console Commands

### Legacy Pattern (Deprecated)
Minigames previously dispatched raw console commands such as:
`console: team assign runner %leader%` or `/rga conclude <world>`

### Recommended Event-Driven Pattern
Register a standard Paper Bukkit Listener for lifecycle events:

```java
package com.ronlab.blockshuffle.listener;

import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.MinigameStartEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class RgaEventListener implements Listener {

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

1. **Score Mutation (Last-Write-Wins)**:
   - The `scores` map in `MinigameConcludeEvent` is mutable. If multiple listeners alter the score for a player, standard Bukkit `EventPriority` ordering applies (higher priority runs later and takes precedence).

2. **Hot-Swapping & Server Reloads**:
   - Registered `rga-api` listeners should unregister or handle plugin disable gracefully during `/reload`. Active session state is protected by write-ahead session persistence.

3. **In-Process Conclude API Feedback**:
   - `PartyManager.concludeGame(worldName, scores)` returns a `boolean` (`true` if concluded, `false` if cancelled or party not found). Companion plugins calling this method directly in Java receive synchronous feedback.
