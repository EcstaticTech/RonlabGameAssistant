# Companion Plugin Migration Kit (CPMK) — Migration Checklist

> [!IMPORTANT]
> **Developer Standard**: This checklist details the step-by-step procedure for converting a standalone Paper minigame plugin into an RGA-integrated Companion Plugin.

---

## Migration Checklist Steps

### Step 1: Declare Dependency in `paper-plugin.yml`
Add `RonlabGameAssistant` to your plugin's dependency section so RGA loads before your companion plugin:
```yaml
dependencies:
  server:
    RonlabGameAssistant:
      load: BEFORE
      required: true
```

---

### Step 2: Configure `minigames.yml` & Solo-Developer Testing Guard
* Ensure your minigame definition is registered under `minigames.yml` in the RGA configuration directory.
* Set `min-players: 1` during local solo testing.
* **CRITICAL GUARD**: At the top of your game loop or win-condition evaluator, explicitly check for single-player testing mode:
```java
// Prevent single-player testing from immediately triggering 0-opponent win conditions
if (initialPlayerCount == 1) {
    plugin.getLogger().info("[CPM] Single-player testing mode detected; suppressing automatic 0-opponent win condition.");
    return;
}
```

---

### Step 3: Subscribe to `MinigameStartEvent`
Remove any custom world generation or teleportation code from your plugin. RGA handles template cloning, nested dimension setup, and player teleportation automatically.

In your listener:
```java
@EventHandler
public void onMinigameStart(MinigameStartEvent event) {
    if (!event.getMinigameId().equals("deathrace")) return;

    String targetWorld = event.getWorldName();
    List<UUID> players = event.getPlayerUuids();
    UUID leader = event.getLeaderUuid();

    // Initialize game session state for targetWorld
    setupGameSession(targetWorld, players, leader);
}
```

---

### Step 4: Programmatically Trigger Session Conclusion
When your game loop finishes or a winner is declared, request session conclusion via RGA's companion API:
```java
Map<UUID, Integer> scores = new HashMap<>();
scores.put(winnerUuid, 100);

// RGA handles teleporting players back to Hub, restoring inventories, and cleaning up dynamic world folders
RGA.getInstance().requestSessionConclude(worldName, "Game Completed", scores);
```

---

### Step 5: Clean Up Local Session State on `MinigameConcludeEvent`
Listen for `MinigameConcludeEvent` to release any remaining task runnables, scoreboards, or local memory caches:
```java
@EventHandler
public void onMinigameConclude(MinigameConcludeEvent event) {
    cleanUpSessionState(event.getWorldName());
}
```

---

### Step 6: Spatial Mapping Standard (Admin-Configurable Absolute Coordinates)
Companion plugins must accept absolute platform coordinates in `config.yml` (or `settings.yml`) and calculate boundary thresholds dynamically relative to $Y_{\text{spawn}}$ ($\text{Elimination } Y = Y_{\text{spawn}} - \text{fall-threshold-offset}$). Operators can import template maps built at any world coordinates without needing to modify schematics or origin points.

