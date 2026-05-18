package com.ronlab.rga.minigame;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public class Minigame {

    public enum WorldType { VANILLA, TEMPLATE }

    private final String id;
    private final String name;
    private final Material displayItem;
    private final List<String> displayLore;
    private final int maxPlayers;
    private final int minPlayers;
    private final WorldType worldType;
    private final String templateWorld;
    private final List<String> startCommands;
    private final List<String> concludeCommands;

    // World settings applied when the game world is created
    private final GameMode gameMode;
    private final boolean pvp;
    private final Difficulty difficulty;
    private final Map<String, String> gamerules;

    public Minigame(String id, String name, Material displayItem, List<String> displayLore,
                    int maxPlayers, int minPlayers, WorldType worldType, String templateWorld,
                    List<String> startCommands, List<String> concludeCommands,
                    GameMode gameMode, boolean pvp,
                    Difficulty difficulty, Map<String, String> gamerules) {
        this.id = id;
        this.name = name;
        this.displayItem = displayItem;
        this.displayLore = displayLore;
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.worldType = worldType;
        this.templateWorld = templateWorld;
        this.startCommands = startCommands;
        this.concludeCommands = concludeCommands;
        this.gameMode = gameMode;
        this.pvp = pvp;
        this.difficulty = difficulty;
        this.gamerules = gamerules;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getDisplayItem() { return displayItem; }
    public List<String> getDisplayLore() { return displayLore; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public WorldType getWorldType() { return worldType; }
    public String getTemplateWorld() { return templateWorld; }
    public List<String> getStartCommands() { return startCommands; }
    public List<String> getConcludeCommands() { return concludeCommands; }
    public GameMode getGameMode() { return gameMode; }
    public boolean isPvp() { return pvp; }
    public Difficulty getDifficulty() { return difficulty; }
    public Map<String, String> getGamerules() { return gamerules; }
}
