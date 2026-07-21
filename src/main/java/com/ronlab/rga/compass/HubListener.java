package com.ronlab.rga.compass;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        if (plugin.getSessionManager().isPendingRecovery(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getSessionManager().recoverPlayer(player);
            }, 5L);
            return;
        }

        String hubWorld = plugin.getConfigManager().getHubWorld();

        plugin.getInventoryManager().markIgnoreNextWorldChange(player.getUniqueId());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            World hub = Bukkit.getWorld(hubWorld);
            if (hub != null) {
                player.teleport(hub.getSpawnLocation());
            }
            plugin.getInventoryManager().clearPlayer(player);
            giveCompass(player);
            plugin.getSocialItem().giveSocialItem(player);
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
                plugin.getSocialItem().giveSocialItem(player);
            }, 1L);
        } else if (oldWorld.equalsIgnoreCase(hubWorld)) {
            boolean removeOnLeave = plugin.getConfig().getBoolean("compass.remove-on-leave-hub", true);
            if (removeOnLeave) {
                removeCompass(player);
                plugin.getSocialItem().removeSocialItem(player);
            }
        }
    }

    /**
     * Catch-all respawn handler — sends players to Hub unless they are
     * in an SMP world or an active minigame world (those are handled by
     * their own listeners at higher priority).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
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
                player.sendMessage(Component.text("The game has ended! You have been returned to Hub.", NamedTextColor.GOLD));

            }
            return;
        }

        // Active minigame world — handled by MinigameWorldListener at HIGH priority
        if (currentWorld.startsWith("minigame_")) return;

        // SMP worlds — let them respawn normally there
        if (smpWorlds.contains(currentWorld)) return;

        // Hub — set respawn to hub spawn point and also teleport on next tick
        if (currentWorld.equalsIgnoreCase(hubWorld)) {
            World hub = Bukkit.getWorld(hubWorld);
            if (hub != null) {
                event.setRespawnLocation(hub.getSpawnLocation());

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) player.teleport(hub.getSpawnLocation());
                }, 2L);
            }
            return;
        }

        // Everything else (Creative, Adventure, Parkour, unknown worlds)
        // redirect to Hub on death
        World hub = Bukkit.getWorld(hubWorld);
        if (hub != null) {
            event.setRespawnLocation(hub.getSpawnLocation());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.teleport(hub.getSpawnLocation());
            }, 2L);
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
        Material material = AdventureUtil.safeMaterial(materialName, Material.COMPASS);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = config.getString("compass.name", "&6World Navigator");
        meta.displayName(AdventureUtil.color(name));

        List<String> rawLore = config.getStringList("compass.lore");
        if (!rawLore.isEmpty()) {
            meta.lore(AdventureUtil.color(rawLore));
        }

        meta.getPersistentDataContainer().set(COMPASS_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public RGA getPlugin() { return plugin; }
}
