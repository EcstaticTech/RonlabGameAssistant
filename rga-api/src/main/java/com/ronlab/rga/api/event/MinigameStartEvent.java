package com.ronlab.rga.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.UUID;

/**
 * Fired when a minigame session is about to start after world generation succeeds.
 * Companion plugins can cancel this event to cleanly abort game initialization.
 */
@NullMarked
public class MinigameStartEvent extends MinigameEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    public MinigameStartEvent(String minigameId, String minigameName, String worldName, List<UUID> playerUuids) {
        super(minigameId, minigameName, worldName, playerUuids);
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
