package com.ronlab.rga.social;

import com.ronlab.rga.RGA;
import com.ronlab.rga.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class SocialListener implements Listener {

    private final RGA plugin;

    public SocialListener(RGA plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only fire once — ignore the duplicate off-hand event Paper sends
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (!plugin.getSocialItem().isSocialItem(item)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        Party party = plugin.getPartyManager().getPartyForPlayer(player.getUniqueId());

        if (party != null) {
            // Player is already in a party — open the existing lobby GUI
            plugin.getLobbyGui().openLobby(player, party);
        } else {
            // No party — open the browse screen
            plugin.getBrowsePartiesGui().open(player);
        }
    }
}
