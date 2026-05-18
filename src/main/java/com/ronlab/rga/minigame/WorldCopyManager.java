package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.UUID;

public class WorldCopyManager {

    private final RGA plugin;

    public WorldCopyManager(RGA plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a fresh set of three linked vanilla worlds (overworld, nether, end).
     * Returns the base world name, or null on failure.
     */
    public String createVanillaWorld(Minigame minigame) {
        String baseName = "minigame_" + minigame.getId() + "_"
                + UUID.randomUUID().toString().substring(0, 8);

        String overworldName = baseName;
        String netherName    = baseName + "_the_nether";
        String endName       = baseName + "_the_end";

        // ── Overworld ─────────────────────────────────────────────
        WorldCreator overworldCreator = new WorldCreator(overworldName);
        overworldCreator.environment(World.Environment.NORMAL);
        overworldCreator.generateStructures(true);
        World overworld = Bukkit.createWorld(overworldCreator);
        if (overworld == null) {
            plugin.getLogger().severe("Failed to create overworld for minigame: " + minigame.getId());
            return null;
        }
        applyMinigameSettings(overworld, minigame);

        // ── Nether ────────────────────────────────────────────────
        WorldCreator netherCreator = new WorldCreator(netherName);
        netherCreator.environment(World.Environment.NETHER);
        netherCreator.generateStructures(true);
        World nether = Bukkit.createWorld(netherCreator);
        if (nether == null) {
            plugin.getLogger().severe("Failed to create nether for minigame: " + minigame.getId());
            Bukkit.unloadWorld(overworld, false);
            return null;
        }
        applyMinigameSettings(nether, minigame);

        // ── End ───────────────────────────────────────────────────
        WorldCreator endCreator = new WorldCreator(endName);
        endCreator.environment(World.Environment.THE_END);
        endCreator.generateStructures(true);
        World end = Bukkit.createWorld(endCreator);
        if (end == null) {
            plugin.getLogger().severe("Failed to create end for minigame: " + minigame.getId());
            Bukkit.unloadWorld(overworld, false);
            Bukkit.unloadWorld(nether, false);
            return null;
        }
        applyMinigameSettings(end, minigame);

        plugin.getLogger().info("Created vanilla minigame worlds: "
                + overworldName + ", " + netherName + ", " + endName);
        return baseName;
    }

    /**
     * Copies a top-level template world folder to a new top-level folder.
     * Datapacks and all world data are preserved since the entire folder is copied.
     * Returns the new world name, or null on failure.
     */
    public String copyTemplateWorld(Minigame minigame) {
        String templateWorldName = minigame.getTemplateWorld();
        String newWorldName = "minigame_" + minigame.getId() + "_"
                + UUID.randomUUID().toString().substring(0, 8);

        // Template must be a top-level folder in the server directory
        File serverDir = Bukkit.getWorldContainer();
        File templateFolder = new File(serverDir, templateWorldName);

        if (!templateFolder.exists() || !templateFolder.isDirectory()) {
            plugin.getLogger().severe("Template world folder not found at server root: "
                    + templateWorldName
                    + ". Make sure the template world is a top-level folder, not inside world/dimensions/.");
            return null;
        }

        File destination = new File(serverDir, newWorldName);

        // Copy the entire template folder including datapacks
        try {
            copyFolder(templateFolder.toPath(), destination.toPath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy template world: " + e.getMessage());
            return null;
        }

        // Delete files that cause Paper to detect this as a duplicate world
        deleteDuplicateFiles(destination);

        // Load the copied world
        WorldCreator creator = new WorldCreator(newWorldName);
        creator.environment(World.Environment.NORMAL);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("Failed to load copied world: " + newWorldName);
            deleteFolder(destination);
            return null;
        }

        applyMinigameSettings(world, minigame);
        plugin.getLogger().info("Copied template '" + templateWorldName
                + "' to '" + newWorldName + "' with datapacks.");
        return newWorldName;
    }

    /**
     * Applies world settings from the minigame config to a world.
     */
    @SuppressWarnings("unchecked")
    public void applyMinigameSettings(World world, Minigame minigame) {
        world.setPVP(minigame.isPvp());
        world.setDifficulty(minigame.getDifficulty());

        for (Map.Entry<String, String> entry : minigame.getGamerules().entrySet()) {
            GameRule<?> rule = GameRule.getByName(entry.getKey());
            if (rule == null) {
                plugin.getLogger().warning("Unknown gamerule '" + entry.getKey()
                        + "' in minigame " + minigame.getId() + ". Skipping.");
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

    /**
     * Unloads and deletes minigame world(s).
     * For VANILLA type, also cleans up nether and end.
     * For TEMPLATE type, deletes the entire top-level copied folder.
     */
    public void cleanupWorld(String baseName, boolean isVanilla) {
        if (isVanilla) {
            unloadAndDelete(baseName + "_the_end", false);
            unloadAndDelete(baseName + "_the_nether", false);
            unloadAndDelete(baseName, false);
        } else {
            // Template worlds are top-level folders — delete the whole folder
            unloadAndDelete(baseName, true);
        }
    }

    private void unloadAndDelete(String worldName, boolean topLevel) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
            if (hub != null) {
                for (Player p : world.getPlayers()) {
                    p.teleport(hub.getSpawnLocation());
                }
            }
            Bukkit.unloadWorld(world, false);
        }

        // For template copies — delete the entire top-level folder
        if (topLevel) {
            File topLevelFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (topLevelFolder.exists()) {
                deleteFolder(topLevelFolder);
                plugin.getLogger().info("Deleted minigame world folder: " + worldName);
                return;
            }
        }

        // For vanilla dimensions — find and delete the dimension subfolder
        File folder = findDimensionFolder(worldName);
        if (folder != null && folder.exists()) {
            deleteFolder(folder);
            plugin.getLogger().info("Deleted minigame world: " + worldName);
        }
    }

    // ── Folder utilities ─────────────────────────────────────────

    /**
     * Finds a world folder inside world/dimensions/minecraft/ subfolders.
     * Used for vanilla minigame dimension cleanup.
     */
    private File findDimensionFolder(String worldName) {
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

    /**
     * Recursively deletes files that cause Paper duplicate world detection.
     * metadata.dat contains the world UUID in Paper 26.1.
     */
    private void deleteDuplicateFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDuplicateFiles(file);
            } else if (file.getName().equals("uid.dat")
                    || file.getName().equals("session.lock")
                    || file.getName().equals("metadata.dat")) {
                file.delete();
                plugin.getLogger().info("Deleted " + file.getName()
                        + " from copied world to prevent duplicate detection.");
            }
        }
    }

    private void copyFolder(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteFolder(file);
                else file.delete();
            }
        }
        folder.delete();
    }
}
