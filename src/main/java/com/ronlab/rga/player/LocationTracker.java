package com.ronlab.rga.player;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LocationTracker implements Listener {

    private final RGA plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    /**
     * In-memory cache: UUID → (worldName → Location).
     * Replaced the old flat {@code lastSmpLocations} map to support per-world tracking.
     */
    private final Map<UUID, Map<String, Location>> worldLocations = new HashMap<>();

    public LocationTracker(RGA plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "player-data.yml");
        loadFromDisk();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Event Handlers ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();
        if (plugin.getConfigManager().getSmpWorlds().contains(currentWorld)) {
            saveLocation(player, player.getLocation());
        }
    }

    // ── Public API ───────────────────────────────────────────────

    /**
     * Saves the player's location, keyed by the world that location belongs to.
     */
    public void saveLocation(Player player, Location location) {
        if (location == null || location.getWorld() == null) return;
        String worldName = location.getWorld().getName();
        worldLocations
                .computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
                .put(worldName, location.clone());
        writeLocationToDisk(player.getUniqueId(), worldName, location);
        persistToDisk();
    }

    /**
     * Returns {@code true} if a tracked location exists for the player in <em>any</em> SMP world.
     * Triggers legacy migration when needed.
     */
    public boolean hasLocation(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Location> perWorld = worldLocations.get(uuid);
        if (perWorld != null && !perWorld.isEmpty()) return true;
        if (dataConfig.contains(uuid + ".worlds")) return true;
        // Legacy path — migrate if present
        if (dataConfig.contains(uuid + ".x")) {
            migrateLegacyEntry(uuid);
            return worldLocations.containsKey(uuid) && !worldLocations.get(uuid).isEmpty();
        }
        return false;
    }

    /**
     * Returns {@code true} if a tracked location exists for the player in the given world.
     * Triggers legacy migration when needed.
     */
    public boolean hasLocation(Player player, String worldName) {
        UUID uuid = player.getUniqueId();
        ensureMigrated(uuid);
        Map<String, Location> perWorld = worldLocations.get(uuid);
        if (perWorld != null && perWorld.containsKey(worldName)) return true;
        return dataConfig.contains(uuid + ".worlds." + worldName);
    }

    /**
     * Returns the tracked location for the player's first available SMP world, or {@code null}.
     * Triggers legacy migration when needed.
     */
    public Location getLocation(Player player) {
        UUID uuid = player.getUniqueId();
        ensureMigrated(uuid);
        Map<String, Location> perWorld = worldLocations.get(uuid);
        if (perWorld != null && !perWorld.isEmpty()) {
            return perWorld.values().iterator().next();
        }
        if (dataConfig.contains(uuid + ".worlds")) {
            var worldsSection = dataConfig.getConfigurationSection(uuid + ".worlds");
            if (worldsSection != null) {
                for (String wn : worldsSection.getKeys(false)) {
                    Location loc = loadLocationFromDisk(uuid, wn);
                    if (loc != null) return loc;
                }
            }
        }
        return null;
    }

    /**
     * Returns the tracked location for the player in the given world, or {@code null}.
     * Triggers legacy migration when needed.
     */
    public Location getLocation(Player player, String worldName) {
        UUID uuid = player.getUniqueId();
        ensureMigrated(uuid);
        Map<String, Location> perWorld = worldLocations.get(uuid);
        if (perWorld != null && perWorld.containsKey(worldName)) {
            return perWorld.get(worldName);
        }
        return loadLocationFromDisk(uuid, worldName);
    }

    public void teleportToLastLocation(Player player) {
        if (hasLocation(player)) {
            Location loc = getLocation(player);
            if (loc != null && loc.getWorld() != null) {
                World world = Bukkit.getWorld(loc.getWorld().getName());
                if (world != null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("teleporting"));
                    player.teleport(loc);
                    var settings = plugin.getWorldManager().getSettings(world.getName());
                    if (settings != null) player.setGameMode(settings.getGamemode());
                    return;
                }
            }
        }

        // No saved location — fall back to first SMP world, honouring first-visit-spawn
        List<String> smpWorlds = plugin.getConfigManager().getSmpWorlds();
        if (smpWorlds.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-smp-location"));
            return;
        }

        String fallbackWorldName = smpWorlds.get(0);
        World fallback = Bukkit.getWorld(fallbackWorldName);
        if (fallback == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("world-not-found", fallbackWorldName));
            return;
        }

        player.sendMessage(Component.text("No saved SMP location found. Sending you to the SMP world spawn.", NamedTextColor.GRAY));
        player.sendMessage(plugin.getConfigManager().getMessage("teleporting"));

        var settings = plugin.getWorldManager().getSettings(fallbackWorldName);
        Location spawnLoc = (settings != null) ? settings.getSpawnLocation(fallback) : fallback.getSpawnLocation();
        player.teleport(spawnLoc);
        if (settings != null) player.setGameMode(settings.getGamemode());
    }

    // ── Persistence ──────────────────────────────────────────────

    public void saveAll() {
        for (Map.Entry<UUID, Map<String, Location>> entry : worldLocations.entrySet()) {
            UUID uuid = entry.getKey();
            for (Map.Entry<String, Location> worldEntry : entry.getValue().entrySet()) {
                writeLocationToDisk(uuid, worldEntry.getKey(), worldEntry.getValue());
            }
        }
        persistToDisk();
    }

    private void loadFromDisk() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player-data.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Eagerly load all persisted per-world locations into memory
        for (String uuidStr : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                if (dataConfig.contains(uuidStr + ".worlds")) {
                    var worldsSection = dataConfig.getConfigurationSection(uuidStr + ".worlds");
                    if (worldsSection == null) continue;
                    for (String worldName : worldsSection.getKeys(false)) {
                        Location loc = loadLocationFromDisk(uuid, worldName);
                        if (loc != null) {
                            worldLocations.computeIfAbsent(uuid, id -> new HashMap<>()).put(worldName, loc);
                        }
                    }
                }
                // Legacy flat entries are left on disk and migrated lazily on first access
            } catch (IllegalArgumentException ignored) {
                // key is not a UUID — skip
            }
        }
    }

    private void writeLocationToDisk(UUID uuid, String worldName, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String path = uuid + ".worlds." + worldName;
        dataConfig.set(path + ".world", loc.getWorld().getName());
        dataConfig.set(path + ".x",     loc.getX());
        dataConfig.set(path + ".y",     loc.getY());
        dataConfig.set(path + ".z",     loc.getZ());
        dataConfig.set(path + ".yaw",   (double) loc.getYaw());
        dataConfig.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location loadLocationFromDisk(UUID uuid, String worldName) {
        String path = uuid + ".worlds." + worldName;
        if (!dataConfig.contains(path)) return null;

        String storedWorldName = dataConfig.getString(path + ".world", worldName);
        World world = Bukkit.getWorld(storedWorldName);
        if (world == null) return null;

        double x     = dataConfig.getDouble(path + ".x");
        double y     = dataConfig.getDouble(path + ".y");
        double z     = dataConfig.getDouble(path + ".z");
        float  yaw   = (float) dataConfig.getDouble(path + ".yaw");
        float  pitch = (float) dataConfig.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    private void persistToDisk() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player-data.yml: " + e.getMessage());
        }
    }

    // ── Legacy migration ─────────────────────────────────────────

    /**
     * Migrates a legacy top-level {@code <uuid>.world/.x/.y/.z/.yaw/.pitch} entry into the new
     * {@code <uuid>.worlds.<world>} structure. Called at most once per UUID per server session.
     *
     * <p>When the world is currently loaded the migrated location is also cached in memory.
     * When the world is unloaded the raw coordinates are still written to the new disk path so
     * they are available when the world comes back online, and are not wrongly treated as absent.
     */
    private void migrateLegacyEntry(UUID uuid) {
        String base = uuid.toString();
        if (!dataConfig.contains(base + ".x")) return; // nothing to migrate

        String worldName = dataConfig.getString(base + ".world");
        World world = (worldName != null) ? Bukkit.getWorld(worldName) : null;

        if (worldName != null && !worldName.isBlank()) {
            double x     = dataConfig.getDouble(base + ".x");
            double y     = dataConfig.getDouble(base + ".y");
            double z     = dataConfig.getDouble(base + ".z");
            float  yaw   = (float) dataConfig.getDouble(base + ".yaw");
            float  pitch = (float) dataConfig.getDouble(base + ".pitch");

            // Write into the new per-world path on disk (works even when world is unloaded)
            String path = uuid + ".worlds." + worldName;
            dataConfig.set(path + ".world",  worldName);
            dataConfig.set(path + ".x",      x);
            dataConfig.set(path + ".y",      y);
            dataConfig.set(path + ".z",      z);
            dataConfig.set(path + ".yaw",    (double) yaw);
            dataConfig.set(path + ".pitch",  (double) pitch);

            // Cache in memory only when the world is currently loaded
            if (world != null) {
                Location loc = new Location(world, x, y, z, yaw, pitch);
                worldLocations.computeIfAbsent(uuid, id -> new HashMap<>()).put(worldName, loc);
            }
        }

        // Remove legacy top-level keys
        dataConfig.set(base + ".world",  null);
        dataConfig.set(base + ".x",      null);
        dataConfig.set(base + ".y",      null);
        dataConfig.set(base + ".z",      null);
        dataConfig.set(base + ".yaw",    null);
        dataConfig.set(base + ".pitch",  null);
        persistToDisk();

        plugin.getLogger().info("Migrated legacy location data for player " + uuid + " \u2192 " + worldName);
    }

    /**
     * Triggers legacy migration for the given UUID if needed. No-op when already migrated
     * or when no legacy data exists.
     */
    private void ensureMigrated(UUID uuid) {
        if (!worldLocations.containsKey(uuid) && dataConfig.contains(uuid + ".x")) {
            migrateLegacyEntry(uuid);
        }
    }
}
