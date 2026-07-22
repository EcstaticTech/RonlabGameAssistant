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
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ── Convenience getters ──────────────────────────────────────

    public String getHubWorld() {
        return plugin.getConfig().getString("hub-world", "Hub");
    }

    public boolean isClearInventoryOnHubEntry() {
        return plugin.getConfig().getBoolean("hub-entry.clear-inventory-on-entry", true);
    }

    public boolean isRestoreInventoryOnHubReturn() {
        return plugin.getConfig().getBoolean("hub-entry.restore-inventory-on-return", false);
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

    public Component getMessage(String key) {
        String raw = plugin.getConfig().getString("messages." + key, "&cMessage not found: " + key);
        String resolved = raw.replace("{world}", "");
        return AdventureUtil.color(resolved);
    }

    public Component getMessage(String key, String worldName) {
        String raw = plugin.getConfig().getString("messages." + key, "&cMessage not found: " + key);
        String resolved = raw.replace("{world}", worldName);
        return AdventureUtil.color(resolved);
    }

    public FileConfiguration getWorldsConfig() { return worldsConfig; }
    public FileConfiguration getMenusConfig() { return menusConfig; }
}
