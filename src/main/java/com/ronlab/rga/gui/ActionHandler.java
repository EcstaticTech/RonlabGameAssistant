package com.ronlab.rga.gui;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.ronlab.rga.util.PlaceholderSanitizer;
import java.util.List;

public class ActionHandler {

    private final RGA plugin;

    public ActionHandler(RGA plugin) {
        this.plugin = plugin;
    }

    public void handle(Player player, List<String> actions) {
        if (actions == null || actions.isEmpty()) return;

        for (String action : actions) {
            action = action.trim();

            if (action.startsWith("rga:")) {
                handleRGAAction(player, action.substring(4).trim());
            } else if (action.startsWith("console:")) {
                String command = action.substring(8).trim().replace("%player%", PlaceholderSanitizer.sanitize(player.getName()));
                if (!PlaceholderSanitizer.isSafeToExecute(command)) {
                    plugin.getLogger().warning("Blocked unsafe console command (action handler): " + command);
                    continue;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else if (action.startsWith("player:")) {
                String command = action.substring(7).trim().replace("%player%", PlaceholderSanitizer.sanitize(player.getName()));
                if (!PlaceholderSanitizer.isSafeToExecute(command)) {
                    plugin.getLogger().warning("Blocked unsafe player command (action handler): " + command);
                    continue;
                }
                player.performCommand(command);
            } else if (!action.isEmpty()) {
                String command = action.replace("%player%", PlaceholderSanitizer.sanitize(player.getName()));
                if (!PlaceholderSanitizer.isSafeToExecute(command)) {
                    plugin.getLogger().warning("Blocked unsafe fallback command (action handler): " + command);
                    continue;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }

    private void handleRGAAction(Player player, String action) {
        if (action.startsWith("open_menu ")) {
            String menuName = action.substring(10).trim();
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getMenuManager().openMenu(player, menuName), 1L);

        } else if (action.equals("last_location")) {
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getLocationTracker().teleportToLastLocation(player), 1L);

        } else if (action.startsWith("tp ")) {
            String worldName = action.substring(3).trim();
            String currentWorld = player.getWorld().getName();
            if (plugin.getConfigManager().getSmpWorlds().contains(currentWorld)) {
                plugin.getLocationTracker().saveLocation(player, player.getLocation());
            }
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getWorldManager().teleportToWorld(player, worldName), 1L);

        } else if (action.startsWith("join_minigame ")) {
            String minigameId = action.substring(14).trim();
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getPartyManager().joinMinigame(player, minigameId), 1L);

        } else if (action.equals("close")) {
            player.closeInventory();

        } else {
            plugin.getLogger().warning("Unknown RGA action: rga:" + action);
            player.sendMessage(Component.text("Unknown action: " + action, NamedTextColor.RED));
        }
    }
}
