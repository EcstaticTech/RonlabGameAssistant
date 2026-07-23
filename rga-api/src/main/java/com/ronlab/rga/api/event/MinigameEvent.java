package com.ronlab.rga.api.event;

import org.bukkit.event.Event;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base abstract event for all Ronlab Game Assistant minigame lifecycle transitions.
 */
public abstract class MinigameEvent extends Event {

    private final String minigameId;
    private final String minigameName;
    private final String worldName;
    private final List<UUID> playerUuids;

    public MinigameEvent(String minigameId, String minigameName, String worldName, List<UUID> playerUuids) {
        this.minigameId = minigameId;
        this.minigameName = minigameName;
        this.worldName = worldName;
        this.playerUuids = playerUuids != null ? List.copyOf(playerUuids) : Collections.emptyList();
    }

    public String getMinigameId() {
        return minigameId;
    }

    public String getMinigameName() {
        return minigameName;
    }

    public String getWorldName() {
        return worldName;
    }

    public List<UUID> getPlayerUuids() {
        return playerUuids;
    }
}
