package com.ronlab.rga.social;

import com.ronlab.rga.RGA;
import com.ronlab.rga.util.AdventureUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class SocialItem {

    private final RGA plugin;
    public static NamespacedKey SOCIAL_KEY;

    public SocialItem(RGA plugin) {
        this.plugin = plugin;
        SOCIAL_KEY = new NamespacedKey(plugin, "rga_social");
    }

    // ── Public API ───────────────────────────────────────────────

    public void giveSocialItem(Player player) {
        if (hasSocialItem(player)) return;

        ItemStack item = buildSocialItem(player);
        int slot = plugin.getConfig().getInt("social-item.slot", 7);

        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) {
            player.getInventory().setItem(slot, item);
        } else {
            boolean placed = false;
            for (int i = 0; i <= 8; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s == null || s.getType().isAir()) {
                    player.getInventory().setItem(i, item);
                    placed = true;
                    break;
                }
            }
            if (!placed) player.getInventory().addItem(item);
        }
    }

    public void removeSocialItem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isSocialItem(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public boolean hasSocialItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSocialItem(item)) return true;
        }
        return false;
    }

    public boolean isSocialItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(SOCIAL_KEY, PersistentDataType.BYTE);
    }

    // ── Item builder ─────────────────────────────────────────────

    public ItemStack buildSocialItem(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);

        String name = plugin.getConfig().getString("social-item.name", "&bSocial");
        meta.displayName(AdventureUtil.color(name));

        List<String> rawLore = plugin.getConfig().getStringList("social-item.lore");
        if (!rawLore.isEmpty()) {
            meta.lore(AdventureUtil.color(rawLore));
        }

        meta.getPersistentDataContainer().set(SOCIAL_KEY, PersistentDataType.BYTE, (byte) 1);
        head.setItemMeta(meta);
        return head;
    }
}
