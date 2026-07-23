package com.ronlab.rga.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fired when a minigame session is concluding.
 * Companion plugins can inspect or modify the mutable scores map before cleanup,
 * or cancel the event to prevent conclusion.
 */
@NullMarked
public class MinigameConcludeEvent extends MinigameEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Map<UUID, Number> scores;

    public MinigameConcludeEvent(String minigameId, String minigameName, String worldName, List<UUID> playerUuids, @Nullable Map<UUID, ? extends Number> initialScores) {
        super(minigameId, minigameName, worldName, playerUuids);
        this.scores = initialScores != null ? new HashMap<>(initialScores) : new HashMap<>();
    }

    /**
     * Gets the mutable scores map associated with this minigame conclusion.
     * Event listeners may modify values in this map to adjust final placement or rewards.
     */
    public Map<UUID, Number> getScores() {
        return scores;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
