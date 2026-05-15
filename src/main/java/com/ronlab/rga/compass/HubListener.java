package com.ronlab.rga.compass;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class HubListener implements Listener {

    private final RGA plugin;
    public static NamespacedKey COMPASS_KEY;

    public HubListener(RGA plugin) {
        this.plugin = plugin;
        COMPASS_KEY = new NamespacedKey(plugin, "rga_navigator");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String hubWorld = plugin.getConfigManager().getHubWorld();

        plugin.getInventoryManager().markIgnoreNextWorldChange(player.getUniqueId());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            World hub = Bukkit.getWorld(hubWorld);
            if (hub != null) {
                player.teleport(hub.getSpawnLocation());
            }
            plugin.getInventoryManager().clearPlayer(player);
            giveCompass(player);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String hubWorld = plugin.getConfigManager().getHubWorld();
        String newWorld = player.getWorld().getName();
        String oldWorld = event.getFrom().getName();

        if (newWorld.equalsIgnoreCase(hubWorld)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                giveCompass(player);
            }, 1L);
        } else if (oldWorld.equalsIgnoreCase(hubWorld)) {
            boolean removeOnLeave = plugin.getConfig().getBoolean("compass.remove-on-leave-hub", true);
            if (removeOnLeave) {
                removeCompass(player);
            }
        }
    }

    /**
     * Catch-all respawn handler — sends players to Hub unless they are
     * in an SMP world or an active minigame world (those are handled by
     * their own listeners at higher priority).
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();
        String hubWorld = plugin.getConfigManager().getHubWorld();
        List<String> smpWorlds = plugin.getConfigManager().getSmpWorlds();

        // Player whose game was concluded while they were dead
        // Route them to Hub and restore their advancements
        if (plugin.getPartyManager().isConcluded(player.getUniqueId())) {
            World hub = Bukkit.getWorld(hubWorld);
            if (hub != null) {
                event.setRespawnLocation(hub.getSpawnLocation());
                player.sendMessage("§6The game has ended! You have been returned to Hub.");
                // Restore advancements after respawn on next tick
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getAdvancementManager().restore(player);
                }, 1L);
            }
            return;
        }

        // Active minigame world — handled by MinigameWorldListener at HIGH priority
        if (currentWorld.startsWith("minigame_")) return;

        // SMP worlds — let them respawn normally there
        if (smpWorlds.contains(currentWorld)) return;

        // Already in Hub — nothing to do
        if (currentWorld.equalsIgnoreCase(hubWorld)) return;

        // Any configured world (Creative, Adventure, Parkour etc.)
        // let them respawn normally in their own world
        if (plugin.getWorldManager().getSettings(currentWorld) != null) return;

        // Only redirect truly unknown worlds to Hub
        World hub = Bukkit.getWorld(hubWorld);
        if (hub != null) {
            event.setRespawnLocation(hub.getSpawnLocation());
        }
    }

    public void giveCompass(Player player) {
        if (hasCompass(player)) return;

        ItemStack compass = buildCompass();
        int slot = plugin.getConfig().getInt("compass.slot", 8);

        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) {
            player.getInventory().setItem(slot, compass);
        } else {
            boolean placed = false;
            for (int i = 0; i <= 8; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s == null || s.getType().isAir()) {
                    player.getInventory().setItem(i, compass);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                player.getInventory().addItem(compass);
            }
        }
    }

    public void removeCompass(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isNavigatorCompass(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public boolean hasCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isNavigatorCompass(item)) return true;
        }
        return false;
    }

    public boolean isNavigatorCompass(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(COMPASS_KEY, PersistentDataType.BYTE);
    }

    private ItemStack buildCompass() {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("compass.material", "COMPASS").toUpperCase();
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.COMPASS;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = config.getString("compass.name", "&6World Navigator");
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));

        List<String> rawLore = config.getStringList("compass.lore");
        if (!rawLore.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
        }

        meta.getPersistentDataContainer().set(COMPASS_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public RGA getPlugin() { return plugin; }
}
