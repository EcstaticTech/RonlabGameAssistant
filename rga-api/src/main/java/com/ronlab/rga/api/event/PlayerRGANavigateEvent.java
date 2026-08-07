package com.ronlab.rga.api.event;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired synchronously on the main thread immediately prior to RGA executing 
 * a navigation teleport (Compass, /hub, /smp, Portals).
 * 
 * Provides rga-announcer and companions advance notice of world transitions 
 * before the client chunk map updates.
 */
public class PlayerRGANavigateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final World originWorld;
    private final World targetWorld;
    private final String targetWorldName;
    private final NavigationType navigationType;

    public enum NavigationType {
        COMPASS_MENU,
        COMMAND_HUB,
        COMMAND_TP,
        PORTAL
    }

    public PlayerRGANavigateEvent(@NotNull Player player, @NotNull World originWorld,
                                  @Nullable World targetWorld, @Nullable String targetWorldName,
                                  @NotNull NavigationType navigationType) {
        this.player = player;
        this.originWorld = originWorld;
        this.targetWorld = targetWorld;
        this.targetWorldName = targetWorldName != null ? targetWorldName : (targetWorld != null ? targetWorld.getName() : "");
        this.navigationType = navigationType;
    }

    public PlayerRGANavigateEvent(@NotNull Player player, @NotNull World originWorld,
                                  @NotNull World targetWorld, @NotNull NavigationType navigationType) {
        this(player, originWorld, targetWorld, targetWorld.getName(), navigationType);
    }

    @NotNull
    public Player getPlayer() { return player; }

    @NotNull
    public World getOriginWorld() { return originWorld; }

    @Nullable
    public World getTargetWorld() { return targetWorld; }

    @NotNull
    public String getTargetWorldName() { return targetWorldName; }

    @NotNull
    public NavigationType getNavigationType() { return navigationType; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
