package com.ronlab.rga.listener;

import com.ronlab.rga.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class CorePlayerDeathListener implements Listener {

    private final SessionManager sessionManager;

    public CorePlayerDeathListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // STRUCTURAL GUARD: If the player is actively in a minigame session,
        // yield 100% control to the companion plugin listener.
        if (sessionManager != null && sessionManager.isPlayerInActiveSession(player.getUniqueId())) {
            return;
        }

        // Default Core Behavior (Hub deaths / non-session worlds only)
        handleHubDeathSequence(player, event);
    }

    public void handleHubDeathSequence(Player player, PlayerDeathEvent event) {
        // Default core hub death behavior (non-session world)
    }
}
