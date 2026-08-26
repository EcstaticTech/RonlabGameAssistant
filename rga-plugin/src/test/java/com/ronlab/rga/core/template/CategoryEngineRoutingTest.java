package com.ronlab.rga.core.template;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import com.ronlab.rga.command.DefaultRGACommandRouter;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.MinigameManager;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CategoryEngineRoutingTest {

    private static File tempServerRoot;
    private static Server serverMock;
    private static Map<UUID, Player> playerMap = new HashMap<>();

    private RGA plugin;
    private TemplateDiscoveryService discoveryService;
    private PartyManager partyManager;
    private MinigameManager minigameManager;
    private DefaultRGACommandRouter commandRouter;

    @BeforeAll
    static void setupBukkit() throws Exception {
        tempServerRoot = Files.createTempDirectory("rga-routing-test").toFile();

        BukkitScheduler schedulerMock = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    if ("runTaskLater".equals(method.getName()) || "runTask".equals(method.getName())) {
                        Runnable r = (Runnable) args[1];
                        r.run();
                        return Proxy.newProxyInstance(
                                BukkitTask.class.getClassLoader(),
                                new Class<?>[]{BukkitTask.class},
                                (p, m, a) -> null
                        );
                    }
                    return null;
                }
        );

        org.bukkit.plugin.PluginManager pluginManagerMock = (org.bukkit.plugin.PluginManager) Proxy.newProxyInstance(
                org.bukkit.plugin.PluginManager.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.PluginManager.class},
                (proxy, method, args) -> null
        );

        serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getWorldContainer".equals(name)) return tempServerRoot;
                    if ("getLogger".equals(name)) return Logger.getLogger("Minecraft");
                    if ("getScheduler".equals(name)) return schedulerMock;
                    if ("getPluginManager".equals(name)) return pluginManagerMock;
                    if ("getPlayer".equals(name)) {
                        if (args != null && args.length == 1) {
                            if (args[0] instanceof UUID u) return playerMap.get(u);
                            if (args[0] instanceof String s) {
                                for (Player p : playerMap.values()) {
                                    if (p.getName().equalsIgnoreCase(s)) return p;
                                }
                            }
                        }
                    }
                    return null;
                }
        );

        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, serverMock);
    }

    private static Player createMockPlayer(UUID uuid, String name) {
        Player p = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String m = method.getName();
                    if ("getUniqueId".equals(m)) return uuid;
                    if ("getName".equals(m)) return name;
                    if ("hasPermission".equals(m)) return true;
                    if ("closeInventory".equals(m)) return null;
                    if ("sendMessage".equals(m)) return null;
                    return null;
                }
        );
        playerMap.put(uuid, p);
        return p;
    }

    @BeforeEach
    void setUp() throws Exception {
        playerMap.clear();

        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        plugin = (RGA) unsafe.allocateInstance(RGA.class);

        Field dataFolderField = RGA.class.getSuperclass().getDeclaredField("dataFolder");
        dataFolderField.setAccessible(true);
        dataFolderField.set(plugin, tempServerRoot);

        Field loggerField = RGA.class.getSuperclass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(plugin, Logger.getLogger("RGA"));

        Field serverField = RGA.class.getSuperclass().getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(plugin, serverMock);

        discoveryService = new TemplateDiscoveryService(plugin);
        minigameManager = (MinigameManager) unsafe.allocateInstance(MinigameManager.class);
        Field mgPluginField = MinigameManager.class.getDeclaredField("plugin");
        mgPluginField.setAccessible(true);
        mgPluginField.set(minigameManager, plugin);

        partyManager = (PartyManager) unsafe.allocateInstance(PartyManager.class);
        Field pmPluginField = PartyManager.class.getDeclaredField("plugin");
        pmPluginField.setAccessible(true);
        pmPluginField.set(partyManager, plugin);

        Field pmActiveParties = PartyManager.class.getDeclaredField("activeParties");
        pmActiveParties.setAccessible(true);
        pmActiveParties.set(partyManager, new HashMap<>());

        Field pmPlayerParties = PartyManager.class.getDeclaredField("playerParties");
        pmPlayerParties.setAccessible(true);
        pmPlayerParties.set(partyManager, new HashMap<>());

        Field pmQueuedPlayers = PartyManager.class.getDeclaredField("queuedPlayers");
        pmQueuedPlayers.setAccessible(true);
        pmQueuedPlayers.set(partyManager, new HashMap<>());

        Field pmSpectatorSnapshots = PartyManager.class.getDeclaredField("spectatorSnapshots");
        pmSpectatorSnapshots.setAccessible(true);
        pmSpectatorSnapshots.set(partyManager, new HashMap<>());

        commandRouter = new DefaultRGACommandRouter(plugin);

        Field pluginDiscovery = RGA.class.getDeclaredField("templateDiscoveryService");
        pluginDiscovery.setAccessible(true);
        pluginDiscovery.set(plugin, discoveryService);

        Field pluginMinigame = RGA.class.getDeclaredField("minigameManager");
        pluginMinigame.setAccessible(true);
        pluginMinigame.set(plugin, minigameManager);

        Field pluginParty = RGA.class.getDeclaredField("partyManager");
        pluginParty.setAccessible(true);
        pluginParty.set(plugin, partyManager);

        TestLobbyGui lobbyGui = new TestLobbyGui(plugin);
        Field pluginLobbyGui = RGA.class.getDeclaredField("lobbyGui");
        pluginLobbyGui.setAccessible(true);
        pluginLobbyGui.set(plugin, lobbyGui);

        Field pluginRouter = RGA.class.getDeclaredField("commandRouter");
        pluginRouter.setAccessible(true);
        pluginRouter.set(plugin, commandRouter);

        Minigame parkourEngine = new Minigame(
                "parkour", "Parkour", Material.RABBIT_FOOT, List.of(),
                1, 8, Minigame.WorldType.TEMPLATE, "1000blocks",
                List.of("leader: parkour start %leader% %template%"),
                List.of("leader: parkour stop"),
                GameMode.ADVENTURE, false, Difficulty.PEACEFUL,
                Map.of(), false, false, false, false, false, 0
        );

        Field minigamesField = MinigameManager.class.getDeclaredField("minigames");
        minigamesField.setAccessible(true);
        Map<String, Minigame> map = new LinkedHashMap<>();
        map.put("parkour", parkourEngine);
        minigamesField.set(minigameManager, map);
    }

    @Test
    void testParkourCategoryTemplateAutoResolvesEngineId() throws Exception {
        File parkourDir = new File(tempServerRoot, "templates/parkour/1000blocks");
        assertTrue(parkourDir.mkdirs());
        File mapYml = new File(parkourDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: 1000blocks\ncategory: parkour\nname: 1000 Blocks\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath().resolve("templates"));
        MapTemplateMetadata meta = discoveryService.get("1000blocks");
        assertNotNull(meta);
        assertEquals("parkour", meta.resolveEngineId());
        assertEquals("1000blocks", meta.id());
    }

    @Test
    void testCommandRouterDispatchesJoinWithEngineAndTemplate() {
        Player player = createMockPlayer(UUID.randomUUID(), "Alice");
        boolean dispatched = commandRouter.dispatchAction(player, "rga:join_minigame parkour 1000blocks");
        assertTrue(dispatched);

        Party party = partyManager.getPartyForPlayer(player.getUniqueId());
        assertNotNull(party);
        assertEquals("parkour", party.getMinigameId());
        assertEquals("1000blocks", party.getSelectedTemplateWorld());
    }

    @Test
    void testPartyManagerFallbackWhenTemplateIdPassedAsMinigameId() throws Exception {
        File parkourDir = new File(tempServerRoot, "templates/parkour/flow_parkour");
        assertTrue(parkourDir.mkdirs());
        File mapYml = new File(parkourDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: flow_parkour\ncategory: parkour\nname: Flow Parkour\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath().resolve("templates"));

        Player player = createMockPlayer(UUID.randomUUID(), "Bob");
        // Legacy or raw template join call: rga:join_minigame flow_parkour
        partyManager.joinMinigame(player, "flow_parkour");

        Party party = partyManager.getPartyForPlayer(player.getUniqueId());
        assertNotNull(party, "Party should be created through auto-resolution fallback");
        assertEquals("parkour", party.getMinigameId());
        assertEquals("flow_parkour", party.getSelectedTemplateWorld());
    }

    private static class TestLobbyGui extends com.ronlab.rga.party.LobbyGui {
        public TestLobbyGui(RGA plugin) { super(plugin); }
        @Override public void openLobby(Player player, Party party) {}
    }
}
