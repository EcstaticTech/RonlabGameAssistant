package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class MinigameManager {

    private final RGA plugin;
    private final Map<String, Minigame> minigames = new LinkedHashMap<>();

    public MinigameManager(RGA plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        minigames.clear();

        File file = new File(plugin.getDataFolder(), "minigames.yml");
        if (!file.exists()) plugin.saveResource("minigames.yml", false);

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("minigames");
        if (section == null) {
            plugin.getLogger().warning("No minigames section found in minigames.yml!");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection mg = section.getConfigurationSection(id);
            if (mg == null) continue;

            String name = mg.getString("name", id);

            String materialName = mg.getString("display-item", "STONE").toUpperCase();
            Material material = Material.matchMaterial(materialName);
            if (material == null) material = Material.STONE;

            List<String> rawLore = mg.getStringList("display-lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            int maxPlayers = mg.getInt("max-players", 8);
            int minPlayers = mg.getInt("min-players", 2);

            String worldTypeStr = mg.getString("world-type", "VANILLA").toUpperCase();
            Minigame.WorldType worldType;
            try {
                worldType = Minigame.WorldType.valueOf(worldTypeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid world-type '" + worldTypeStr
                        + "' for minigame " + id + ". Defaulting to VANILLA.");
                worldType = Minigame.WorldType.VANILLA;
            }

            String templateWorld = mg.getString("template-world", null);
            List<String> startCommands = mg.getStringList("start-commands");
            List<String> concludeCommands = mg.getStringList("conclude-commands");

            // ── World settings ────────────────────────────────────
            ConfigurationSection ws = mg.getConfigurationSection("world-settings");

            GameMode gameMode = GameMode.SURVIVAL;
            boolean pvp = true;
            Difficulty difficulty = Difficulty.NORMAL;
            Map<String, String> gamerules = new LinkedHashMap<>();

            if (ws != null) {
                String gmStr = ws.getString("gamemode", "SURVIVAL").toUpperCase();
                try { gameMode = GameMode.valueOf(gmStr); }
                catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid gamemode '" + gmStr
                            + "' in world-settings for " + id + ". Defaulting to SURVIVAL.");
                }

                pvp = ws.getBoolean("pvp", true);

                String diffStr = ws.getString("difficulty", "NORMAL").toUpperCase();
                try { difficulty = Difficulty.valueOf(diffStr); }
                catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid difficulty '" + diffStr
                            + "' in world-settings for " + id + ". Defaulting to NORMAL.");
                }

                ConfigurationSection grSection = ws.getConfigurationSection("gamerules");
                if (grSection != null) {
                    for (String rule : grSection.getKeys(false)) {
                        gamerules.put(rule, grSection.getString(rule, ""));
                    }
                }
            }

            minigames.put(id, new Minigame(id, name, material, lore,
                    maxPlayers, minPlayers, worldType, templateWorld,
                    startCommands, concludeCommands, gameMode, pvp, difficulty, gamerules));
        }

        plugin.getLogger().info("Loaded " + minigames.size() + " minigame(s) from minigames.yml.");
    }

    public Minigame getMinigame(String id) { return minigames.get(id); }
    public Map<String, Minigame> getAllMinigames() { return Collections.unmodifiableMap(minigames); }
}
