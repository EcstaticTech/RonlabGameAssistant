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
 * Read and veto-only event fired when a programmatic session conclusion request is initiated via RGA API.
 * Companion plugins may inspect the reason and scores, or cancel the event to prevent conclusion.
 * <p>
 * Firing this event manually does NOT trigger session teardown logic. Companion plugins must invoke
 * RGA.requestSessionConclude(...) instead.
 */
@NullMarked
public class RGAGameRequestConcludeEvent extends MinigameEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final String reason;
    private final Map<UUID, Number> scores;

    public RGAGameRequestConcludeEvent(String minigameId,
                                       String minigameName,
                                       String worldName,
                                       List<UUID> playerUuids,
                                       @Nullable String reason,
                                       @Nullable Map<UUID, ? extends Number> scores) {
        super(minigameId, minigameName, worldName, playerUuids);
        this.reason = (reason != null && !reason.trim().isEmpty()) ? reason : "Companion-initiated";
        this.scores = scores != null ? new HashMap<>(scores) : new HashMap<>();
    }

    public String getReason() {
        return reason;
    }

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
