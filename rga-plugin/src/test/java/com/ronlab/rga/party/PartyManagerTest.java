package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.event.ConcludeResult;
import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.MinigameStartEvent;
import com.ronlab.rga.config.ConfigManager;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.MinigameManager;
import com.ronlab.rga.minigame.WorldCopyManager;
import com.ronlab.rga.player.AdvancementManager;
import com.ronlab.rga.player.InventoryManager;
import com.ronlab.rga.session.SessionManager;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PartyManagerTest {

    private File tempDir;
    private TestRGA plugin;
    private PartyManager partyManager;

    private TestWorldCopyManager worldCopyManagerFake;
    private TestSessionManager sessionManagerFake;
    private TestInventoryManager inventoryManagerFake;
    private TestAdvancementManager advancementManagerFake;
    private TestConfigManager configManagerFake;
    private TestMinigameManager minigameManagerFake;
    private TestLobbyGui lobbyGuiFake;

    private Map<String, World> worldMap;
    private Map<UUID, Player> playerMap;
    private Consumer<Event> eventListener;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("rga-party-test").toFile();

        worldMap = new HashMap<>();
        playerMap = new HashMap<>();
        eventListener = null;

        Object registryAccessMock = Proxy.newProxyInstance(
                Class.forName("io.papermc.paper.registry.RegistryAccess").getClassLoader(),
                new Class<?>[] { Class.forName("io.papermc.paper.registry.RegistryAccess") },
                (proxy, method, args) -> null
        );

        PluginManager pluginManagerMock = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[] { PluginManager.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("callEvent")) {
                        Event event = (Event) args[0];
                        if (eventListener != null) {
                            eventListener.accept(event);
                        }
                        return null;
                    }
                    return null;
                }
        );

        BukkitScheduler schedulerMock = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[] { BukkitScheduler.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("runTaskLater") || method.getName().equals("runTask")) {
                        Runnable runnable = (Runnable) args[1];
                        runnable.run();
                        return Proxy.newProxyInstance(
                                BukkitTask.class.getClassLoader(),
                                new Class<?>[] { BukkitTask.class },
                                (p, m, a) -> null
                        );
                    }
                    if (method.getName().equals("runTaskTimer")) {
                        @SuppressWarnings("unchecked")
                        Consumer<BukkitTask> consumer = (Consumer<BukkitTask>) args[1];
                        BukkitTask task = (BukkitTask) Proxy.newProxyInstance(
                                BukkitTask.class.getClassLoader(),
                                new Class<?>[] { BukkitTask.class },
                                (p, m, a) -> null
                        );
                        consumer.accept(task);
                        return task;
                    }
                    return null;
                }
        );

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getLogger")) {
                        return Logger.getLogger("Minecraft");
                    }
                    if (method.getName().equals("getPluginManager")) {
                        return pluginManagerMock;
                    }
                    if (method.getName().equals("getScheduler")) {
                        return schedulerMock;
                    }
                    if (method.getName().equals("getWorld")) {
                        String name = (String) args[0];
                        return worldMap.get(name);
                    }
                    if (method.getName().equals("getPlayer")) {
                        if (args[0] instanceof UUID uuid) {
                            return playerMap.get(uuid);
                        }
                        if (args[0] instanceof String name) {
                            for (Player p : playerMap.values()) {
                                if (p.getName().equalsIgnoreCase(name)) return p;
                            }
                        }
                        return null;
                    }
                    if (method.getName().equals("getRegistryAccess")) {
                        return registryAccessMock;
                    }
                    return null;
                }
        );
        setStaticField(Bukkit.class, "server", serverMock);

        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);

        setPrivateField(plugin, "dataFolder", tempDir);
        setPrivateField(plugin, "logger", Logger.getLogger("RGA"));
        setPrivateField(plugin, "server", serverMock);

        YamlConfiguration config = new YamlConfiguration();
        config.set("hub-world", "Hub");
        config.set("minigames.countdown.enabled", false);
        plugin.config = config;

        sessionManagerFake = new TestSessionManager(plugin);
        inventoryManagerFake = new TestInventoryManager(plugin);
        advancementManagerFake = new TestAdvancementManager(plugin);
        configManagerFake = new TestConfigManager(plugin);
        minigameManagerFake = new TestMinigameManager(plugin);
        lobbyGuiFake = new TestLobbyGui(plugin);

        setPrivateField(plugin, "sessionManager", sessionManagerFake);
        setPrivateField(plugin, "inventoryManager", inventoryManagerFake);
        setPrivateField(plugin, "advancementManager", advancementManagerFake);
        setPrivateField(plugin, "configManager", configManagerFake);
        setPrivateField(plugin, "minigameManager", minigameManagerFake);
        setPrivateField(plugin, "lobbyGui", lobbyGuiFake);

        partyManager = new PartyManager(plugin);

        worldCopyManagerFake = new TestWorldCopyManager(plugin);
        setPrivateField(partyManager, "worldCopyManager", worldCopyManagerFake);
    }

    private Player createMockPlayer(UUID uuid, String name) {
        World hubWorld = createMockWorld("Hub");
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] { Player.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "getWorld" -> hubWorld;
                    case "isOnline" -> true;
                    case "isDead" -> false;
                    case "teleport" -> true;
                    case "getInventory" -> Proxy.newProxyInstance(
                            PlayerInventory.class.getClassLoader(),
                            new Class<?>[] { PlayerInventory.class },
                            (p, m, a) -> null
                    );
                    default -> null;
                }
        );
        playerMap.put(uuid, player);
        return player;
    }

    private World createMockWorld(String name) {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getSpawnLocation" -> new Location((World) proxy, 0, 64, 0);
                    case "getPlayers" -> new ArrayList<Player>();
                    default -> null;
                }
        );
        worldMap.put(name, world);
        return world;
    }

    private Minigame createVanillaMinigame() {
        return new Minigame(
                "vanilla_test",
                "Vanilla Test",
                Material.DIRT,
                List.of("Description"),
                4, // maxPlayers
                1, // minPlayers
                Minigame.WorldType.VANILLA,
                null,
                List.of(),
                List.of(),
                GameMode.SURVIVAL,
                false,
                Difficulty.NORMAL,
                Map.of(),
                false,
                false,
                true,
                true,
                true,
                4
        );
    }

    private Minigame createTemplateMinigame() {
        return new Minigame(
                "template_test",
                "Template Test",
                Material.GRASS_BLOCK,
                List.of("Description"),
                4, // maxPlayers
                1, // minPlayers
                Minigame.WorldType.TEMPLATE,
                "template_world",
                List.of(),
                List.of(),
                GameMode.SURVIVAL,
                false,
                Difficulty.NORMAL,
                Map.of(),
                false,
                false,
                true,
                true,
                true,
                4
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. #31 Regression Failure Paths (2 scenarios)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void testStartGame_Vanilla_WorldLoadFailure_CleansUpSessionAndInventoryGroup() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createVanillaMinigame();

        minigameManagerFake.minigames.put("vanilla_test", minigame);
        partyManager.joinMinigame(leader, "vanilla_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        String worldName = "minigame_vanilla_123";
        worldCopyManagerFake.nextVanillaWorld = worldName;
        // Do NOT add worldName to worldMap so Bukkit.getWorld(worldName) returns null (world load failure)

        partyManager.toggleReady(leader);

        assertEquals(Party.State.LOBBY, party.getState());
        assertNull(party.getActiveWorldName());

        assertTrue(inventoryManagerFake.removedGroups.contains(worldName), "Temporary inventory group should be removed");
        assertTrue(worldCopyManagerFake.cleanedUpWorlds.contains(worldName), "World cleanup should be called");
        assertTrue(worldCopyManagerFake.cleanupWorldVanillaFlags.get(worldName), "Cleanup should specify vanilla");
        assertTrue(sessionManagerFake.deletedSessions.contains(worldName), "Session state should be deleted");
    }

    @Test
    void testStartGame_Template_WorldLoadFailure_CleansUpSessionAndInventoryGroup() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createTemplateMinigame();

        minigameManagerFake.minigames.put("template_test", minigame);
        partyManager.joinMinigame(leader, "template_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        String worldName = "minigame_template_123";
        worldCopyManagerFake.nextTemplateWorld = worldName;
        // Do NOT add worldName to worldMap so Bukkit.getWorld(worldName) returns null (world load failure)

        partyManager.toggleReady(leader);

        assertEquals(Party.State.LOBBY, party.getState());
        assertNull(party.getActiveWorldName());

        assertTrue(inventoryManagerFake.removedGroups.contains(worldName), "Temporary inventory group should be removed");
        assertTrue(worldCopyManagerFake.cleanedUpWorlds.contains(worldName), "Template world cleanup should be called");
        assertFalse(worldCopyManagerFake.cleanupWorldVanillaFlags.get(worldName), "Cleanup should specify non-vanilla template");
        assertTrue(sessionManagerFake.deletedSessions.contains(worldName), "Session state should be deleted");
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. #33 MinigameStartEvent Matrix (4 scenarios)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void testStartGame_Vanilla_EventNotCancelled_FiresEventAndCommitsState() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createVanillaMinigame();

        minigameManagerFake.minigames.put("vanilla_test", minigame);
        partyManager.joinMinigame(leader, "vanilla_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        String worldName = "minigame_vanilla_123";
        worldCopyManagerFake.nextVanillaWorld = worldName;
        createMockWorld(worldName);

        List<Event> firedEvents = new ArrayList<>();
        eventListener = firedEvents::add;

        partyManager.toggleReady(leader);

        assertEquals(Party.State.IN_GAME, party.getState());
        assertEquals(worldName, party.getActiveWorldName());

        boolean fired = firedEvents.stream().anyMatch(e -> e instanceof MinigameStartEvent startEvent
                && startEvent.getMinigameId().equals("vanilla_test")
                && startEvent.getWorldName().equals(worldName)
                && !startEvent.isCancelled());
        assertTrue(fired, "MinigameStartEvent should be fired and not cancelled");

        assertTrue(sessionManagerFake.savedSessions.contains(worldName), "Session snapshot should be saved");
        assertTrue(inventoryManagerFake.temporaryGroups.containsKey(worldName), "Temporary inventory group should be registered");
    }

    @Test
    void testStartGame_Vanilla_EventCancelled_AbortsGameStartAndRestoresLobby() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createVanillaMinigame();

        minigameManagerFake.minigames.put("vanilla_test", minigame);
        partyManager.joinMinigame(leader, "vanilla_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        String worldName = "minigame_vanilla_123";
        worldCopyManagerFake.nextVanillaWorld = worldName;
        createMockWorld(worldName);

        eventListener = event -> {
            if (event instanceof MinigameStartEvent startEvent) {
                startEvent.setCancelled(true);
            }
        };

        partyManager.toggleReady(leader);

        assertEquals(Party.State.LOBBY, party.getState());
        assertNull(party.getActiveWorldName());

        assertTrue(sessionManagerFake.deletedSessions.contains(worldName), "Session state should be deleted on cancellation");
        assertTrue(inventoryManagerFake.removedGroups.contains(worldName), "Temporary inventory group should be removed");
        assertTrue(worldCopyManagerFake.cleanedUpWorlds.contains(worldName), "World cleanup should be called");
    }

    @Test
    void testStartGame_Template_EventNotCancelled_FiresEventAndCommitsState() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createTemplateMinigame();

        minigameManagerFake.minigames.put("template_test", minigame);
        partyManager.joinMinigame(leader, "template_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        String worldName = "minigame_template_123";
        worldCopyManagerFake.nextTemplateWorld = worldName;
        createMockWorld(worldName);

        List<Event> firedEvents = new ArrayList<>();
        eventListener = firedEvents::add;

        partyManager.toggleReady(leader);

        assertEquals(Party.State.IN_GAME, party.getState());
        assertEquals(worldName, party.getActiveWorldName());

        boolean fired = firedEvents.stream().anyMatch(e -> e instanceof MinigameStartEvent startEvent
                && startEvent.getMinigameId().equals("template_test")
                && startEvent.getWorldName().equals(worldName)
                && !startEvent.isCancelled());
        assertTrue(fired, "MinigameStartEvent should be fired for TEMPLATE minigame");

        assertTrue(sessionManagerFake.savedSessions.contains(worldName), "Session snapshot should be saved");
    }

    @Test
    void testStartGame_Template_EventCancelled_AbortsGameStartAndCleansUpTemplate() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createTemplateMinigame();

        minigameManagerFake.minigames.put("template_test", minigame);
        partyManager.joinMinigame(leader, "template_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        String worldName = "minigame_template_123";
        worldCopyManagerFake.nextTemplateWorld = worldName;
        createMockWorld(worldName);

        eventListener = event -> {
            if (event instanceof MinigameStartEvent startEvent) {
                startEvent.setCancelled(true);
            }
        };

        partyManager.toggleReady(leader);

        assertEquals(Party.State.LOBBY, party.getState());
        assertNull(party.getActiveWorldName());

        assertTrue(worldCopyManagerFake.cleanedUpWorlds.contains(worldName), "Copied template world should be cleaned up");
        assertFalse(worldCopyManagerFake.cleanupWorldVanillaFlags.get(worldName), "Cleanup should be non-vanilla");
        assertTrue(sessionManagerFake.deletedSessions.contains(worldName), "Session state should be deleted");
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. #34 MinigameConcludeEvent Matrix (5 scenarios)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void testConcludeGame_EventNotCancelled_ReturnsSuccessAndCleansUp() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createVanillaMinigame();

        minigameManagerFake.minigames.put("vanilla_test", minigame);
        partyManager.joinMinigame(leader, "vanilla_test");

        String worldName = "minigame_vanilla_123";
        worldCopyManagerFake.nextVanillaWorld = worldName;
        createMockWorld(worldName);
        partyManager.toggleReady(leader); // IN_GAME

        List<Event> firedEvents = new ArrayList<>();
        eventListener = firedEvents::add;

        ConcludeResult result = partyManager.concludeGame(worldName);

        assertEquals(ConcludeResult.SUCCESS, result);
        assertNull(partyManager.getPartyForMinigame("vanilla_test"));

        boolean fired = firedEvents.stream().anyMatch(e -> e instanceof MinigameConcludeEvent concludeEvent
                && concludeEvent.getWorldName().equals(worldName)
                && !concludeEvent.isCancelled());
        assertTrue(fired, "MinigameConcludeEvent should be fired");

        assertTrue(sessionManagerFake.deletedSessions.contains(worldName), "Session should be deleted on conclude");
        assertTrue(worldCopyManagerFake.cleanedUpWorlds.contains(worldName), "World should be cleaned up on conclude");
    }

    @Test
    void testConcludeGame_EventCancelled_WithMembers_ReturnsCancelledAndPreservesSession() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createVanillaMinigame();

        minigameManagerFake.minigames.put("vanilla_test", minigame);
        partyManager.joinMinigame(leader, "vanilla_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        String worldName = "minigame_vanilla_123";
        worldCopyManagerFake.nextVanillaWorld = worldName;
        createMockWorld(worldName);
        partyManager.toggleReady(leader); // IN_GAME

        eventListener = event -> {
            if (event instanceof MinigameConcludeEvent concludeEvent) {
                concludeEvent.setCancelled(true);
            }
        };

        ConcludeResult result = partyManager.concludeGame(worldName);

        assertEquals(ConcludeResult.CANCELLED, result);
        assertNotNull(partyManager.getPartyForMinigame("vanilla_test"));
        assertEquals(Party.State.IN_GAME, party.getState());

        assertFalse(sessionManagerFake.deletedSessions.contains(worldName), "Session must be preserved when conclude is cancelled");
        assertFalse(worldCopyManagerFake.cleanedUpWorlds.contains(worldName), "World cleanup must not run when conclude is cancelled");
    }

    @Test
    void testConcludeGame_EventCancelled_ZeroMembers_BypassesCancellationAndLogsWarning() throws Exception {
        UUID offlineUuid = UUID.randomUUID();
        // Do NOT put offlineUuid into playerMap so Bukkit.getPlayer(offlineUuid) returns null!
        Minigame minigame = createVanillaMinigame();

        Party party = new Party(offlineUuid, minigame);
        String worldName = "minigame_vanilla_123";
        party.setActiveWorldName(worldName);
        party.setState(Party.State.IN_GAME);

        setPrivateFieldMapEntry(partyManager, "activeParties", minigame.getId(), party);
        setPrivateFieldMapEntry(partyManager, "playerParties", offlineUuid, party);

        eventListener = event -> {
            if (event instanceof MinigameConcludeEvent concludeEvent) {
                concludeEvent.setCancelled(true);
            }
        };

        ConcludeResult result = partyManager.concludeGame(worldName);

        assertEquals(ConcludeResult.SUCCESS, result);
        assertNull(partyManager.getPartyForMinigame("vanilla_test"));

        assertTrue(sessionManagerFake.deletedSessions.contains(worldName), "Session should be deleted when zero-member cancellation bypass triggers");
        assertTrue(worldCopyManagerFake.cleanedUpWorlds.contains(worldName), "World cleanup should run when zero-member cancellation bypass triggers");
    }

    @Test
    void testConcludeGame_PartyNotFound_ReturnsNotFound() {
        ConcludeResult result = partyManager.concludeGame("unknown_world_123");
        assertEquals(ConcludeResult.NOT_FOUND, result);
    }

    @Test
    void testConcludeGame_ScoresMapMutatedByListener_PassesMutatedScoresToCleanup() {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createVanillaMinigame();

        minigameManagerFake.minigames.put("vanilla_test", minigame);
        partyManager.joinMinigame(leader, "vanilla_test");

        String worldName = "minigame_vanilla_123";
        worldCopyManagerFake.nextVanillaWorld = worldName;
        createMockWorld(worldName);
        partyManager.toggleReady(leader); // IN_GAME

        Map<UUID, Integer> initialScores = new HashMap<>();
        initialScores.put(leader.getUniqueId(), 10);

        Map<UUID, Number> capturedScoresFromListener = new HashMap<>();

        eventListener = event -> {
            if (event instanceof MinigameConcludeEvent concludeEvent) {
                concludeEvent.getScores().put(leader.getUniqueId(), 999);
                capturedScoresFromListener.putAll(concludeEvent.getScores());
            }
        };

        ConcludeResult result = partyManager.concludeGame(worldName, initialScores);

        assertEquals(ConcludeResult.SUCCESS, result);
        assertEquals(999, capturedScoresFromListener.get(leader.getUniqueId()));
    }

    // ═══════════════════════════════════════════════════════════════
    // Reflection Helpers & Test Subclasses
    // ═══════════════════════════════════════════════════════════════

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field != null) {
            field.setAccessible(true);
            field.set(target, value);
        } else {
            throw new NoSuchFieldException("Field " + fieldName + " not found in class hierarchy");
        }
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private <K, V> void setPrivateFieldMapEntry(Object target, String fieldName, K key, V value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<K, V> map = (Map<K, V>) field.get(target);
        map.put(key, value);
    }

    private static class TestRGA extends RGA {
        FileConfiguration config;
        @Override
        public FileConfiguration getConfig() {
            return config;
        }
    }

    private static class TestSessionManager extends SessionManager {
        final Set<String> savedSessions = new HashSet<>();
        final Set<String> deletedSessions = new HashSet<>();

        public TestSessionManager(RGA plugin) { super(plugin); }

        @Override
        public void saveSession(Party party, String worldName) {
            savedSessions.add(worldName);
        }

        @Override
        public void deleteSession(String worldName) {
            deletedSessions.add(worldName);
        }

        @Override
        public void loadOrphanedSessions() {}
    }

    private static class TestInventoryManager extends InventoryManager {
        final Map<String, List<String>> temporaryGroups = new HashMap<>();
        final Set<String> removedGroups = new HashSet<>();

        public TestInventoryManager(RGA plugin) { super(plugin); }

        @Override
        public void reload() {}

        @Override
        public String getGroup(String worldName) {
            return "smp";
        }

        @Override
        public void addTemporaryGroup(String name, List<String> worlds) {
            temporaryGroups.put(name, worlds);
        }

        @Override
        public void removeTemporaryGroup(String name) {
            removedGroups.add(name);
        }
    }

    private static class TestWorldCopyManager extends WorldCopyManager {
        String nextVanillaWorld = null;
        String nextTemplateWorld = null;
        final Set<String> cleanedUpWorlds = new HashSet<>();
        final Map<String, Boolean> cleanupWorldVanillaFlags = new HashMap<>();

        public TestWorldCopyManager(RGA plugin) { super(plugin); }

        @Override
        public String createVanillaWorld(Minigame minigame) {
            return nextVanillaWorld;
        }

        @Override
        public CompletableFuture<String> copyTemplateWorld(Minigame minigame) {
            return copyTemplateWorld(minigame, minigame.getTemplateWorld());
        }

        @Override
        public CompletableFuture<String> copyTemplateWorld(Minigame minigame, String templateWorldOverride) {
            return CompletableFuture.completedFuture(nextTemplateWorld);
        }

        @Override
        public void cleanupWorld(String baseName, boolean isVanilla) {
            cleanedUpWorlds.add(baseName);
            cleanupWorldVanillaFlags.put(baseName, isVanilla);
        }
    }

    private static class TestAdvancementManager extends AdvancementManager {
        public TestAdvancementManager(RGA plugin) { super(plugin); }
        @Override public Map<String, List<String>> captureCompleted(Player player) { return Map.of(); }
        @Override public void revokeAll(Player player) {}
        @Override public void restoreCompleted(Player player, Map<String, List<String>> advs) {}
    }

    private static class TestConfigManager extends ConfigManager {
        public TestConfigManager(RGA plugin) { super(plugin); }
        @Override public void reload() {}
        @Override public String getHubWorld() { return "Hub"; }
        @Override public int getPartyGracePeriodDurationSeconds() { return 60; }
        @Override public boolean isPartyGracePeriodEnabled() { return true; }
        @Override public boolean isPartyGracePeriodAllowedInGame() { return true; }
        @Override public boolean isConsoleCommandAllowed(String command) { return true; }
    }

    private static class TestMinigameManager extends MinigameManager {
        final Map<String, Minigame> minigames = new HashMap<>();
        public TestMinigameManager(RGA plugin) { super(plugin); }
        @Override public void reload() {}
        @Override public Minigame getMinigame(String id) { return minigames.get(id); }
    }

    private static class TestLobbyGui extends LobbyGui {
        public TestLobbyGui(RGA plugin) { super(plugin); }
        @Override public void openLobby(Player player, Party party) {}
    }
}
