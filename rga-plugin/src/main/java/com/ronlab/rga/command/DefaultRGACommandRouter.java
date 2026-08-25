package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.command.RGACommandRouter;
import com.ronlab.rga.api.event.PlayerRGANavigateEvent;
import com.ronlab.rga.util.PlaceholderSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Core implementation of RGACommandRouter.
 * Sanitizes action dispatches, enforces party-state validations, and checks permissions.
 */
public class DefaultRGACommandRouter implements RGACommandRouter {

    public static final String PERM_ADMIN = "rga.admin";
    public static final String PERM_JOIN = "rga.user.join";
    public static final String PERM_TP = "rga.tp";

    private final RGA plugin;

    public DefaultRGACommandRouter(RGA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean dispatchAction(Player player, String action) {
        if (player == null || action == null || action.isBlank()) return false;

        action = action.trim();

        if (action.startsWith("rga:")) {
            return handleRGAAction(player, action.substring(4).trim());
        } else if (action.startsWith("console:")) {
            String command = action.substring(8).trim().replace("%player%", PlaceholderSanitizer.sanitize(player.getName()));
            if (!PlaceholderSanitizer.isSafeToExecute(command)) {
                plugin.getLogger().warning("Blocked unsafe console command: " + command);
                return false;
            }
            if (!plugin.getConfigManager().isConsoleCommandAllowed(command)) {
                plugin.getLogger().warning("Blocked disallowed console command: " + command);
                return false;
            }
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else if (action.startsWith("player:")) {
            String command = action.substring(7).trim().replace("%player%", PlaceholderSanitizer.sanitize(player.getName()));
            if (!PlaceholderSanitizer.isSafeToExecute(command)) {
                plugin.getLogger().warning("Blocked unsafe player command: " + command);
                return false;
            }
            return player.performCommand(command);
        } else {
            String command = action.replace("%player%", PlaceholderSanitizer.sanitize(player.getName()));
            if (!PlaceholderSanitizer.isSafeToExecute(command)) {
                plugin.getLogger().warning("Blocked unsafe fallback command: " + command);
                return false;
            }
            if (!plugin.getConfigManager().isConsoleCommandAllowed(command)) {
                plugin.getLogger().warning("Blocked disallowed console command: " + command);
                return false;
            }
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    @Override
    public boolean dispatchActions(Player player, List<String> actions) {
        if (actions == null || actions.isEmpty()) return true;
        boolean allSuccess = true;
        for (String action : actions) {
            if (!dispatchAction(player, action)) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean handleRGAAction(Player player, String action) {
        if (action.startsWith("open_menu ")) {
            String menuName = action.substring(10).trim();
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getMenuManager().openMenu(player, menuName), 1L);
            return true;

        } else if (action.startsWith("open_category ")) {
            String category = action.substring(14).trim();
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getPaginatedMapMenu().openMenu(player, category, 0), 1L);
            return true;

        } else if (action.startsWith("menu_page ")) {
            // Format: category pageIndex
            String args = action.substring(10).trim();
            String[] parts = args.split("\\s+");
            if (parts.length >= 2) {
                String category = parts[0];
                try {
                    int page = Integer.parseInt(parts[1]);
                    plugin.getPaginatedMapMenu().openMenu(player, category, page);
                    return true;
                } catch (NumberFormatException ignored) {}
            }
            return false;

        } else if (action.equals("last_location")) {
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getLocationTracker().teleportToLastLocation(player), 1L);
            return true;

        } else if (action.startsWith("tp ")) {
            String worldName = action.substring(3).trim();
            return executeTeleport(player, worldName);

        } else if (action.startsWith("join_minigame ")) {
            String minigameId = action.substring(14).trim();
            return executeJoinMinigame(player, minigameId);

        } else if (action.equals("close")) {
            player.closeInventory();
            return true;

        } else {
            plugin.getLogger().warning("Unknown RGA action: rga:" + action);
            player.sendMessage(Component.text("Unknown action: " + action, NamedTextColor.RED));
            return false;
        }
    }

    @Override
    public boolean canExecuteJoin(Player player, String minigameId) {
        if (player == null || minigameId == null || minigameId.isBlank()) return false;

        // Check permission rga.user.join (bypassed by rga.admin)
        if (!player.hasPermission(PERM_ADMIN) && !player.hasPermission(PERM_JOIN)) {
            player.sendMessage(Component.text("You do not have permission to join minigames.", NamedTextColor.RED));
            return false;
        }

        // Validate party state
        if (plugin.getPartyManager() != null) {
            boolean isSpectator = plugin.getPartyManager().isPlayerSpectating(player.getUniqueId());
            if (isSpectator) {
                player.sendMessage(Component.text("You are currently spectating. Leave spectator mode first.", NamedTextColor.RED));
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean canExecuteTeleport(Player player, String targetWorld) {
        if (player == null || targetWorld == null || targetWorld.isBlank()) return false;

        // Check permission rga.tp (bypassed by rga.admin)
        if (!player.hasPermission(PERM_ADMIN) && !player.hasPermission(PERM_TP)) {
            player.sendMessage(Component.text("You do not have permission to teleport.", NamedTextColor.RED));
            return false;
        }

        return true;
    }

    @Override
    public boolean executeJoinMinigame(Player player, String minigameId) {
        if (!canExecuteJoin(player, minigameId)) return false;

        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getPartyManager().joinMinigame(player, minigameId), 1L);
        return true;
    }

    @Override
    public boolean executeTeleport(Player player, String targetWorld) {
        if (!canExecuteTeleport(player, targetWorld)) return false;

        String currentWorld = player.getWorld().getName();
        if (plugin.getConfigManager().getSmpWorlds().contains(currentWorld)) {
            plugin.getLocationTracker().saveLocation(player, player.getLocation());
        }

        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getWorldManager().teleportToWorld(player, targetWorld, PlayerRGANavigateEvent.NavigationType.COMPASS_MENU), 1L);
        return true;
    }
}
