package com.ronlab.rga.session;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.event.ConcludeResult;
import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.RGAGameRequestConcludeEvent;
import com.ronlab.rga.config.ConfigManager;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.MinigameManager;
import com.ronlab.rga.minigame.WorldCopyManager;
import com.ronlab.rga.party.LobbyGui;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.party.PartyManager;
import com.ronlab.rga.player.AdvancementManager;
import com.ronlab.rga.player.InventoryManager;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SessionConcludeApiTest {

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
    private Thread primaryTestThread;

    @BeforeEach
    void setUp() throws Exception {
        primaryTestThread = Thread.currentThread();
        tempDir = Files.createTempDirectory("rga-conclude-test").toFile();

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
                    if (method.getName().equals("isPrimaryThread")) {
                        return Thread.currentThread().equals(primaryTestThread);
                    }
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
        setPrivateField(plugin, "partyManager", partyManager);

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
                4, 1,
                Minigame.WorldType.VANILLA,
                null, List.of(), List.of(),
                GameMode.SURVIVAL, false, Difficulty.NORMAL,
                Map.of(), false, false, true, true, true, 4
        );
    }

    private Party setupActiveGame(String worldName) {
        Player leader = createMockPlayer(UUID.randomUUID(), "Leader");
        Minigame minigame = createVanillaMinigame();
        minigameManagerFake.minigames.put("vanilla_test", minigame);
        partyManager.joinMinigame(leader, "vanilla_test");
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());
        worldCopyManagerFake.nextVanillaWorld = worldName;
        createMockWorld(worldName);
        partyManager.toggleReady(leader); // Puts party IN_GAME
        return party;
    }

    @Test
    @DisplayName("1. Successful API Call fires request event, converts scores, executes teardown, returns SUCCESS")
    void testRequestSessionConclude_Successful() {
        String worldName = "minigame_vanilla_101";
        setupActiveGame(worldName);

        List<Event> firedEvents = new ArrayList<>();
        eventListener = firedEvents::add;

        UUID playerUuid = playerMap.keySet().iterator().next();
        Map<UUID, Number> rawScores = Map.of(playerUuid, 42);

        ConcludeResult result = plugin.requestSessionConclude(worldName, "Game ended normally", rawScores);

        assertEquals(ConcludeResult.SUCCESS, result);

        boolean requestEventFired = firedEvents.stream().anyMatch(e -> e instanceof RGAGameRequestConcludeEvent req
                && req.getWorldName().equals(worldName)
                && req.getReason().equals("Game ended normally")
                && req.getScores().get(playerUuid).equals(42));
        assertTrue(requestEventFired, "RGAGameRequestConcludeEvent should be fired with reason and raw scores");

        boolean concludeEventFired = firedEvents.stream().anyMatch(e -> e instanceof MinigameConcludeEvent conc
                && conc.getWorldName().equals(worldName));
        assertTrue(concludeEventFired, "MinigameConcludeEvent should also be fired during teardown");
    }

    @Test
    @DisplayName("2. Cancellation Veto: Listener sets cancelled=true, method returns CANCELLED, session remains active")
    void testRequestSessionConclude_CancelledByListener() {
        String worldName = "minigame_vanilla_102";
        Party party = setupActiveGame(worldName);

        eventListener = event -> {
            if (event instanceof RGAGameRequestConcludeEvent req) {
                req.setCancelled(true);
            }
        };

        ConcludeResult result = plugin.requestSessionConclude(worldName, "Attempt conclude", Map.of());

        assertEquals(ConcludeResult.CANCELLED, result);
        assertNotNull(partyManager.getPartyByWorld(worldName), "Party should still exist");
        assertNotEquals(Party.State.CONCLUDING, party.getState(), "Party state should not be CONCLUDING");
    }

    @Test
    @DisplayName("3. Thread Safety Guard: Calling from async thread throws IllegalStateException")
    void testRequestSessionConclude_AsyncThread_ThrowsException() throws ExecutionException, InterruptedException {
        String worldName = "minigame_vanilla_103";
        setupActiveGame(worldName);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            plugin.requestSessionConclude(worldName, "Async test", Map.of());
        });

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof IllegalStateException, "Cause should be IllegalStateException");
        assertTrue(ex.getCause().getMessage().contains("must be called on the main server thread"));
    }

    @Test
    @DisplayName("4. Already Concluding Guard: Calling on party in CONCLUDING state returns ALREADY_CONCLUDING")
    void testRequestSessionConclude_AlreadyConcluding() {
        String worldName = "minigame_vanilla_104";
        Party party = setupActiveGame(worldName);
        party.setState(Party.State.CONCLUDING);

        ConcludeResult result = plugin.requestSessionConclude(worldName, "Duplicate conclude", Map.of());

        assertEquals(ConcludeResult.ALREADY_CONCLUDING, result);
    }

    @Test
    @DisplayName("5. Score Truncation & Overflow Safety: Converts Double(3.7)->3 and Long(3000000000L)->Integer.MAX_VALUE")
    void testRequestSessionConclude_ScoreConversionSafeguards() {
        String worldName = "minigame_vanilla_105";
        setupActiveGame(worldName);

        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        Map<UUID, Number> rawScores = Map.of(
                player1, 3.7,
                player2, 3000000000L
        );

        Map<UUID, Number> capturedFinalScores = new HashMap<>();
        eventListener = event -> {
            if (event instanceof MinigameConcludeEvent conc) {
                capturedFinalScores.putAll(conc.getScores());
            }
        };

        ConcludeResult result = plugin.requestSessionConclude(worldName, "Score test", rawScores);

        assertEquals(ConcludeResult.SUCCESS, result);
        assertEquals(3, capturedFinalScores.get(player1), "Double 3.7 should truncate to int 3");
        assertEquals(Integer.MAX_VALUE, capturedFinalScores.get(player2), "Long exceeding Integer.MAX_VALUE should clamp to Integer.MAX_VALUE");
    }

    @Test
    @DisplayName("6. Non-Existent World: Calling with unmapped world name returns NOT_FOUND")
    void testRequestSessionConclude_NonExistentWorld() {
        ConcludeResult result = plugin.requestSessionConclude("non_existent_world_xyz", "Test", Map.of());
        assertEquals(ConcludeResult.NOT_FOUND, result);
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

    private static class TestRGA extends RGA {
        FileConfiguration config;
        @Override
        public FileConfiguration getConfig() {
            return config;
        }
    }

    private static class TestSessionManager extends com.ronlab.rga.session.SessionManager {
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
        public TestInventoryManager(RGA plugin) { super(plugin); }
        @Override public void reload() {}
        @Override public String getGroup(String worldName) { return "smp"; }
        @Override public void addTemporaryGroup(String name, List<String> worlds) {}
        @Override public void removeTemporaryGroup(String name) {}
    }

    private static class TestWorldCopyManager extends WorldCopyManager {
        String nextVanillaWorld = null;
        public TestWorldCopyManager(RGA plugin) { super(plugin); }
        @Override public String createVanillaWorld(Minigame minigame) { return nextVanillaWorld; }
        @Override public void cleanupWorld(String baseName, boolean isVanilla) {}
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
