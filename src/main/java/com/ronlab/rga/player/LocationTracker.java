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

    private final Map<UUID, Location> lastSmpLocations = new HashMap<>();

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

    public void saveLocation(Player player, Location location) {
        lastSmpLocations.put(player.getUniqueId(), location.clone());
        writeLocationToDisk(player.getUniqueId(), location);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player-data.yml: " + e.getMessage());
        }
    }

    public boolean hasLocation(Player player) {
        if (lastSmpLocations.containsKey(player.getUniqueId())) return true;
        return dataConfig.contains(player.getUniqueId().toString());
    }

    public Location getLocation(Player player) {
        if (lastSmpLocations.containsKey(player.getUniqueId())) {
            return lastSmpLocations.get(player.getUniqueId());
        }
        return loadLocationFromDisk(player.getUniqueId());
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

        // No saved location — fall back to first SMP world spawn
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
        player.teleport(fallback.getSpawnLocation());

        var settings = plugin.getWorldManager().getSettings(fallbackWorldName);
        if (settings != null) player.setGameMode(settings.getGamemode());
    }

    // ── Persistence ──────────────────────────────────────────────

    public void saveAll() {
        for (Map.Entry<UUID, Location> entry : lastSmpLocations.entrySet()) {
            writeLocationToDisk(entry.getKey(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player-data.yml: " + e.getMessage());
        }
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
    }

    private void writeLocationToDisk(UUID uuid, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String path = uuid.toString();
        dataConfig.set(path + ".world", loc.getWorld().getName());
        dataConfig.set(path + ".x", loc.getX());
        dataConfig.set(path + ".y", loc.getY());
        dataConfig.set(path + ".z", loc.getZ());
        dataConfig.set(path + ".yaw", loc.getYaw());
        dataConfig.set(path + ".pitch", loc.getPitch());
    }

    private Location loadLocationFromDisk(UUID uuid) {
        String path = uuid.toString();
        if (!dataConfig.contains(path)) return null;

        String worldName = dataConfig.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = dataConfig.getDouble(path + ".x");
        double y = dataConfig.getDouble(path + ".y");
        double z = dataConfig.getDouble(path + ".z");
        float yaw = (float) dataConfig.getDouble(path + ".yaw");
        float pitch = (float) dataConfig.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}
