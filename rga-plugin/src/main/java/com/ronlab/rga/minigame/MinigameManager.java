package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.AdventureUtil;
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
        plugin.saveResourceIfNotExists("minigames.yml");

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
            Material material = AdventureUtil.safeMaterial(materialName, Material.STONE);

            List<String> rawLore = mg.getStringList("display-lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(line);
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
            if (templateWorld != null) {
                var res = com.ronlab.rga.util.WorldNameValidator.validate(templateWorld, false);
                if (!res.isValid()) {
                    plugin.getLogger().severe(String.format(
                        "[RGA] SECURITY ERROR: Minigame '%s' template-world '%s': %s",
                        id, templateWorld, res.getErrorMessage()
                    ));
                    continue;
                }
                if (res.hasWarnings()) {
                    for (String warning : res.getWarnings()) {
                        plugin.getLogger().warning(String.format(
                            "[RGA] WARNING: Minigame '%s' template-world '%s': %s",
                            id, templateWorld, warning
                        ));
                    }
                }
            }

            for (String cmdKey : List.of("start-commands", "conclude-commands")) {
                if (!mg.isSet(cmdKey)) {
                    plugin.getLogger().warning(String.format(
                        "[RGA] Minigame '%s' has no '%s' configured. Verify setup if integrating with companion plugins.",
                        id, cmdKey
                    ));
                }
            }
            List<String> startCommands = mg.getStringList("start-commands");
            List<String> concludeCommands = mg.getStringList("conclude-commands");


            // ── World settings ────────────────────────────────────
            ConfigurationSection ws = mg.getConfigurationSection("world-settings");

            GameMode gameMode = GameMode.SURVIVAL;
            boolean pvp = true;
            Difficulty difficulty = Difficulty.NORMAL;
            Map<String, String> gamerules = new LinkedHashMap<>();
            boolean disableNether = false;
            boolean disableEnd = false;

            if (mg.isSet("disable-nether")) {
                disableNether = mg.getBoolean("disable-nether");
            } else if (mg.isSet("allow-nether")) {
                disableNether = !mg.getBoolean("allow-nether");
            }
            if (ws != null) {
                if (ws.isSet("disable-nether")) {
                    disableNether = ws.getBoolean("disable-nether");
                } else if (ws.isSet("allow-nether")) {
                    disableNether = !ws.getBoolean("allow-nether");
                }
            }

            if (mg.isSet("disable-end")) {
                disableEnd = mg.getBoolean("disable-end");
            } else if (mg.isSet("allow-end")) {
                disableEnd = !mg.getBoolean("allow-end");
            }
            if (ws != null) {
                if (ws.isSet("disable-end")) {
                    disableEnd = ws.getBoolean("disable-end");
                } else if (ws.isSet("allow-end")) {
                    disableEnd = !ws.getBoolean("allow-end");
                }
            }

            if (ws != null) {
                gameMode = plugin.getWorldManager().parseGameMode(id, ws.get("gamemode"));
                pvp = ws.getBoolean("pvp", true);
                difficulty = plugin.getWorldManager().parseDifficulty(id, ws.get("difficulty"));

                ConfigurationSection grSection = ws.getConfigurationSection("gamerules");
                if (grSection != null) {
                    for (String rule : grSection.getKeys(false)) {
                        gamerules.put(rule, grSection.getString(rule, ""));
                    }
                }
            }

            boolean queueEnabled = mg.getBoolean("queue-enabled", true);
            boolean autoStart = mg.getBoolean("auto-start", true);
            boolean allowSpectators = mg.getBoolean("allow-spectators", true);
            int maxSpectators = mg.getInt("max-spectators", 4);

            minigames.put(id, new Minigame(id, name, material, lore,
                    maxPlayers, minPlayers, worldType, templateWorld,
                    startCommands, concludeCommands, gameMode, pvp, difficulty, gamerules,
                    disableNether, disableEnd, queueEnabled, autoStart,
                    allowSpectators, maxSpectators));
        }

        plugin.getLogger().info("Loaded " + minigames.size() + " minigame(s) from minigames.yml.");
    }

    public Minigame getMinigame(String id) { return minigames.get(id); }
    public Map<String, Minigame> getAllMinigames() { return Collections.unmodifiableMap(minigames); }
}
