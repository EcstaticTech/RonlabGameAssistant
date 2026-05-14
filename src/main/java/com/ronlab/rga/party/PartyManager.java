package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.WorldCopyManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.stream.Collectors;

public class PartyManager implements Listener {

    private final RGA plugin;
    private final WorldCopyManager worldCopyManager;

    private final Map<String, Party> activeParties = new HashMap<>();
    private final Map<UUID, Party> playerParties = new HashMap<>();

    // Players whose game has concluded but may still need to respawn
    private final Set<UUID> concludedPlayers = new HashSet<>();

    public PartyManager(RGA plugin) {
        this.plugin = plugin;
        this.worldCopyManager = new WorldCopyManager(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Disconnect handling ──────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Party party = playerParties.get(player.getUniqueId());
        if (party == null) return;

        if (party.getState() == Party.State.IN_GAME) {
            playerParties.remove(player.getUniqueId());
            party.removeMember(player.getUniqueId());
            if (party.getMemberCount() == 0 && party.getActiveWorldName() != null) {
                concludeGame(party.getActiveWorldName());
            }
            return;
        }

        if (player.getUniqueId().equals(party.getLeaderUuid())) {
            transferLeader(party, player.getUniqueId());
        }

        party.removeMember(player.getUniqueId());
        playerParties.remove(player.getUniqueId());

        if (party.getMemberCount() == 0) {
            activeParties.remove(party.getMinigameId());
            return;
        }

        broadcastToParty(party, "§e" + player.getName() + " §cdisconnected from the party. §7("
                + party.getMemberCount() + "/" + party.getMinigame().getMaxPlayers() + ")", null);
        refreshLobbyForAll(party);
    }

    // ── Join / Leave ─────────────────────────────────────────────

    public void joinMinigame(Player player, String minigameId) {
        Minigame minigame = plugin.getMinigameManager().getMinigame(minigameId);
        if (minigame == null) {
            player.sendMessage("§cUnknown minigame: " + minigameId);
            return;
        }

        // Already in a party for this minigame — just reopen lobby
        Party existingParty = playerParties.get(player.getUniqueId());
        if (existingParty != null && existingParty.getMinigameId().equals(minigameId)) {
            plugin.getLobbyGui().openLobby(player, existingParty);
            return;
        }

        // In a different party — leave first
        if (existingParty != null) leaveParty(player);

        Party party = activeParties.get(minigameId);

        if (party == null || party.getState() == Party.State.IN_GAME) {
            party = new Party(player.getUniqueId(), minigame);
            activeParties.put(minigameId, party);
            playerParties.put(player.getUniqueId(), party);
            player.sendMessage("§aCreated a new party for §6" + minigame.getName() + "§a!");
        } else if (party.isFull()) {
            player.sendMessage("§cThe party for §6" + minigame.getName() + "§c is full!");
            return;
        } else {
            party.addMember(player.getUniqueId());
            playerParties.put(player.getUniqueId(), party);
            player.sendMessage("§aJoined the party for §6" + minigame.getName() + "§a!");
            broadcastToParty(party, "§e" + player.getName() + " §ajoined the party! §7("
                    + party.getMemberCount() + "/" + minigame.getMaxPlayers() + ")",
                    player.getUniqueId());
        }

        refreshLobbyForAll(party);
    }

    public void leaveParty(Player player) {
        Party party = playerParties.remove(player.getUniqueId());
        if (party == null) return;

        boolean wasLeader = player.getUniqueId().equals(party.getLeaderUuid());
        if (wasLeader && party.getMemberCount() > 1) {
            transferLeader(party, player.getUniqueId());
        }

        party.removeMember(player.getUniqueId());
        player.sendMessage("§eYou left the party.");
        player.closeInventory();

        if (party.getMemberCount() == 0) {
            activeParties.remove(party.getMinigameId());
            return;
        }

        broadcastToParty(party, "§e" + player.getName() + " §cleft the party. §7("
                + party.getMemberCount() + "/" + party.getMinigame().getMaxPlayers() + ")", null);
        refreshLobbyForAll(party);
    }

    public void toggleReady(Player player) {
        Party party = playerParties.get(player.getUniqueId());
        if (party == null) return;
        if (party.getState() == Party.State.IN_GAME) return;

        boolean nowReady = !party.isReady(player.getUniqueId());
        party.setReady(player.getUniqueId(), nowReady);

        player.sendMessage(nowReady ? "§aYou are now ready!" : "§eYou are no longer ready.");
        broadcastToParty(party, "§e" + player.getName()
                + (nowReady ? " §ais ready!" : " §eis no longer ready."), null);

        refreshLobbyForAll(party);

        if (party.allReady()) startGame(party);
    }

    // ── Leader transfer ──────────────────────────────────────────

    private void transferLeader(Party party, UUID currentLeaderUuid) {
        for (UUID uuid : party.getMembers()) {
            if (!uuid.equals(currentLeaderUuid)) {
                party.setLeader(uuid);
                Player newLeader = Bukkit.getPlayer(uuid);
                String newLeaderName = newLeader != null ? newLeader.getName() : "Unknown";
                broadcastToParty(party,
                        "§6" + newLeaderName + " §eis now the party leader.", null);
                return;
            }
        }
    }

    // ── Game Start ───────────────────────────────────────────────

    private void startGame(Party party) {
        party.setState(Party.State.IN_GAME);
        Minigame minigame = party.getMinigame();

        broadcastToParty(party, "§a§lAll players ready! Starting §6§l"
                + minigame.getName() + "§a§l...", null);

        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.closeInventory();
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String worldName = null;

            if (minigame.getWorldType() == Minigame.WorldType.VANILLA) {
                worldName = worldCopyManager.createVanillaWorld(minigame);
            } else {
                worldName = worldCopyManager.copyTemplateWorld(minigame);
            }

            if (worldName == null) {
                broadcastToParty(party, "§cFailed to create game world. Please try again.", null);
                party.setState(Party.State.LOBBY);
                refreshLobbyForAll(party);
                return;
            }

            party.setActiveWorldName(worldName);

            if (minigame.getWorldType() == Minigame.WorldType.VANILLA) {
                // Register all three dimensions as a shared inventory group
                plugin.getInventoryManager().addTemporaryGroup(worldName,
                        List.of(worldName, worldName + "_the_nether", worldName + "_the_end"));
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                broadcastToParty(party, "§cGame world failed to load. Please try again.", null);
                party.setState(Party.State.LOBBY);
                return;
            }

            // Teleport all players
            List<String> playerNames = new ArrayList<>();
            Player leaderPlayer = Bukkit.getPlayer(party.getLeaderUuid());
            String leaderName = leaderPlayer != null ? leaderPlayer.getName() : "";

            for (UUID uuid : party.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.teleport(world.getSpawnLocation());
                    p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    p.sendMessage("§aThe game has started! Good luck!");
                    playerNames.add(p.getName());
                }
            }

            // Build %players% placeholder value
            String allPlayers = String.join(",", playerNames);

            // Execute start commands
            if (!minigame.getStartCommands().isEmpty()) {
                executeStartCommands(minigame.getStartCommands(), worldName,
                        leaderName, allPlayers, playerNames, leaderPlayer);
            }

        }, 5L);
    }

    private void executeStartCommands(List<String> commands, String worldName,
                                      String leaderName, String allPlayers,
                                      List<String> playerNames, Player leaderPlayer) {
        // %second% is the first non-leader player in join order
        String secondName = playerNames.size() >= 2 ? playerNames.get(1) : "";

        for (String command : commands) {
            if (command.startsWith("player-each:")) {
                // Run once per player as console, %player% = each player's name
                String cmd = command.substring("player-each:".length()).trim();
                for (String playerName : playerNames) {
                    String resolved = resolveCommand(cmd, worldName, leaderName,
                            allPlayers, playerName, secondName);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
            } else if (command.startsWith("leader:")) {
                // Run as the leader player — opens GUIs and player-only commands
                String cmd = command.substring("leader:".length()).trim();
                String resolved = resolveCommand(cmd, worldName, leaderName,
                        allPlayers, leaderName, secondName);
                if (leaderPlayer != null) {
                    leaderPlayer.performCommand(resolved);
                } else {
                    // Fallback to console if leader is offline
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
            } else {
                // Default — run as console once
                String cmd = command.startsWith("console:")
                        ? command.substring("console:".length()).trim()
                        : command.trim();
                String resolved = resolveCommand(cmd, worldName, leaderName, allPlayers, "", secondName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            }
        }
    }

    private String resolveCommand(String command, String worldName,
                                   String leaderName, String allPlayers,
                                   String playerName, String secondName) {
        return command
                .replace("%world%", worldName)
                .replace("%leader%", leaderName)
                .replace("%players%", allPlayers)
                .replace("%second%", secondName)
                .replace("%player%", playerName);
    }

    // ── Game End ─────────────────────────────────────────────────

    public void concludeGame(String worldName) {
        // Resolve to base world name in case a dimension suffix was passed
        // e.g. minigame_tag_abc_the_nether -> minigame_tag_abc
        String baseName = worldName;
        if (baseName.endsWith("_the_nether")) {
            baseName = baseName.substring(0, baseName.length() - "_the_nether".length());
        } else if (baseName.endsWith("_the_end")) {
            baseName = baseName.substring(0, baseName.length() - "_the_end".length());
        }

        Party party = null;
        for (Party p : activeParties.values()) {
            if (baseName.equals(p.getActiveWorldName())) {
                party = p;
                break;
            }
        }

        if (party == null) {
            plugin.getLogger().warning("No party found for world: " + worldName);
            return;
        }

        // Use the resolved base name going forward
        worldName = baseName;

        Minigame minigame = party.getMinigame();
        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());

        // Remove inventory groups immediately so dead players respawn with Hub inventory
        if (minigame.getWorldType() == Minigame.WorldType.VANILLA) {
            plugin.getInventoryManager().removeTemporaryGroup(worldName);
            plugin.getInventoryManager().removeTemporaryGroup(worldName + "_the_nether");
            plugin.getInventoryManager().removeTemporaryGroup(worldName + "_the_end");
        }

        // Mark players as concluded so respawn handler can route them to Hub
        for (UUID uuid : party.getMembers()) {
            concludedPlayers.add(uuid);
        }

        // Teleport alive players to Hub immediately
        // Dead players will be routed to Hub via PlayerRespawnEvent
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && !p.isDead()) {
                if (hub != null) p.teleport(hub.getSpawnLocation());
                p.sendMessage("§6The game has ended! You have been returned to Hub.");
            } else if (p != null) {
                p.sendMessage("§6The game has ended! You will be returned to Hub on respawn.");
            }
        }

        for (UUID uuid : party.getMembers()) {
            playerParties.remove(uuid);
        }
        activeParties.remove(party.getMinigameId());

        // Capture member list before lambda since party reference is not final
        List<UUID> finalMembers = new ArrayList<>(party.getMembers());
        String finalWorldName = worldName;
        boolean isVanilla = minigame.getWorldType() == Minigame.WorldType.VANILLA;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Clear concluded player flags
            for (UUID uuid : finalMembers) {
                concludedPlayers.remove(uuid);
            }
            worldCopyManager.cleanupWorld(finalWorldName, isVanilla);
        }, 300L);

        plugin.getLogger().info("Concluded minigame '" + minigame.getName()
                + "' in world '" + worldName + "'.");
    }

    // ── Helpers ──────────────────────────────────────────────────

    public void refreshLobbyForAll(Party party) {
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) plugin.getLobbyGui().openLobby(p, party);
        }
    }

    private void broadcastToParty(Party party, String message, UUID exclude) {
        for (UUID uuid : party.getMembers()) {
            if (uuid.equals(exclude)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    public Party getPartyForPlayer(UUID uuid) { return playerParties.get(uuid); }
    public Party getPartyForMinigame(String minigameId) { return activeParties.get(minigameId); }
    public Map<String, Party> getActiveParties() { return Collections.unmodifiableMap(activeParties); }
    public boolean isConcluded(UUID uuid) { return concludedPlayers.contains(uuid); }
}
