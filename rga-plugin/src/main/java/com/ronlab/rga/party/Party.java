package com.ronlab.rga.party;

import com.ronlab.rga.minigame.Minigame;

import java.util.*;

public class Party {

    public enum State { LOBBY, QUEUED, IN_GAME, CONCLUDING }

    private final UUID id;
    private final String minigameId;
    private final Minigame minigame;
    private UUID leaderUuid;
    private final List<UUID> members;
    private final Set<UUID> readyPlayers;
    private State state;
    private String activeWorldName;

    // ── Spectator support ──────────────────────────────────────────
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, String> spectatorPreGameGroups = new HashMap<>();
    private final Map<UUID, Map<String, List<String>>> spectatorPreGameAdvancements = new HashMap<>();

    private final Set<UUID> awayPlayers = new HashSet<>();

    public Party(UUID leaderUuid, Minigame minigame) {
        this.id = UUID.randomUUID();
        this.minigameId = minigame.getId();
        this.minigame = minigame;
        this.leaderUuid = leaderUuid;
        this.members = new ArrayList<>();
        this.readyPlayers = new HashSet<>();
        this.state = State.LOBBY;
        this.members.add(leaderUuid);
    }

    public boolean addMember(UUID uuid) {
        if (members.size() >= minigame.getMaxPlayers()) return false;
        if (members.contains(uuid)) return false;
        members.add(uuid);
        return true;
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        readyPlayers.remove(uuid);
        awayPlayers.remove(uuid);
    }

    public void setReady(UUID uuid, boolean ready) {
        if (ready && !isAway(uuid)) readyPlayers.add(uuid);
        else readyPlayers.remove(uuid);
    }

    public boolean isReady(UUID uuid) {
        return readyPlayers.contains(uuid);
    }

    public boolean allReady() {
        if (members.size() < minigame.getMinPlayers()) return false;
        if (hasAwayPlayers()) return false;
        return readyPlayers.containsAll(members);
    }

    public void setAway(UUID uuid, boolean away) {
        if (away) {
            awayPlayers.add(uuid);
            readyPlayers.remove(uuid);
        } else {
            awayPlayers.remove(uuid);
        }
    }

    public boolean isAway(UUID uuid) {
        return awayPlayers.contains(uuid);
    }

    public Set<UUID> getAwayPlayers() {
        return Collections.unmodifiableSet(awayPlayers);
    }

    public boolean hasAwayPlayers() {
        return !awayPlayers.isEmpty();
    }

    public int getActiveMemberCount() {
        return members.size() - awayPlayers.size();
    }


    public boolean isFull() {
        return members.size() >= minigame.getMaxPlayers();
    }

    // Leader transfer
    public void setLeader(UUID uuid) {
        this.leaderUuid = uuid;
    }

    private final Map<UUID, String> preGameGroups = new HashMap<>();
    private final Map<UUID, Map<String, List<String>>> preGameAdvancements = new HashMap<>();

    public void setPreGameGroup(UUID uuid, String group) {
        preGameGroups.put(uuid, group);
    }
    public String getPreGameGroup(UUID uuid) {
        return preGameGroups.get(uuid);
    }

    public void setPreGameAdvancements(UUID uuid, Map<String, List<String>> advancements) {
        preGameAdvancements.put(uuid, advancements);
    }
    public Map<String, List<String>> getPreGameAdvancements(UUID uuid) {
        return preGameAdvancements.getOrDefault(uuid, Collections.emptyMap());
    }

    public Map<UUID, String> getPreGameGroups() {
        return Collections.unmodifiableMap(preGameGroups);
    }

    public Map<UUID, Map<String, List<String>>> getPreGameAdvancementsMap() {
        return Collections.unmodifiableMap(preGameAdvancements);
    }

    public void clearPreGameData() {
        preGameGroups.clear();
        preGameAdvancements.clear();
    }

    // ── Spectator methods ─────────────────────────────────────────

    public boolean addSpectator(UUID uuid) {
        if (spectators.contains(uuid)) return false;
        spectators.add(uuid);
        return true;
    }

    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
        spectatorPreGameGroups.remove(uuid);
        spectatorPreGameAdvancements.remove(uuid);
    }

    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    public int getSpectatorCount() {
        return spectators.size();
    }

    public void setSpectatorPreGameGroup(UUID uuid, String group) {
        spectatorPreGameGroups.put(uuid, group);
    }

    public String getSpectatorPreGameGroup(UUID uuid) {
        return spectatorPreGameGroups.get(uuid);
    }

    public void setSpectatorPreGameAdvancements(UUID uuid, Map<String, List<String>> advancements) {
        spectatorPreGameAdvancements.put(uuid, advancements);
    }

    public Map<String, List<String>> getSpectatorPreGameAdvancements(UUID uuid) {
        return spectatorPreGameAdvancements.getOrDefault(uuid, Collections.emptyMap());
    }

    public void clearSpectatorPreGameData() {
        spectatorPreGameGroups.clear();
        spectatorPreGameAdvancements.clear();
    }

    public UUID getId() { return id; }
    public String getMinigameId() { return minigameId; }
    public Minigame getMinigame() { return minigame; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public List<UUID> getMembers() { return Collections.unmodifiableList(members); }
    public Set<UUID> getReadyPlayers() { return Collections.unmodifiableSet(readyPlayers); }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public String getActiveWorldName() { return activeWorldName; }
    public void setActiveWorldName(String name) { this.activeWorldName = name; }
    public int getMemberCount() { return members.size(); }
}