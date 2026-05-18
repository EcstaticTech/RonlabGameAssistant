package com.ronlab.rga.world;

import com.ronlab.rga.RGA;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalBlockListener implements Listener {

    private final RGA plugin;

    public PortalBlockListener(RGA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPortal(PlayerPortalEvent event) {
        String currentWorld = event.getPlayer().getWorld().getName();

        // Skip minigame worlds — handled by MinigameWorldListener
        if (currentWorld.startsWith("minigame_")) return;

        WorldSettings settings = plugin.getWorldManager().getSettings(currentWorld);
        if (settings == null) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && settings.isDisableNether()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThe Nether is disabled in this world.");
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && settings.isDisableEnd()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThe End is disabled in this world.");
        }
    }
}
