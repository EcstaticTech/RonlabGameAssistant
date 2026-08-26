package com.ronlab.rga.gui;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MenuListener implements Listener {

    private final RGA plugin;
    private final MenuManager menuManager;
    private final ActionHandler actionHandler;

    public MenuListener(RGA plugin, MenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.actionHandler = new ActionHandler(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // 1. Check if title is a dynamic PaginatedMapMenu title
        PaginatedMapMenu.ParsedTitle parsedTitle = plugin.getPaginatedMapMenu().parseTitle(title);
        if (parsedTitle != null) {
            event.setCancelled(true);
            handlePaginatedMenuClick(player, event, parsedTitle);
            return;
        }

        // 2. Check if title is a static menus.yml menu
        if (!menuManager.isRGAMenu(title)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;

        MenuDefinition menu = menuManager.getMenuByTitle(title);
        if (menu == null) return;

        int slot = event.getSlot();

        for (MenuItemDefinition item : menu.getItems()) {
            if (item.getSlot() != slot) continue;

            boolean isLeft = event.isLeftClick();
            boolean isRight = event.isRightClick();

            if (isLeft && !item.getLeftClick().isEmpty()) {
                actionHandler.handle(player, item.getLeftClick());
            } else if (isRight && !item.getRightClick().isEmpty()) {
                actionHandler.handle(player, item.getRightClick());
            }
            break;
        }
    }

    private void handlePaginatedMenuClick(Player player, InventoryClickEvent event, PaginatedMapMenu.ParsedTitle parsed) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) return;

        int slot = event.getSlot();

        if (slot == PaginatedMapMenu.PREV_PAGE_SLOT && item.getType() == Material.ARROW) {
            plugin.getPaginatedMapMenu().openMenu(player, parsed.category(), parsed.page() - 1);
            return;
        }

        if (slot == PaginatedMapMenu.BACK_BUTTON_SLOT) {
            plugin.getCommandRouter().dispatchAction(player, "rga:open_menu navigator");
            return;
        }

        if (slot == PaginatedMapMenu.NEXT_PAGE_SLOT && item.getType() == Material.ARROW) {
            plugin.getPaginatedMapMenu().openMenu(player, parsed.category(), parsed.page() + 1);
            return;
        }

        // Inner grid click handling
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null) return;

        String templateId = null;
        for (net.kyori.adventure.text.Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("ID: ")) {
                templateId = plain.substring(4).trim();
                break;
            }
        }

        if (templateId != null) {
            MapTemplateMetadata template = plugin.getTemplateDiscoveryService().getTemplate(templateId);
            if (template == null) {
                template = plugin.getTemplateDiscoveryService().get(templateId);
            }
            if (template != null) {
                plugin.getCommandRouter().executeJoinMinigame(player, template.resolveEngineId(), template.id());
            } else {
                plugin.getCommandRouter().executeJoinMinigame(player, templateId);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (menuManager.isRGAMenu(title) || plugin.getPaginatedMapMenu().parseTitle(title) != null) {
            event.setCancelled(true);
        }
    }
}
