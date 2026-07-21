package com.ronlab.rga.party;

import com.ronlab.rga.minigame.Minigame;

import java.util.*;

public class Party {

    public enum State { LOBBY, IN_GAME }

    private final UUID id;
    private final String minigameId;
    private final Minigame minigame;
    private UUID leaderUuid;
    private final List<UUID> members;
    private final Set<UUID> readyPlayers;
    private State state;
    private String activeWorldName;

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
    }

    public void setReady(UUID uuid, boolean ready) {
        if (ready) readyPlayers.add(uuid);
        else readyPlayers.remove(uuid);
    }

    public boolean isReady(UUID uuid) {
        return readyPlayers.contains(uuid);
    }

    public boolean allReady() {
        if (members.size() < minigame.getMinPlayers()) return false;
        return readyPlayers.containsAll(members);
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
