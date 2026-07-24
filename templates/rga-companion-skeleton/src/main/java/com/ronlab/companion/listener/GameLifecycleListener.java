package com.ronlab.companion.listener;

import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.MinigameStartEvent;
import com.ronlab.rga.api.model.MinigameId;
import com.ronlab.companion.CompanionSkeletonPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class GameLifecycleListener implements Listener {

    private final CompanionSkeletonPlugin plugin;
    private final Set<String> activeWorldSessions = new HashSet<>();

    public GameLifecycleListener(CompanionSkeletonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMinigameStart(MinigameStartEvent event) {
        MinigameId minigameId = MinigameId.parse(event.getMinigameId());
        String worldName = event.getWorldName();
        List<UUID> players = event.getPlayerUuids();
        int initialPlayerCount = players.size();

        plugin.getLogger().info(String.format("MinigameStartEvent received for '%s' in world '%s' with %d player(s).",
                minigameId, worldName, initialPlayerCount));

        activeWorldSessions.add(worldName);

        // STEP 2 GUARD: Prevent single-player local testing from triggering immediate 0-opponent win logic
        if (initialPlayerCount == 1) {
            plugin.getLogger().info("[CPM] Single-player testing mode detected; suppressing automatic win condition.");
            return;
        }

        // Example: Initialize custom minigame loop/timer for target world session
        startMinigameLoop(worldName, players);
    }

    @EventHandler
    public void onMinigameConclude(MinigameConcludeEvent event) {
        String worldName = event.getWorldName();
        if (activeWorldSessions.remove(worldName)) {
            plugin.getLogger().info("MinigameConcludeEvent handled cleanly for world: " + worldName);
            // Clean up any local runnables or memory maps associated with this world session
        }
    }

    private void startMinigameLoop(String worldName, List<UUID> players) {
        // Implement game loop, scoreboards, and win condition checks here
    }

    public void triggerConclusion(String worldName, String reason, Map<UUID, Integer> scores) {
        Plugin rgaPlugin = Bukkit.getPluginManager().getPlugin("RonlabGameAssistant");
        if (rgaPlugin != null) {
            try {
                // Programmatic session conclusion invocation via RGA helper
                rgaPlugin.getClass()
                        .getMethod("requestSessionConclude", String.class, String.class, Map.class)
                        .invoke(rgaPlugin, worldName, reason, scores);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to invoke requestSessionConclude on RGA: " + e.getMessage());
            }
        }
    }
}
