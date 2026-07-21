package com.ronlab.rga.compass;

import com.ronlab.rga.RGA;
import com.ronlab.rga.gui.MenuManager;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CompassListener implements Listener {

    private final RGA plugin;
    private final MenuManager menuManager;
    private final HubListener hubListener;

    public CompassListener(RGA plugin, MenuManager menuManager, HubListener hubListener) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.hubListener = hubListener;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only fire once — ignore the duplicate off-hand event Paper sends
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();

        // If they're holding an untagged compass in the hub, replace it with the proper one
        if (item.getType() == Material.COMPASS && !hubListener.isNavigatorCompass(item)) {
            String hubWorld = plugin.getConfigManager().getHubWorld();
            if (player.getWorld().getName().equalsIgnoreCase(hubWorld)) {
                player.getInventory().setItem(player.getInventory().getHeldItemSlot(), null);
                hubListener.giveCompass(player);
                player.sendMessage(Component.text("Your navigator compass has been updated.", NamedTextColor.GOLD));
            }
            return;
        }

        if (!hubListener.isNavigatorCompass(item)) return;

        event.setCancelled(true);
        menuManager.openMenu(player, "navigator");
    }
}
