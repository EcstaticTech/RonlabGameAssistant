package com.ronlab.rga.config;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.AdventureUtil;
import com.ronlab.rga.util.ConsoleCommandAllowlist;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class ConfigManager {

    private final RGA plugin;

    private FileConfiguration worldsConfig;
    private FileConfiguration menusConfig;

    public ConfigManager(RGA plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        worldsConfig = loadConfig("worlds.yml");
        menusConfig = loadConfig("menus.yml");
    }

    private FileConfiguration loadConfig(String name) {
        File file = new File(plugin.getDataFolder(), name);
        plugin.saveResourceIfNotExists(name);
        return YamlConfiguration.loadConfiguration(file);
    }

    // ── Convenience getters ──────────────────────────────────────

    public String getHubWorld() {
        String hub = plugin.getConfig().getString("hub-world", "hub");
        if (hub == null || hub.isBlank()) {
            plugin.getLogger().warning("[RGA] No hub-world configured. Falling back to 'hub'. "
                    + "Verify this world exists on your server or set hub-world in config.yml.");
            return "hub";
        }
        return hub;
    }

    public boolean isClearInventoryOnHubEntry() {
        return plugin.getConfig().getBoolean("hub-entry.clear-inventory-on-entry", true);
    }

    public boolean isRestoreInventoryOnHubReturn() {
        return plugin.getConfig().getBoolean("hub-entry.restore-inventory-on-return", false);
    }

    public boolean isGiveCompassOnJoin() {
        return plugin.getConfig().getBoolean("compass.give-on-join", true);
    }

    public boolean isGiveSocialOnJoin() {
        return plugin.getConfig().getBoolean("social-item.give-on-join", true);
    }

    public boolean isPartyGracePeriodEnabled() {
        return plugin.getConfig().getBoolean("minigames.grace-period.enabled", true);
    }

    public int getPartyGracePeriodDurationSeconds() {
        return plugin.getConfig().getInt("minigames.grace-period.duration-seconds", 60);
    }

    public boolean isPartyGracePeriodAllowedInGame() {
        return plugin.getConfig().getBoolean("minigames.grace-period.allow-in-game", true);
    }


    public List<String> getSmpWorlds() {
        return plugin.getConfig().getStringList("smp-worlds");
    }

    public List<String> getConsoleCommandAllowlist() {
        return plugin.getConfig().getStringList("console-command-allowlist");
    }

    public boolean isConsoleCommandAllowed(String command) {
        return ConsoleCommandAllowlist.isAllowed(getConsoleCommandAllowlist(), command);
    }

    public boolean isStrictWorldNameValidation() {
        return plugin.getConfig().getBoolean("strict-world-name-validation", false);
    }


    public Component getMessage(String key) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null) {
            plugin.getLogger().info(String.format("[RGA] Message key '%s' not found in config. Returning raw key string.", key));
            return AdventureUtil.color(key);
        }
        String resolved = raw.replace("{world}", "");
        return AdventureUtil.color(resolved);
    }

    public Component getMessage(String key, String worldName) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null) {
            plugin.getLogger().info(String.format("[RGA] Message key '%s' not found in config. Returning raw key string.", key));
            return AdventureUtil.color(key);
        }
        String resolved = raw.replace("{world}", worldName != null ? worldName : "");
        return AdventureUtil.color(resolved);
    }

    public FileConfiguration getWorldsConfig() { return worldsConfig; }
    public FileConfiguration getMenusConfig() { return menusConfig; }
}
