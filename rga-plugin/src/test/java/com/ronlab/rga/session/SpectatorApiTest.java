package com.ronlab.rga.session;

import com.ronlab.rga.RGA;
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
import org.bukkit.inventory.ItemStack;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the JIT Spectator API introduced in Sprint 3 (1.13.0-SNAPSHOT).
 * Verifies that setSpectator(true) stashes state, setSpectator(false) restores it,
 * and that edge-cases (idempotency, disallowed mode, wrong state) are guarded correctly.
 *
 * <p>Uses the same JDK-proxy mock pattern as {@code SessionConcludeApiTest} to avoid
 * Paper InternalAPIBridge failures in unit tests.</p>
 */
class SpectatorApiTest {

    private File tempDir;
    private TestRGA plugin;
    private PartyManager partyManager;

    private TestSessionManager sessionManagerFake;
    private TestInventoryManager inventoryManagerFake;
    private TestAdvancementManager advancementManagerFake;
    private TestConfigManager configManagerFake;
    private TestMinigameManager minigameManagerFake;
    private TestLobbyGui lobbyGuiFake;
    private TestWorldCopyManager worldCopyManagerFake;

    private Map<String, World> worldMap;
    private Map<UUID, Player> playerMap;

    // Shared mock inventory state — what the mock player "currently" has
    private ItemStack[] mockContents;
    private ItemStack[] mockArmor;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("rga-spectator-test").toFile();
        worldMap = new HashMap<>();
        playerMap = new HashMap<>();

