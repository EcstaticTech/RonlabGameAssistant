package com.ronlab.rga.gui;

import com.ronlab.rga.RGA;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MenuManager {

    private final RGA plugin;
    private final Map<String, MenuDefinition> menus = new HashMap<>();
    private final Map<String, String> titleToMenuName = new HashMap<>();

    public MenuManager(RGA plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        menus.clear();
        titleToMenuName.clear();

        ConfigurationSection menusSection = plugin.getConfigManager()
                .getMenusConfig().getConfigurationSection("menus");
        if (menusSection == null) {
            plugin.getLogger().warning("No menus section found in menus.yml!");
            return;
        }

        for (String menuName : menusSection.getKeys(false)) {
            ConfigurationSection menuSection = menusSection.getConfigurationSection(menuName);
            if (menuSection == null) continue;

            String title = menuSection.getString("title");
            if (title == null || title.isBlank()) {
                plugin.getLogger().warning(String.format("[RGA] Menu '%s' has no title configured, defaulting to 'Unnamed Menu'.", menuName));
                title = "Unnamed Menu";
            }
            int size = menuSection.getInt("size", 27);
            List<MenuItemDefinition> items = new ArrayList<>();

            ConfigurationSection itemsSection = menuSection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemKey : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    String materialName = itemSection.getString("material");
                    Material material = Material.STONE;
                    if (materialName == null || materialName.isBlank()) {
                        plugin.getLogger().warning(String.format("[RGA] Missing material for item '%s' in menu '%s', defaulting to STONE.", itemKey, menuName));
                    } else {
                        material = AdventureUtil.safeMaterial(materialName.toUpperCase(Locale.ROOT), Material.STONE);
                        if (material == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                            plugin.getLogger().warning(String.format("[RGA] Invalid material '%s' for item '%s' in menu '%s', defaulting to STONE.", materialName, itemKey, menuName));
                        }
                    }

                    String name = itemSection.getString("name", "&fItem");
                    List<String> rawLore = itemSection.getStringList("lore");

                    int slot = itemSection.getInt("slot", 0);
                    List<String> leftClick = itemSection.getStringList("left_click");
                    List<String> rightClick = itemSection.getStringList("right_click");
                    boolean showPlayerCount = itemSection.getBoolean("show-player-count", false);
                    String playerCountWorld = itemSection.getString("player-count-world", "");
                    String minigameId = itemSection.getString("minigame-id", "");

                    if (itemSection.contains("minigame-id") && minigameId.isBlank()) {
                        plugin.getLogger().warning(String.format("[RGA] Item '%s' in menu '%s' has empty minigame-id configured.", itemKey, menuName));
                    }

                    items.add(new MenuItemDefinition(material, name, rawLore, slot,
                            leftClick, rightClick, showPlayerCount, playerCountWorld, minigameId));
                }
            }

            MenuDefinition def = new MenuDefinition(menuName, title, size, items);
            menus.put(menuName, def);
            titleToMenuName.put(title, menuName);
        }

        plugin.getLogger().info("Loaded " + menus.size() + " menu(s) from menus.yml.");
    }

    public void openMenu(Player player, String menuName) {
        MenuDefinition def = menus.get(menuName);
        if (def == null) {
            plugin.getLogger().warning("Tried to open unknown menu: " + menuName);
            player.sendMessage(Component.text("Menu not found: " + menuName, NamedTextColor.RED));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, def.getSize(), def.getTitle());

        for (MenuItemDefinition item : def.getItems()) {
            ItemStack stack = new ItemStack(item.getMaterial());
            ItemMeta meta = stack.getItemMeta();

            // Display name
            meta.displayName(AdventureUtil.color(item.getName()));

            // Build lore components
            List<Component> lore = new ArrayList<>(AdventureUtil.color(item.getLore()));

            // Inject live player count for world-based items
            if (item.isShowPlayerCount() && !item.getPlayerCountWorld().isEmpty()) {
                int count = getPlayerCount(item.getPlayerCountWorld());
                String playerWord = count == 1 ? "player" : "players";
                lore.add(Component.empty());
                lore.add(Component.text(count + " " + playerWord + " online", NamedTextColor.YELLOW));
            }

            // Inject party size for minigame items
            if (!item.getMinigameId().isEmpty()) {
                Party party = plugin.getPartyManager().getPartyForMinigame(item.getMinigameId());
                var minigame = plugin.getMinigameManager().getMinigame(item.getMinigameId());
                if (party != null && party.getState() == Party.State.LOBBY) {
                    lore.add(Component.empty());
                    lore.add(
                            Component.text(party.getMemberCount() + "/", NamedTextColor.YELLOW)
                                    .append(Component.text(minigame != null ? String.valueOf(minigame.getMaxPlayers()) : "?",
                                            NamedTextColor.GRAY))
                                    .append(Component.text(" players in lobby", NamedTextColor.YELLOW))
                    );
                } else {
                    lore.add(Component.empty());
                    lore.add(Component.text("No open party", NamedTextColor.GRAY));
                }
            }

            if (!lore.isEmpty()) meta.lore(lore);
            stack.setItemMeta(meta);

            if (item.getSlot() >= 0 && item.getSlot() < def.getSize()) {
                inv.setItem(item.getSlot(), stack);
            }
        }

        player.openInventory(inv);
    }

    private int getPlayerCount(String worldName) {
        String[] worlds = worldName.split(",");
        int count = 0;
        for (String w : worlds) {
            World world = Bukkit.getWorld(w.trim());
            if (world != null) count += world.getPlayers().size();
        }
        return count;
    }

    public MenuDefinition getMenuByTitle(String title) {
        String menuName = titleToMenuName.get(title);
        if (menuName == null) return null;
        return menus.get(menuName);
    }

    public boolean isRGAMenu(String title) {
        return titleToMenuName.containsKey(title);
    }
}