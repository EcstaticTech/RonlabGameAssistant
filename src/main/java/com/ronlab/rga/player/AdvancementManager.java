package com.ronlab.rga.player;

import com.ronlab.rga.RGA;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.Collection;

public class AdvancementManager {

    private final RGA plugin;

    // Player UUID -> map of advancement key -> set of awarded criteria
    private final Map<UUID, Map<String, Set<String>>> savedAdvancements = new HashMap<>();

    public AdvancementManager(RGA plugin) {
        this.plugin = plugin;
    }

    /**
     * Saves all current advancements for a player then revokes them all.
     * Called when a player enters a minigame world.
     */
    public void saveAndRevoke(Player player) {
        Map<String, Set<String>> saved = new HashMap<>();

        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);

            Collection<String> awarded = progress.getAwardedCriteria();
            if (!awarded.isEmpty()) {
                // Save the awarded criteria for this advancement
                saved.put(advancement.getKey().toString(), new HashSet<>(awarded));

                // Revoke all awarded criteria
                for (String criterion : awarded) {
                    progress.revokeCriteria(criterion);
                }
            }
        }

        savedAdvancements.put(player.getUniqueId(), saved);
        plugin.getLogger().info("Saved and revoked " + saved.size()
                + " advancement(s) for " + player.getName());
    }

    /**
     * Restores previously saved advancements for a player.
     * Called when a player returns to Hub after a minigame.
     */
    public void restore(Player player) {
        Map<String, Set<String>> saved = savedAdvancements.remove(player.getUniqueId());
        if (saved == null || saved.isEmpty()) return;

        // First revoke any advancements earned during the minigame
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criterion);
            }
        }

        // Restore saved advancements
        iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            String key = advancement.getKey().toString();
            if (!saved.containsKey(key)) continue;

            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : saved.get(key)) {
                progress.awardCriteria(criterion);
            }
        }

        plugin.getLogger().info("Restored advancements for " + player.getName());
    }

    /**
     * Clears saved advancement data for a player without restoring.
     * Used if a player disconnects mid-game.
     */
    public void clearSaved(UUID uuid) {
        savedAdvancements.remove(uuid);
    }

    public boolean hasSaved(UUID uuid) {
        return savedAdvancements.containsKey(uuid);
    }
}
