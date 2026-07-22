package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.WorldCopyManager;
import com.ronlab.rga.util.AdventureUtil;
import com.ronlab.rga.util.PlaceholderSanitizer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
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

        broadcastToParty(party, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" disconnected from the party. ", NamedTextColor.RED))
                .append(Component.text("(" + party.getMemberCount() + "/" + party.getMinigame().getMaxPlayers() + ")", NamedTextColor.GRAY))
                .build(), null);
        refreshLobbyForAll(party);
    }

    // ── Join / Leave ─────────────────────────────────────────────

    public void joinMinigame(Player player, String minigameId) {
        Minigame minigame = plugin.getMinigameManager().getMinigame(minigameId);
        if (minigame == null) {
            player.sendMessage(Component.text("Unknown minigame: " + minigameId, NamedTextColor.RED));
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
            player.sendMessage(Component.text()
                    .append(Component.text("Created a new party for ", NamedTextColor.GREEN))
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text("!", NamedTextColor.GREEN))
                    .build());
        } else if (party.isFull()) {
            player.sendMessage(Component.text()
                    .append(Component.text("The party for ", NamedTextColor.RED))
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" is full!", NamedTextColor.RED))
                    .build());
            return;
        } else {
            party.addMember(player.getUniqueId());
            playerParties.put(player.getUniqueId(), party);
            player.sendMessage(Component.text()
                    .append(Component.text("Joined the party for ", NamedTextColor.GREEN))
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text("!", NamedTextColor.GREEN))
                    .build());
            broadcastToParty(party, Component.text()
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" joined the party! ", NamedTextColor.GREEN))
                    .append(Component.text("(" + party.getMemberCount() + "/" + minigame.getMaxPlayers() + ")", NamedTextColor.GRAY))
                    .build(), player.getUniqueId());
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
        player.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW));
        player.closeInventory();

        if (party.getMemberCount() == 0) {
            activeParties.remove(party.getMinigameId());
            return;
        }

        broadcastToParty(party, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" left the party. ", NamedTextColor.RED))
                .append(Component.text("(" + party.getMemberCount() + "/" + party.getMinigame().getMaxPlayers() + ")", NamedTextColor.GRAY))
                .build(), null);
        refreshLobbyForAll(party);
    }

    public void toggleReady(Player player) {
        Party party = playerParties.get(player.getUniqueId());
        if (party == null) return;
        if (party.getState() == Party.State.IN_GAME) return;

        boolean nowReady = !party.isReady(player.getUniqueId());
        party.setReady(player.getUniqueId(), nowReady);

        player.sendMessage(nowReady
                ? Component.text("You are now ready!", NamedTextColor.GREEN)
                : Component.text("You are no longer ready.", NamedTextColor.YELLOW));
        broadcastToParty(party, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(nowReady
                        ? Component.text(" is ready!", NamedTextColor.GREEN)
                        : Component.text(" is no longer ready.", NamedTextColor.YELLOW))
                .build(), null);

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
                broadcastToParty(party, Component.text()
                        .append(Component.text(newLeaderName, NamedTextColor.GOLD))
                        .append(Component.text(" is now the party leader.", NamedTextColor.YELLOW))
                        .build(), null);
                return;
            }
        }
    }

    // ── Game Start ───────────────────────────────────────────────

    private void startGame(Party party) {
        Minigame minigame = party.getMinigame();

        broadcastToParty(party, Component.text()
                .append(Component.text("All players ready! Starting ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(minigame.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("...", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build(), null);

        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.closeInventory();
        }

        broadcastActionBarToParty(party, Component.text("Preparing arena world, please wait...", NamedTextColor.YELLOW));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (party.getMembers().isEmpty()) return;

            // Capture pre-game data for all online members before teleport/revoke
            for (UUID uuid : party.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    String group = plugin.getInventoryManager().getGroup(p.getWorld().getName());
                    party.setPreGameGroup(uuid, group);
                    party.setPreGameAdvancements(uuid, plugin.getAdvancementManager().captureCompleted(p));
                }
            }

            if (minigame.getWorldType() == Minigame.WorldType.VANILLA) {
                String worldName = worldCopyManager.createVanillaWorld(minigame);
                if (worldName == null) {
                    broadcastToParty(party, Component.text("Failed to create game world. Please try again.", NamedTextColor.RED), null);
                    party.setState(Party.State.LOBBY);
                    refreshLobbyForAll(party);
                    return;
                }

                party.setActiveWorldName(worldName);
                party.setState(Party.State.IN_GAME);

                // Write-ahead session snapshot right after world creation
                plugin.getSessionManager().saveSession(party, worldName);

                // Register all three dimensions as a shared inventory group
                plugin.getInventoryManager().addTemporaryGroup(worldName,
                        List.of(worldName, worldName + "_the_nether", worldName + "_the_end"));

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    broadcastToParty(party, Component.text("Game world failed to load. Please try again.", NamedTextColor.RED), null);
                    party.setState(Party.State.LOBBY);
                    return;
                }

                startCountdownAndLaunch(party, worldName, world, minigame, true);
            } else {
                worldCopyManager.copyTemplateWorld(minigame).thenAccept(worldName -> {
                    if (worldName == null) {
                        broadcastToParty(party, Component.text("Failed to create game world. Please try again.", NamedTextColor.RED), null);
                        party.setState(Party.State.LOBBY);
                        refreshLobbyForAll(party);
                        return;
                    }

                    party.setActiveWorldName(worldName);
                    party.setState(Party.State.IN_GAME);

                    // Write-ahead session snapshot right after world creation
                    plugin.getSessionManager().saveSession(party, worldName);

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        broadcastToParty(party, Component.text("Game world failed to load. Please try again.", NamedTextColor.RED), null);
                        party.setState(Party.State.LOBBY);
                        return;
                    }

                    startCountdownAndLaunch(party, worldName, world, minigame, false);
                });
            }
        }, 5L);
    }

    private void startCountdownAndLaunch(Party party, String worldName, World world, Minigame minigame, boolean isVanilla) {
        if (!plugin.getConfig().getBoolean("minigames.countdown.enabled", true)) {
            finalizeGameLaunch(party, worldName, world, minigame, isVanilla);
            return;
        }

        int duration = Math.max(1, plugin.getConfig().getInt("minigames.countdown.duration-seconds", 3));
        int[] remainingSeconds = {duration};

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!isCountdownPartyValid(party)) {
                task.cancel();
                return;
            }

            List<Player> onlinePlayers = getOnlinePartyMembers(party);
            if (onlinePlayers.isEmpty()) {
                task.cancel();
                abortGameStart(party, worldName, isVanilla);
                return;
            }

            if (remainingSeconds[0] <= 0) {
                task.cancel();
                finalizeGameLaunch(party, worldName, world, minigame, isVanilla);
                return;
            }

            int displayValue = remainingSeconds[0];
            Component titleText = Component.text(String.valueOf(displayValue), getCountdownTitleColor());
            Title countdownTitle = Title.title(
                    titleText,
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200))
            );

            for (Player player : onlinePlayers) {
                player.showTitle(countdownTitle);
                if (plugin.getConfig().getBoolean("minigames.countdown.sound-enabled", true)) {
                    player.playSound(Sound.sound(
                            Key.key("minecraft:ui.button.click"),
                            Sound.Source.MASTER,
                            1.0f,
                            1.0f));
                }
            }

            remainingSeconds[0]--;
        }, 0L, 20L);
    }

    private void finalizeGameLaunch(Party party, String worldName, World world, Minigame minigame, boolean isVanilla) {
        List<String> playerNames = new ArrayList<>();
        Player leaderPlayer = Bukkit.getPlayer(party.getLeaderUuid());
        String leaderName = leaderPlayer != null ? leaderPlayer.getName() : "";

        for (UUID uuid : party.getMembers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            player.teleport(world.getSpawnLocation());
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.sendMessage(Component.text("The game has started! Good luck!", NamedTextColor.GREEN));
            if (plugin.getConfig().getBoolean("minigames.countdown.sound-enabled", true)) {
                player.playSound(Sound.sound(
                        Key.key("minecraft:entity.player.levelup"),
                        Sound.Source.MASTER,
                        1.0f,
                        1.5f));
            }
            playerNames.add(player.getName());
            plugin.getAdvancementManager().revokeAll(player);
        }

        if (playerNames.isEmpty()) {
            abortGameStart(party, worldName, isVanilla);
            return;
        }

        String allPlayers = String.join(",", playerNames);
        if (!minigame.getStartCommands().isEmpty()) {
            executeStartCommands(minigame.getStartCommands(), worldName,
                    leaderName, allPlayers, playerNames, leaderPlayer);
        }
    }

    private boolean isCountdownPartyValid(Party party) {
        return party != null
                && party.getState() == Party.State.IN_GAME
                && activeParties.get(party.getMinigameId()) == party;
    }

    private List<Player> getOnlinePartyMembers(Party party) {
        return party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void abortGameStart(Party party, String worldName, boolean isVanilla) {
        if (party == null) return;

        broadcastToParty(party, Component.text("The game start was cancelled because the party became unavailable.", NamedTextColor.YELLOW), null);
        party.setState(Party.State.LOBBY);

        for (UUID uuid : party.getMembers()) {
            playerParties.remove(uuid);
        }
        activeParties.remove(party.getMinigameId());

        worldCopyManager.cleanupWorld(worldName, isVanilla);
        plugin.getSessionManager().deleteSession(worldName);
    }

    private NamedTextColor getCountdownTitleColor() {
        String rawColor = plugin.getConfig().getString("minigames.countdown.title-color", "<gold>").trim();
        String normalized = rawColor.startsWith("<") && rawColor.endsWith(">")
                ? rawColor.substring(1, rawColor.length() - 1)
                : rawColor;

        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "green" -> NamedTextColor.GREEN;
            case "yellow" -> NamedTextColor.YELLOW;
            case "red" -> NamedTextColor.RED;
            case "gold", "golden" -> NamedTextColor.GOLD;
            case "white" -> NamedTextColor.WHITE;
            case "gray", "grey" -> NamedTextColor.GRAY;
            default -> NamedTextColor.GOLD;
        };
    }

    private void broadcastActionBarToParty(Party party, Component message) {
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendActionBar(message);
        }
    }

    private void executeStartCommands(List<String> commands, String worldName,
                                      String leaderName, String allPlayers,
                                      List<String> playerNames, Player leaderPlayer) {
        // Named placeholders for each party position in join order
        String secondName  = playerNames.size() >= 2 ? playerNames.get(1) : "";
        String thirdName   = playerNames.size() >= 3 ? playerNames.get(2) : "";
        String fourthName  = playerNames.size() >= 4 ? playerNames.get(3) : "";
        String fifthName   = playerNames.size() >= 5 ? playerNames.get(4) : "";
        String sixthName   = playerNames.size() >= 6 ? playerNames.get(5) : "";
        String seventhName = playerNames.size() >= 7 ? playerNames.get(6) : "";
        String eighthName  = playerNames.size() >= 8 ? playerNames.get(7) : "";

        for (String command : commands) {
            if (command.startsWith("player-each:")) {
                // Run once per player as console, %player% = each player's name
                String cmd = command.substring("player-each:".length()).trim();
                for (String playerName : playerNames) {
                    String resolved = resolveCommand(cmd, worldName, leaderName,
                            allPlayers, playerName, secondName,
                            thirdName, fourthName, fifthName,
                            sixthName, seventhName, eighthName);
                    if (!PlaceholderSanitizer.isSafeToExecute(resolved)) {
                        plugin.getLogger().warning("Blocked unsafe start/conclude command: " + resolved);
                        continue;
                    }
                    if (!plugin.getConfigManager().isConsoleCommandAllowed(resolved)) {
                        plugin.getLogger().warning("Blocked disallowed console command (party lifecycle): " + resolved);
                        continue;
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
            } else if (command.startsWith("leader:")) {
                // Run as the leader player — opens GUIs and player-only commands
                String cmd = command.substring("leader:".length()).trim();
                String resolved = resolveCommand(cmd, worldName, leaderName,
                        allPlayers, leaderName, secondName,
                        thirdName, fourthName, fifthName,
                        sixthName, seventhName, eighthName);
                if (!PlaceholderSanitizer.isSafeToExecute(resolved)) {
                    plugin.getLogger().warning("Blocked unsafe start/conclude command: " + resolved);
                    continue;
                }
                if (leaderPlayer != null) {
                    leaderPlayer.performCommand(resolved);
                } else {
                    // Fallback to console if leader is offline
                    if (!plugin.getConfigManager().isConsoleCommandAllowed(resolved)) {
                        plugin.getLogger().warning("Blocked disallowed console command (party lifecycle): " + resolved);
                        continue;
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
            } else {
                // Default — run as console once
                String cmd = command.startsWith("console:")
                        ? command.substring("console:".length()).trim()
                        : command.trim();
                String resolved = resolveCommand(cmd, worldName, leaderName, allPlayers, "", secondName,
                        thirdName, fourthName, fifthName, sixthName, seventhName, eighthName);
                if (!PlaceholderSanitizer.isSafeToExecute(resolved)) {
                    plugin.getLogger().warning("Blocked unsafe start/conclude command: " + resolved);
                    continue;
                }
                if (!plugin.getConfigManager().isConsoleCommandAllowed(resolved)) {
                    plugin.getLogger().warning("Blocked disallowed console command (party lifecycle): " + resolved);
                    continue;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            }
        }
    }

    private String resolveCommand(String command, String worldName,
                                   String leaderName, String allPlayers,
                                   String playerName, String secondName,
                                   String thirdName, String fourthName,
                                   String fifthName, String sixthName,
                                   String seventhName, String eighthName) {
        return command
                .replace("%world%",   PlaceholderSanitizer.sanitize(worldName))
                .replace("%leader%",  PlaceholderSanitizer.sanitize(leaderName))
                .replace("%players%", PlaceholderSanitizer.sanitize(allPlayers))
                .replace("%second%",  PlaceholderSanitizer.sanitize(secondName))
                .replace("%third%",   PlaceholderSanitizer.sanitize(thirdName))
                .replace("%fourth%",  PlaceholderSanitizer.sanitize(fourthName))
                .replace("%fifth%",   PlaceholderSanitizer.sanitize(fifthName))
                .replace("%sixth%",   PlaceholderSanitizer.sanitize(sixthName))
                .replace("%seventh%", PlaceholderSanitizer.sanitize(seventhName))
                .replace("%eighth%",  PlaceholderSanitizer.sanitize(eighthName))
                .replace("%player%",  PlaceholderSanitizer.sanitize(playerName));
    }

    // ── Shutdown Cleanup ────────────────────────────────────────────

    /**
     * Cleans up all active parties during plugin shutdown.
     * Called from onDisable() to ensure graceful state transition.
     * Preserves session files for player recovery on next startup.
     * Note: Sessions are preserved so players can recover on next join.
     */
    public void cleanupAllActiveParties() {
        // Create a snapshot to avoid concurrent modification
        List<Party> partiesToClean = new ArrayList<>();
        for (Party party : activeParties.values()) {
            if (party.getState() == Party.State.IN_GAME) {
                partiesToClean.add(party);
            }
        }

        for (Party party : partiesToClean) {
            plugin.getLogger().info("Cleaning up active session for '" + party.getMinigame().getName() + "'.");
            cleanupPartyForShutdown(party);
        }
    }

    private void cleanupPartyForShutdown(Party party) {
        String worldName = party.getActiveWorldName();
        if (worldName == null) return;

        Minigame minigame = party.getMinigame();
        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
        boolean isVanilla = minigame.getWorldType() == Minigame.WorldType.VANILLA;

        // Remove temporary inventory groups immediately (in memory only, not persisted)
        if (isVanilla) {
            plugin.getInventoryManager().removeTemporaryGroup(worldName);
            plugin.getInventoryManager().removeTemporaryGroup(worldName + "_the_nether");
            plugin.getInventoryManager().removeTemporaryGroup(worldName + "_the_end");
        }

        // Teleport online players to hub if possible - this ensures they're not in a minigame world on shutdown
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && hub != null && p.getWorld().getName().startsWith("minigame_")) {
                p.teleport(hub.getSpawnLocation());
            }
        }

        // Clear party associations - session files are preserved for recovery
        for (UUID uuid : party.getMembers()) {
            playerParties.remove(uuid);
        }
        activeParties.remove(party.getMinigameId());
        party.clearPreGameData();

        // NOTE: We intentionally do NOT clean up world files during shutdown because:
        // 1. Session files already contain all recovery data (pre-game inventory, advancements, world)
        // 2. File operations during shutdown can cause concurrency issues
        // 3. World folders will be cleaned up on next startup if recovery is needed
        // The session file will be loaded and used by SessionManager.loadOrphanedSessions() on next startup

        plugin.getLogger().warning("Session '" + worldName + "' preserved for recovery. Players will be restored on next join.");
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

        // Fire conclude commands before cleanup so game plugin can do its own teardown
        if (!minigame.getConcludeCommands().isEmpty()) {
            // Build player info for placeholders
            List<String> playerNames = new ArrayList<>();
            Player leaderPlayer = Bukkit.getPlayer(party.getLeaderUuid());
            String leaderName = leaderPlayer != null ? leaderPlayer.getName() : "";
            for (UUID uuid : party.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) playerNames.add(p.getName());
            }
            String allPlayers = String.join(",", playerNames);
            executeStartCommands(minigame.getConcludeCommands(), worldName,
                    leaderName, allPlayers, playerNames, leaderPlayer);
        }

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

        // Restore pre-game advancements for online members
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Map<String, List<String>> preAdvs = party.getPreGameAdvancements(uuid);
                if (preAdvs != null && !preAdvs.isEmpty()) {
                    plugin.getAdvancementManager().restoreCompleted(p, preAdvs);
                }
            }
        }

        // Teleport alive players to Hub immediately
        // Dead players will be routed to Hub via PlayerRespawnEvent
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && !p.isDead()) {
                if (hub != null) p.teleport(hub.getSpawnLocation());
                p.sendMessage(Component.text("The game has ended! You have been returned to Hub.", NamedTextColor.GOLD));
            } else if (p != null) {
                p.sendMessage(Component.text("The game has ended! You will be returned to Hub on respawn.", NamedTextColor.GOLD));
            } else {
            }
        }

        for (UUID uuid : party.getMembers()) {
            playerParties.remove(uuid);
        }
        activeParties.remove(party.getMinigameId());
        party.clearPreGameData();

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
            plugin.getSessionManager().deleteSession(finalWorldName);
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

    private void broadcastToParty(Party party, Component message, UUID exclude) {
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
    public WorldCopyManager getWorldCopyManager() { return worldCopyManager; }
}