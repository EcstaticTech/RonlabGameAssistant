package com.ronlab.rga.api;

import org.bukkit.entity.Player;

/**
 * API contract for programmatic spectator management within an active RGA minigame session.
 * <p>
 * Implementations of this interface are exposed by the RGA plugin main class and allow
 * companion plugins to place or remove players from spectator mode in a JIT (just-in-time)
 * fashion without needing to manage inventory state themselves.
 * <p>
 * <b>Thread safety:</b> All methods defined here must be called from the primary server thread.
 */
public interface RGASessionControl {

    /**
     * Places or removes a player from spectator mode within their active minigame session.
     * <p>
     * When {@code isSpectator} is {@code true}:
     * <ul>
     *   <li>The player's current inventory, armor, offhand, and vital stats (exp, health, food)
     *       are captured into an in-memory {@code SpectatorSnapshot}.</li>
     *   <li>The player's inventory is cleared.</li>
     *   <li>The player's game mode is set to {@link org.bukkit.GameMode#SPECTATOR}, making them
     *       automatically hidden from active participants via Paper's built-in visibility rules.</li>
     *   <li>The player's advancements are revoked to prevent spectator-triggered progress.</li>
     * </ul>
     * <p>
     * When {@code isSpectator} is {@code false}:
     * <ul>
     *   <li>The previously captured {@code SpectatorSnapshot} is restored to the player.</li>
     *   <li>The player's advancements are restored from the pre-spectate state.</li>
     *   <li>The player's game mode is set to {@link org.bukkit.GameMode#SURVIVAL}.</li>
     *   <li>The player is teleported to the hub world.</li>
     *   <li>All spectator tracking data for this player is cleared.</li>
     * </ul>
     * <p>
     * This method is a no-op if:
     * <ul>
     *   <li>The player is not associated with any active session (for {@code isSpectator=true}).</li>
     *   <li>The player is not currently registered as a spectator (for {@code isSpectator=false}).</li>
     *   <li>The minigame for this session has {@code allow-spectators: false}.</li>
     * </ul>
     *
     * @param player      the player to promote to or demote from spectator
     * @param isSpectator {@code true} to enter spectator mode, {@code false} to leave
     */
    void setSpectator(Player player, boolean isSpectator);

    /**
     * Returns whether the given player is currently registered as a spectator in any
     * active RGA minigame session.
     *
     * @param player the player to check
     * @return {@code true} if the player is an active spectator, {@code false} otherwise
     */
    boolean isSpectator(Player player);
}
