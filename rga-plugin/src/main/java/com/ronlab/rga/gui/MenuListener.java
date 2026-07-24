package com.ronlab.rga.gui;

import com.ronlab.rga.RGA;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

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
        if (!menuManager.isRGAMenu(title)) return;

        // Always cancel clicks in RGA menus to prevent item theft
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().isAir()) return;

        MenuDefinition menu = menuManager.getMenuByTitle(title);
        if (menu == null) return;

        int slot = event.getSlot();

        // Find the item definition at this slot
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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (menuManager.isRGAMenu(title)) {
            event.setCancelled(true);
        }
    }
}
