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
        // 1. Check server container root first: <server_root>/templates
        Path rootTemplatesDir = Bukkit.getWorldContainer().toPath().resolve("templates");
        Path targetDir = rootTemplatesDir;

        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            // 2. Fall back to plugin data folder: <data_folder>/templates
            targetDir = plugin.getDataFolder().toPath().resolve("templates");
        }

        discoverTemplates(targetDir);
    }

    /**
     * Synchronous discovery method targeting a specific directory.
     */
    public void discoverTemplates(Path targetDir) {
        registry.clear();

        if (targetDir == null) return;

        if (!Files.exists(targetDir)) {
            try {
                Files.createDirectories(targetDir);
                logger.info("[RGA] Created empty templates directory at: " + targetDir.toAbsolutePath());
            } catch (IOException e) {
                logger.warning("[RGA] Failed to create templates directory at " + targetDir + ": " + e.getMessage());
                return;
            }
        }

        logger.info("[RGA] Scanning template descriptors in: " + targetDir.toAbsolutePath());

        final Path templatesRoot = targetDir;

        try {
            Files.walkFileTree(templatesRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(templatesRoot)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Look for template folders containing map.yml or top-level template subdirectories
                    Path parent = dir.getParent();
                    if (parent != null && parent.equals(templatesRoot)) {
                        processTemplateFolder(dir);
                        // Skip subtrees of a template folder
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warning("[RGA] Error during template directory walk: " + e.getMessage());
        }

        logger.info("[RGA] Template discovery complete. Registered " + registry.size() + " template(s).");
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

            List<Vector> spawnVectors = parseSpawnVectors(config);

            MapTemplateMetadata metadata = MapTemplateMetadata.builder()
                    .id(id)
                    .category(category)
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
            logger.info("[RGA] Registered template descriptor: '" + id + "' [" + category + "] (" + icon + ")");

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
