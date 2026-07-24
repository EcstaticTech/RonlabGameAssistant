package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.util.AdventureUtil;
import com.ronlab.rga.util.StatusReportFormatter;
import com.ronlab.rga.util.WorldNameValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class RGACommand implements CommandExecutor, TabCompleter {

    private final RGA plugin;

    private static final Map<String, String> SUBCOMMAND_PERMISSIONS = Map.ofEntries(
        Map.entry("help",             "rga.world.teleport"),  // anyone with any access can see help
        Map.entry("reload",           "rga.reload"),
        Map.entry("listworlds",       "rga.world.teleport"),
        Map.entry("tp",               "rga.world.teleport"),
        Map.entry("compass",          "rga.world.teleport"),
        Map.entry("createworld",      "rga.world.manage"),
        Map.entry("importworld",      "rga.world.manage"),
        Map.entry("loadworld",        "rga.world.manage"),
        Map.entry("unloadworld",      "rga.world.manage"),
        Map.entry("deleteworld",      "rga.world.manage"),
        Map.entry("setspawn",         "rga.world.configure"),
        Map.entry("setworldgamemode", "rga.world.configure"),
        Map.entry("setworldpvp",      "rga.world.configure"),
        Map.entry("setworlddifficulty","rga.world.configure"),
        Map.entry("setworldtime",     "rga.world.configure"),
        Map.entry("setworldweather",  "rga.world.configure"),
        Map.entry("setworldalias",    "rga.world.configure"),
        Map.entry("setworldtemplate", "rga.world.configure"),
        Map.entry("gamerule",         "rga.world.configure"),
        Map.entry("conclude",         "rga.session.conclude"),
        Map.entry("concludeall",      "rga.session.conclude"),
        Map.entry("cleanupsession",   "rga.session.cleanup"),
        Map.entry("spectate",         "rga.world.teleport"),
        Map.entry("queue",            "rga.session.status"),
        Map.entry("sessions",         "rga.session.status"),
        Map.entry("status",           "rga.session.status")
    );

    public RGACommand(RGA plugin) {
        this.plugin = plugin;
    }

    /** Returns true if the sender holds at least one RGA permission node. */
    private boolean hasAnyRGAPermission(CommandSender sender) {
        return sender.hasPermission("rga.admin")
                || sender.hasPermission("rga.reload")
                || sender.hasPermission("rga.world.teleport")
                || sender.hasPermission("rga.world.manage")
                || sender.hasPermission("rga.world.configure")
                || sender.hasPermission("rga.session.conclude")
                || sender.hasPermission("rga.session.status")
                || sender.hasPermission("rga.session.cleanup");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAnyRGAPermission(sender)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();
        String worldToCheck = null;
        if (sub.equals("tp")) {
            if (args.length >= 3) worldToCheck = args[2];
        } else if (sub.equals("setspawn")) {
            if (args.length >= 2) worldToCheck = args[1];
        } else if (java.util.Set.of("createworld", "importworld", "loadworld", "unloadworld", "deleteworld",
                "setworldgamemode", "setworldpvp", "setworlddifficulty", "setworldtime",
                "setworldweather", "setworldalias", "setworldtemplate", "gamerule", "conclude", "cleanupsession").contains(sub)) {
            if (args.length >= 2) worldToCheck = args[1];
        }

        if (worldToCheck != null && !com.ronlab.rga.util.WorldNameValidator.isValid(worldToCheck)) {
            sender.sendMessage(Component.text("Invalid world name. World names may only contain letters, numbers, underscores, and hyphens (max 64 characters).", NamedTextColor.RED));
            return true;
        }

        // Per-subcommand permission check
        String requiredPerm = SUBCOMMAND_PERMISSIONS.get(sub);
        if (requiredPerm != null && !sender.hasPermission(requiredPerm)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        switch (sub) {

            case "help" -> sendHelp(sender);

            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("reloaded"));
            }

            case "listworlds" -> {
                sender.sendMessage(Component.text("======= Loaded Worlds =======", NamedTextColor.GOLD, TextDecoration.BOLD));
                for (World world : Bukkit.getWorlds()) {
                    var settings = plugin.getWorldManager().getSettings(world.getName());
                    String alias = settings != null ? settings.getAlias() : world.getName();
                    String gamemode = settings != null ? settings.getGamemode().name() : "UNKNOWN";
                    String pvpPart = settings != null ? (settings.isPvp() ? "PVP" : "NoPVP") : "?";
                    String template = (settings != null && settings.isTemplate()) ? " [TEMPLATE]" : "";
                    String timeLock = (settings != null && settings.getTimeLock() >= 0)
                            ? " [TIME:" + settings.getTimeLock() + "]" : "";
                    String weatherLock = (settings != null && settings.isWeatherLock())
                            ? " [WEATHER LOCKED]" : "";
                    int players = world.getPlayers().size();
                    sender.sendMessage(Component.text()
                            .append(Component.text(alias, NamedTextColor.YELLOW))
                            .append(Component.text(" (" + world.getName() + ") | ", NamedTextColor.GRAY))
                            .append(Component.text(gamemode + " | " + pvpPart))
                            .append(Component.text(" | " + players + " player(s)", NamedTextColor.WHITE))
                            .append(Component.text(template, NamedTextColor.LIGHT_PURPLE))
                            .append(Component.text(timeLock, NamedTextColor.YELLOW))
                            .append(Component.text(weatherLock, NamedTextColor.AQUA))
                            .build());
                }
                sender.sendMessage(Component.text("=============================", NamedTextColor.GOLD, TextDecoration.BOLD));
            }

            case "tp" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /rga tp <player> <world>", NamedTextColor.RED)); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
                if (!WorldNameValidator.isValid(args[2])) {
                    sender.sendMessage(Component.text("Invalid world name.", NamedTextColor.RED));
                    return true;
                }
                plugin.getWorldManager().teleportToWorld(target, args[2]);
            }

            case "compass" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /rga compass <player>", NamedTextColor.RED)); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
                plugin.getHubListener().giveCompass(target);
                sender.sendMessage(Component.text("Gave navigator compass to " + target.getName() + ".", NamedTextColor.GREEN));
            }

            case "createworld" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /rga createworld <name> <environment> <gamemode> <pvp>", NamedTextColor.RED));
                    return true;
                }
                if (!WorldNameValidator.isValid(args[1])) {
                    sender.sendMessage(Component.text("Invalid world name. Must be alphanumeric plus underscores, periods, and hyphens (max 64 chars).", NamedTextColor.RED));
                    return true;
                }
                World.Environment env; GameMode gm;
                try { env = World.Environment.valueOf(args[2].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage(Component.text("Invalid environment.", NamedTextColor.RED)); return true; }
                try { gm = GameMode.valueOf(args[3].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage(Component.text("Invalid gamemode.", NamedTextColor.RED)); return true; }
                boolean pvp = Boolean.parseBoolean(args[4]);
                if (Bukkit.getWorld(args[1]) != null) { sender.sendMessage(Component.text("World already loaded.", NamedTextColor.RED)); return true; }
                sender.sendMessage(Component.text("Creating world '" + args[1] + "'...", NamedTextColor.YELLOW));
                boolean ok = plugin.getWorldManager().createWorld(args[1], env, gm, pvp);
                sender.sendMessage(ok ? Component.text("World created!", NamedTextColor.GREEN) : Component.text("Failed to create world.", NamedTextColor.RED));
            }

            case "importworld" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /rga importworld <foldername>", NamedTextColor.RED)); return true; }
                if (!WorldNameValidator.isValid(args[1])) {
                    sender.sendMessage(Component.text("Invalid world name.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Importing world '" + args[1] + "'...", NamedTextColor.YELLOW));
                boolean ok = plugin.getWorldManager().importWorld(args[1], sender);
                if (ok) {
                    sender.sendMessage(Component.text("World '" + args[1] + "' imported successfully!", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text()
                            .append(Component.text("Use ", NamedTextColor.GRAY))
                            .append(Component.text("/rga setworldgamemode", NamedTextColor.YELLOW))
                            .append(Component.text(", ", NamedTextColor.GRAY))
                            .append(Component.text("/rga setworldpvp", NamedTextColor.YELLOW))
                            .append(Component.text(" etc. to configure it.", NamedTextColor.GRAY))
                            .build());
                }
            }

            case "loadworld" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /rga loadworld <name>", NamedTextColor.RED)); return true; }
                if (!WorldNameValidator.isValid(args[1])) {
                    sender.sendMessage(Component.text("Invalid world name.", NamedTextColor.RED));
                    return true;
                }
                if (Bukkit.getWorld(args[1]) != null) { sender.sendMessage(Component.text("World already loaded.", NamedTextColor.RED)); return true; }
                sender.sendMessage(Component.text("Loading world '" + args[1] + "'...", NamedTextColor.YELLOW));
                boolean ok = plugin.getWorldManager().loadExistingWorld(args[1]);
                sender.sendMessage(ok ? Component.text("World loaded!", NamedTextColor.GREEN) : Component.text("Failed. Does the folder exist?", NamedTextColor.RED));
            }

            case "unloadworld" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /rga unloadworld <name>", NamedTextColor.RED)); return true; }
                if (!WorldNameValidator.isValid(args[1])) {
                    sender.sendMessage(Component.text("Invalid world name.", NamedTextColor.RED));
                    return true;
                }
                String hub = plugin.getConfigManager().getHubWorld();
                if (args[1].equalsIgnoreCase(hub) || args[1].equalsIgnoreCase("world")) {
                    sender.sendMessage(Component.text("You cannot unload the Hub or default world.", NamedTextColor.RED)); return true;
                }
                boolean ok = plugin.getWorldManager().unloadWorld(args[1], sender);
                sender.sendMessage(ok ? Component.text("World unloaded.", NamedTextColor.GREEN) : Component.text("Failed to unload world.", NamedTextColor.RED));
            }

            case "deleteworld" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /rga deleteworld <name>", NamedTextColor.RED)); return true; }
                if (!WorldNameValidator.isValid(args[1])) {
                    sender.sendMessage(Component.text("Invalid world name.", NamedTextColor.RED));
                    return true;
                }
                String hub = plugin.getConfigManager().getHubWorld();
                if (args[1].equalsIgnoreCase(hub) || args[1].equalsIgnoreCase("world")) {
                    sender.sendMessage(Component.text("You cannot delete the Hub or default world.", NamedTextColor.RED)); return true;
                }
                sender.sendMessage(Component.text("Deleting world '" + args[1] + "'...", NamedTextColor.YELLOW));
                boolean ok = plugin.getWorldManager().deleteWorld(args[1], sender);
                sender.sendMessage(ok ? Component.text("World deleted.", NamedTextColor.GREEN) : Component.text("Failed to delete world.", NamedTextColor.RED));
            }

            case "setspawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Players only.", NamedTextColor.RED)); return true; }
                String worldName = args.length >= 2 ? args[1] : player.getWorld().getName();
                World world = Bukkit.getWorld(worldName);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                world.setSpawnLocation(player.getLocation());
                sender.sendMessage(Component.text("Spawn for '" + worldName + "' set to your location.", NamedTextColor.GREEN));
            }

            case "setworldgamemode" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /rga setworldgamemode <world> <gamemode>", NamedTextColor.RED)); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                GameMode gm;
                try { gm = GameMode.valueOf(args[2].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage(Component.text("Invalid gamemode.", NamedTextColor.RED)); return true; }
                plugin.getWorldManager().setWorldGamemode(args[1], gm);
                for (Player p : world.getPlayers()) p.setGameMode(gm);
                sender.sendMessage(Component.text("Gamemode for '" + args[1] + "' set to " + gm.name() + ".", NamedTextColor.GREEN));
            }

            case "setworldpvp" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /rga setworldpvp <world> <true/false>", NamedTextColor.RED)); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                boolean pvp = Boolean.parseBoolean(args[2]);
                world.setPVP(pvp);
                plugin.getWorldManager().setWorldPvp(args[1], pvp);
                sender.sendMessage(Component.text("PVP for '" + args[1] + "' set to " + pvp + ".", NamedTextColor.GREEN));
            }

            case "setworlddifficulty" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /rga setworlddifficulty <world> <difficulty>", NamedTextColor.RED)); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                Difficulty diff;
                try { diff = Difficulty.valueOf(args[2].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage(Component.text("Invalid difficulty.", NamedTextColor.RED)); return true; }
                plugin.getWorldManager().setWorldDifficulty(args[1], diff);
                sender.sendMessage(Component.text("Difficulty for '" + args[1] + "' set to " + diff.name() + ".", NamedTextColor.GREEN));
            }

            case "setworldtime" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /rga setworldtime <world> <day|noon|night|midnight|<ticks>|-1>", NamedTextColor.RED));
                    sender.sendMessage(Component.text("Use -1 to unlock time.", NamedTextColor.GRAY));
                    return true;
                }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                long ticks = switch (args[2].toLowerCase()) {
                    case "day" -> 1000L;
                    case "noon" -> 6000L;
                    case "night" -> 13000L;
                    case "midnight" -> 18000L;
                    default -> {
                        try { yield Long.parseLong(args[2]); }
                        catch (NumberFormatException e) { yield Long.MIN_VALUE; }
                    }
                };
                if (ticks == Long.MIN_VALUE) { sender.sendMessage(Component.text("Invalid time value.", NamedTextColor.RED)); return true; }
                plugin.getWorldManager().setWorldTimeLock(args[1], ticks);
                sender.sendMessage(ticks >= 0
                        ? Component.text("Time for '" + args[1] + "' locked to " + ticks + " ticks.", NamedTextColor.GREEN)
                        : Component.text("Time lock removed for '" + args[1] + "'.", NamedTextColor.GREEN));
            }

            case "setworldweather" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /rga setworldweather <world> <true/false>", NamedTextColor.RED)); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                boolean locked = Boolean.parseBoolean(args[2]);
                plugin.getWorldManager().setWorldWeatherLock(args[1], locked);
                sender.sendMessage(Component.text("Weather lock for '" + args[1] + "' set to " + locked + ".", NamedTextColor.GREEN));
            }

            case "setworldalias" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /rga setworldalias <world> <alias>", NamedTextColor.RED)); return true; }
                plugin.getWorldManager().setWorldAlias(args[1], args[2]);
                sender.sendMessage(Component.text("Alias for '" + args[1] + "' set to '" + args[2] + "'.", NamedTextColor.GREEN));
            }

            case "setworldtemplate" -> {
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /rga setworldtemplate <world> <true/false>", NamedTextColor.RED)); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                boolean template = Boolean.parseBoolean(args[2]);
                plugin.getWorldManager().setWorldTemplate(args[1], template);
                sender.sendMessage(Component.text("Template status for '" + args[1] + "' set to " + template + ".", NamedTextColor.GREEN));
            }

            case "gamerule" -> {
                if (args.length < 4) { sender.sendMessage(Component.text("Usage: /rga gamerule <world> <rule> <value>", NamedTextColor.RED)); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage(Component.text("World not found.", NamedTextColor.RED)); return true; }
                GameRule<?> rule = GameRule.getByName(args[2]);
                if (rule == null) { sender.sendMessage(Component.text("Unknown gamerule: " + args[2], NamedTextColor.RED)); return true; }
                boolean applied = applyGameRule(world, rule, args[3]);
                sender.sendMessage(applied
                        ? Component.text("Gamerule " + args[2] + " set to " + args[3] + " in " + args[1] + ".", NamedTextColor.GREEN)
                        : Component.text("Invalid value '" + args[3] + "' for gamerule " + args[2] + ".", NamedTextColor.RED));
            }

            case "conclude" -> {
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /rga conclude <worldname>", NamedTextColor.RED)); return true; }
                String worldName = args[1];
                String baseName = worldName;
                if (baseName.endsWith("_the_nether")) {
                    baseName = baseName.substring(0, baseName.length() - "_the_nether".length());
                } else if (baseName.endsWith("_the_end")) {
                    baseName = baseName.substring(0, baseName.length() - "_the_end".length());
                }
                boolean active = false;
                for (com.ronlab.rga.party.Party p : plugin.getPartyManager().getActiveParties().values()) {
                    if (baseName.equals(p.getActiveWorldName())) {
                        active = true;
                        break;
                    }
                }
                if (!active) {
                    sender.sendMessage(Component.text("No active session for world '" + worldName + "'.", NamedTextColor.RED));
                    return true;
                }
                plugin.getPartyManager().concludeGame(worldName);
                sender.sendMessage(Component.text("Game concluded for world: " + worldName, NamedTextColor.GREEN));
            }

            case "concludeall" -> {
                var parties = plugin.getPartyManager().getActiveParties();
                if (parties.isEmpty()) {
                    sender.sendMessage(Component.text("No active games to conclude.", NamedTextColor.YELLOW));
                    return true;
                }
                int count = parties.size();
                // Copy values to avoid concurrent modification
                new java.util.ArrayList<>(parties.values()).forEach(party -> {
                    if (party.getActiveWorldName() != null) {
                        plugin.getPartyManager().concludeGame(party.getActiveWorldName());
                    }
                });
                sender.sendMessage(Component.text("Concluded " + count + " active game(s).", NamedTextColor.GREEN));
            }

            case "cleanupsession" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /rga cleanupsession <worldname>", NamedTextColor.RED));
                    return true;
                }
                String targetWorld = args[1];

                // Warn if players still have pending recovery for this session
                int pendingCount = (int) plugin.getSessionManager().getOrphanedSessionWorlds().stream()
                        .filter(w -> w.equalsIgnoreCase(targetWorld)).count();
                if (pendingCount > 0) {
                    sender.sendMessage(Component.text("Warning: There may be players with pending recovery for this session.", NamedTextColor.YELLOW));
                }

                // Clean up world folders first (VANILLA=true is safe — unloadAndDelete skips non-existent folders)
                plugin.getPartyManager().getWorldCopyManager().cleanupWorld(targetWorld, true);
                // Then remove the session metadata
                plugin.getSessionManager().deleteSession(targetWorld);
                sender.sendMessage(Component.text("Session file and world data cleaned up for '" + targetWorld + "'.", NamedTextColor.GREEN));
            }

            case "status" -> sendStatus(sender);

            case "queue" -> sendQueueStatus(sender);

            case "spectate" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }

                // /rga spectate leave — leave spectator mode
                if (args.length >= 2 && args[1].equalsIgnoreCase("leave")) {
                    plugin.getPartyManager().leaveSpectatorMode(player);
                    return true;
                }

                // /rga spectate <minigame> or /rga spectate
                if (args.length < 2) {
                    // Show list of spectatable games
                    sender.sendMessage(Component.text("======= Spectatable Games =======", NamedTextColor.GOLD, TextDecoration.BOLD));
                    var activeParties = plugin.getPartyManager().getActiveParties();
                    boolean found = false;
                    for (var entry : activeParties.entrySet()) {
                        var party = entry.getValue();
                        if (party.getState() == Party.State.IN_GAME) {
                            found = true;
                            Minigame mg = plugin.getMinigameManager().getMinigame(entry.getKey());
                            String displayName = mg != null ? mg.getName() : entry.getKey();
                            sender.sendMessage(Component.text()
                                    .append(Component.text(" - ", NamedTextColor.GRAY))
                                    .append(Component.text(displayName, NamedTextColor.GOLD))
                                    .append(Component.text(" (" + entry.getKey() + ")", NamedTextColor.DARK_GRAY))
                                    .append(Component.text(" — " + party.getMemberCount() + " player(s)", NamedTextColor.GRAY))
                                    .build());
                        }
                    }
                    if (!found) {
                        sender.sendMessage(Component.text("No games currently in progress.", NamedTextColor.YELLOW));
                    }
                    sender.sendMessage(Component.text()
                            .append(Component.text("Usage: ", NamedTextColor.GRAY))
                            .append(Component.text("/rga spectate <minigame>", NamedTextColor.YELLOW))
                            .build());
                    return true;
                }

                String minigameId = args[1].toLowerCase();
                plugin.getPartyManager().joinAsSpectator(player, minigameId);
            }

            case "sessions" -> {
                sender.sendMessage(Component.text("======= Active & Orphaned Sessions =======", NamedTextColor.GOLD, TextDecoration.BOLD));
                var activeParties = plugin.getPartyManager().getActiveParties();
                if (activeParties.isEmpty()) {
                    sender.sendMessage(Component.text("Active minigame sessions: None", NamedTextColor.GRAY));
                } else {
                    sender.sendMessage(Component.text("Active Sessions:", NamedTextColor.YELLOW));
                    activeParties.forEach((mgId, party) -> {
                        sender.sendMessage(Component.text()
                                .append(Component.text(" - Game: ", NamedTextColor.GRAY))
                                .append(Component.text(mgId, NamedTextColor.WHITE))
                                .append(Component.text(" | World: ", NamedTextColor.GRAY))
                                .append(Component.text(party.getActiveWorldName(), NamedTextColor.WHITE))
                                .append(Component.text(" | Members: ", NamedTextColor.GRAY))
                                .append(Component.text(party.getMemberCount(), NamedTextColor.WHITE))
                                .build());
                    });
                }
                var orphans = plugin.getSessionManager().getOrphanedSessionWorlds();
                if (orphans.isEmpty()) {
                    sender.sendMessage(Component.text("Orphaned sessions pending recovery: None", NamedTextColor.GRAY));
                } else {
                    sender.sendMessage(Component.text("Orphaned Recovery Sessions:", NamedTextColor.RED));
                    orphans.forEach(worldName -> sender.sendMessage(Component.text()
                            .append(Component.text(" - World: ", NamedTextColor.GRAY))
                            .append(Component.text(worldName, NamedTextColor.WHITE))
                            .build()));
                }
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    // ── Gamerule helper ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> boolean applyGameRule(World world, GameRule<T> rule, String value) {
        if (rule.getType() == Boolean.class) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) return false;
            world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            return true;
        } else if (rule.getType() == Integer.class) {
            try { world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value)); return true; }
            catch (NumberFormatException e) { return false; }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!hasAnyRGAPermission(sender)) return Collections.emptyList();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> allSubs = List.of(
                "help", "reload", "listworlds", "tp", "compass",
                "createworld", "importworld", "loadworld", "unloadworld", "deleteworld",
                "setspawn", "setworldgamemode", "setworldpvp", "setworlddifficulty",
                "setworldtime", "setworldweather", "setworldalias", "setworldtemplate",
                "gamerule", "conclude", "concludeall", "cleanupsession",
                "spectate", "queue", "sessions", "status"
            );
            for (String s : allSubs) {
                String perm = SUBCOMMAND_PERMISSIONS.get(s);
                if (perm == null || sender.hasPermission(perm)) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "unloadworld", "deleteworld", "setspawn",
                     "setworldgamemode", "setworldpvp", "setworlddifficulty",
                     "setworldtime", "setworldweather", "setworldalias",
                     "setworldtemplate", "gamerule" ->
                    Bukkit.getWorlds().forEach(w -> completions.add(w.getName()));
                case "compass", "tp" ->
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                case "importworld", "loadworld" -> {
                    File serverDir = Bukkit.getWorldContainer();
                    File[] dirs = serverDir.listFiles(File::isDirectory);
                    if (dirs != null) {
                        Set<String> loaded = Bukkit.getWorlds().stream()
                                .map(World::getName).collect(Collectors.toSet());
                        for (File dir : dirs) {
                            String name = dir.getName();
                            if (name.equalsIgnoreCase("world") || name.equalsIgnoreCase("world_nether") || name.equalsIgnoreCase("world_the_end")) {
                                continue;
                            }
                            if (name.equalsIgnoreCase(com.ronlab.rga.world.WorldManager.BACKUP_DIR_NAME)) {
                                continue;
                            }
                            if (!loaded.contains(name) && WorldNameValidator.isValid(name)) {
                                completions.add(name);
                            }
                        }
                    }
                }
                case "createworld" -> completions.add("<worldname>");
                case "conclude" -> {
                    // Suggest active minigame world names
                    plugin.getPartyManager().getActiveParties().values().forEach(party -> {
                        if (party.getActiveWorldName() != null) {
                            completions.add(party.getActiveWorldName());
                        }
                    });
                }
                case "spectate" -> {
                    // Suggest active minigame IDs that are IN_GAME
                    plugin.getPartyManager().getActiveParties().forEach((mgId, party) -> {
                        if (party.getState() == Party.State.IN_GAME) {
                            completions.add(mgId);
                        }
                    });
                    completions.add("leave");
                }
                case "cleanupsession" -> completions.addAll(plugin.getSessionManager().getOrphanedSessionWorlds());
                case "sessions" -> completions.add("list");
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "createworld" -> completions.addAll(List.of("NORMAL", "NETHER", "THE_END"));
                case "setworldgamemode" -> completions.addAll(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"));
                case "setworldpvp", "setworldweather", "setworldtemplate" ->
                    completions.addAll(List.of("true", "false"));
                case "setworlddifficulty" -> completions.addAll(List.of("PEACEFUL", "EASY", "NORMAL", "HARD"));
                case "setworldtime" -> completions.addAll(List.of("day", "noon", "night", "midnight", "-1"));
                case "setworldalias" -> completions.add("<alias>");
                case "tp" -> Bukkit.getWorlds().forEach(w -> completions.add(w.getName()));
                case "gamerule" -> {
                    for (GameRule<?> rule : GameRule.values()) completions.add(rule.getName());
                }
            }
        } else if (args.length == 4) {
            switch (args[0].toLowerCase()) {
                case "createworld" -> completions.addAll(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"));
                case "gamerule" -> {
                    GameRule<?> rule = GameRule.getByName(args[2]);
                    if (rule != null) {
                        if (rule.getType() == Boolean.class) completions.addAll(List.of("true", "false"));
                        else completions.add("<number>");
                    }
                }
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("createworld")) {
            completions.addAll(List.of("true", "false"));
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
    }

    // ── Status ───────────────────────────────────────────────────

    private void sendStatus(CommandSender sender) {
        List<StatusReportFormatter.SessionEntry> sessions = new ArrayList<>();
        for (var entry : plugin.getPartyManager().getActiveParties().entrySet()) {
            var party = entry.getValue();
            sessions.add(new StatusReportFormatter.SessionEntry(
                    entry.getKey(),
                    party.getActiveWorldName() != null ? party.getActiveWorldName() : "n/a",
                    party.getMemberCount()
            ));
        }

        List<StatusReportFormatter.WorldEntry> worlds = new ArrayList<>();
        for (String worldName : plugin.getWorldManager().getConfiguredWorldNames()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            var settings = plugin.getWorldManager().getSettings(worldName);
            worlds.add(new StatusReportFormatter.WorldEntry(
                    world.getName(),
                    "LOADED",
                    world.getPlayers().size(),
                    settings != null ? settings.getGamemode().name() : "UNKNOWN",
                    settings != null && settings.isPvp()
            ));
        }

        for (String line : StatusReportFormatter.buildLines(
                sessions,
                worlds,
                plugin.getSessionManager().hasPendingRecoveries(),
                plugin.getSessionManager().getPendingRecoveryCount(),
                plugin.getSessionManager().getOrphanedSessionWorlds()
        )) {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    // ── Queue Status ──────────────────────────────────────────────

    private void sendQueueStatus(CommandSender sender) {
        var queues = plugin.getPartyManager().getMinigameQueues();
        if (queues.isEmpty()) {
            sender.sendMessage(Component.text("No minigame queues active.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("======= Minigame Queues =======", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (Map.Entry<String, Queue<Party>> entry : queues.entrySet()) {
            String minigameId = entry.getKey();
            Queue<Party> queue = entry.getValue();
            Minigame minigame = plugin.getMinigameManager().getMinigame(minigameId);

            String displayName = minigame != null ? minigame.getName() : minigameId;
            int queueSize = queue.size();

            sender.sendMessage(Component.text()
                    .append(Component.text(displayName, NamedTextColor.GOLD))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(queueSize + " party/ies waiting", NamedTextColor.WHITE))
                    .build());

            // Show each queued party
            int pos = 1;
            for (Party p : queue) {
                StringBuilder memberList = new StringBuilder();
                for (UUID uuid : p.getMembers()) {
                    Player member = Bukkit.getPlayer(uuid);
                    if (member != null) {
                        if (memberList.length() > 0) memberList.append(", ");
                        memberList.append(member.getName());
                    }
                }
                sender.sendMessage(Component.text()
                        .append(Component.text("  #" + pos + " ", NamedTextColor.GRAY))
                        .append(Component.text("(" + p.getMemberCount() + " players)", NamedTextColor.DARK_GRAY))
                        .append(Component.text(" — " + memberList, NamedTextColor.WHITE))
                        .build());
                pos++;
            }
        }
        sender.sendMessage(Component.text("=======" + "=".repeat(24), NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    // ── Help ─────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("====== Ronlab Game Assistant ======", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text()
                .append(Component.text("/rga help", NamedTextColor.YELLOW))
                .append(Component.text(" - Show this help menu", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga reload", NamedTextColor.YELLOW))
                .append(Component.text(" - Reload all configs", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga listworlds", NamedTextColor.YELLOW))
                .append(Component.text(" - List all loaded worlds", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga tp <player> <world>", NamedTextColor.YELLOW))
                .append(Component.text(" - Teleport player to world", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga compass <player>", NamedTextColor.YELLOW))
                .append(Component.text(" - Give navigator compass", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text("--- World Creation ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text()
                .append(Component.text("/rga createworld <name> <env> <gamemode> <pvp>", NamedTextColor.YELLOW))
                .append(Component.text(" - Create world", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga importworld <foldername>", NamedTextColor.YELLOW))
                .append(Component.text(" - Import existing world folder", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga loadworld <name>", NamedTextColor.YELLOW))
                .append(Component.text(" - Load existing world", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga unloadworld <name>", NamedTextColor.YELLOW))
                .append(Component.text(" - Unload world", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga deleteworld <name>", NamedTextColor.YELLOW))
                .append(Component.text(" - Delete world permanently", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text("--- World Settings ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setspawn [world]", NamedTextColor.YELLOW))
                .append(Component.text(" - Set spawn to your location", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setworldgamemode <world> <gamemode>", NamedTextColor.YELLOW))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setworldpvp <world> <true/false>", NamedTextColor.YELLOW))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setworlddifficulty <world> <difficulty>", NamedTextColor.YELLOW))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setworldtime <world> <day|noon|night|midnight|ticks|-1>", NamedTextColor.YELLOW))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setworldweather <world> <true/false>", NamedTextColor.YELLOW))
                .append(Component.text(" - Lock clear weather", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setworldalias <world> <alias>", NamedTextColor.YELLOW))
                .append(Component.text(" - Set display name", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga setworldtemplate <world> <true/false>", NamedTextColor.YELLOW))
                .append(Component.text(" - Mark as template", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga gamerule <world> <rule> <value>", NamedTextColor.YELLOW))
                .build());
        sender.sendMessage(Component.text("--- Sessions & Conclude ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text()
                .append(Component.text("/rga conclude <worldname>", NamedTextColor.YELLOW))
                .append(Component.text(" - Manually conclude a minigame", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga concludeall", NamedTextColor.YELLOW))
                .append(Component.text(" - Conclude all active minigames", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga cleanupsession <worldname>", NamedTextColor.YELLOW))
                .append(Component.text(" - Delete orphaned session and world", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga sessions [list]", NamedTextColor.YELLOW))
                .append(Component.text(" - List active and orphaned sessions", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga queue", NamedTextColor.YELLOW))
                .append(Component.text(" - Show minigame queue status", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga status", NamedTextColor.YELLOW))
                .append(Component.text(" - Show runtime status", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text("--- Spectate ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text()
                .append(Component.text("/rga spectate [minigame]", NamedTextColor.YELLOW))
                .append(Component.text(" - Spectate an active minigame", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/rga spectate leave", NamedTextColor.YELLOW))
                .append(Component.text(" - Leave spectator mode", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text("/hub", NamedTextColor.YELLOW))
                .append(Component.text(" - Return to the Hub world", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.text("================================", NamedTextColor.GOLD, TextDecoration.BOLD));
    }
}
