package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.event.ConcludeResult;
import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.MinigameStartEvent;
import com.ronlab.rga.api.event.RGAGameRequestConcludeEvent;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.WorldCopyManager;
import com.ronlab.rga.session.SpectatorSnapshot;
import com.ronlab.rga.util.AdventureUtil;
import com.ronlab.rga.util.PlaceholderSanitizer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class PartyManager implements Listener {

    private final RGA plugin;
    private final WorldCopyManager worldCopyManager;

    private final Map<String, Party> activeParties = new HashMap<>();
    private final Map<UUID, Party> playerParties = new HashMap<>();

    // ── Grace Period Tasks ─────────────────────────────────────────
    private final Map<UUID, BukkitTask> gracePeriodTasks = new HashMap<>();

    // ── Queueing structures ───────────────────────────────────────

    // Per-minigame FIFO queue of waiting parties
    private final Map<String, Queue<Party>> minigameQueues = new HashMap<>();
    // Tracks which players are in a queued party (distinct from active lobby players)
    private final Map<UUID, Party> queuedPlayers = new HashMap<>();

    // Players whose game has concluded but may still need to respawn
    private final Set<UUID> concludedPlayers = new HashSet<>();

    // ── Spectator Snapshots ───────────────────────────────────────
    // In-memory inventory/stats snapshots taken when a player enters spectator mode.
    // Consumed on setSpectator(player, false) or concludeGame() spectator flush.
    private final Map<UUID, SpectatorSnapshot> spectatorSnapshots = new java.util.concurrent.ConcurrentHashMap<>();

    public PartyManager(RGA plugin) {
        this.plugin = plugin;
        this.worldCopyManager = new WorldCopyManager(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ═══════════════════════════════════════════════════════════════
    //  SPECTATOR MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * JIT Spectator API — places or removes a player from spectator mode.
     * <p>
     * When {@code isSpectator=true}: captures a {@link SpectatorSnapshot} of the player's
     * full inventory and vital stats, clears the player, sets SPECTATOR game mode, and
     * revokes advancements. The player's party association is updated.
     * <p>
     * When {@code isSpectator=false}: restores the captured snapshot, restores advancements,
     * sets SURVIVAL game mode, teleports the player to hub, and removes all spectator tracking.
     * <p>
     * This method is a no-op if the player is not in a valid state for the requested transition.
     *
     * @param player      the target player
     * @param isSpectator {@code true} to enter spectator, {@code false} to leave
     */
    public void setSpectator(Player player, boolean isSpectator) {
        UUID uuid = player.getUniqueId();

        if (isSpectator) {
            // ── Enter spectator ────────────────────────────────────
            Party party = playerParties.get(uuid);
            if (party == null) {
                // Try finding via minigame name lookup in case the caller is joining fresh
                party = findPartyForSpectator(uuid);
            }
            if (party == null || party.getState() != Party.State.IN_GAME) {
                plugin.getLogger().warning("[RGA] setSpectator(true) called for " + player.getName()
                        + " but player has no active IN_GAME party association.");
                return;
            }
            if (party.isSpectator(uuid)) {
                // Already spectating — idempotent, just ensure game mode is correct
                player.setGameMode(GameMode.SPECTATOR);
                return;
            }
            if (!party.getMinigame().isAllowSpectators()) {
                plugin.getLogger().warning("[RGA] setSpectator(true) rejected for " + player.getName()
                        + " — minigame '" + party.getMinigame().getId() + "' has allow-spectators: false.");
                return;
            }

            // 1. Capture SpectatorSnapshot before any mutation
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            SpectatorSnapshot snapshot = new SpectatorSnapshot(
                    inv.getContents().clone(),
                    inv.getArmorContents().clone(),
                    inv.getItemInOffHand() != null ? inv.getItemInOffHand().clone() : null,
                    inv.getHeldItemSlot(),
                    player.getExp(),
                    player.getLevel(),
                    player.getTotalExperience(),
                    player.getHealth(),
                    player.getFoodLevel(),
                    player.getSaturation()
            );
            spectatorSnapshots.put(uuid, snapshot);

            // 2. Save pre-game group and advancements for restoration on exit
            String preGroup = plugin.getInventoryManager().getGroup(player.getWorld().getName());
            party.setSpectatorPreGameGroup(uuid, preGroup);
            party.setSpectatorPreGameAdvancements(uuid, plugin.getAdvancementManager().captureCompleted(player));

            // 3. Clear player state and register as spectator
            plugin.getInventoryManager().clearPlayer(player);
            party.addSpectator(uuid);

            // 4. Apply SPECTATOR game mode (Paper automatically hides spectators from participants)
            player.setGameMode(GameMode.SPECTATOR);

            // 5. Teleport into game world if not already there
            World gameWorld = party.getActiveWorldName() != null
                    ? Bukkit.getWorld(party.getActiveWorldName()) : null;
            if (gameWorld != null && !player.getWorld().getName().equals(party.getActiveWorldName())) {
                player.teleport(gameWorld.getSpawnLocation());
            }

            // 6. Revoke advancements so spectator cannot trigger game achievements
            plugin.getAdvancementManager().revokeAll(player);

            plugin.getLogger().info("[RGA] " + player.getName() + " entered spectator mode for '"
                    + party.getMinigame().getName() + "' via setSpectator API.");

        } else {
            // ── Leave spectator ────────────────────────────────────
            Party party = findPartyForSpectator(uuid);
            if (party == null) {
                plugin.getLogger().warning("[RGA] setSpectator(false) called for " + player.getName()
                        + " but no spectator party found.");
                return;
            }

            // 1. Restore SpectatorSnapshot if present
            SpectatorSnapshot snapshot = spectatorSnapshots.remove(uuid);
            if (snapshot != null) {
                restoreSpectatorSnapshot(player, snapshot);
            }

            // 2. Restore pre-game advancements
            Map<String, List<String>> preAdvs = party.getSpectatorPreGameAdvancements(uuid);
            if (preAdvs != null && !preAdvs.isEmpty()) {
                plugin.getAdvancementManager().restoreCompleted(player, preAdvs);
            }

            // 3. Set SURVIVAL game mode and teleport to hub
            player.setGameMode(GameMode.SURVIVAL);
            World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
            if (hub != null) {
                player.teleport(hub.getSpawnLocation());
            }

            // 4. Clean up all spectator tracking
            party.removeSpectator(uuid);
            playerParties.remove(uuid);

            player.sendMessage(Component.text("You are no longer spectating.", NamedTextColor.YELLOW));
            plugin.getLogger().info("[RGA] " + player.getName() + " left spectator mode for '"
                    + party.getMinigame().getName() + "' via setSpectator API.");
        }
    }

    /**
     * Returns {@code true} if the given player UUID is currently registered as a spectator
     * in any active minigame session. Suitable for use by {@link RGA#isSpectator(Player)}.
     *
     * @param uuid the player's unique ID
     * @return {@code true} if spectating
     */
    public boolean isPlayerSpectating(UUID uuid) {
        return isSpectator(uuid);
    }

    /**
     * Restores all fields from a {@link SpectatorSnapshot} onto the player.
     * Called by both {@link #setSpectator(Player, boolean)} and the conclude-path spectator flush.
     */
    private void restoreSpectatorSnapshot(Player player, SpectatorSnapshot snapshot) {
        plugin.getInventoryManager().clearPlayer(player);
        player.getInventory().setContents(snapshot.contents());
        player.getInventory().setArmorContents(snapshot.armor());
        if (snapshot.offhand() != null) {
            player.getInventory().setItemInOffHand(snapshot.offhand());
        }
        player.getInventory().setHeldItemSlot(snapshot.heldSlot());
        player.setExp(snapshot.exp());
        player.setLevel(snapshot.level());
        player.setTotalExperience(snapshot.totalExp());
        player.setHealth(Math.min(snapshot.health(), player.getMaxHealth()));
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
    }

    /**
     * Allows a player to join an active minigame as a spectator.
     * The player is teleported into the game world in SPECTATOR mode.
     * Their pre-game inventory group and advancements are saved for restoration on exit.
     * <p>
     * For programmatic JIT spectating, prefer {@link #setSpectator(Player, boolean)}.
     */
    public void joinAsSpectator(Player player, String minigameId) {
        Minigame minigame = plugin.getMinigameManager().getMinigame(minigameId);
        if (minigame == null) {
            player.sendMessage(Component.text("Unknown minigame: " + minigameId, NamedTextColor.RED));
            return;
        }

        Party party = activeParties.get(minigameId);
        if (party == null || party.getState() != Party.State.IN_GAME) {
            player.sendMessage(Component.text()
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" is not currently in progress.", NamedTextColor.RED))
                    .build());
            return;
        }

        if (!minigame.isAllowSpectators()) {
            player.sendMessage(Component.text()
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" does not allow spectators.", NamedTextColor.RED))
                    .build());
            return;
        }

        UUID uuid = player.getUniqueId();

        // If the player is already a spectator, just teleport them to the world
        if (party.isSpectator(uuid)) {
            World world = Bukkit.getWorld(party.getActiveWorldName());
            if (world != null) {
                player.teleport(world.getSpawnLocation());
                player.setGameMode(GameMode.SPECTATOR);
            }
            player.sendMessage(Component.text("You are already spectating this game.", NamedTextColor.YELLOW));
            return;
        }

        // If the player is a member, they cannot spectate their own game
        if (party.getMembers().contains(uuid)) {
            player.sendMessage(Component.text("You cannot spectate your own game. Use /rga leave first.", NamedTextColor.RED));
            return;
        }

        if (party.getSpectatorCount() >= minigame.getMaxSpectators()) {
            player.sendMessage(Component.text()
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" has reached its spectator limit (", NamedTextColor.RED))
                    .append(Component.text(minigame.getMaxSpectators(), NamedTextColor.WHITE))
                    .append(Component.text(").", NamedTextColor.RED))
                    .build());
            return;
        }

        // Leave any existing party or queue the player is in
        Party existingParty = playerParties.get(uuid);
        if (existingParty != null) {
            leaveParty(player);
        }
        Party queuedParty = queuedPlayers.get(uuid);
        if (queuedParty != null) {
            leaveQueuedParty(player, queuedParty);
        }

        // Save pre-game state for restoration on exit
        String preGroup = plugin.getInventoryManager().getGroup(player.getWorld().getName());
        party.setSpectatorPreGameGroup(uuid, preGroup);
        party.setSpectatorPreGameAdvancements(uuid, plugin.getAdvancementManager().captureCompleted(player));

        // Register the spectator
        party.addSpectator(uuid);
        playerParties.put(uuid, party);

        // Teleport to game world in spectator mode
        World world = Bukkit.getWorld(party.getActiveWorldName());
        if (world != null) {
            player.teleport(world.getSpawnLocation());
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(Component.text()
                    .append(Component.text("You are now spectating ", NamedTextColor.GRAY))
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(".", NamedTextColor.GRAY))
                    .build());
            player.sendMessage(Component.text("Use /rga spectate leave to stop spectating.", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Game world not found.", NamedTextColor.RED));
            party.removeSpectator(uuid);
            playerParties.remove(uuid);
            return;
        }

        // Revoke advancements so the spectator doesn't see game achievements
        plugin.getAdvancementManager().revokeAll(player);

        plugin.getLogger().info(player.getName() + " joined as spectator for '" + minigame.getName() + "'.");
    }

    /**
     * Removes a player from spectator mode, restores their advancements,
     * and teleports them back to the hub.
     */
    public void leaveSpectatorMode(Player player) {
        UUID uuid = player.getUniqueId();
        Party party = playerParties.get(uuid);

        if (party == null || !party.isSpectator(uuid)) {
            player.sendMessage(Component.text("You are not currently spectating any game.", NamedTextColor.RED));
            return;
        }

        // Restore pre-game advancements
        Map<String, List<String>> preAdvs = party.getSpectatorPreGameAdvancements(uuid);
        if (preAdvs != null && !preAdvs.isEmpty()) {
            plugin.getAdvancementManager().restoreCompleted(player, preAdvs);
        }

        // Teleport to hub — InventoryManager will handle inventory restoration on world change
        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
        if (hub != null) {
            player.teleport(hub.getSpawnLocation());
        }

        player.sendMessage(Component.text("You are no longer spectating.", NamedTextColor.YELLOW));

        // Clean up spectator data
        party.removeSpectator(uuid);
        playerParties.remove(uuid);

        plugin.getLogger().info(player.getName() + " stopped spectating '" + party.getMinigame().getName() + "'.");
    }

    /**
     * Returns the set of players who are currently spectating any active minigame.
     */
    public Set<UUID> getAllSpectators() {
        Set<UUID> all = new HashSet<>();
        for (Party party : activeParties.values()) {
            all.addAll(party.getSpectators());
        }
        return all;
    }

    // ═══════════════════════════════════════════════════════════════
    //  QUEUE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Enqueues a party for a minigame whose game is currently in progress.
     * The party enters the QUEUED state and members are notified of their position.
     */
    public void enqueueParty(Party party) {
        String minigameId = party.getMinigameId();
        Minigame minigame = party.getMinigame();

        if (!minigame.isQueueEnabled()) {
            // Queueing not supported for this minigame — just reject
            broadcastToParty(party, Component.text()
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" does not support queueing.", NamedTextColor.RED))
                    .build(), null);
            cleanupEmptyQueuedParty(party);
            return;
        }

        party.setState(Party.State.QUEUED);
        minigameQueues.computeIfAbsent(minigameId, k -> new LinkedList<>()).add(party);

        // Track all members as queued
        for (UUID uuid : party.getMembers()) {
            queuedPlayers.put(uuid, party);
        }

        int position = getQueuePosition(minigameId, party.getId());
        int queueSize = getQueueLength(minigameId);

        broadcastToParty(party, Component.text()
                .append(Component.text("You have been added to the queue for ", NamedTextColor.YELLOW))
                .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.YELLOW))
                .build(), null);

        broadcastToParty(party, Component.text()
                .append(Component.text("Position in queue: ", NamedTextColor.GRAY))
                .append(Component.text("#" + position, NamedTextColor.WHITE))
                .append(Component.text(" of ", NamedTextColor.GRAY))
                .append(Component.text(queueSize, NamedTextColor.WHITE))
                .build(), null);

        // Send queue info to all online members
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(Component.text("You'll be promoted to the lobby when the current game finishes.", NamedTextColor.GRAY));
            }
        }

        plugin.getLogger().info("Party for '" + minigame.getName() + "' queued at position #" + position + " (" + party.getMemberCount() + " players).");
    }

    /**
     * Promotes the next waiting party from QUEUED → LOBBY for the given minigame.
     * Returns the promoted party, or null if the queue is empty.
     */
    public Party dequeueNextParty(String minigameId) {
        Queue<Party> queue = minigameQueues.get(minigameId);
        if (queue == null || queue.isEmpty()) return null;

        Party nextParty = queue.poll();
        if (nextParty == null) return null;

        // If the party has no members, skip it and try the next one
        if (nextParty.getMemberCount() == 0) {
            for (UUID uuid : nextParty.getMembers()) {
                queuedPlayers.remove(uuid);
            }
            return dequeueNextParty(minigameId);
        }

        // Clear ready states so players must re-ready in the lobby
        nextParty.getMembers().forEach(uuid -> nextParty.setReady(uuid, false));

        // Promote to LOBBY and register as an active party
        nextParty.setState(Party.State.LOBBY);
        activeParties.put(minigameId, nextParty);

        // Move from queuedPlayers to playerParties
        for (UUID uuid : nextParty.getMembers()) {
            queuedPlayers.remove(uuid);
            playerParties.put(uuid, nextParty);
        }

        // Teleport online members to hub and open the lobby GUI
        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
        for (UUID uuid : nextParty.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (hub != null) p.teleport(hub.getSpawnLocation());
                p.sendMessage(Component.text()
                        .append(Component.text("Your turn! The lobby for ", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text(nextParty.getMinigame().getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text(" is now open!", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .build());
                // Play a notification sound
                p.playSound(Sound.sound(
                        Key.key("minecraft:block.note_block.pling"),
                        Sound.Source.MASTER,
                        1.0f, 2.0f));
                plugin.getLobbyGui().openLobby(p, nextParty);
            }
        }

        plugin.getLogger().info("Promoted queued party for '" + nextParty.getMinigame().getName()
                + "' to lobby (" + nextParty.getMemberCount() + " players).");
        return nextParty;
    }

    /**
     * Removes a party from its minigame queue. Called when all members leave or the party disbands.
     */
    public void removeFromQueue(Party party) {
        if (party.getState() != Party.State.QUEUED) return;

        String minigameId = party.getMinigameId();
        Queue<Party> queue = minigameQueues.get(minigameId);
        if (queue == null) return;

        // Remove this specific party from the queue
        queue.remove(party);

        // If queue is empty, clean up the map entry
        if (queue.isEmpty()) {
            minigameQueues.remove(minigameId);
        }

        // Remove member tracking
        for (UUID uuid : party.getMembers()) {
            queuedPlayers.remove(uuid);
        }

        // Notify remaining queued parties of their new position
        updateAllQueuePositions(minigameId);

        plugin.getLogger().info("Removed party from queue for '" + party.getMinigame().getName() + "'.");
    }

    /**
     * Returns the 1-based position of a party in the queue, or -1 if not queued.
     */
    public int getQueuePosition(String minigameId, UUID partyId) {
        Queue<Party> queue = minigameQueues.get(minigameId);
        if (queue == null) return -1;

        int idx = 0;
        for (Party p : queue) {
            idx++;
            if (p.getId().equals(partyId)) return idx;
        }
        return -1;
    }

    /**
     * Returns the number of parties waiting in the queue for a minigame.
     */
    public int getQueueLength(String minigameId) {
        Queue<Party> queue = minigameQueues.get(minigameId);
        return queue == null ? 0 : queue.size();
    }

    /**
     * Returns an unmodifiable view of all queues, keyed by minigame ID.
     */
    public Map<String, Queue<Party>> getMinigameQueues() {
        return Collections.unmodifiableMap(minigameQueues);
    }

    /**
     * Gets the queue for a specific minigame, or null.
     */
    public Queue<Party> getQueueForMinigame(String minigameId) {
        return minigameQueues.get(minigameId);
    }

    /**
     * Gets the party a player is currently queued in, or null.
     */
    public Party getQueuedParty(UUID playerUuid) {
        return queuedPlayers.get(playerUuid);
    }

    private void updateAllQueuePositions(String minigameId) {
        Queue<Party> queue = minigameQueues.get(minigameId);
        if (queue == null) return;

        int pos = 1;
        for (Party p : queue) {
            int newPosition = pos++;
            for (UUID uuid : p.getMembers()) {
                Player member = Bukkit.getPlayer(uuid);
                if (member != null) {
                    member.sendMessage(Component.text()
                            .append(Component.text("Queue position updated: ", NamedTextColor.GRAY))
                            .append(Component.text("#" + newPosition, NamedTextColor.WHITE))
                            .build());
                }
            }
        }
    }

    /**
     * Cleans up a queued party that has become empty or invalid.
     */
    private void cleanupEmptyQueuedParty(Party party) {
        if (party.getState() == Party.State.QUEUED) {
            removeFromQueue(party);
        }
    }

    // ── Grace Period Handling ─────────────────────────────────────

    public void startGracePeriod(Player player, Party party) {
        UUID uuid = player.getUniqueId();

        // Timer Duplication Guard: if timer is already active for this player, do not spawn another
        if (gracePeriodTasks.containsKey(uuid) || party.isAway(uuid)) {
            return;
        }

        party.setAway(uuid, true);
        int durationSeconds = plugin.getConfigManager().getPartyGracePeriodDurationSeconds();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            gracePeriodTasks.remove(uuid);
            handleGracePeriodTimeout(uuid, party);
        }, durationSeconds * 20L);

        gracePeriodTasks.put(uuid, task);

        broadcastToParty(party, Component.text()
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" visited the Hub or disconnected. Grace period active (", NamedTextColor.GRAY))
                .append(Component.text(durationSeconds + "s", NamedTextColor.WHITE))
                .append(Component.text(" to return).", NamedTextColor.GRAY))
                .build(), null);

        if (party.getState() == Party.State.LOBBY) {
            refreshLobbyForAll(party);
        }
    }

    public void cancelGracePeriod(UUID uuid, Party party) {
        BukkitTask task = gracePeriodTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        if (party != null && party.isAway(uuid)) {
            party.setAway(uuid, false);
            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : "A player";
            broadcastToParty(party, Component.text()
                    .append(Component.text(name, NamedTextColor.GREEN))
                    .append(Component.text(" returned to the party.", NamedTextColor.YELLOW))
                    .build(), null);

            if (party.getState() == Party.State.LOBBY) {
                refreshLobbyForAll(party);
            }
        }
    }

    private void handleGracePeriodTimeout(UUID uuid, Party party) {
        if (party == null || !party.getMembers().contains(uuid) || !party.isAway(uuid)) {
            return;
        }

        boolean wasLeader = uuid.equals(party.getLeaderUuid());
        party.setAway(uuid, false);
        party.removeMember(uuid);
        playerParties.remove(uuid);
        queuedPlayers.remove(uuid);

        Player player = Bukkit.getPlayer(uuid);
        String playerName = player != null ? player.getName() : "A player";

        if (player != null) {
            player.sendMessage(Component.text("Your party grace period has expired.", NamedTextColor.RED));
        }

        broadcastToParty(party, Component.text()
                .append(Component.text(playerName, NamedTextColor.YELLOW))
                .append(Component.text("'s grace period expired and they were removed from the party.", NamedTextColor.RED))
                .build(), null);

        if (party.getMemberCount() == 0) {
            if (party.getState() == Party.State.QUEUED) {
                removeFromQueue(party);
            } else if (party.getState() == Party.State.IN_GAME && party.getActiveWorldName() != null) {
                concludeGame(party.getActiveWorldName());
            } else {
                activeParties.remove(party.getMinigameId());
            }
            return;
        }

        if (wasLeader) {
            transferLeader(party, uuid);
        }

        if (party.getState() == Party.State.LOBBY) {
            refreshLobbyForAll(party);
        } else if (party.getState() == Party.State.QUEUED) {
            notifyQueuedPartyMemberChange(party);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String hubWorld = plugin.getConfigManager().getHubWorld();
        String newWorld = player.getWorld().getName();
        String oldWorld = event.getFrom().getName();

        Party party = playerParties.get(uuid);
        if (party == null) {
            party = queuedPlayers.get(uuid);
        }
        if (party == null) return;

        boolean isEnteringHub = newWorld.equalsIgnoreCase(hubWorld);
        boolean isLeavingHub = oldWorld.equalsIgnoreCase(hubWorld);

        if (isEnteringHub) {
            if (!plugin.getConfigManager().isPartyGracePeriodEnabled()) {
                leaveParty(player);
                return;
            }

            if (party.getState() == Party.State.IN_GAME && !plugin.getConfigManager().isPartyGracePeriodAllowedInGame()) {
                leaveParty(player);
                return;
            }

            startGracePeriod(player, party);
        } else if (isLeavingHub) {
            if (party.isAway(uuid)) {
                cancelGracePeriod(uuid, party);

                // Teleport returning player back to the active arena if party is IN_GAME
                if (party.getState() == Party.State.IN_GAME && party.getActiveWorldName() != null) {
                    World gameWorld = Bukkit.getWorld(party.getActiveWorldName());
                    if (gameWorld != null && !newWorld.equals(party.getActiveWorldName())) {
                        player.teleport(gameWorld.getSpawnLocation());
                        player.setGameMode(GameMode.SURVIVAL);
                        player.sendMessage(Component.text("Welcome back to the game!", NamedTextColor.GREEN));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Party party = playerParties.get(uuid);
        if (party == null) {
            party = queuedPlayers.get(uuid);
        }
        if (party != null && party.isAway(uuid)) {
            cancelGracePeriod(uuid, party);
            if (party.getState() == Party.State.IN_GAME && party.getActiveWorldName() != null) {
                World gameWorld = Bukkit.getWorld(party.getActiveWorldName());
                if (gameWorld != null) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.teleport(gameWorld.getSpawnLocation());
                            player.setGameMode(GameMode.SURVIVAL);
                            player.sendMessage(Component.text("Welcome back to the game!", NamedTextColor.GREEN));
                        }
                    }, 2L);
                }
            }
        }
    }

    // ── Disconnect handling ──────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if player is a spectator
        Party spectatorParty = null;
        for (Party p : activeParties.values()) {
            if (p.isSpectator(uuid)) {
                spectatorParty = p;
                break;
            }
        }
        if (spectatorParty != null) {
            spectatorParty.removeSpectator(uuid);
            playerParties.remove(uuid);
            plugin.getLogger().info("Spectator " + player.getName() + " disconnected from '" + spectatorParty.getMinigame().getName() + "'.");
            return;
        }

        // Check if the player is in a queued party
        Party queuedParty = queuedPlayers.get(uuid);
        if (queuedParty != null) {
            if (plugin.getConfigManager().isPartyGracePeriodEnabled()) {
                startGracePeriod(player, queuedParty);
            } else {
                handleQueuedPlayerDisconnect(uuid, queuedParty);
            }
            return;
        }

        // Otherwise check active party
        Party party = playerParties.get(uuid);
        if (party == null) return;

        if (plugin.getConfigManager().isPartyGracePeriodEnabled()) {
            if (party.getState() == Party.State.IN_GAME && !plugin.getConfigManager().isPartyGracePeriodAllowedInGame()) {
                playerParties.remove(uuid);
                party.removeMember(uuid);
                if (party.getMemberCount() == 0 && party.getActiveWorldName() != null) {
                    concludeGame(party.getActiveWorldName());
                }
                return;
            }
            startGracePeriod(player, party);
            return;
        }

        if (party.getState() == Party.State.IN_GAME) {
            playerParties.remove(uuid);
            party.removeMember(uuid);
            if (party.getMemberCount() == 0 && party.getActiveWorldName() != null) {
                concludeGame(party.getActiveWorldName());
            }
            return;
        }

        handleLobbyPlayerDisconnect(player, uuid, party);
    }


    private void handleQueuedPlayerDisconnect(UUID uuid, Party party) {
        boolean wasLeader = uuid.equals(party.getLeaderUuid());

        party.removeMember(uuid);
        queuedPlayers.remove(uuid);

        if (party.getMemberCount() == 0) {
            // All members left — remove party from queue entirely
            removeFromQueue(party);
            plugin.getLogger().info("Disbanded empty queued party for '" + party.getMinigame().getName() + "'.");
            return;
        }

        if (wasLeader) {
            transferLeader(party, uuid);
        }

        notifyQueuedPartyMemberChange(party);
    }

    private void handleLobbyPlayerDisconnect(Player player, UUID uuid, Party party) {
        if (player.getUniqueId().equals(party.getLeaderUuid())) {
            transferLeader(party, uuid);
        }

        party.removeMember(uuid);
        playerParties.remove(uuid);

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

    private void notifyQueuedPartyMemberChange(Party party) {
        int count = party.getMemberCount();
        int position = getQueuePosition(party.getMinigameId(), party.getId());

        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(Component.text()
                        .append(Component.text("Party member count updated: ", NamedTextColor.GRAY))
                        .append(Component.text(count, NamedTextColor.WHITE))
                        .append(Component.text(" | Queue position: ", NamedTextColor.GRAY))
                        .append(Component.text("#" + position, NamedTextColor.WHITE))
                        .build());
            }
        }
    }

    // ── Join / Leave ─────────────────────────────────────────────

    public void joinMinigame(Player player, String minigameId) {
        joinMinigame(player, minigameId, null);
    }

    public void joinMinigame(Player player, String minigameId, String templateId) {
        Minigame minigame = plugin.getMinigameManager().getMinigame(minigameId);
        if (minigame == null && plugin.getTemplateDiscoveryService() != null) {
            MapTemplateMetadata meta = plugin.getTemplateDiscoveryService().get(minigameId);
            if (meta != null) {
                minigame = plugin.getMinigameManager().getMinigame(meta.resolveEngineId());
                if (templateId == null || templateId.isBlank()) {
                    templateId = meta.id();
                }
                minigameId = meta.resolveEngineId();
            }
        }

        if (minigame == null) {
            player.sendMessage(Component.text("Unknown minigame: " + minigameId, NamedTextColor.RED));
            return;
        }

        if (templateId != null && templateId.equalsIgnoreCase(minigameId)) {
            templateId = null;
        }

        // If the player is a spectator, redirect them to leave spectate mode first
        Party spectatorParty = findPartyForSpectator(player.getUniqueId());
        if (spectatorParty != null && spectatorParty.getMinigameId().equals(minigameId)) {
            player.sendMessage(Component.text("You are spectating this game. Use /rga spectate leave first.", NamedTextColor.RED));
            return;
        }

        // If player is already in a queued party, show queue status
        Party queuedParty = queuedPlayers.get(player.getUniqueId());
        if (queuedParty != null && queuedParty.getMinigameId().equals(minigameId)) {
            int position = getQueuePosition(minigameId, queuedParty.getId());
            int queueSize = getQueueLength(minigameId);
            player.sendMessage(Component.text()
                    .append(Component.text("You are already in the queue for ", NamedTextColor.YELLOW))
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(". Position: #" + position + "/" + queueSize, NamedTextColor.WHITE))
                    .build());
            return;
        }

        // Already in a party for this minigame — just reopen lobby
        Party existingParty = playerParties.get(player.getUniqueId());
        if (existingParty != null && existingParty.getMinigameId().equals(minigameId)) {
            if (templateId != null && !templateId.isBlank() && existingParty.getLeaderUuid().equals(player.getUniqueId())) {
                existingParty.setSelectedTemplateWorld(templateId);
            }
            plugin.getLobbyGui().openLobby(player, existingParty);
            return;
        }

        // In a different party (or queued in a different one) — leave first
        if (existingParty != null) leaveParty(player);
        if (queuedParty != null) leaveQueuedParty(player, queuedParty);

        Party party = activeParties.get(minigameId);

        if (party == null) {
            // No active party — create a new lobby
            party = new Party(player.getUniqueId(), minigame);
            if (templateId != null && !templateId.isBlank()) {
                party.setSelectedTemplateWorld(templateId);
            }
            activeParties.put(minigameId, party);
            playerParties.put(player.getUniqueId(), party);
            player.sendMessage(Component.text()
                    .append(Component.text("Created a new party for ", NamedTextColor.GREEN))
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text("!", NamedTextColor.GREEN))
                    .build());
        } else if (party.getState() == Party.State.IN_GAME) {
            // Game is in progress — attempt to queue
            if (!minigame.isQueueEnabled()) {
                player.sendMessage(Component.text()
                        .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                        .append(Component.text(" is currently in progress and does not support queueing.", NamedTextColor.RED))
                        .build());
                return;
            }

            // Create a new party and enqueue it
            Party queueParty = new Party(player.getUniqueId(), minigame);
            if (templateId != null && !templateId.isBlank()) {
                queueParty.setSelectedTemplateWorld(templateId);
            }
            queueParty.addMember(player.getUniqueId());
            playerParties.put(player.getUniqueId(), queueParty);
            player.sendMessage(Component.text()
                    .append(Component.text("Game in progress for ", NamedTextColor.YELLOW))
                    .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                    .append(Component.text(". Adding you to the queue.", NamedTextColor.YELLOW))
                    .build());
            enqueueParty(queueParty);
            return;
        } else if (party.getState() == Party.State.LOBBY) {
            if (party.isFull()) {
                player.sendMessage(Component.text()
                        .append(Component.text("The party for ", NamedTextColor.RED))
                        .append(Component.text(minigame.getName(), NamedTextColor.GOLD))
                        .append(Component.text(" is full!", NamedTextColor.RED))
                        .build());
                return;
            }
            if (templateId != null && !templateId.isBlank() && party.getLeaderUuid().equals(player.getUniqueId())) {
                party.setSelectedTemplateWorld(templateId);
            }
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

    private void leaveQueuedParty(Player player, Party party) {
        if (party == null) return;
        UUID uuid = player.getUniqueId();

        cancelGracePeriod(uuid, party);

        boolean wasLeader = uuid.equals(party.getLeaderUuid());
        boolean wasMember = party.getMembers().contains(uuid);

        party.removeMember(uuid);
        queuedPlayers.remove(uuid);

        if (party.getMemberCount() == 0) {
            removeFromQueue(party);
            return;
        }

        if (wasLeader && wasMember) {
            transferLeader(party, uuid);
        }

        notifyQueuedPartyMemberChange(party);
    }

    public void leaveParty(Player player) {
        // Check if the player is a spectator
        if (isSpectator(player.getUniqueId())) {
            leaveSpectatorMode(player);
            return;
        }

        UUID uuid = player.getUniqueId();

        // Check if player is in a queued party
        Party queued = queuedPlayers.get(uuid);
        if (queued != null) {
            leaveQueuedParty(player, queued);
            player.sendMessage(Component.text("You left the queue.", NamedTextColor.YELLOW));
            player.closeInventory();
            return;
        }

        Party party = playerParties.remove(uuid);
        if (party == null) return;

        cancelGracePeriod(uuid, party);

        boolean wasLeader = uuid.equals(party.getLeaderUuid());
        if (wasLeader && party.getMemberCount() > 1) {
            transferLeader(party, uuid);
        }

        party.removeMember(uuid);
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
        if (party.getState() == Party.State.IN_GAME || party.getState() == Party.State.QUEUED) return;

        if (party.hasAwayPlayers() && !party.isReady(player.getUniqueId())) {
            player.sendMessage(Component.text("Cannot ready up while a party member is visiting the Hub.", NamedTextColor.RED));
            return;
        }

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

            // Session Safety & Random Map Quick Play Resolution
            String targetTemplateWorld = party.getSelectedTemplateWorld();
            if (minigame.getWorldType() != Minigame.WorldType.VANILLA) {
                boolean invalidTemplate = (targetTemplateWorld == null
                        || targetTemplateWorld.isBlank()
                        || targetTemplateWorld.equalsIgnoreCase(minigame.getId())
                        || (worldCopyManager != null && !worldCopyManager.hasWorldData(targetTemplateWorld)));

                if (invalidTemplate) {
                    if (plugin.getTemplateDiscoveryService() != null) {
                        List<MapTemplateMetadata> categoryMaps = plugin.getTemplateDiscoveryService().getTemplatesByCategory(minigame.getId());
                        if (categoryMaps.isEmpty()) {
                            categoryMaps = plugin.getTemplateDiscoveryService().getTemplatesByCategory(minigame.getName().toLowerCase(java.util.Locale.ROOT));
                        }
                        categoryMaps = categoryMaps.stream()
                                .filter(m -> !m.id().equalsIgnoreCase(minigame.getId()))
                                .toList();

                        if (!categoryMaps.isEmpty()) {
                            int randomIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(categoryMaps.size());
                            targetTemplateWorld = categoryMaps.get(randomIndex).id();
                            party.setSelectedTemplateWorld(targetTemplateWorld);
                            broadcastToParty(party, Component.text()
                                    .append(Component.text("[RGA] ", NamedTextColor.GOLD))
                                    .append(Component.text("Quick Play selected map: ", NamedTextColor.YELLOW))
                                    .append(Component.text(targetTemplateWorld, NamedTextColor.AQUA))
                                    .append(Component.text("!", NamedTextColor.YELLOW))
                                    .build(), null);
                        }
                    }
                }
            }

            if (targetTemplateWorld == null || targetTemplateWorld.isBlank() || targetTemplateWorld.equalsIgnoreCase(minigame.getId())) {
                targetTemplateWorld = minigame.getTemplateWorld();
            }

            if (minigame.getWorldType() != Minigame.WorldType.VANILLA && targetTemplateWorld != null) {
                if (plugin.getTemplateStagingManager() != null && plugin.getTemplateStagingManager().isTemplateEditing(targetTemplateWorld)) {
                    broadcastToParty(party, Component.text("Cannot start game: Template '" + targetTemplateWorld + "' is currently open for administrative editing.", NamedTextColor.RED), null);
                    party.setState(Party.State.LOBBY);
                    refreshLobbyForAll(party);
                    return;
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

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    broadcastToParty(party, Component.text("Game world failed to load. Please try again.", NamedTextColor.RED), null);
                    abortGameStart(party, worldName, true);
                    return;
                }

                MinigameStartEvent startEvent = new MinigameStartEvent(minigame.getId(), minigame.getName(), worldName, party.getMembers());

                // ── Diagnostic: confirm payload before companion plugins receive it ──
                plugin.getLogger().info("[RGA DEBUG] Dispatching MinigameStartEvent...");
                plugin.getLogger().info("[RGA DEBUG] -> Minigame ID: " + minigame.getId());
                plugin.getLogger().info("[RGA DEBUG] -> World: " + world.getName());
                plugin.getLogger().info("[RGA DEBUG] -> Players in payload: " + party.getMembers().size());
                if (party.getMembers().isEmpty()) {
                    plugin.getLogger().warning("[RGA DEBUG] -> WARNING: Player list is empty! Companion plugins will not start.");
                }

                Bukkit.getPluginManager().callEvent(startEvent);
                if (startEvent.isCancelled()) {
                    broadcastToParty(party, Component.text("Game start was cancelled by an event listener.", NamedTextColor.YELLOW), null);
                    abortGameStart(party, worldName, true);
                    return;
                }

                party.setActiveWorldName(worldName);
                party.setState(Party.State.IN_GAME);

                // Write-ahead session snapshot right after world creation
                plugin.getSessionManager().saveSession(party, worldName);

                // Register all three dimensions as a shared inventory group
                plugin.getInventoryManager().addTemporaryGroup(worldName,
                        List.of(worldName, worldName + "_the_nether", worldName + "_the_end"));

                startCountdownAndLaunch(party, worldName, world, minigame, true);
            } else {
                worldCopyManager.copyTemplateWorld(minigame, targetTemplateWorld).thenAccept(worldName -> {
                    if (worldName == null) {
                        broadcastToParty(party, Component.text("Failed to create game world. Please try again.", NamedTextColor.RED), null);
                        party.setState(Party.State.LOBBY);
                        refreshLobbyForAll(party);
                        return;
                    }

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        broadcastToParty(party, Component.text("Game world failed to load. Please try again.", NamedTextColor.RED), null);
                        abortGameStart(party, worldName, false);
                        return;
                    }

                    MinigameStartEvent startEvent = new MinigameStartEvent(minigame.getId(), minigame.getName(), worldName, party.getMembers());

                    // ── Diagnostic: confirm payload before companion plugins receive it ──
                    plugin.getLogger().info("[RGA DEBUG] Dispatching MinigameStartEvent...");
                    plugin.getLogger().info("[RGA DEBUG] -> Minigame ID: " + minigame.getId());
                    plugin.getLogger().info("[RGA DEBUG] -> World: " + world.getName());
                    plugin.getLogger().info("[RGA DEBUG] -> Players in payload: " + party.getMembers().size());
                    if (party.getMembers().isEmpty()) {
                        plugin.getLogger().warning("[RGA DEBUG] -> WARNING: Player list is empty! Companion plugins will not start.");
                    }

                    Bukkit.getPluginManager().callEvent(startEvent);
                    if (startEvent.isCancelled()) {
                        broadcastToParty(party, Component.text("Game start was cancelled by an event listener.", NamedTextColor.YELLOW), null);
                        abortGameStart(party, worldName, false);
                        return;
                    }

                    party.setActiveWorldName(worldName);
                    party.setState(Party.State.IN_GAME);

                    // Write-ahead session snapshot right after world creation
                    plugin.getSessionManager().saveSession(party, worldName);

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

            // Only teleport to world spawn if player is not already in the game world.
            // Companion plugins subscribing to MinigameStartEvent hold 100% authority over spatial coordinates.
            if (!player.getWorld().getName().equalsIgnoreCase(world.getName())) {
                player.teleport(world.getSpawnLocation());
            }
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

        // Notify spectators that the game has started
        for (UUID uuid : party.getSpectators()) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) {
                spectator.sendMessage(Component.text("The game has started!", NamedTextColor.GREEN));
            }
        }

        if (playerNames.isEmpty()) {
            abortGameStart(party, worldName, isVanilla);
            return;
        }

        String allPlayers = String.join(",", playerNames);
        List<String> spectatorNames = new ArrayList<>();
        for (UUID specUuid : party.getSpectators()) {
            Player spectator = Bukkit.getPlayer(specUuid);
            if (spectator != null) spectatorNames.add(spectator.getName());
        }
        String allSpectators = String.join(",", spectatorNames);

        if (!minigame.getStartCommands().isEmpty()) {
            executeStartCommands(minigame.getStartCommands(), worldName,
                    leaderName, allPlayers, allSpectators, playerNames, leaderPlayer, party.getSelectedTemplateWorld());
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
        party.setActiveWorldName(null);

        if (worldName != null) {
            plugin.getInventoryManager().removeTemporaryGroup(worldName);
            plugin.getInventoryManager().removeTemporaryGroup(worldName + "_the_nether");
            plugin.getInventoryManager().removeTemporaryGroup(worldName + "_the_end");
            worldCopyManager.cleanupWorld(worldName, isVanilla);
            plugin.getSessionManager().deleteSession(worldName);
        }
        refreshLobbyForAll(party);
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
                                       String allSpectators,
                                       List<String> playerNames, Player leaderPlayer,
                                       String templateName) {
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
                            allPlayers, allSpectators, playerName, secondName,
                            thirdName, fourthName, fifthName,
                            sixthName, seventhName, eighthName, templateName);
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
                        allPlayers, allSpectators, leaderName, secondName,
                        thirdName, fourthName, fifthName,
                        sixthName, seventhName, eighthName, templateName);
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
                String resolved = resolveCommand(cmd, worldName, leaderName, allPlayers, allSpectators, "", secondName,
                        thirdName, fourthName, fifthName, sixthName, seventhName, eighthName, templateName);
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
                                   String allSpectators,
                                   String playerName, String secondName,
                                   String thirdName, String fourthName,
                                   String fifthName, String sixthName,
                                   String seventhName, String eighthName,
                                   String templateName) {
        return command
                .replace("%world%",      PlaceholderSanitizer.sanitize(worldName))
                .replace("%leader%",     PlaceholderSanitizer.sanitize(leaderName))
                .replace("%players%",    PlaceholderSanitizer.sanitize(allPlayers))
                .replace("%spectators%", PlaceholderSanitizer.sanitize(allSpectators))
                .replace("%second%",     PlaceholderSanitizer.sanitize(secondName))
                .replace("%third%",      PlaceholderSanitizer.sanitize(thirdName))
                .replace("%fourth%",     PlaceholderSanitizer.sanitize(fourthName))
                .replace("%fifth%",      PlaceholderSanitizer.sanitize(fifthName))
                .replace("%sixth%",      PlaceholderSanitizer.sanitize(sixthName))
                .replace("%seventh%",    PlaceholderSanitizer.sanitize(seventhName))
                .replace("%eighth%",     PlaceholderSanitizer.sanitize(eighthName))
                .replace("%player%",     PlaceholderSanitizer.sanitize(playerName))
                .replace("%template%",   PlaceholderSanitizer.sanitize(templateName != null ? templateName : ""));
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

        // Teleport online players and spectators to hub if possible
        Set<UUID> allParticipants = new HashSet<>();
        allParticipants.addAll(party.getMembers());
        allParticipants.addAll(party.getSpectators());

        for (UUID uuid : allParticipants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && hub != null && p.getWorld().getName().startsWith("minigame_")) {
                p.teleport(hub.getSpawnLocation());
            }
        }

        // Clear party associations - session files are preserved for recovery
        for (UUID uuid : allParticipants) {
            playerParties.remove(uuid);
        }
        activeParties.remove(party.getMinigameId());
        party.clearPreGameData();
        party.clearSpectatorPreGameData();

        plugin.getLogger().warning("Session '" + worldName + "' preserved for recovery. Players will be restored on next join.");
    }

    // ── Programmatic Conclude API ────────────────────────────────

    public Party getPartyByWorld(String worldName) {
        if (worldName == null) return null;
        String baseName = worldName;
        if (baseName.endsWith("_the_nether")) {
            baseName = baseName.substring(0, baseName.length() - "_the_nether".length());
        } else if (baseName.endsWith("_the_end")) {
            baseName = baseName.substring(0, baseName.length() - "_the_end".length());
        }
        for (Party p : activeParties.values()) {
            if (baseName.equals(p.getActiveWorldName())) {
                return p;
            }
        }
        return null;
    }

    public Optional<Party> findPartyByWorld(String worldName) {
        return Optional.ofNullable(getPartyByWorld(worldName));
    }

    public synchronized void dissociatePartyFromWorld(String worldName) {
        Party party = getPartyByWorld(worldName);
        if (party == null) {
            return;
        }

        for (UUID uuid : party.getMembers()) {
            playerParties.remove(uuid);
        }

        List<UUID> remainingSpectators = new ArrayList<>(party.getSpectators());
        for (UUID uuid : remainingSpectators) {
            playerParties.remove(uuid);
            party.removeSpectator(uuid);
        }

        activeParties.remove(party.getMinigameId());
        party.clearPreGameData();
        plugin.getLogger().info("[RGA PARTY] Dissociated party for minigame '"
                + party.getMinigameId() + "' from world '" + worldName + "'.");
    }

    private int safeConvertScore(Number value) {
        if (value == null) return 0;
        long longVal = value.longValue();
        if (longVal > Integer.MAX_VALUE) {
            plugin.getLogger().warning("[RGA] Score value " + longVal + " exceeds Integer.MAX_VALUE. Clamping to MAX_VALUE.");
            return Integer.MAX_VALUE;
        }
        if (longVal < Integer.MIN_VALUE) {
            plugin.getLogger().warning("[RGA] Score value " + longVal + " below Integer.MIN_VALUE. Clamping to MIN_VALUE.");
            return Integer.MIN_VALUE;
        }
        return value.intValue();
    }

    public ConcludeResult requestSessionConclude(String worldName, String rawReason, Map<UUID, ? extends Number> rawScores) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "requestSessionConclude must be called on the main server thread. " +
                "Use Bukkit.getScheduler().runTask() when calling from an async context."
            );
        }

        Party party = getPartyByWorld(worldName);
        if (party == null) {
            return ConcludeResult.NOT_FOUND;
        }
        if (party.getState() == Party.State.CONCLUDING) {
            return ConcludeResult.ALREADY_CONCLUDING;
        }

        String reason = (rawReason != null && rawReason.length() > 100) ? rawReason.substring(0, 100) : rawReason;
        Minigame minigame = party.getMinigame();
        RGAGameRequestConcludeEvent requestEvent = new RGAGameRequestConcludeEvent(
                minigame.getId(),
                minigame.getName(),
                party.getActiveWorldName() != null ? party.getActiveWorldName() : worldName,
                party.getMembers(),
                reason,
                rawScores
        );

        Bukkit.getPluginManager().callEvent(requestEvent);
        if (requestEvent.isCancelled()) {
            return ConcludeResult.CANCELLED;
        }

        Map<UUID, Integer> convertedScores = new HashMap<>();
        if (requestEvent.getScores() != null) {
            for (Map.Entry<UUID, Number> entry : requestEvent.getScores().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    convertedScores.put(entry.getKey(), safeConvertScore(entry.getValue()));
                }
            }
        }

        try {
            party.setState(Party.State.CONCLUDING);
            return concludeGame(worldName, convertedScores);
        } catch (Exception e) {
            plugin.getLogger().severe("[RGA] Internal error during programmatic conclude for world '" + worldName + "': " + e.getMessage());
            return ConcludeResult.ERROR;
        }
    }

    // ── Game End ─────────────────────────────────────────────────

    public ConcludeResult concludeGame(String worldName) {
        return concludeGame(worldName, null);
    }

    public ConcludeResult concludeGame(String worldName, Map<UUID, ? extends Number> scores) {
        Optional<Party> partyOpt = findPartyByWorld(worldName);
        if (partyOpt.isEmpty()) {
            plugin.getLogger().warning("No party found for world: " + worldName);
            return ConcludeResult.NOT_FOUND;
        }
        Party party = partyOpt.get();

        if (party.getActiveWorldName() != null) {
            worldName = party.getActiveWorldName();
        }

        Minigame minigame = party.getMinigame();

        MinigameConcludeEvent concludeEvent = new MinigameConcludeEvent(
                minigame.getId(),
                minigame.getName(),
                worldName,
                party.getMembers(),
                scores
        );
        Bukkit.getPluginManager().callEvent(concludeEvent);
        if (concludeEvent.isCancelled()) {
            boolean zeroOnlineMembers = getOnlinePartyMembers(party).isEmpty();
            if (zeroOnlineMembers) {
                plugin.getLogger().warning("Zero members remaining - overriding event cancellation for session: " + worldName);
            } else {
                plugin.getLogger().info("MinigameConcludeEvent was cancelled for session: " + worldName);
                return ConcludeResult.CANCELLED;
            }
        }

        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());

        // Collect spectator names before handling/clearing spectator state
        List<String> spectatorNames = new ArrayList<>();
        for (UUID uuid : party.getSpectators()) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) spectatorNames.add(spectator.getName());
        }
        String allSpectators = String.join(",", spectatorNames);

        // ── Flush all spectators before conclusion commands ──────────
        // Restore SpectatorSnapshots (inventory + stats), advancements, and teleport to hub.
        // Snapshot iteration uses a copy of the set to avoid ConcurrentModificationException
        // since restoring state may trigger world-change events.
        List<UUID> spectatorsCopy = new ArrayList<>(party.getSpectators());
        for (UUID uuid : spectatorsCopy) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) {
                // Restore explicit SpectatorSnapshot if one was captured via setSpectator API
                SpectatorSnapshot snapshot = spectatorSnapshots.remove(uuid);
                if (snapshot != null) {
                    restoreSpectatorSnapshot(spectator, snapshot);
                }
                // Restore pre-game advancements
                Map<String, List<String>> preAdvs = party.getSpectatorPreGameAdvancements(uuid);
                if (preAdvs != null && !preAdvs.isEmpty()) {
                    plugin.getAdvancementManager().restoreCompleted(spectator, preAdvs);
                }
                // Set game mode back to survival before teleport
                spectator.setGameMode(GameMode.SURVIVAL);
                // Teleport to hub
                if (hub != null) {
                    spectator.teleport(hub.getSpawnLocation());
                }
                spectator.sendMessage(Component.text("The game has ended. You have been returned to Hub.", NamedTextColor.GOLD));
            } else {
                // Player offline — discard snapshot to avoid memory leak
                spectatorSnapshots.remove(uuid);
            }
        }
        // Clean up spectator data
        party.clearSpectatorPreGameData();

        // Fire conclude commands before cleanup so game plugin can do its own teardown
        if (!minigame.getConcludeCommands().isEmpty()) {
            // Build player info for placeholders (members only, not spectators)
            List<String> playerNames = new ArrayList<>();
            Player leaderPlayer = Bukkit.getPlayer(party.getLeaderUuid());
            String leaderName = leaderPlayer != null ? leaderPlayer.getName() : "";
            for (UUID uuid : party.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) playerNames.add(p.getName());
            }
            String allPlayers = String.join(",", playerNames);
            executeStartCommands(minigame.getConcludeCommands(), worldName,
                    leaderName, allPlayers, allSpectators, playerNames, leaderPlayer, party.getSelectedTemplateWorld());
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
            }
        }

        // Capture values for the deferred cleanup lambda
        List<UUID> finalMembers = new ArrayList<>(party.getMembers());
        String finalWorldName = worldName;
        boolean isVanilla = minigame.getWorldType() == Minigame.WorldType.VANILLA;
        String finalMinigameId = party.getMinigameId();
        Minigame finalMinigame = minigame;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Clear concluded player flags
            for (UUID uuid : finalMembers) {
                concludedPlayers.remove(uuid);
            }
            worldCopyManager.cleanupWorld(finalWorldName, isVanilla);
            plugin.getSessionManager().deleteSession(finalWorldName);
            dissociatePartyFromWorld(finalWorldName);

            // ── Auto-start: promote next queued party ───────────────
            if (finalMinigame.isAutoStart()) {
                Party nextParty = dequeueNextParty(finalMinigameId);
                if (nextParty != null) {
                    plugin.getLogger().info("Auto-started next queued party for '" + finalMinigame.getName() + "'.");
                }
            }

        }, 300L);

        plugin.getLogger().info("Concluded minigame '" + minigame.getName()
                + "' in world '" + worldName + "'.");
        return ConcludeResult.SUCCESS;
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Finds the party for which the given player UUID is a spectator.
     */
    private Party findPartyForSpectator(UUID uuid) {
        for (Party party : activeParties.values()) {
            if (party.isSpectator(uuid)) {
                return party;
            }
        }
        return null;
    }

    /**
     * Returns true if the player is currently spectating any active game.
     */
    public boolean isSpectator(UUID uuid) {
        for (Party party : activeParties.values()) {
            if (party.isSpectator(uuid)) {
                return true;
            }
        }
        return false;
    }

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