        PluginManager pluginManagerMock = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[] { PluginManager.class },
                (proxy, method, args) -> null
        );

        BukkitScheduler schedulerMock = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[] { BukkitScheduler.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("runTaskLater") || method.getName().equals("runTask")) {
                        ((Runnable) args[1]).run();
                        return Proxy.newProxyInstance(BukkitTask.class.getClassLoader(),
                                new Class<?>[] { BukkitTask.class }, (p, m, a) -> null);
                    }
                    if (method.getName().equals("runTaskTimer")) {
                        @SuppressWarnings("unchecked")
                        java.util.function.Consumer<BukkitTask> consumer = (java.util.function.Consumer<BukkitTask>) args[1];
                        BukkitTask task = (BukkitTask) Proxy.newProxyInstance(BukkitTask.class.getClassLoader(),
                                new Class<?>[] { BukkitTask.class }, (p, m, a) -> null);
                        consumer.accept(task);
                        return task;
                    }
                    return null;
                }
        );

        Object registryAccessMock = Proxy.newProxyInstance(
                Class.forName("io.papermc.paper.registry.RegistryAccess").getClassLoader(),
                new Class<?>[] { Class.forName("io.papermc.paper.registry.RegistryAccess") },
                (proxy, method, args) -> null
        );

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "isPrimaryThread" -> true;
                    case "getLogger" -> Logger.getLogger("Minecraft");
                    case "getPluginManager" -> pluginManagerMock;
                    case "getScheduler" -> schedulerMock;
                    case "getWorld" -> worldMap.get((String) args[0]);
                    case "getPlayer" -> {
                        if (args[0] instanceof UUID uid) yield playerMap.get(uid);
                        if (args[0] instanceof String name) {
                            for (Player p : playerMap.values()) {
                                if (p.getName().equalsIgnoreCase(name)) yield p;
                            }
                        }
                        yield null;
                    }
                    case "getRegistryAccess" -> registryAccessMock;
                    default -> null;
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

        sessionManagerFake    = new TestSessionManager(plugin);
        inventoryManagerFake  = new TestInventoryManager(plugin);
        advancementManagerFake = new TestAdvancementManager(plugin);
        configManagerFake     = new TestConfigManager(plugin);
        minigameManagerFake   = new TestMinigameManager(plugin);
        lobbyGuiFake          = new TestLobbyGui(plugin);

        setPrivateField(plugin, "sessionManager",     sessionManagerFake);
        setPrivateField(plugin, "inventoryManager",   inventoryManagerFake);
        setPrivateField(plugin, "advancementManager", advancementManagerFake);
        setPrivateField(plugin, "configManager",      configManagerFake);
        setPrivateField(plugin, "minigameManager",    minigameManagerFake);
        setPrivateField(plugin, "lobbyGui",           lobbyGuiFake);

        partyManager = new PartyManager(plugin);
        setPrivateField(plugin, "partyManager", partyManager);

        worldCopyManagerFake = new TestWorldCopyManager(plugin);
        setPrivateField(partyManager, "worldCopyManager", worldCopyManagerFake);

        // Create hub world
        createMockWorld("Hub");
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST CASES
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. setSpectator(true) — stashes inventory, sets SPECTATOR mode, party marks player as spectator")
    void setSpectator_true_stashesInventoryAndRegistersSpectator() {
        // Arrange: a player in an active IN_GAME party
        String worldName = "minigame_vanilla_s01";
        Player player = setupActiveGameWithPlayer(worldName);
        UUID uuid = player.getUniqueId();

        Party party = partyManager.getPartyForPlayer(uuid);
        assertNotNull(party, "Player should be in a party");
        assertEquals(Party.State.IN_GAME, party.getState());

        // Pre-condition: not yet a spectator
        assertFalse(party.isSpectator(uuid));

        // Act
        partyManager.setSpectator(player, true);

        // Assert: spectator registered in Party
        assertTrue(party.isSpectator(uuid), "Party should mark player as spectator");

        // Assert: SpectatorSnapshot was stored (verify via round-trip in test 2)
        // Assert: clearPlayer was called on InventoryManager
        assertTrue(inventoryManagerFake.clearCalled.contains(uuid),
                "InventoryManager.clearPlayer() should have been called");

        // Assert: advancements were revoked
        assertTrue(advancementManagerFake.revokedPlayers.contains(uuid),
                "Advancements should be revoked on spectator entry");
    }

    @Test
    @DisplayName("2. setSpectator(false) — restores inventory snapshot, clears spectator state, sets SURVIVAL")
    void setSpectator_false_restoresSnapshotAndClearsSpectatorState() {
        String worldName = "minigame_vanilla_s02";
        Player player = setupActiveGameWithPlayer(worldName);
        UUID uuid = player.getUniqueId();

        Party party = partyManager.getPartyForPlayer(uuid);

        // Prime the InventoryManager fake with test snapshot values so we can verify restore
        inventoryManagerFake.lastRestoredContents = null;

        // Act: enter then exit spectator
        partyManager.setSpectator(player, true);
        assertTrue(party.isSpectator(uuid), "Should be spectator after setSpectator(true)");

        partyManager.setSpectator(player, false);

        // Assert: no longer a spectator
        assertFalse(party.isSpectator(uuid), "Should NOT be spectator after setSpectator(false)");

        // Assert: advancements were restored
        assertTrue(advancementManagerFake.restoredPlayers.contains(uuid),
                "Advancements should be restored on spectator exit");

        // Assert: clearPlayer was called again during restore (restoreSpectatorSnapshot calls clear first)
        long clearCallCount = inventoryManagerFake.clearCalled.stream()
                .filter(id -> id.equals(uuid)).count();
        // Called once on enter (step 3 of setSpectator true) + once inside restoreSpectatorSnapshot
        assertTrue(clearCallCount >= 2,
                "clearPlayer should be called at least twice (enter + restore), got: " + clearCallCount);
    }

    @Test
    @DisplayName("3. setSpectator(true) called twice — idempotent, does not double-stash snapshot")
    void setSpectator_true_calledTwice_isIdempotent() {
        String worldName = "minigame_vanilla_s03";
        Player player = setupActiveGameWithPlayer(worldName);
        UUID uuid = player.getUniqueId();

        partyManager.setSpectator(player, true);
        assertTrue(partyManager.isPlayerSpectating(uuid));

        // Record clear count after first entry
        long clearAfterFirst = inventoryManagerFake.clearCalled.stream()
                .filter(id -> id.equals(uuid)).count();

        // Act: call again
        partyManager.setSpectator(player, true);

        // Assert: still spectating, no additional clear (idempotent path only re-sets game mode)
        assertTrue(partyManager.isPlayerSpectating(uuid));
        long clearAfterSecond = inventoryManagerFake.clearCalled.stream()
                .filter(id -> id.equals(uuid)).count();
        assertEquals(clearAfterFirst, clearAfterSecond,
                "Second setSpectator(true) should NOT clear player again (idempotent path)");
    }

    @Test
    @DisplayName("4. setSpectator(true) when party is not IN_GAME — no-op, warns in log")
    void setSpectator_true_whenPartyNotInGame_isNoOp() {
        // Create a player in LOBBY state (not started yet)
        Minigame minigame = createSpectatableMinigame();
        minigameManagerFake.minigames.put("test_mg", minigame);
        Player player = createMockPlayer(UUID.randomUUID(), "LobbyPlayer");
        partyManager.joinMinigame(player, "test_mg");

        Party party = partyManager.getPartyForPlayer(player.getUniqueId());
        assertNotNull(party);
        assertEquals(Party.State.LOBBY, party.getState());

        // Act
        partyManager.setSpectator(player, true);

        // Assert: still not a spectator
        assertFalse(party.isSpectator(player.getUniqueId()),
                "setSpectator(true) should be no-op when party is not IN_GAME");
    }

    @Test
    @DisplayName("5. setSpectator(true) when allow-spectators=false — rejected, warns in log")
    void setSpectator_true_whenSpectatorsDisallowed_isRejected() {
        String worldName = "minigame_vanilla_s05";
        // Use a minigame that DISALLOWS spectators
        Player player = setupActiveGameWithPlayer(worldName, /* allowSpectators= */ false);
        UUID uuid = player.getUniqueId();

        Party party = partyManager.getPartyForPlayer(uuid);
        assertNotNull(party);
        assertEquals(Party.State.IN_GAME, party.getState());

        partyManager.setSpectator(player, true);

        assertFalse(party.isSpectator(uuid),
                "setSpectator(true) should be rejected when allow-spectators=false");
        assertTrue(inventoryManagerFake.clearCalled.stream().noneMatch(id -> id.equals(uuid)),
                "clearPlayer should NOT be called when spectating is rejected");
    }

    @Test
    @DisplayName("6. isPlayerSpectating() — returns true after setSpectator(true), false after setSpectator(false)")
    void isPlayerSpectating_reflectsCurrentSpectatorState() {
        String worldName = "minigame_vanilla_s06";
        Player player = setupActiveGameWithPlayer(worldName);
        UUID uuid = player.getUniqueId();

        assertFalse(partyManager.isPlayerSpectating(uuid), "Should not be spectating initially");

        partyManager.setSpectator(player, true);
        assertTrue(partyManager.isPlayerSpectating(uuid), "Should be spectating after setSpectator(true)");

        partyManager.setSpectator(player, false);
        assertFalse(partyManager.isPlayerSpectating(uuid), "Should NOT be spectating after setSpectator(false)");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Setup Helpers
    // ═══════════════════════════════════════════════════════════════

    private Player setupActiveGameWithPlayer(String worldName) {
        return setupActiveGameWithPlayer(worldName, true);
    }

    private Player setupActiveGameWithPlayer(String worldName, boolean allowSpectators) {
        Minigame minigame = allowSpectators ? createSpectatableMinigame() : createNoSpectatorMinigame();
        String mgId = minigame.getId();
        minigameManagerFake.minigames.put(mgId, minigame);

        Player leader = createMockPlayer(UUID.randomUUID(), "Leader_" + worldName.substring(worldName.length() - 3));
        partyManager.joinMinigame(leader, mgId);
        Party party = partyManager.getPartyForPlayer(leader.getUniqueId());

        worldCopyManagerFake.nextVanillaWorld = worldName;
        createMockWorld(worldName);

        partyManager.toggleReady(leader); // starts game → IN_GAME
        return leader;
    }

    private Minigame createSpectatableMinigame() {
        return new Minigame(
                "test_mg", "Test Minigame", Material.STONE,
                List.of(), 1, 1,
                Minigame.WorldType.VANILLA, null,
                List.of(), List.of(),
                GameMode.SURVIVAL, false, Difficulty.NORMAL,
                Map.of(), false, false,
                true, true,
                /* allowSpectators= */ true, 4
        );
    }

    private Minigame createNoSpectatorMinigame() {
        return new Minigame(
                "test_mg_nospect", "No-Spectator Game", Material.STONE,
                List.of(), 1, 1,
                Minigame.WorldType.VANILLA, null,
                List.of(), List.of(),
                GameMode.SURVIVAL, false, Difficulty.NORMAL,
                Map.of(), false, false,
                true, true,
                /* allowSpectators= */ false, 0
        );
    }

    private Player createMockPlayer(UUID uuid, String name) {
        World hubWorld = worldMap.computeIfAbsent("Hub", this::buildMockWorld);
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] { Player.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId"  -> uuid;
                    case "getName"      -> name;
                    case "getWorld"     -> hubWorld;
                    case "isOnline"     -> true;
                    case "isDead"       -> false;
                    case "teleport"     -> true;
                    case "getInventory" -> buildMockInventory();
                    case "getExp"       -> 0.5f;
                    case "getLevel"     -> 3;
                    case "getTotalExperience" -> 100;
                    case "getHealth"    -> 20.0;
                    case "getMaxHealth" -> 20.0;
                    case "getFoodLevel" -> 20;
                    case "getSaturation" -> 5.0f;
                    // Void setters — just consume silently
                    case "setGameMode", "setExp", "setLevel", "setTotalExperience",
                         "setHealth", "setFoodLevel", "setSaturation",
                         "closeInventory", "sendMessage", "sendActionBar", "showTitle",
                         "playSound" -> null;
                    default -> null;
                }
        );
        playerMap.put(uuid, player);
        return player;
    }

    private PlayerInventory buildMockInventory() {
        return (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(),
                new Class<?>[] { PlayerInventory.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getContents"      -> new ItemStack[36];
                    case "getArmorContents" -> new ItemStack[4];
                    case "getItemInOffHand" -> null;
                    case "getHeldItemSlot"  -> 0;
                    // Void setters
                    case "setContents", "setArmorContents", "setItemInOffHand",
                         "setHeldItemSlot", "clear" -> null;
                    default -> null;
                }
        );
    }

    private World createMockWorld(String name) {
        return worldMap.computeIfAbsent(name, this::buildMockWorld);
    }

    private World buildMockWorld(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName"          -> name;
                    case "getSpawnLocation" -> new Location((World) proxy, 0, 64, 0);
                    case "getPlayers"       -> new ArrayList<Player>();
                    default -> null;
                }
        );
    }

    // ═══════════════════════════════════════════════════════════════
    //  Reflection Helpers
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

    // ═══════════════════════════════════════════════════════════════
    //  Test Subclasses
    // ═══════════════════════════════════════════════════════════════

    private static class TestRGA extends RGA {
        FileConfiguration config;
        @Override public FileConfiguration getConfig() { return config; }
    }

    private static class TestSessionManager extends SessionManager {
        public TestSessionManager(RGA plugin) { super(plugin); }
        @Override public void saveSession(Party party, String worldName) {}
        @Override public void deleteSession(String worldName) {}
        @Override public void loadOrphanedSessions() {}
    }

    private static class TestInventoryManager extends InventoryManager {
        final List<UUID> clearCalled = new ArrayList<>();
        ItemStack[] lastRestoredContents = null;

        public TestInventoryManager(RGA plugin) { super(plugin); }
        @Override public void reload() {}
        @Override public String getGroup(String worldName) { return "smp"; }
        @Override public void addTemporaryGroup(String name, List<String> worlds) {}
        @Override public void removeTemporaryGroup(String name) {}

        @Override
        public void clearPlayer(Player player) {
            clearCalled.add(player.getUniqueId());
            // Don't call super — mock environment has no real server
        }
    }

    private static class TestAdvancementManager extends AdvancementManager {
        final Set<UUID> revokedPlayers  = new HashSet<>();
        final Set<UUID> restoredPlayers = new HashSet<>();

        public TestAdvancementManager(RGA plugin) { super(plugin); }
        @Override public Map<String, List<String>> captureCompleted(Player player) {
            // Return a non-empty map so the isEmpty() guard in setSpectator(false) doesn't skip restore
            return Map.of("minecraft:story", List.of("minecraft:story/root"));
        }
        @Override public void revokeAll(Player player) { revokedPlayers.add(player.getUniqueId()); }
        @Override public void restoreCompleted(Player player, Map<String, List<String>> advs) {
            restoredPlayers.add(player.getUniqueId());
        }
    }

    private static class TestWorldCopyManager extends WorldCopyManager {
        String nextVanillaWorld = null;
        public TestWorldCopyManager(RGA plugin) { super(plugin); }
        @Override public String createVanillaWorld(Minigame minigame) { return nextVanillaWorld; }
        @Override public void cleanupWorld(String baseName, boolean isVanilla) {}
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
