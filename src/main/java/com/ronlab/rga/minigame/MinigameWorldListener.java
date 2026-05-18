package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import com.ronlab.rga.party.Party;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class MinigameWorldListener implements Listener {

    private final RGA plugin;

    public MinigameWorldListener(RGA plugin) {
        this.plugin = plugin;
    }

    // ── Portal routing ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();

        if (!isMinigameWorld(currentWorld)) return;

        String baseName = getBaseName(currentWorld);
        if (baseName == null) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (currentWorld.equals(baseName)) {
                // Overworld → Nether
                World nether = Bukkit.getWorld(baseName + "_the_nether");
                if (nether != null) {
                    event.setTo(new Location(nether,
                            player.getLocation().getX() / 8,
                            player.getLocation().getY(),
                            player.getLocation().getZ() / 8));
                }
            } else if (currentWorld.equals(baseName + "_the_nether")) {
                // Nether → Overworld
                World overworld = Bukkit.getWorld(baseName);
                if (overworld != null) {
                    event.setTo(new Location(overworld,
                            player.getLocation().getX() * 8,
                            player.getLocation().getY(),
                            player.getLocation().getZ() * 8));
                }
            }
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (currentWorld.equals(baseName)) {
                // Overworld → End
                World end = Bukkit.getWorld(baseName + "_the_end");
                if (end != null) {
                    event.setTo(end.getSpawnLocation());
                }
            } else if (currentWorld.equals(baseName + "_the_end")) {
                // End exit portal → Overworld
                World overworld = Bukkit.getWorld(baseName);
                if (overworld != null) {
                    event.setTo(overworld.getSpawnLocation());
                }
            }
        }
    }

    // ── Respawn routing ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();

        // Check if this is a minigame world that is still active
        if (isMinigameWorld(currentWorld)) {
            String baseName = getBaseName(currentWorld);
            if (baseName != null) {
                // If the player has a bed or respawn anchor set, respect it
                // Only override if they have no custom respawn point
                Location bedSpawn = player.getBedSpawnLocation();
                if (bedSpawn != null && isMinigameWorld(bedSpawn.getWorld().getName())) {
                    // Bed is in a minigame world — let vanilla handle it
                    return;
                }

                // No bed set or bed is outside the minigame — use world spawn
                World overworld = Bukkit.getWorld(baseName);
                if (overworld != null) {
                    event.setRespawnLocation(overworld.getSpawnLocation());
                    return;
                }
            }
        }

        // World is gone (conclude fired before respawn) or not a minigame world
        // Check if the world name looks like a minigame world that was just deleted
        if (currentWorld.startsWith("minigame_")) {
            // Redirect to Hub since the world no longer exists
            World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
            if (hub != null) {
                event.setRespawnLocation(hub.getSpawnLocation());
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean isMinigameWorld(String worldName) {
        if (!worldName.startsWith("minigame_")) return false;
        for (Party party : plugin.getPartyManager().getActiveParties().values()) {
            String active = party.getActiveWorldName();
            if (active == null) continue;
            if (worldName.equals(active)
                    || worldName.equals(active + "_the_nether")
                    || worldName.equals(active + "_the_end")) {
                return true;
            }
        }
        return false;
    }

    private String getBaseName(String worldName) {
        for (Party party : plugin.getPartyManager().getActiveParties().values()) {
            String active = party.getActiveWorldName();
            if (active == null) continue;
            if (worldName.equals(active)
                    || worldName.equals(active + "_the_nether")
                    || worldName.equals(active + "_the_end")) {
                return active;
            }
        }
        return null;
    }
}
