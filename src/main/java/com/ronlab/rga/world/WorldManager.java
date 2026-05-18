package com.ronlab.rga.world;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WorldManager {

    private final RGA plugin;
    private final Map<String, WorldSettings> worldSettings = new HashMap<>();

    public WorldManager(RGA plugin) {
        this.plugin = plugin;
    }

    public void loadConfiguredWorlds() {
        worldSettings.clear();
        ConfigurationSection worlds = plugin.getConfigManager().getWorldsConfig()
                .getConfigurationSection("worlds");
        if (worlds == null) {
            plugin.getLogger().warning("No worlds section found in worlds.yml!");
            return;
        }

        for (String worldName : worlds.getKeys(false)) {
            ConfigurationSection section = worlds.getConfigurationSection(worldName);
            if (section == null) continue;

            boolean loadOnStartup = section.getBoolean("load-on-startup", true);
            World.Environment environment = parseEnvironment(section.getString("environment", "NORMAL"), worldName);
            GameMode gamemode = parseGameMode(section.getString("gamemode", "SURVIVAL"), worldName);
            boolean pvp = section.getBoolean("pvp", true);
            Difficulty difficulty = parseDifficulty(section.getString("difficulty", "NORMAL"), worldName);
            String alias = section.getString("alias", worldName);
            boolean template = section.getBoolean("template", false);
            long timeLock = section.getLong("time-lock", -1);
            boolean weatherLock = section.getBoolean("weather-lock", false);

            WorldSettings settings = new WorldSettings(gamemode, pvp, environment,
                    difficulty, alias, template, timeLock, weatherLock);
            worldSettings.put(worldName, settings);

            if (loadOnStartup) loadWorld(worldName, environment, settings);
        }
    }

    // ── Load / Unload / Delete ───────────────────────────────────

    private void loadWorld(String worldName, World.Environment environment, WorldSettings settings) {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) { applySettings(existing, settings); return; }

        if (!worldFolderExists(worldName)) {
            plugin.getLogger().warning("World folder for '" + worldName + "' does not exist. Skipping.");
            return;
        }

        WorldCreator creator = new WorldCreator(worldName).environment(environment);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().warning("Failed to load world: " + worldName);
            return;
        }
        applySettings(world, settings);
        plugin.getLogger().info("Loaded world: " + worldName);
    }

    public boolean loadExistingWorld(String worldName) {
        if (Bukkit.getWorld(worldName) != null) return false;
        if (!worldFolderExists(worldName)) return false;

        WorldSettings settings = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, -1, false));

        WorldCreator creator = new WorldCreator(worldName).environment(settings.getEnvironment());
        World world = Bukkit.createWorld(creator);
        if (world == null) return false;

        applySettings(world, settings);
        worldSettings.put(worldName, settings);
        return true;
    }

    public boolean importWorld(String worldName, CommandSender sender) {
        // Check if already loaded
        if (Bukkit.getWorld(worldName) != null) {
            sender.sendMessage("§cWorld '" + worldName + "' is already loaded.");
            return false;
        }

        // Check if folder exists
        if (!worldFolderExists(worldName)) {
            sender.sendMessage("§cNo world folder found for '" + worldName + "'.");
            return false;
        }

        // Load with default settings
        WorldSettings settings = new WorldSettings(GameMode.SURVIVAL, true,
                World.Environment.NORMAL, Difficulty.NORMAL, worldName, false, -1, false);

        WorldCreator creator = new WorldCreator(worldName);
        World world = Bukkit.createWorld(creator);
        if (world == null) return false;

        applySettings(world, settings);
        worldSettings.put(worldName, settings);

        // Save to worlds.yml
        saveWorldToConfig(worldName, World.Environment.NORMAL, GameMode.SURVIVAL,
                true, Difficulty.NORMAL, worldName, false, -1, false);

        plugin.getLogger().info("Imported world: " + worldName);
        return true;
    }

    public boolean unloadWorld(String worldName, CommandSender sender) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld '" + worldName + "' is not loaded.");
            return false;
        }
        kickPlayersToHub(world);
        return Bukkit.unloadWorld(world, true);
    }

    public boolean deleteWorld(String worldName, CommandSender sender) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            kickPlayersToHub(world);
            Bukkit.unloadWorld(world, false);
        }

        File worldFolder = findWorldFolder(worldName);
        if (worldFolder == null || !worldFolder.exists()) {
            sender.sendMessage("§cCould not find world folder for '" + worldName + "'.");
            return false;
        }

        boolean deleted = deleteFolder(worldFolder);
        if (deleted) {
            worldSettings.remove(worldName);
            removeWorldFromConfig(worldName);
        }
        return deleted;
    }

    private void kickPlayersToHub(World world) {
        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
        if (hub == null) return;
        for (Player player : world.getPlayers()) {
            player.sendMessage("§eThe world you were in is being modified. Sending you to Hub.");
            player.teleport(hub.getSpawnLocation());
        }
    }

    // ── Create ───────────────────────────────────────────────────

    public boolean createWorld(String worldName, World.Environment environment,
                               GameMode gamemode, boolean pvp) {
        if (Bukkit.getWorld(worldName) != null) return false;

        WorldCreator creator = new WorldCreator(worldName).environment(environment);
        World world = Bukkit.createWorld(creator);
        if (world == null) return false;

        WorldSettings settings = new WorldSettings(gamemode, pvp, environment,
                Difficulty.NORMAL, worldName, false, -1, false);
        worldSettings.put(worldName, settings);
        applySettings(world, settings);
        saveWorldToConfig(worldName, environment, gamemode, pvp,
                Difficulty.NORMAL, worldName, false, -1, false);

        plugin.getLogger().info("Created and loaded world: " + worldName);
        return true;
    }

    // ── Modify ───────────────────────────────────────────────────

    public void setWorldGamemode(String worldName, GameMode gamemode) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(gamemode, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, -1, false));
        worldSettings.put(worldName, new WorldSettings(gamemode, old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock()));
        updateWorldConfig(worldName, "gamemode", gamemode.name());
    }

    public void setWorldPvp(String worldName, boolean pvp) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, pvp, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, -1, false));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), pvp,
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock()));
        updateWorldConfig(worldName, "pvp", String.valueOf(pvp));
    }

    public void setWorldDifficulty(String worldName, Difficulty difficulty) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        difficulty, worldName, false, -1, false));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), difficulty, old.getAlias(),
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock()));
        updateWorldConfig(worldName, "difficulty", difficulty.name());
        World world = Bukkit.getWorld(worldName);
        if (world != null) world.setDifficulty(difficulty);
    }

    public void setWorldTimeLock(String worldName, long time) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, time, false));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), time, old.isWeatherLock()));
        updateWorldConfig(worldName, "time-lock", String.valueOf(time));
        // Apply immediately
        World world = Bukkit.getWorld(worldName);
        if (world != null && time >= 0) {
            world.setTime(time);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        } else if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }
    }

    public void setWorldWeatherLock(String worldName, boolean locked) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, -1, locked));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), old.getTimeLock(), locked));
        updateWorldConfig(worldName, "weather-lock", String.valueOf(locked));
        World world = Bukkit.getWorld(worldName);
        if (world != null && locked) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        } else if (world != null) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        }
    }

    public void setWorldAlias(String worldName, String alias) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, alias, false, -1, false));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), alias,
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock()));
        updateWorldConfig(worldName, "alias", alias);
    }

    public void setWorldTemplate(String worldName, boolean template) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, template, -1, false));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                template, old.getTimeLock(), old.isWeatherLock()));
        updateWorldConfig(worldName, "template", String.valueOf(template));
    }

    // ── Teleport ─────────────────────────────────────────────────

    public boolean teleportToWorld(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("world-not-found", worldName));
            return false;
        }

        // Check template restriction
        WorldSettings settings = worldSettings.get(worldName);
        if (settings != null && settings.isTemplate() && !player.hasPermission("rga.admin")) {
            player.sendMessage("§cYou cannot enter a template world.");
            return false;
        }

        player.sendMessage(plugin.getConfigManager().getMessage("teleporting"));
        player.teleport(world.getSpawnLocation());
        if (settings != null) player.setGameMode(settings.getGamemode());
        return true;
    }

    // ── Settings application ─────────────────────────────────────

    private void applySettings(World world, WorldSettings settings) {
        world.setPVP(settings.isPvp());
        world.setDifficulty(settings.getDifficulty());

        // Time lock
        if (settings.getTimeLock() >= 0) {
            world.setTime(settings.getTimeLock());
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }

        // Weather lock
        if (settings.isWeatherLock()) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        }
    }

    // ── Config persistence ───────────────────────────────────────

    private void saveWorldToConfig(String worldName, World.Environment environment,
                                   GameMode gamemode, boolean pvp, Difficulty difficulty,
                                   String alias, boolean template, long timeLock,
                                   boolean weatherLock) {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "worlds." + worldName;
        config.set(path + ".load-on-startup", true);
        config.set(path + ".environment", environment.name());
        config.set(path + ".gamemode", gamemode.name());
        config.set(path + ".pvp", pvp);
        config.set(path + ".difficulty", difficulty.name());
        config.set(path + ".alias", alias);
        config.set(path + ".template", template);
        config.set(path + ".time-lock", timeLock);
        config.set(path + ".weather-lock", weatherLock);
        config.set(path + ".announce-join", false);
        saveConfig(config, file);
        plugin.getConfigManager().reload();
    }

    private void updateWorldConfig(String worldName, String key, String value) {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("worlds." + worldName + "." + key, value);
        saveConfig(config, file);
    }

    private void removeWorldFromConfig(String worldName) {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("worlds." + worldName, null);
        saveConfig(config, file);
        plugin.getConfigManager().reload();
    }

    private void saveConfig(YamlConfiguration config, File file) {
        try { config.save(file); }
        catch (IOException e) {
            plugin.getLogger().severe("Could not save worlds.yml: " + e.getMessage());
        }
    }

    // ── Folder utilities ─────────────────────────────────────────

    private boolean worldFolderExists(String worldName) {
        if (new File(Bukkit.getWorldContainer(), worldName).exists()) return true;
        File[] topFolders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (topFolders == null) return false;
        for (File worldFolder : topFolders) {
            File dimensionsDir = new File(worldFolder, "dimensions");
            if (!dimensionsDir.exists()) continue;
            File[] namespaceDirs = dimensionsDir.listFiles(File::isDirectory);
            if (namespaceDirs == null) continue;
            for (File nsDir : namespaceDirs) {
                if (new File(nsDir, worldName).exists()) return true;
            }
        }
        return false;
    }

    private File findWorldFolder(String worldName) {
        File topLevel = new File(Bukkit.getWorldContainer(), worldName);
        if (topLevel.exists()) return topLevel;
        File[] topFolders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (topFolders == null) return null;
        for (File worldFolder : topFolders) {
            File dimensionsDir = new File(worldFolder, "dimensions");
            if (!dimensionsDir.exists()) continue;
            File[] namespaceDirs = dimensionsDir.listFiles(File::isDirectory);
            if (namespaceDirs == null) continue;
            for (File nsDir : namespaceDirs) {
                File candidate = new File(nsDir, worldName);
                if (candidate.exists()) return candidate;
            }
        }
        return null;
    }

    private boolean deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteFolder(file);
                else file.delete();
            }
        }
        return folder.delete();
    }

    // ── Parsers ──────────────────────────────────────────────────

    private World.Environment parseEnvironment(String value, String worldName) {
        try { return World.Environment.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid environment '" + value + "' for " + worldName + ". Defaulting to NORMAL.");
            return World.Environment.NORMAL;
        }
    }

    private GameMode parseGameMode(String value, String worldName) {
        try { return GameMode.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid gamemode '" + value + "' for " + worldName + ". Defaulting to SURVIVAL.");
            return GameMode.SURVIVAL;
        }
    }

    private Difficulty parseDifficulty(String value, String worldName) {
        try { return Difficulty.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid difficulty '" + value + "' for " + worldName + ". Defaulting to NORMAL.");
            return Difficulty.NORMAL;
        }
    }

    // ── Getters ──────────────────────────────────────────────────

    public WorldSettings getSettings(String worldName) { return worldSettings.get(worldName); }
    public Set<String> getConfiguredWorldNames() { return worldSettings.keySet(); }
}
