package com.ronlab.rga.gui;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Dynamic paginated menu for rendering registered map template metadata inside a 54-slot inventory.
 */
public class PaginatedMapMenu {

    public static final int PAGE_SIZE = 21;
    public static final int INVENTORY_SIZE = 54;

    // 21 Inner grid slots: 10-16, 19-25, 28-34
    public static final int[] INNER_GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    // Border slots to inject stained glass panes
    public static final int[] BORDER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44,
            45, 46, 47, 51, 52, 53
    };

    public static final int PREV_PAGE_SLOT = 48;
    public static final int BACK_BUTTON_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 50;

    private final RGA plugin;

    public PaginatedMapMenu(RGA plugin) {
        this.plugin = plugin;
    }

    public record ParsedTitle(String category, int page) {}

    public ParsedTitle parseTitle(String title) {
        if (title == null || !title.contains("(Page ")) return null;
        try {
            int pageStart = title.indexOf("(Page ");
            int pageSlash = title.indexOf('/', pageStart);
            if (pageSlash == -1) return null;

            String pageNumStr = title.substring(pageStart + 6, pageSlash).trim();
            int page = Integer.parseInt(pageNumStr) - 1;

            String catPart = title.substring(0, pageStart).replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
            String category = "minigames";
            if (catPart.toLowerCase(Locale.ROOT).contains("parkour")) {
                category = "parkour";
            } else if (catPart.toLowerCase(Locale.ROOT).contains("minigame")) {
                category = "minigames";
            } else {
                category = catPart.toLowerCase(Locale.ROOT).replace("maps", "").trim();
            }
            return new ParsedTitle(category, page);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Opens dynamic paginated menu for player filtered by category.
     */
    public void openMenu(Player player, String category, int page) {
        List<MapTemplateMetadata> templates = plugin.getTemplateDiscoveryService().getTemplatesByCategory(category);
        int totalItems = templates.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        int currentPage = Math.max(0, Math.min(page, maxPages - 1));

        String categoryTitle = (category == null || category.isBlank() || category.equalsIgnoreCase("all"))
                ? "Minigames & Maps"
                : category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1).toLowerCase(Locale.ROOT) + " Maps";

        Component title = AdventureUtil.color("&8&l" + categoryTitle + " &7(Page " + (currentPage + 1) + "/" + maxPages + ")");
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, title);

        // 1. Auto-border injection (pattern: BORDER) using GRAY_STAINED_GLASS_PANE
        ItemStack borderItem = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int slot : BORDER_SLOTS) {
            inv.setItem(slot, borderItem);
        }

        // 2. Map registered MapTemplateMetadata into inner grid
        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            MapTemplateMetadata meta = templates.get(i);
            int gridIndex = i - startIndex;
            int targetSlot = INNER_GRID_SLOTS[gridIndex];

            ItemStack stack = createTemplateItem(meta);
            inv.setItem(targetSlot, stack);
        }

        // 3. Navigation controls on bottom row
        if (currentPage > 0) {
            ItemStack prevItem = createItem(Material.ARROW, "&e&l<- Previous Page",
                    List.of("&7Click to view page " + currentPage));
            inv.setItem(PREV_PAGE_SLOT, prevItem);
        } else {
            inv.setItem(PREV_PAGE_SLOT, borderItem);
        }

        ItemStack backItem = createItem(Material.BARRIER, "&c&lBack to Navigator",
                List.of("&7Return to the main menu."));
        inv.setItem(BACK_BUTTON_SLOT, backItem);

        if (currentPage < maxPages - 1) {
            ItemStack nextItem = createItem(Material.ARROW, "&e&lNext Page ->",
                    List.of("&7Click to view page " + (currentPage + 2)));
            inv.setItem(NEXT_PAGE_SLOT, nextItem);
        } else {
            inv.setItem(NEXT_PAGE_SLOT, borderItem);
        }

        player.openInventory(inv);
    }

    private ItemStack createTemplateItem(MapTemplateMetadata meta) {
        ItemStack stack = new ItemStack(meta.icon());
        ItemMeta itemMeta = stack.getItemMeta();

        itemMeta.displayName(meta.displayName());

        List<Component> lore = new ArrayList<>();
        if (!meta.lore().isEmpty()) {
            lore.addAll(meta.lore());
            lore.add(Component.empty());
        }

        lore.add(AdventureUtil.color("&7Category: &f" + meta.category()));
        lore.add(AdventureUtil.color("&7Difficulty: &f" + meta.difficulty()));
        lore.add(AdventureUtil.color("&7Players: &f" + meta.minPlayers() + "-" + meta.maxPlayers()));
        lore.add(AdventureUtil.color("&8ID: " + meta.id()));

        Party party = plugin.getPartyManager().getPartyForMinigame(meta.id());
        if (party != null && party.getState() == Party.State.LOBBY) {
            lore.add(Component.empty());
            lore.add(Component.text(party.getMemberCount() + "/" + meta.maxPlayers() + " players in lobby", NamedTextColor.YELLOW));
        }

        lore.add(Component.empty());
        lore.add(AdventureUtil.color("&eClick to join / start!"));

        itemMeta.lore(lore);
        stack.setItemMeta(itemMeta);
        return stack;
    }

    private ItemStack createItem(Material material, String name, List<String> rawLore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(AdventureUtil.color(name));
        if (rawLore != null && !rawLore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(AdventureUtil.color(l));
            }
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
