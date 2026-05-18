package com.ronlab.rga.player;

import com.ronlab.rga.RGA;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Iterator;

public class AdvancementManager {

    private final RGA plugin;

    public AdvancementManager(RGA plugin) {
        this.plugin = plugin;
    }

    /**
     * Revokes all advancements for a player.
     * Called when a player enters a minigame world.
     */
    public void revokeAll(Player player) {
        Iterator<Advancement> iterator = plugin.getServer().advancementIterator();
        int count = 0;
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            Collection<String> awarded = progress.getAwardedCriteria();
            if (!awarded.isEmpty()) {
                for (String criterion : awarded) {
                    progress.revokeCriteria(criterion);
                }
                count++;
            }
        }
        plugin.getLogger().info("Revoked " + count + " advancement(s) for " + player.getName());
    }
}
