package com.ronlab.rga.world;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.WorldNameValidator;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
            try {
                if (!WorldNameValidator.isValid(worldName)) {
                    plugin.getLogger().warning("Skipping invalid world entry '"
                            + worldName + "' in worlds.yml: world names may only "
                            + "contain letters, numbers, underscores, and hyphens "
                            + "(max 64 characters).");
                    continue;
                }
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
                boolean disableNether = section.getBoolean("disable-nether", false);
                boolean disableEnd = section.getBoolean("disable-end", false);

                // Load gamerules
                Map<String, String> gamerules = new LinkedHashMap<>();
                ConfigurationSection grSection = section.getConfigurationSection("gamerules");
                if (grSection != null) {
                    for (String rule : grSection.getKeys(false)) {
                        gamerules.put(rule, grSection.getString(rule, ""));
                    }
                }

                // Load optional first-visit-spawn
                FirstVisitSpawn firstVisitSpawn = parseFirstVisitSpawn(section, worldName);

                WorldSettings settings = new WorldSettings(gamemode, pvp, environment,
                        difficulty, alias, template, timeLock, weatherLock, disableNether, disableEnd,
                        gamerules, firstVisitSpawn);
                worldSettings.put(worldName, settings);

                if (loadOnStartup) loadWorld(worldName, environment, settings);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Unexpected error processing configured world '" + worldName + "'", e);
            }
        }
    }

    // ── Load / Unload / Delete ───────────────────────────────────

    private void loadWorld(String worldName, World.Environment environment, WorldSettings settings) {
        try {
            World existing = Bukkit.getWorld(worldName);
            if (existing != null) { applySettings(existing, settings); return; }

            if (isLegacyLayout(worldName)) {
                plugin.getLogger().info("Legacy world layout detected for '" + worldName + "'. Attempting upgrade...");
                if (!upgradeLegacyLayout(worldName)) {
                    plugin.getLogger().severe("Failed to upgrade legacy layout for world '" + worldName + "'. Aborting load to prevent data corruption.");
                    return;
                }
            }

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
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to load world '" + worldName + "' (" + e.getClass().getName() + ": " + e.getMessage() + ")", e);
        }
    }

    public boolean loadExistingWorld(String worldName) {
        try {
            if (Bukkit.getWorld(worldName) != null) return false;

            if (isLegacyLayout(worldName)) {
                plugin.getLogger().info("Legacy world layout detected for '" + worldName + "'. Attempting upgrade...");
                if (!upgradeLegacyLayout(worldName)) {
                    plugin.getLogger().severe("Failed to upgrade legacy layout for world '" + worldName + "'. Aborting load.");
                    return false;
                }
            }

            if (!worldFolderExists(worldName)) return false;

            WorldSettings settings = worldSettings.getOrDefault(worldName,
                    new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                            Difficulty.NORMAL, worldName, false, -1, false, false, false, java.util.Collections.emptyMap()));

            WorldCreator creator = new WorldCreator(worldName).environment(settings.getEnvironment());
            World world = Bukkit.createWorld(creator);
            if (world == null) return false;

            applySettings(world, settings);
            worldSettings.put(worldName, settings);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to load existing world '" + worldName + "' (" + e.getClass().getName() + ": " + e.getMessage() + ")", e);
            return false;
        }
    }

    public boolean importWorld(String worldName, CommandSender sender) {
        try {
            // Check if already loaded
            if (Bukkit.getWorld(worldName) != null) {
                sender.sendMessage("§cWorld '" + worldName + "' is already loaded.");
                return false;
            }

            if (isLegacyLayout(worldName)) {
                sender.sendMessage("§eLegacy layout detected. Upgrading first...");
                if (!upgradeLegacyLayout(worldName)) {
                    sender.sendMessage("§cFailed to upgrade legacy layout for '" + worldName + "'.");
                    return false;
                }
            }

            // Check if folder exists
            if (!worldFolderExists(worldName)) {
                sender.sendMessage("§cNo world folder found for '" + worldName + "'.");
                return false;
            }

            // Load with default settings
            WorldSettings settings = new WorldSettings(GameMode.SURVIVAL, true,
                    World.Environment.NORMAL, Difficulty.NORMAL, worldName, false, -1, false, false, false, java.util.Collections.emptyMap());

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
        } catch (Exception e) {
            sender.sendMessage("§cFailed to import world '" + worldName + "': " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to import world '" + worldName + "' (" + e.getClass().getName() + ": " + e.getMessage() + ")", e);
            return false;
        }
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
        if (!WorldNameValidator.isValid(worldName)) {
            sender.sendMessage("§cInvalid world name.");
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            kickPlayersToHub(world);
            Bukkit.unloadWorld(world, false);
        }

        Path worldPath = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.exists(worldPath)) {
            sender.sendMessage("§cCould not find world folder for '" + worldName + "'.");
            return false;
        }

        try {
            deletePathRecursively(worldPath);
            worldSettings.remove(worldName);
            removeWorldFromConfig(worldName);
            return true;
        } catch (IOException e) {
            sender.sendMessage("§cFailed to delete world: " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to delete world " + worldName, e);
            return false;
        }
    }

    private void deletePathRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private void kickPlayersToHub(World world) {
        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
        if (hub == null) return;
        for (Player player : world.getPlayers()) {
            player.sendMessage("§eThe world you were in is being modified. Sending you to Hub.");
            player.teleport(hub.getSpawnLocation());
        }
    }

    // ── Layout Upgrade & Discovery ───────────────────────────────────

    public static final String BACKUP_DIR_NAME = "_legacy_backups";

    /**
     * Checks if a world has a legacy pre-26.1 layout.
     * Legacy detection criteria: Its dimension folders (e.g. nether, the_end)
     * exist as sibling directories to the main world folder in the server container.
     */
    public boolean isLegacyLayout(String worldName) {
        if (worldName.equalsIgnoreCase(BACKUP_DIR_NAME)) return false;
        Path container = Bukkit.getWorldContainer().toPath();
        Path rootFolder = container.resolve(worldName);
        if (!Files.isDirectory(rootFolder)) return false;
        
        Path netherSibling = container.resolve(worldName + "_nether");
        Path endSibling = container.resolve(worldName + "_the_end");
        return Files.isDirectory(netherSibling) || Files.isDirectory(endSibling);
    }

    /**
     * Performs an out-of-place upgrade copy.
     */
    public boolean upgradeLegacyLayout(String worldName) {
        Path container = Bukkit.getWorldContainer().toPath();
        Path rootLegacy = container.resolve(worldName);
        Path netherLegacy = container.resolve(worldName + "_nether");
        Path endLegacy = container.resolve(worldName + "_the_end");

        Path tempTarget = container.resolve(worldName + "_upgrade_temp_" + System.currentTimeMillis());

        try {
            // 1. Copy overworld root
            if (Files.exists(rootLegacy)) {
                copyPathRecursively(rootLegacy, tempTarget);
            }

            // 2. Copy nether into nested structure: tempTarget/dimensions/minecraft/the_nether
            if (Files.exists(netherLegacy)) {
                Path nestedNether = tempTarget.resolve("dimensions").resolve("minecraft").resolve("the_nether");
                copyPathRecursively(netherLegacy, nestedNether);
            }

            // 3. Copy end into nested structure: tempTarget/dimensions/minecraft/the_end
            if (Files.exists(endLegacy)) {
                Path nestedEnd = tempTarget.resolve("dimensions").resolve("minecraft").resolve("the_end");
                copyPathRecursively(endLegacy, nestedEnd);
            }

            // 4. Verify new layout
            if (!verifyNewLayout(tempTarget, Files.exists(netherLegacy), Files.exists(endLegacy))) {
                throw new IOException("Verification of upgraded layout level.dat failed.");
            }

            // 5. Success! Move upgraded temp folder to overwrite the original root folder (requires unloading or removing old root)
            // Rename/backup the original legacy folders first
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            Path backupParent = container.resolve(BACKUP_DIR_NAME);
            Files.createDirectories(backupParent);

            // Move legacy directories to backup
            if (Files.exists(rootLegacy)) {
                Files.move(rootLegacy, backupParent.resolve(worldName + "_" + timestamp), StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(netherLegacy)) {
                Files.move(netherLegacy, backupParent.resolve(worldName + "_nether_" + timestamp), StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(endLegacy)) {
                Files.move(endLegacy, backupParent.resolve(worldName + "_the_end_" + timestamp), StandardCopyOption.REPLACE_EXISTING);
            }

            // Move the temp folder to target root location
            Files.move(tempTarget, rootLegacy, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Successfully upgraded legacy world layout for: " + worldName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed layout upgrade for " + worldName + ", cleaning up temp folder.", e);
            try {
                deletePathRecursively(tempTarget);
            } catch (IOException cleanupEx) {
                // ignore
            }
            return false;
        }
    }

    private boolean verifyNewLayout(Path rootPath, boolean expectNether, boolean expectEnd) {
        // level.dat must exist, be readable and parseable (e.g. valid file size/header check or simply exists + readable)
        Path mainLevelDat = rootPath.resolve("level.dat");
        if (!Files.isReadable(mainLevelDat) || mainLevelDat.toFile().length() == 0) {
            return false;
        }
        if (expectNether) {
            Path netherLevelDat = rootPath.resolve("dimensions").resolve("minecraft").resolve("the_nether").resolve("level.dat");
            // Nether level.dat might not exist if minecraft nether didn't write it, but if it exists, check it.
            // Under vanilla/paper 26.1, nether and end dimensions typically share the main level.dat or might have their own depending on layout.
            // Let's verify that the nether/end folders exist and are directories if expected.
            Path netherDir = rootPath.resolve("dimensions").resolve("minecraft").resolve("the_nether");
            if (!Files.isDirectory(netherDir)) {
                return false;
            }
        }
        if (expectEnd) {
            Path endDir = rootPath.resolve("dimensions").resolve("minecraft").resolve("the_end");
            if (!Files.isDirectory(endDir)) {
                return false;
            }
        }
        return true;
    }

    private void copyPathRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    // ── Folder utilities ─────────────────────────────────────────

    private boolean worldFolderExists(String worldName) {
        if (!WorldNameValidator.isValid(worldName) || worldName.equalsIgnoreCase(BACKUP_DIR_NAME)) {
            return false;
        }
        Path path = Bukkit.getWorldContainer().toPath().resolve(worldName);
        return Files.isDirectory(path);
    }

    private File findWorldFolder(String worldName) {
        if (!WorldNameValidator.isValid(worldName) || worldName.equalsIgnoreCase(BACKUP_DIR_NAME)) {
            return null;
        }
        Path path = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (Files.isDirectory(path)) {
            return path.toFile();
        }
        return null;
    }

    // ── Create ───────────────────────────────────────────────────

    public boolean createWorld(String worldName, World.Environment environment,
                               GameMode gamemode, boolean pvp) {
        try {
            if (Bukkit.getWorld(worldName) != null) return false;

            WorldCreator creator = new WorldCreator(worldName).environment(environment);
            World world = Bukkit.createWorld(creator);
            if (world == null) return false;

            WorldSettings settings = new WorldSettings(gamemode, pvp, environment,
                    Difficulty.NORMAL, worldName, false, -1, false, false, false, java.util.Collections.emptyMap());
            worldSettings.put(worldName, settings);
            applySettings(world, settings);
            saveWorldToConfig(worldName, environment, gamemode, pvp,
                    Difficulty.NORMAL, worldName, false, -1, false);

            plugin.getLogger().info("Created and loaded world: " + worldName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to create world '" + worldName + "' (" + e.getClass().getName() + ": " + e.getMessage() + ")", e);
            return false;
        }
    }

    // ── Modify ───────────────────────────────────────────────────

    public void setWorldGamemode(String worldName, GameMode gamemode) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(gamemode, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, -1, false, false, false, java.util.Collections.emptyMap()));
        worldSettings.put(worldName, new WorldSettings(gamemode, old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock(),
                old.isDisableNether(), old.isDisableEnd(), old.getGamerules()));
        updateWorldConfig(worldName, "gamemode", gamemode.name());
    }

    public void setWorldPvp(String worldName, boolean pvp) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, pvp, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, -1, false, false, false, java.util.Collections.emptyMap()));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), pvp,
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock(),
                old.isDisableNether(), old.isDisableEnd(), old.getGamerules()));
        updateWorldConfig(worldName, "pvp", String.valueOf(pvp));
    }

    public void setWorldDifficulty(String worldName, Difficulty difficulty) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        difficulty, worldName, false, -1, false, false, false, java.util.Collections.emptyMap()));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), difficulty, old.getAlias(),
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock(),
                old.isDisableNether(), old.isDisableEnd(), old.getGamerules()));
        updateWorldConfig(worldName, "difficulty", difficulty.name());
        World world = Bukkit.getWorld(worldName);
        if (world != null) world.setDifficulty(difficulty);
    }

    public void setWorldTimeLock(String worldName, long time) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, false, time, false, false, false, java.util.Collections.emptyMap()));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), time, old.isWeatherLock(),
                old.isDisableNether(), old.isDisableEnd(), old.getGamerules()));
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
                        Difficulty.NORMAL, worldName, false, -1, locked, false, false, java.util.Collections.emptyMap()));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                old.isTemplate(), old.getTimeLock(), locked,
                old.isDisableNether(), old.isDisableEnd(), old.getGamerules()));
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
                        Difficulty.NORMAL, alias, false, -1, false, false, false, java.util.Collections.emptyMap()));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), alias,
                old.isTemplate(), old.getTimeLock(), old.isWeatherLock(),
                old.isDisableNether(), old.isDisableEnd(), old.getGamerules()));
        updateWorldConfig(worldName, "alias", alias);
    }

    public void setWorldTemplate(String worldName, boolean template) {
        WorldSettings old = worldSettings.getOrDefault(worldName,
                new WorldSettings(GameMode.SURVIVAL, true, World.Environment.NORMAL,
                        Difficulty.NORMAL, worldName, template, -1, false, false, false, java.util.Collections.emptyMap()));
        worldSettings.put(worldName, new WorldSettings(old.getGamemode(), old.isPvp(),
                old.getEnvironment(), old.getDifficulty(), old.getAlias(),
                template, old.getTimeLock(), old.isWeatherLock(),
                old.isDisableNether(), old.isDisableEnd(), old.getGamerules()));
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

        // Resolve spawn: if the player has a tracked location for this world use it;
        // otherwise use first-visit-spawn (or standard world spawn as final fallback).
        var tracker = plugin.getLocationTracker();
        if (tracker != null && tracker.hasLocation(player, worldName)) {
            Location saved = tracker.getLocation(player, worldName);
            if (saved != null && saved.getWorld() != null) {
                player.teleport(saved);
                if (settings != null) player.setGameMode(settings.getGamemode());
                return true;
            }
        }

        // No tracked location — use first-visit-spawn (or world default)
        Location spawnLoc = (settings != null)
                ? settings.getSpawnLocation(world)
                : world.getSpawnLocation();
        player.teleport(spawnLoc);
        if (settings != null) player.setGameMode(settings.getGamemode());
        return true;
    }

    // ── Settings application ─────────────────────────────────────

    @SuppressWarnings("unchecked")
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

        // Gamerules
        for (Map.Entry<String, String> entry : settings.getGamerules().entrySet()) {
            GameRule<?> rule = GameRule.getByName(entry.getKey());
            if (rule == null) {
                plugin.getLogger().warning("Unknown gamerule '" + entry.getKey()
                        + "' in worlds.yml for world '" + world.getName() + "'. Skipping.");
                continue;
            }
            String value = entry.getValue();
            if (rule.getType() == Boolean.class) {
                world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            } else if (rule.getType() == Integer.class) {
                try {
                    world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid value '" + value
                            + "' for gamerule '" + entry.getKey() + "'. Skipping.");
                }
            }
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

    /**
     * Parses a {@code first-visit-spawn} entry from a world config section.
     *
     * <p>Supported YAML formats:
     * <ul>
     *   <li><b>Map/Section</b>: {@code first-visit-spawn: {x: 0, y: 64, z: 0, yaw: 90, pitch: 0}}</li>
     *   <li><b>String</b>: {@code first-visit-spawn: "0, 64, 0, 90, 0"} — yaw and pitch are optional</li>
     *   <li><b>List</b>: {@code first-visit-spawn: [0, 64, 0, 90, 0]} — yaw and pitch are optional</li>
     * </ul>
     *
     * <p>Missing yaw and/or pitch values always default to {@code 0.0f}.
     *
     * @return the parsed {@link FirstVisitSpawn}, or {@code null} if the key is absent or invalid
     */
    private FirstVisitSpawn parseFirstVisitSpawn(ConfigurationSection section, String worldName) {
        if (!section.contains("first-visit-spawn")) return null;
        try {
            // ── Map / section format ─────────────────────────────
            ConfigurationSection sub = section.getConfigurationSection("first-visit-spawn");
            if (sub != null) {
                double x     = sub.getDouble("x", 0.0);
                double y     = sub.getDouble("y", 64.0);
                double z     = sub.getDouble("z", 0.0);
                float  yaw   = (float) sub.getDouble("yaw",   0.0);
                float  pitch = (float) sub.getDouble("pitch", 0.0);
                return new FirstVisitSpawn(x, y, z, yaw, pitch);
            }

            // ── List format ──────────────────────────────────────
            List<?> list = section.getList("first-visit-spawn");
            if (list != null && !list.isEmpty()) {
                double x     = toDouble(list, 0, 0.0);
                double y     = toDouble(list, 1, 64.0);
                double z     = toDouble(list, 2, 0.0);
                float  yaw   = (float) toDouble(list, 3, 0.0);
                float  pitch = (float) toDouble(list, 4, 0.0);
                return new FirstVisitSpawn(x, y, z, yaw, pitch);
            }

            // ── String format ────────────────────────────────────
            String raw = section.getString("first-visit-spawn");
            if (raw != null && !raw.isBlank()) {
                String[] parts = raw.split(",");
                if (parts.length < 3) {
                    plugin.getLogger().warning("first-visit-spawn for '" + worldName
                            + "' needs at least x, y, z. Ignoring.");
                    return null;
                }
                double x     = Double.parseDouble(parts[0].trim());
                double y     = Double.parseDouble(parts[1].trim());
                double z     = Double.parseDouble(parts[2].trim());
                float  yaw   = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 0.0f;
                float  pitch = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0.0f;
                return new FirstVisitSpawn(x, y, z, yaw, pitch);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid first-visit-spawn for '" + worldName
                    + "': " + e.getMessage() + ". Ignoring.");
        }
        return null;
    }

    /** Safely extracts a double from a list by index, returning {@code def} when out of bounds. */
    private double toDouble(List<?> list, int index, double def) {
        if (index >= list.size()) return def;
        Object val = list.get(index);
        if (val instanceof Number num) return num.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return def; }
    }

    // ── Getters ──────────────────────────────────────────────────

    public WorldSettings getSettings(String worldName) { return worldSettings.get(worldName); }
    public Set<String> getConfiguredWorldNames() { return worldSettings.keySet(); }
}
