package com.ronlab.rga.session;

import com.ronlab.rga.RGA;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final RGA plugin;
    private final File sessionsDir;
    private final Map<UUID, OrphanedMemberData> pendingRecoveries = new ConcurrentHashMap<>();
    private final Set<String> orphanedSessionWorlds = ConcurrentHashMap.newKeySet();

    public static class OrphanedMemberData {
        private final String worldName;
        private final String preGameGroup;
        private final String preGameWorld;
        private final Map<String, List<String>> savedAdvancements;

        public OrphanedMemberData(String worldName, String preGameGroup, String preGameWorld, Map<String, List<String>> savedAdvancements) {
            this.worldName = worldName;
            this.preGameGroup = preGameGroup;
            this.preGameWorld = preGameWorld;
            this.savedAdvancements = savedAdvancements;
        }

        public String getWorldName() { return worldName; }
        public String getPreGameGroup() { return preGameGroup; }
        public String getPreGameWorld() { return preGameWorld; }
        public Map<String, List<String>> getSavedAdvancements() { return savedAdvancements; }
    }

    public SessionManager(RGA plugin) {
        this.plugin = plugin;
        this.sessionsDir = new File(plugin.getDataFolder(), "sessions");
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs();
        }
    }

    public synchronized void saveSession(Party party, String worldName) {
        File sessionFile = new File(sessionsDir, worldName + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("world-name", worldName);
        config.set("minigame-id", party.getMinigameId());
        config.set("world-type", party.getMinigame().getWorldType().name());
        config.set("leader-uuid", party.getLeaderUuid().toString());
        config.set("started-at", System.currentTimeMillis());

        for (UUID memberUuid : party.getMembers()) {
            String path = "members." + memberUuid.toString();
            Player player = Bukkit.getPlayer(memberUuid);
            config.set(path + ".name", player != null ? player.getName() : "Unknown");
            config.set(path + ".pre-game-group", party.getPreGameGroup(memberUuid));
            config.set(path + ".pre-game-world", player != null ? player.getWorld().getName() : "");

            Map<String, List<String>> advs = party.getPreGameAdvancements(memberUuid);
            if (advs != null && !advs.isEmpty()) {
                ConfigurationSection advSection = config.createSection(path + ".advancements");
                for (Map.Entry<String, List<String>> entry : advs.entrySet()) {
                    advSection.set(entry.getKey(), entry.getValue());
                }
            }
        }

        try {
            config.save(sessionFile);
            plugin.getLogger().info("Saved write-ahead session snapshot: " + sessionFile.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save session snapshot for " + worldName + ": " + e.getMessage());
        }
    }

    public synchronized void deleteSession(String worldName) {
        File sessionFile = new File(sessionsDir, worldName + ".yml");
        if (sessionFile.exists()) {
            if (sessionFile.delete()) {
                plugin.getLogger().info("Deleted session file: " + sessionFile.getName());
            } else {
                plugin.getLogger().warning("Could not delete session file: " + sessionFile.getName());
            }
        }
        orphanedSessionWorlds.remove(worldName);
        pendingRecoveries.values().removeIf(data -> data.getWorldName().equalsIgnoreCase(worldName));
    }

    public synchronized void loadOrphanedSessions() {
        if (!sessionsDir.exists()) return;
        File[] files = sessionsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return;

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String worldName = config.getString("world-name");
            if (worldName == null) continue;

            orphanedSessionWorlds.add(worldName);
            ConfigurationSection membersSec = config.getConfigurationSection("members");
            int count = 0;
            if (membersSec != null) {
                for (String uuidStr : membersSec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String preGroup = membersSec.getString(uuidStr + ".pre-game-group", "smp");
                        String preWorld = membersSec.getString(uuidStr + ".pre-game-world", "");
                        Map<String, List<String>> advMap = new HashMap<>();
                        ConfigurationSection advSec = membersSec.getConfigurationSection(uuidStr + ".advancements");
                        if (advSec != null) {
                            for (String advKey : advSec.getKeys(false)) {
                                advMap.put(advKey, advSec.getStringList(advKey));
                            }
                        }
                        pendingRecoveries.put(uuid, new OrphanedMemberData(worldName, preGroup, preWorld, advMap));
                        count++;
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            plugin.getLogger().warning("Detected orphaned session '" + worldName + "' with " + count + " player(s) pending recovery.");
        }
    }

    public boolean isPendingRecovery(UUID playerUuid) {
        return pendingRecoveries.containsKey(playerUuid);
    }

    public synchronized void recoverPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        OrphanedMemberData data = pendingRecoveries.remove(uuid);
        if (data == null) return;

        plugin.getLogger().info("Executing crash recovery for " + player.getName() + " (Pre-game group: " + data.getPreGameGroup() + ")...");

        // 1. Restore Inventory
        if (data.getPreGameGroup() != null) {
            plugin.getInventoryManager().loadInventory(player, data.getPreGameGroup());
        }

        // 2. Restore Advancements (Revoke-First + Convergence loop)
        plugin.getAdvancementManager().restoreCompleted(player, data.getSavedAdvancements());

        // 3. Teleport to appropriate location
        // Use the captured pre-game world name to determine SMP status, not the group name
        // (group names are InventoryManager logical groups, world names are actual world names)
        boolean isSmpWorld = plugin.getConfigManager().getSmpWorlds().contains(data.getPreGameWorld());
        boolean isSmpGroup = !isSmpWorld && data.getPreGameGroup() != null
                ? plugin.getConfigManager().getSmpWorlds().stream()
                        .anyMatch(w -> plugin.getInventoryManager().getGroup(w).equals(data.getPreGameGroup()))
                : isSmpWorld;

        if (isSmpGroup && plugin.getLocationTracker().hasLocation(player)) {
            plugin.getLocationTracker().teleportToLastLocation(player);
        } else {
            World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
            if (hub != null) {
                player.teleport(hub.getSpawnLocation());
            }
        }

        player.sendMessage(Component.text("[RGA] Your pre-game state has been restored after server recovery.", NamedTextColor.GREEN));

        // Check if all members of this orphaned session are recovered
        boolean remaining = pendingRecoveries.values().stream()
                .anyMatch(d -> d.getWorldName().equalsIgnoreCase(data.getWorldName()));
        if (!remaining) {
            plugin.getLogger().info("All players recovered for session '" + data.getWorldName() + "'. Cleaning up session file.");
            deleteSession(data.getWorldName());
        }
    }

    public Set<String> getOrphanedSessionWorlds() {
        return Collections.unmodifiableSet(orphanedSessionWorlds);
    }
}
