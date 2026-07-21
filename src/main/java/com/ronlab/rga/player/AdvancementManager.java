package com.ronlab.rga.player;

import com.ronlab.rga.RGA;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.*;

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

    /**
     * Captures all awarded advancement criteria for a player.
     */
    public Map<String, List<String>> captureCompleted(Player player) {
        Map<String, List<String>> map = new HashMap<>();
        Iterator<Advancement> iterator = plugin.getServer().advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            Collection<String> awarded = progress.getAwardedCriteria();
            if (!awarded.isEmpty()) {
                map.put(advancement.getKey().toString(), new ArrayList<>(awarded));
            }
        }
        return map;
    }

    /**
     * Revokes all current advancements and restores the specified saved advancements
     * using a convergence loop to handle parent dependency ordering.
     */
    public void restoreCompleted(Player player, Map<String, List<String>> savedAdvancements) {
        revokeAll(player);
        if (savedAdvancements == null || savedAdvancements.isEmpty()) return;

        boolean progressMade = true;
        int maxPasses = savedAdvancements.size();
        int pass = 0;

        while (progressMade && pass < maxPasses) {
            progressMade = false;
            pass++;
            for (Map.Entry<String, List<String>> entry : savedAdvancements.entrySet()) {
                NamespacedKey key = NamespacedKey.fromString(entry.getKey());
                if (key == null) continue;
                Advancement advancement = plugin.getServer().getAdvancement(key);
                if (advancement == null) continue;

                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                for (String criterion : entry.getValue()) {
                    if (!progress.getAwardedCriteria().contains(criterion)) {
                        if (progress.awardCriteria(criterion)) {
                            progressMade = true;
                        }
                    }
                }
            }
        }
        plugin.getLogger().info("Restored advancements for " + player.getName() + " in " + pass + " pass(es).");
    }
}
