package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        String cleanId = com.ronlab.rga.util.WorldNameValidator.sanitizeForFilesystem(minigame.getId());
        String baseName = "minigame_" + cleanId + "_"
                + UUID.randomUUID().toString().substring(0, 8);

        String overworldName = baseName;
        String netherName    = baseName + "_the_nether";
        String endName       = baseName + "_the_end";


        WorldCreator overworldCreator = new WorldCreator(overworldName);
        overworldCreator.environment(World.Environment.NORMAL);
        overworldCreator.generateStructures(true);
        World overworld = Bukkit.createWorld(overworldCreator);
        if (overworld == null) {
            plugin.getLogger().severe("Failed to create overworld for minigame: " + minigame.getId());
            return null;
        }
        applyMinigameSettings(overworld, minigame);

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
     * Copies a template world top-level folder to a new top-level folder,
     * then loads it with WorldCreator.
     *
     * Template worlds must be stored as top-level world folders in the server
     * directory (alongside the main 'world' folder), NOT inside dimensions/.
     * This ensures datapacks, level.dat, and all other level-root assets are
     * included in the copy.
     *
     * Returns the new world name, or null on failure.
     */
    public CompletableFuture<String> copyTemplateWorld(Minigame minigame) {
        String templateWorldName = minigame.getTemplateWorld();
        String cleanId = com.ronlab.rga.util.WorldNameValidator.sanitizeForFilesystem(minigame.getId());
        String newWorldName = "minigame_" + cleanId + "_"
                + UUID.randomUUID().toString().substring(0, 8);


        // Templates must be top-level world folders
        File templateFolder = new File(Bukkit.getWorldContainer(), templateWorldName);
        if (!templateFolder.exists() || !templateFolder.isDirectory()) {
            plugin.getLogger().severe(
                "Template world folder not found at server root: " + templateWorldName
                + ". Template worlds must be top-level folders in the server directory.");
            return CompletableFuture.completedFuture(null);
        }

        File destination = new File(Bukkit.getWorldContainer(), newWorldName);
        boolean disableNether = minigame.isDisableNether();
        boolean disableEnd = minigame.isDisableEnd();

        return CompletableFuture.supplyAsync(() -> {
            try {
                copyFolder(templateFolder.toPath(), destination.toPath(), disableNether, disableEnd);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to copy template world: " + e.getMessage());
                deleteFolder(destination);
                return null;
            }

            // Remove identity files so Paper treats this as a fresh world.
            // We do NOT delete level.dat — it carries the map's spawn point,
            // world settings, and datapack load list, all of which the map needs.
            deleteDuplicateFiles(destination);
            return newWorldName;
        }).thenCompose(worldName -> {
            if (worldName == null) {
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<String> loadFuture = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                WorldCreator creator = new WorldCreator(worldName);
                creator.environment(World.Environment.NORMAL);
                World world = Bukkit.createWorld(creator);
                if (world == null) {
                    plugin.getLogger().severe("Failed to load copied world: " + worldName);
                    deleteFolder(destination);
                    loadFuture.complete(null);
                    return;
                }

                applyMinigameSettings(world, minigame);
                plugin.getLogger().info("Copied template '" + templateWorldName
                        + "' to '" + worldName + "'.");
                loadFuture.complete(worldName);
            });
            return loadFuture;
        });
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
     */
    public void cleanupWorld(String baseName, boolean isVanilla) {
        if (isVanilla) {
            unloadAndDelete(baseName + "_the_end");
            unloadAndDelete(baseName + "_the_nether");
        }
        unloadAndDelete(baseName);
    }

    private void unloadAndDelete(String worldName) {
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

        // Minigame worlds are always top-level folders
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (folder.exists()) {
            deleteFolder(folder);
            plugin.getLogger().info("Deleted minigame world: " + worldName);
        }
    }

    // ── Folder utilities ─────────────────────────────────────────

    /**
     * Finds a world folder. Checks the server root (top-level) first.
     */
    public File findWorldFolder(String worldName) {
        Path path = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (Files.isDirectory(path)) {
            return path.toFile();
        }
        return null;
    }

    /**
     * Recursively deletes files that cause Paper duplicate-world detection.
     * level.dat is intentionally preserved.
     */
    private void deleteDuplicateFiles(File folder) {
        Path rootPath = folder.toPath();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    if (name.equals("uid.dat")
                            || name.equals("session.lock")
                            || name.equals("metadata.dat")) {
                        Files.delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to delete duplicate files in " + folder + ": " + e.getMessage());
        }
    }

    private void copyFolder(Path source, Path destination, boolean disableNether, boolean disableEnd) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (!dir.equals(source)) {
                    String dirName = dir.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (disableNether && (dirName.endsWith("_nether") || dirName.equals("the_nether") || dirName.equals("dim-1"))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (disableEnd && (dirName.endsWith("_the_end") || dirName.equals("the_end") || dirName.equals("dim1"))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
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
        if (folder == null || !folder.exists()) return;
        Path rootPath = folder.toPath();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to recursively delete " + folder + ": " + e.getMessage());
        }
    }
}
