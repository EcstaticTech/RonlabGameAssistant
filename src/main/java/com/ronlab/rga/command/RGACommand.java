package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
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

    public RGACommand(RGA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rga.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {

            case "help" -> sendHelp(sender);

            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("reloaded"));
            }

            case "listworlds" -> {
                sender.sendMessage("§6§l======= Loaded Worlds =======");
                for (World world : Bukkit.getWorlds()) {
                    var settings = plugin.getWorldManager().getSettings(world.getName());
                    String alias = settings != null ? settings.getAlias() : world.getName();
                    String gamemode = settings != null ? settings.getGamemode().name() : "UNKNOWN";
                    String pvp = settings != null ? (settings.isPvp() ? "§cPVP" : "§aNoPVP") : "§7?";
                    String template = (settings != null && settings.isTemplate()) ? " §d[TEMPLATE]" : "";
                    String timeLock = (settings != null && settings.getTimeLock() >= 0)
                            ? " §e[TIME:" + settings.getTimeLock() + "]" : "";
                    String weatherLock = (settings != null && settings.isWeatherLock())
                            ? " §b[WEATHER LOCKED]" : "";
                    int players = world.getPlayers().size();
                    sender.sendMessage("§e" + alias + " §7(" + world.getName() + ") | "
                            + gamemode + " | " + pvp
                            + " | §f" + players + " player(s)"
                            + template + timeLock + weatherLock);
                }
                sender.sendMessage("§6§l=============================");
            }

            case "tp" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /rga tp <player> <world>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
                plugin.getWorldManager().teleportToWorld(target, args[2]);
            }

            case "compass" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rga compass <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
                plugin.getHubListener().giveCompass(target);
                sender.sendMessage("§aGave navigator compass to " + target.getName() + ".");
            }

            case "createworld" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /rga createworld <name> <environment> <gamemode> <pvp>");
                    return true;
                }
                World.Environment env; GameMode gm;
                try { env = World.Environment.valueOf(args[2].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage("§cInvalid environment."); return true; }
                try { gm = GameMode.valueOf(args[3].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage("§cInvalid gamemode."); return true; }
                boolean pvp = Boolean.parseBoolean(args[4]);
                if (Bukkit.getWorld(args[1]) != null) { sender.sendMessage("§cWorld already loaded."); return true; }
                sender.sendMessage("§eCreating world '" + args[1] + "'...");
                boolean ok = plugin.getWorldManager().createWorld(args[1], env, gm, pvp);
                sender.sendMessage(ok ? "§aWorld created!" : "§cFailed to create world.");
            }

            case "importworld" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rga importworld <foldername>"); return true; }
                sender.sendMessage("§eImporting world '" + args[1] + "'...");
                boolean ok = plugin.getWorldManager().importWorld(args[1], sender);
                if (ok) {
                    sender.sendMessage("§aWorld '" + args[1] + "' imported successfully!");
                    sender.sendMessage("§7Use §e/rga setworldgamemode§7, §e/rga setworldpvp§7 etc. to configure it.");
                }
            }

            case "loadworld" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rga loadworld <name>"); return true; }
                if (Bukkit.getWorld(args[1]) != null) { sender.sendMessage("§cWorld already loaded."); return true; }
                sender.sendMessage("§eLoading world '" + args[1] + "'...");
                boolean ok = plugin.getWorldManager().loadExistingWorld(args[1]);
                sender.sendMessage(ok ? "§aWorld loaded!" : "§cFailed. Does the folder exist?");
            }

            case "unloadworld" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rga unloadworld <name>"); return true; }
                String hub = plugin.getConfigManager().getHubWorld();
                if (args[1].equalsIgnoreCase(hub) || args[1].equalsIgnoreCase("world")) {
                    sender.sendMessage("§cYou cannot unload the Hub or default world."); return true;
                }
                boolean ok = plugin.getWorldManager().unloadWorld(args[1], sender);
                sender.sendMessage(ok ? "§aWorld unloaded." : "§cFailed to unload world.");
            }

            case "deleteworld" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rga deleteworld <name>"); return true; }
                String hub = plugin.getConfigManager().getHubWorld();
                if (args[1].equalsIgnoreCase(hub) || args[1].equalsIgnoreCase("world")) {
                    sender.sendMessage("§cYou cannot delete the Hub or default world."); return true;
                }
                sender.sendMessage("§eDeleting world '" + args[1] + "'...");
                boolean ok = plugin.getWorldManager().deleteWorld(args[1], sender);
                sender.sendMessage(ok ? "§aWorld deleted." : "§cFailed to delete world.");
            }

            case "setspawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }
                String worldName = args.length >= 2 ? args[1] : player.getWorld().getName();
                World world = Bukkit.getWorld(worldName);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                world.setSpawnLocation(player.getLocation());
                sender.sendMessage("§aSpawn for '" + worldName + "' set to your location.");
            }

            case "setworldgamemode" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /rga setworldgamemode <world> <gamemode>"); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                GameMode gm;
                try { gm = GameMode.valueOf(args[2].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage("§cInvalid gamemode."); return true; }
                plugin.getWorldManager().setWorldGamemode(args[1], gm);
                for (Player p : world.getPlayers()) p.setGameMode(gm);
                sender.sendMessage("§aGamemode for '" + args[1] + "' set to " + gm.name() + ".");
            }

            case "setworldpvp" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /rga setworldpvp <world> <true/false>"); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                boolean pvp = Boolean.parseBoolean(args[2]);
                world.setPVP(pvp);
                plugin.getWorldManager().setWorldPvp(args[1], pvp);
                sender.sendMessage("§aPVP for '" + args[1] + "' set to " + pvp + ".");
            }

            case "setworlddifficulty" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /rga setworlddifficulty <world> <difficulty>"); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                Difficulty diff;
                try { diff = Difficulty.valueOf(args[2].toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage("§cInvalid difficulty."); return true; }
                plugin.getWorldManager().setWorldDifficulty(args[1], diff);
                sender.sendMessage("§aDifficulty for '" + args[1] + "' set to " + diff.name() + ".");
            }

            case "setworldtime" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rga setworldtime <world> <day|noon|night|midnight|<ticks>|-1>");
                    sender.sendMessage("§7Use -1 to unlock time.");
                    return true;
                }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
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
                if (ticks == Long.MIN_VALUE) { sender.sendMessage("§cInvalid time value."); return true; }
                plugin.getWorldManager().setWorldTimeLock(args[1], ticks);
                sender.sendMessage(ticks >= 0
                        ? "§aTime for '" + args[1] + "' locked to " + ticks + " ticks."
                        : "§aTime lock removed for '" + args[1] + "'.");
            }

            case "setworldweather" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /rga setworldweather <world> <true/false>"); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                boolean locked = Boolean.parseBoolean(args[2]);
                plugin.getWorldManager().setWorldWeatherLock(args[1], locked);
                sender.sendMessage("§aWeather lock for '" + args[1] + "' set to " + locked + ".");
            }

            case "setworldalias" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /rga setworldalias <world> <alias>"); return true; }
                plugin.getWorldManager().setWorldAlias(args[1], args[2]);
                sender.sendMessage("§aAlias for '" + args[1] + "' set to '" + args[2] + "'.");
            }

            case "setworldtemplate" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /rga setworldtemplate <world> <true/false>"); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                boolean template = Boolean.parseBoolean(args[2]);
                plugin.getWorldManager().setWorldTemplate(args[1], template);
                sender.sendMessage("§aTemplate status for '" + args[1] + "' set to " + template + ".");
            }

            case "gamerule" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: /rga gamerule <world> <rule> <value>"); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                GameRule<?> rule = GameRule.getByName(args[2]);
                if (rule == null) { sender.sendMessage("§cUnknown gamerule: " + args[2]); return true; }
                boolean applied = applyGameRule(world, rule, args[3]);
                sender.sendMessage(applied
                        ? "§aGamerule " + args[2] + " set to " + args[3] + " in " + args[1] + "."
                        : "§cInvalid value '" + args[3] + "' for gamerule " + args[2] + ".");
            }

            case "conclude" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rga conclude <worldname>"); return true; }
                plugin.getPartyManager().concludeGame(args[1]);
                sender.sendMessage("§aGame concluded for world: " + args[1]);
            }

            case "concludeall" -> {
                var parties = plugin.getPartyManager().getActiveParties();
                if (parties.isEmpty()) {
                    sender.sendMessage("§eNo active games to conclude.");
                    return true;
                }
                int count = parties.size();
                // Copy values to avoid concurrent modification
                new java.util.ArrayList<>(parties.values()).forEach(party -> {
                    if (party.getActiveWorldName() != null) {
                        plugin.getPartyManager().concludeGame(party.getActiveWorldName());
                    }
                });
                sender.sendMessage("§aConcluded " + count + " active game(s).");
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

    // ── Tab Completion ───────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("rga.admin")) return Collections.emptyList();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of(
                "help", "reload", "listworlds", "tp", "compass",
                "createworld", "importworld", "loadworld", "unloadworld", "deleteworld",
                "setspawn", "setworldgamemode", "setworldpvp", "setworlddifficulty",
                "setworldtime", "setworldweather", "setworldalias", "setworldtemplate",
                "gamerule", "conclude", "concludeall"
            ));
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
                            if (!loaded.contains(dir.getName())) completions.add(dir.getName());
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

    // ── Help ─────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l====== Ronlab Game Assistant ======");
        sender.sendMessage("§e/rga help §7- Show this help menu");
        sender.sendMessage("§e/rga reload §7- Reload all configs");
        sender.sendMessage("§e/rga listworlds §7- List all loaded worlds");
        sender.sendMessage("§e/rga tp <player> <world> §7- Teleport player to world");
        sender.sendMessage("§e/rga compass <player> §7- Give navigator compass");
        sender.sendMessage("§6--- World Creation ---");
        sender.sendMessage("§e/rga createworld <name> <env> <gamemode> <pvp> §7- Create world");
        sender.sendMessage("§e/rga importworld <foldername> §7- Import existing world folder");
        sender.sendMessage("§e/rga loadworld <name> §7- Load existing world");
        sender.sendMessage("§e/rga unloadworld <name> §7- Unload world");
        sender.sendMessage("§e/rga deleteworld <name> §7- Delete world permanently");
        sender.sendMessage("§6--- World Settings ---");
        sender.sendMessage("§e/rga setspawn [world] §7- Set spawn to your location");
        sender.sendMessage("§e/rga setworldgamemode <world> <gamemode>");
        sender.sendMessage("§e/rga setworldpvp <world> <true/false>");
        sender.sendMessage("§e/rga setworlddifficulty <world> <difficulty>");
        sender.sendMessage("§e/rga setworldtime <world> <day|noon|night|midnight|ticks|-1>");
        sender.sendMessage("§e/rga setworldweather <world> <true/false> §7- Lock clear weather");
        sender.sendMessage("§e/rga setworldalias <world> <alias> §7- Set display name");
        sender.sendMessage("§e/rga setworldtemplate <world> <true/false> §7- Mark as template");
        sender.sendMessage("§e/rga gamerule <world> <rule> <value>");
        sender.sendMessage("§e/rga conclude <worldname> §7- Manually conclude a minigame");
        sender.sendMessage("§e/rga concludeall §7- Conclude all active minigames");
        sender.sendMessage("§e/hub §7- Return to the Hub world");
        sender.sendMessage("§6§l====================================");
    }
}
