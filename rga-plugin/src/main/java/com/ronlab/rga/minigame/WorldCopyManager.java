package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.FileUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class WorldCopyManager {

    private final RGA plugin;
    private final ConcurrentHashMap<String, ReentrantLock> templateLocks = new ConcurrentHashMap<>();

    public WorldCopyManager(RGA plugin) {
        this.plugin = plugin;
    }

    /**
     * Helper accessor for testing/inspecting template locks.
     */
    public ReentrantLock getTemplateLock(String templateWorldName) {
        return templateLocks.computeIfAbsent(templateWorldName, k -> new ReentrantLock());
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

        File worldContainer = Bukkit.getWorldContainer();
        ensurePaperWorldDefaults(new File(worldContainer, overworldName));
        ensurePaperWorldDefaults(new File(worldContainer, netherName));
        ensurePaperWorldDefaults(new File(worldContainer, endName));

        WorldCreator overworldCreator = new WorldCreator(overworldName);
        overworldCreator.environment(World.Environment.NORMAL);
        overworldCreator.generateStructures(true);
        World overworld = Bukkit.createWorld(overworldCreator);
        if (overworld == null) {
            plugin.getLogger().severe("Failed to create overworld for minigame: " + minigame.getId());
            return null;
        }
        applyDeterministicSpawn(overworld, overworldName);
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
        applyDeterministicSpawn(nether, netherName);
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
        applyDeterministicSpawn(end, endName);
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
     * Key-based concurrency locking is enforced on templateWorldName to ensure
     * concurrent async session starts targeting the same template copy safely.
     *
     * Returns the new world name, or null on failure.
     */
    public CompletableFuture<String> copyTemplateWorld(Minigame minigame) {
        String templateWorldName = minigame.getTemplateWorld();
        String cleanId = com.ronlab.rga.util.WorldNameValidator.sanitizeForFilesystem(minigame.getId());
        long timestamp = System.currentTimeMillis();
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        String newWorldName = "session_" + cleanId + "_" + timestamp + "_" + shortUuid;


        File templateFolder = resolveWorldFolder(templateWorldName);
        if (!templateFolder.exists() || !templateFolder.isDirectory()) {
            plugin.getLogger().severe(
                "Template world folder not found: " + templateWorldName);
            return CompletableFuture.completedFuture(null);
        }

        File destination = new File(templateFolder.getParentFile(), newWorldName);
        boolean disableNether = minigame.isDisableNether();
        boolean disableEnd = minigame.isDisableEnd();

        ReentrantLock templateLock = getTemplateLock(templateWorldName);

        return CompletableFuture.supplyAsync(() -> {
            templateLock.lock();
            try {
                copyFolder(templateFolder.toPath(), destination.toPath(), disableNether, disableEnd);
                // Remove identity files so Paper treats this as a fresh world.
                // We do NOT delete level.dat — it carries the map's spawn point,
                // world settings, and datapack load list, all of which the map needs.
                deleteDuplicateFiles(destination);
                ensurePaperWorldDefaults(destination);
                return newWorldName;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to copy template world: " + e.getMessage());
                deleteFolder(destination);
                return null;
            } finally {
                templateLock.unlock();
            }
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

                applyDeterministicSpawn(world, templateWorldName);
                applyMinigameSettings(world, minigame);
                plugin.getLogger().info("Copied template '" + templateWorldName
                        + "' to '" + worldName + "'.");
                loadFuture.complete(worldName);
            });
            return loadFuture;
        });
    }

    /**
     * Injects an explicit, deterministic spawn location into a world immediately after creation
     * to eliminate PaperMC 26.2 PlayerSpawnFinder main-thread chunk searching watchdog hangs.
     *
     * @param world the newly created/loaded World instance; must not be null
     * @param templateWorldName the associated template world name or world identifier
     */
    public void applyDeterministicSpawn(World world, String templateWorldName) {
        if (world == null) return;

        Location spawnLoc = null;
        if (plugin != null && plugin.getWorldManager() != null && templateWorldName != null) {
            com.ronlab.rga.world.WorldSettings settings = plugin.getWorldManager().getSettings(templateWorldName);
            if (settings != null && settings.getFirstVisitSpawn() != null) {
                spawnLoc = settings.getFirstVisitSpawn().toLocation(world);
            }
        }

        if (spawnLoc == null && templateWorldName != null) {
            World templateWorld = Bukkit.getWorld(templateWorldName);
            if (templateWorld != null) {
                Location tSpawn = templateWorld.getSpawnLocation();
                spawnLoc = new Location(world, tSpawn.getX(), tSpawn.getY(), tSpawn.getZ(), tSpawn.getYaw(), tSpawn.getPitch());
            }
        }

        if (spawnLoc != null) {
            world.setSpawnLocation(spawnLoc);
            if (plugin != null) {
                plugin.getLogger().info("Applied explicit template spawn location to world '" + world.getName()
                        + "': (" + spawnLoc.getX() + ", " + spawnLoc.getY() + ", " + spawnLoc.getZ() + ")");
            }
        } else {
            Location defaultSpawn = new Location(world, 0.5, 100.0, 0.5);
            world.setSpawnLocation(defaultSpawn);
            String displayWorldName = templateWorldName != null ? templateWorldName : world.getName();
            String warningMsg = "[rga-core] WARNING: No template spawn defined for world " + displayWorldName
                    + ". Defaulting spawn to (0.5, 100.0, 0.5). Run /rga setspawn in the template world to suppress.";
            if (plugin != null) {
                plugin.getLogger().warning(warningMsg);
            } else {
                java.util.logging.Logger.getLogger("rga-core").warning(warningMsg);
            }
        }
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

        File folder = resolveWorldFolder(worldName);
        if (folder.exists()) {
            deleteFolder(folder);
            plugin.getLogger().info("Deleted minigame world: " + worldName);
        }
    }

    // ── Folder utilities ─────────────────────────────────────────

    public File resolveWorldFolder(String worldName) {
        if (plugin != null && plugin.getWorldManager() != null) {
            return plugin.getWorldManager().getWorldFolder(worldName);
        }
        File dimensionsFolder = new File(Bukkit.getWorldContainer(), "dimensions/minecraft");
        if (dimensionsFolder.exists() && dimensionsFolder.isDirectory()) {
            File[] files = dimensionsFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory() && file.getName().equalsIgnoreCase(worldName)) {
                        return file;
                    }
                }
            }
        }
        File serverRoot = Bukkit.getWorldContainer();
        File[] rootFiles = serverRoot.listFiles();
        if (rootFiles != null) {
            for (File file : rootFiles) {
                if (file.isDirectory() && file.getName().equalsIgnoreCase(worldName)) {
                    return file;
                }
            }
        }
        return new File(dimensionsFolder, worldName);
    }

    /**
     * Finds a world folder using three-tier dimension resolution.
     */
    public File findWorldFolder(String worldName) {
        File folder = resolveWorldFolder(worldName);
        if (folder.exists() && folder.isDirectory()) {
            return folder;
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
                    if (dirName.equals("datapacks")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
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
        boolean deleted = FileUtils.deleteDirectoryWithRetry(folder, 3, 250);
        if (!deleted) {
            plugin.getLogger().severe("Failed to recursively delete " + folder);
        }
    }

    private void ensurePaperWorldDefaults(File worldDir) {
        if (worldDir == null) return;
        File paperWorldFile = new File(worldDir, "paper-world.yml");
        if (!paperWorldFile.exists()) {
            try {
                if (!worldDir.exists()) worldDir.mkdirs();
                String content = "unsupported-settings:\n  suppress-feature-cross-chunk-loads: true\n";
                Files.writeString(paperWorldFile.toPath(), content);
            } catch (IOException ignored) {}
        }
    }
}
