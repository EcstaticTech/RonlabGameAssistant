package com.ronlab.rga.gui;

import org.bukkit.Material;

import java.util.List;

public class MenuItemDefinition {

    private final Material material;
    private final String name;
    private final List<String> lore;
    private final int slot;
    private final List<String> leftClick;
    private final List<String> rightClick;
    private final boolean showPlayerCount;
    private final String playerCountWorld;
    private final String minigameId;

    public MenuItemDefinition(Material material, String name, List<String> lore,
                               int slot, List<String> leftClick, List<String> rightClick,
                               boolean showPlayerCount, String playerCountWorld,
                               String minigameId) {
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.slot = slot;
        this.leftClick = leftClick;
        this.rightClick = rightClick;
        this.showPlayerCount = showPlayerCount;
        this.playerCountWorld = playerCountWorld;
        this.minigameId = minigameId;
    }

    public Material getMaterial() { return material; }
    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public int getSlot() { return slot; }
    public List<String> getLeftClick() { return leftClick; }
    public List<String> getRightClick() { return rightClick; }
    public boolean isShowPlayerCount() { return showPlayerCount; }
    public String getPlayerCountWorld() { return playerCountWorld; }
    public String getMinigameId() { return minigameId; }
}
