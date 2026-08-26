package com.ronlab.rga.core.template;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Core service responsible for non-blocking async discovery and registry of map template descriptors.
 */
public class TemplateDiscoveryService {

    private final RGA plugin;
    private final Logger logger;
    private final Map<String, MapTemplateMetadata> registry = new ConcurrentHashMap<>();

    public TemplateDiscoveryService(RGA plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Triggers asynchronous scan of templates/ directory on startup or reload.
     */
    public void discoverTemplatesAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> discoverTemplates());
    }

    /**
     * Synchronous discovery method for direct execution or testing.
     */
    public void discoverTemplates() {
        registry.clear();
        Set<Path> scanned = new HashSet<>();

        List<Path> candidateRoots = new ArrayList<>();
        if (Bukkit.getWorldContainer() != null) {
            candidateRoots.add(Bukkit.getWorldContainer().toPath().resolve("templates"));
            candidateRoots.add(Bukkit.getWorldContainer().toPath().resolve("world/dimensions/minecraft/templates"));
            candidateRoots.add(Bukkit.getWorldContainer().toPath().resolve("dimensions/minecraft/templates"));
        }
        if (plugin.getDataFolder() != null) {
            candidateRoots.add(plugin.getDataFolder().toPath().resolve("templates"));
        }

        for (Path root : candidateRoots) {
            if (Files.exists(root) && Files.isDirectory(root) && scanned.add(root.toAbsolutePath().normalize())) {
                discoverTemplatesInPath(root);
            }
        }

        // Also check if any direct world dimension folders contain a map.yml (in-world map declarations)
        if (Bukkit.getWorldContainer() != null) {
            List<Path> dimensionDirs = List.of(
                    Bukkit.getWorldContainer().toPath().resolve("world/dimensions/minecraft"),
                    Bukkit.getWorldContainer().toPath().resolve("dimensions/minecraft")
            );
            for (Path dimRoot : dimensionDirs) {
                if (Files.exists(dimRoot) && Files.isDirectory(dimRoot) && scanned.add(dimRoot.toAbsolutePath().normalize())) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dimRoot, Files::isDirectory)) {
                        for (Path dir : stream) {
                            String dirName = dir.getFileName().toString();
                            if (dirName.equalsIgnoreCase("templates") || dirName.startsWith("session_")) {
                                continue;
                            }
                            if (Files.exists(dir.resolve("map.yml"))) {
                                processTemplateFolder(dir);
                            }
                        }
                    } catch (IOException ignored) {}
                }
            }
        }

        if (plugin.getMinigameManager() != null) {
            registerMinigames(plugin.getMinigameManager().getAllMinigames().values());
        }

        logger.info("[RGA] Template discovery complete. Registered " + registry.size() + " template(s).");
    }

    /**
     * Synchronous discovery method targeting a specific directory.
     */
    public void discoverTemplates(Path targetDir) {
        registry.clear();
        if (targetDir != null && Files.exists(targetDir) && Files.isDirectory(targetDir)) {
            discoverTemplatesInPath(targetDir);
        }
        if (plugin.getMinigameManager() != null) {
            registerMinigames(plugin.getMinigameManager().getAllMinigames().values());
        }
        logger.info("[RGA] Template discovery complete. Registered " + registry.size() + " template(s).");
    }

    private void discoverTemplatesInPath(Path targetDir) {
        logger.info("[RGA] Scanning template descriptors in: " + targetDir.toAbsolutePath());
        final Path templatesRoot = targetDir;

        try {
            Files.walkFileTree(templatesRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(templatesRoot)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String dirName = dir.getFileName().toString();
                    if (dirName.startsWith("session_")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    Path mapYml = dir.resolve("map.yml");
                    if (Files.exists(mapYml) && Files.isRegularFile(mapYml)) {
                        processTemplateFolder(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // Check if dir is a leaf template folder (no subdirectories)
                    boolean hasSubdirs = false;
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isDirectory)) {
                        hasSubdirs = stream.iterator().hasNext();
                    } catch (IOException ignored) {}

                    if (!hasSubdirs) {
                        processTemplateFolder(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warning("[RGA] Error during template directory walk: " + e.getMessage());
        }
    }

    public void registerMinigames(Collection<com.ronlab.rga.minigame.Minigame> minigameList) {
        if (minigameList == null) return;
        for (com.ronlab.rga.minigame.Minigame mg : minigameList) {
            if (mg.getWorldType() == com.ronlab.rga.minigame.Minigame.WorldType.TEMPLATE) {
                List<MapTemplateMetadata> existing = getTemplatesByCategory(mg.getId());
                if (!existing.isEmpty()) {
                    continue;
                }
            }
            if (!registry.containsKey(mg.getId())) {
                List<Component> loreComponents = new ArrayList<>();
                for (String l : mg.getDisplayLore()) {
                    loreComponents.add(AdventureUtil.color(l));
                }
                MapTemplateMetadata meta = MapTemplateMetadata.builder()
                        .id(mg.getId())
                        .category("minigames")
                        .displayName(AdventureUtil.color("&a&l" + mg.getName()))
                        .icon(mg.getDisplayItem() != null ? mg.getDisplayItem() : Material.STONE)
                        .lore(loreComponents)
                        .difficulty(mg.getDifficulty() != null ? mg.getDifficulty().name() : "EASY")
                        .minPlayers(mg.getMinPlayers())
                        .maxPlayers(mg.getMaxPlayers())
                        .spawnVectors(List.of())
                        .fallThresholdY(-64.0)
                        .templatePath(null)
                        .build();
                registry.put(mg.getId(), meta);
            }
        }
    }

    private void processTemplateFolder(Path folder) {
        String folderName = folder.getFileName().toString();
        Path mapYml = folder.resolve("map.yml");

        if (!Files.exists(mapYml) || !Files.isRegularFile(mapYml)) {
            logger.warning("[RGA WARN] Template folder '" + folderName + "' is missing map.yml! Registering fallback BARRIER item.");
            registerFallbackDummy(folderName, folder, "Missing map.yml descriptor");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(mapYml.toFile());
            String id = config.getString("id", folderName);
            String category = config.getString("category", "minigames");

            String rawName = config.getString("display-name", config.getString("name", id));
            Component displayName = AdventureUtil.color(rawName);

            String iconStr = config.getString("icon", "BARRIER");
            Material icon = AdventureUtil.safeMaterial(iconStr.toUpperCase(Locale.ROOT), Material.BARRIER);

            List<String> rawLore = config.getStringList("lore");
            List<Component> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(AdventureUtil.color(l));
            }

            String difficulty = config.getString("difficulty", "NORMAL");
            int minPlayers = config.getInt("min-players", 1);
            int maxPlayers = config.getInt("max-players", 16);
            double fallThresholdY = config.getDouble("fall-threshold-y", 0.0);

            String rawMinigameId = config.getString("minigame");
            if (rawMinigameId == null || rawMinigameId.isBlank()) {
                if (category.equalsIgnoreCase("parkour") || folder.toString().replace('\\', '/').contains("/parkour/")) {
                    rawMinigameId = "parkour";
                } else {
                    rawMinigameId = id;
                }
            }

            List<Vector> spawnVectors = parseSpawnVectors(config);

            MapTemplateMetadata metadata = MapTemplateMetadata.builder()
                    .id(id)
                    .category(category)
                    .minigameId(rawMinigameId)
                    .displayName(displayName)
                    .icon(icon)
                    .lore(lore)
                    .difficulty(difficulty)
                    .minPlayers(minPlayers)
                    .maxPlayers(maxPlayers)
                    .spawnVectors(spawnVectors)
                    .fallThresholdY(fallThresholdY)
                    .templatePath(folder)
                    .build();

            registry.put(id, metadata);
            logger.info("[RGA] Registered template descriptor: '" + id + "' [" + category + "] -> engine '" + rawMinigameId + "' (" + icon + ")");

        } catch (Exception e) {
            logger.warning("[RGA WARN] Corrupt map.yml in template folder '" + folderName + "': " + e.getMessage() + ". Registering fallback BARRIER item.");
            registerFallbackDummy(folderName, folder, "Corrupt map.yml: " + e.getMessage());
        }
    }

    private void registerFallbackDummy(String id, Path folder, String reason) {
        MapTemplateMetadata fallback = MapTemplateMetadata.builder()
                .id(id)
                .category("minigames")
                .displayName(AdventureUtil.color("&c&l[Corrupt] " + id))
                .icon(Material.BARRIER)
                .addLore(AdventureUtil.color("&7Status: &cInvalid Template"))
                .addLore(AdventureUtil.color("&7Reason: &f" + reason))
                .difficulty("UNKNOWN")
                .minPlayers(1)
                .maxPlayers(1)
                .templatePath(folder)
                .build();
        registry.put(id, fallback);
    }

    private List<Vector> parseSpawnVectors(YamlConfiguration config) {
        List<Vector> list = new ArrayList<>();
        if (config.contains("spawn.x") && config.contains("spawn.y") && config.contains("spawn.z")) {
            double x = config.getDouble("spawn.x");
            double y = config.getDouble("spawn.y");
            double z = config.getDouble("spawn.z");
            list.add(new Vector(x, y, z));
        }
        if (config.isList("spawn-vectors")) {
            List<?> rawList = config.getList("spawn-vectors");
            if (rawList != null) {
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?> map) {
                        double x = getDouble(map.get("x"));
                        double y = getDouble(map.get("y"));
                        double z = getDouble(map.get("z"));
                        list.add(new Vector(x, y, z));
                    } else if (item instanceof String str) {
                        String[] parts = str.split(",");
                        if (parts.length >= 3) {
                            try {
                                double x = Double.parseDouble(parts[0].trim());
                                double y = Double.parseDouble(parts[1].trim());
                                double z = Double.parseDouble(parts[2].trim());
                                list.add(new Vector(x, y, z));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        return list;
    }

    private double getDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    public Map<String, MapTemplateMetadata> getRegistry() {
        return Collections.unmodifiableMap(registry);
    }

    public MapTemplateMetadata getTemplate(String id) {
        return registry.get(id);
    }

    /**
     * Look up template metadata by id or folder name with case-insensitive fallback.
     */
    public MapTemplateMetadata get(String id) {
        if (id == null || id.isBlank()) return null;
        MapTemplateMetadata exact = registry.get(id);
        if (exact != null) return exact;

        // Fallback: Case-insensitive ID match
        for (MapTemplateMetadata meta : registry.values()) {
            if (meta.id().equalsIgnoreCase(id)) {
                return meta;
            }
        }

        // Fallback: World folder name match
        for (MapTemplateMetadata meta : registry.values()) {
            if (meta.templatePath() != null) {
                String folderName = meta.templatePath().getFileName().toString();
                if (folderName.equalsIgnoreCase(id)) {
                    return meta;
                }
            }
        }

        // Fallback: Normalized alphanumeric match (e.g. parkour_paradise_3 -> ParkourParadise3)
        String normId = id.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        for (MapTemplateMetadata meta : registry.values()) {
            String normMetaId = meta.id().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
            if (normMetaId.equals(normId)) {
                return meta;
            }
            if (meta.templatePath() != null) {
                String folderName = meta.templatePath().getFileName().toString();
                String normFolder = folderName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                if (normFolder.equals(normId)) {
                    return meta;
                }
            }
        }
        return null;
    }

    public List<MapTemplateMetadata> getTemplatesByCategory(String category) {
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) {
            return new ArrayList<>(registry.values());
        }
        String catLower = category.toLowerCase(Locale.ROOT);
        return registry.values().stream()
                .filter(m -> m.category().equalsIgnoreCase(catLower))
                .toList();
    }
}
