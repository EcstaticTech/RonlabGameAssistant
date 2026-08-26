package com.ronlab.rga.core.template;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import com.ronlab.rga.command.RGACommand;
import com.ronlab.rga.config.ConfigManager;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.WorldCopyManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TemplateStagingManagerTest {

    private static File tempServerRoot;
    private static Server serverMock;
    private static Map<String, World> loadedWorlds = new HashMap<>();

    private RGA plugin;
    private TemplateDiscoveryService discoveryService;
    private TemplateStagingManager stagingManager;
    private ConfigManager configManager;

    @BeforeAll
    static void setupBukkitServerMock() throws Exception {
        tempServerRoot = Files.createTempDirectory("rga-staging-test").toFile();

        UnsafeValues unsafeMock = (UnsafeValues) Proxy.newProxyInstance(
                UnsafeValues.class.getClassLoader(),
                new Class<?>[]{UnsafeValues.class},
                (proxy, method, args) -> {
                    if ("getMainLevelName".equals(method.getName())) {
                        return "world";
                    }
                    return null;
                }
        );

        serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUnsafe".equals(name)) {
                        return unsafeMock;
                    }
                    if ("getWorldContainer".equals(name)) {
                        return tempServerRoot;
                    }
                    if ("getLogger".equals(name)) {
                        return Logger.getLogger("Minecraft");
                    }
                    if ("getWorld".equals(name) && args != null && args.length == 1) {
                        return loadedWorlds.get(args[0]);
                    }
                    if ("createWorld".equals(name) && args != null && args.length == 1) {
                        WorldCreator creator = (WorldCreator) args[0];
                        World w = createMockWorld(creator.name());
                        loadedWorlds.put(creator.name(), w);
                        return w;
                    }
                    if ("unloadWorld".equals(name) && args != null && args.length == 2) {
                        World w = (World) args[0];
                        loadedWorlds.remove(w.getName());
                        return true;
                    }
                    return null;
                }
        );

        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, serverMock);
    }

    private static World createMockWorld(String worldName) {
        List<Player> players = new ArrayList<>();
        AtomicBoolean saved = new AtomicBoolean(false);

        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getName".equals(name)) return worldName;
                    if ("getPlayers".equals(name)) return players;
                    if ("getSpawnLocation".equals(name)) {
                        return new Location((World) proxy, 0, 64, 0);
                    }
                    if ("save".equals(name)) {
                        saved.set(true);
                        return null;
                    }
                    return null;
                }
        );
    }

    private static Player createMockPlayer(String name, UUID uuid, World world, boolean isOp) {
        AtomicReference<GameMode> gm = new AtomicReference<>(GameMode.SURVIVAL);
        AtomicReference<Location> loc = new AtomicReference<>(new Location(world, 0, 64, 0));
        AtomicReference<World> currentWorld = new AtomicReference<>(world);

        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if ("getName".equals(mName)) return name;
                    if ("getUniqueId".equals(mName)) return uuid;
                    if ("isOp".equals(mName)) return isOp;
                    if ("hasPermission".equals(mName) && args != null && args.length == 1) {
                        String perm = (String) args[0];
                        return isOp || "rga.admin".equals(perm) || "rga.admin.template".equals(perm);
                    }
                    if ("getGameMode".equals(mName)) return gm.get();
                    if ("setGameMode".equals(mName)) {
                        gm.set((GameMode) args[0]);
                        return null;
                    }
                    if ("getWorld".equals(mName)) return currentWorld.get();
                    if ("getLocation".equals(mName)) return loc.get();
                    if ("teleportAsync".equals(mName)) {
                        Location dest = (Location) args[0];
                        loc.set(dest);
                        if (dest.getWorld() != null) currentWorld.set(dest.getWorld());
                        return CompletableFuture.completedFuture(true);
                    }
                    if ("sendMessage".equals(mName)) return null;
                    return null;
                }
        );
    }

    @BeforeEach
    void setUp() throws Exception {
        loadedWorlds.clear();

        // Set up Hub world
        World hub = createMockWorld("hub");
        loadedWorlds.put("hub", hub);

        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        plugin = (RGA) unsafe.allocateInstance(RGA.class);

        Field loggerField = RGA.class.getSuperclass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(plugin, Logger.getLogger("RGA"));

        Field serverField = RGA.class.getSuperclass().getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(plugin, serverMock);

        discoveryService = new TemplateDiscoveryService(plugin);
        stagingManager = new TemplateStagingManager(plugin);

        configManager = new ConfigManager(plugin) {
            @Override
            public void reload() {}
            @Override
            public String getHubWorld() {
                return "hub";
            }
        };

        Field pluginConfigManager = RGA.class.getDeclaredField("configManager");
        pluginConfigManager.setAccessible(true);
        pluginConfigManager.set(plugin, configManager);

        Field pluginDiscoveryField = RGA.class.getDeclaredField("templateDiscoveryService");
        pluginDiscoveryField.setAccessible(true);
        pluginDiscoveryField.set(plugin, discoveryService);

        Field pluginStagingField = RGA.class.getDeclaredField("templateStagingManager");
        pluginStagingField.setAccessible(true);
        pluginStagingField.set(plugin, stagingManager);
    }

    @Test
    void testLoadTemplateForEditing_LoadsWorldAndSetsCreative() throws Exception {
        File templateDir = new File(tempServerRoot, "skywars_arena");
        assertTrue(templateDir.mkdirs());
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: skywars_arena\nname: Skywars Arena\nicon: DIAMOND_SWORD\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath());
        assertNotNull(discoveryService.get("skywars_arena"));

        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer("Operator1", playerUuid, loadedWorlds.get("hub"), true);

        stagingManager.loadTemplateForEditing(player, "skywars_arena");

        assertTrue(stagingManager.isTemplateEditing("skywars_arena"));
        assertEquals(GameMode.CREATIVE, player.getGameMode());
        assertNotNull(loadedWorlds.get("skywars_arena"));
    }

    @Test
    void testUnloadTemplate_TeleportsToHubAndUnloads() throws Exception {
        File templateDir = new File(tempServerRoot, "bedwars_castle");
        assertTrue(templateDir.mkdirs());
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: bedwars_castle\nname: Bedwars Castle\nicon: RED_BED\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath());

        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer("Operator1", playerUuid, loadedWorlds.get("hub"), true);
        stagingManager.loadTemplateForEditing(player, "bedwars_castle");

        assertTrue(stagingManager.isTemplateEditing("bedwars_castle"));

        stagingManager.unloadTemplate(player, "bedwars_castle");

        assertFalse(stagingManager.isTemplateEditing("bedwars_castle"));
        assertNull(loadedWorlds.get("bedwars_castle"));
    }

    @Test
    void testAutoUnloadOnDisconnect() throws Exception {
        File templateDir = new File(tempServerRoot, "duel_map");
        assertTrue(templateDir.mkdirs());
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: duel_map\nname: Duel Map\nicon: IRON_SWORD\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath());

        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer("Operator1", playerUuid, loadedWorlds.get("hub"), true);
        stagingManager.loadTemplateForEditing(player, "duel_map");

        assertTrue(stagingManager.isTemplateEditing("duel_map"));

        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, Component.text("Disconnected"));
        stagingManager.onPlayerQuit(quitEvent);

        assertFalse(stagingManager.isTemplateEditing("duel_map"));
        assertNull(loadedWorlds.get("duel_map"));
    }

    @Test
    void testWorldCopyManagerBlocksCloningDuringEditing() throws Exception {
        File templateDir = new File(tempServerRoot, "blockshuffle_arena");
        assertTrue(templateDir.mkdirs());
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: blockshuffle_arena\nname: Block Shuffle Arena\nicon: GRASS_BLOCK\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath());

        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer("Operator1", playerUuid, loadedWorlds.get("hub"), true);
        stagingManager.loadTemplateForEditing(player, "blockshuffle_arena");

        WorldCopyManager copyManager = new WorldCopyManager(plugin);
        Minigame mg = new Minigame(
                "blockshuffle", "Block Shuffle", Material.GRASS_BLOCK, List.of(),
                1, 8, Minigame.WorldType.TEMPLATE, "blockshuffle_arena",
                List.of(), List.of(), GameMode.SURVIVAL, false,
                Difficulty.NORMAL, Map.of(), false, false, false, false, false, 0
        );

        CompletableFuture<String> copyResult = copyManager.copyTemplateWorld(mg);
        String createdWorld = copyResult.get();

        assertNull(createdWorld, "WorldCopyManager must reject copying when template is actively open for editing");
    }

    @Test
    void testRGACommandTemplateSuggestions() {
        RGACommand rgaCommand = new RGACommand(plugin);

        File templateDir = new File(tempServerRoot, "test_template_1");
        templateDir.mkdirs();
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: test_template_1\nname: Test Template 1\n");
        } catch (Exception ignored) {}

        discoveryService.discoverTemplates(tempServerRoot.toPath());

        UUID playerUuid = UUID.randomUUID();
        Player adminPlayer = createMockPlayer("Admin", playerUuid, loadedWorlds.get("hub"), true);

        CommandSourceStack stack = (CommandSourceStack) Proxy.newProxyInstance(
                CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class},
                (proxy, method, args) -> {
                    if ("getSender".equals(method.getName())) return adminPlayer;
                    return null;
                }
        );

        Collection<String> subActions = rgaCommand.suggest(stack, new String[]{"template", ""});
        assertTrue(subActions.contains("load"));
        assertTrue(subActions.contains("tp"));
        assertTrue(subActions.contains("save"));
        assertTrue(subActions.contains("unload"));
        assertTrue(subActions.contains("list"));

        Collection<String> templateIds = rgaCommand.suggest(stack, new String[]{"template", "load", ""});
        assertTrue(templateIds.contains("test_template_1"));
    }

    @Test
    void testSaveTemplate_FlushesWorld() throws Exception {
        File templateDir = new File(tempServerRoot, "save_arena");
        assertTrue(templateDir.mkdirs());
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: save_arena\nname: Save Arena\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath());

        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer("Operator1", playerUuid, loadedWorlds.get("hub"), true);
        stagingManager.loadTemplateForEditing(player, "save_arena");

        assertDoesNotThrow(() -> stagingManager.saveTemplate(player, "save_arena"));
    }

    @Test
    void testOperatorTravelsToHub_DoesNotUnloadUntilQuit() throws Exception {
        File templateDir = new File(tempServerRoot, "hub_travel_map");
        assertTrue(templateDir.mkdirs());
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: hub_travel_map\nname: Hub Travel Map\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath());

        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer("Operator1", playerUuid, loadedWorlds.get("hub"), true);
        stagingManager.loadTemplateForEditing(player, "hub_travel_map");

        assertTrue(stagingManager.isTemplateEditing("hub_travel_map"));

        // Operator travels to Hub (simulated by changing player location/world to hub)
        player.teleportAsync(loadedWorlds.get("hub").getSpawnLocation());
        assertEquals("hub", player.getWorld().getName());

        // Template world must still remain loaded while operator is online
        assertTrue(stagingManager.isTemplateEditing("hub_travel_map"));
        assertNotNull(loadedWorlds.get("hub_travel_map"));

        // Operator disconnects from Hub
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, Component.text("Disconnected"));
        stagingManager.onPlayerQuit(quitEvent);

        // Now template is auto-unloaded
        assertFalse(stagingManager.isTemplateEditing("hub_travel_map"));
        assertNull(loadedWorlds.get("hub_travel_map"));
    }

    @Test
    void testRGACommandExecuteTemplateActions() throws Exception {
        RGACommand rgaCommand = new RGACommand(plugin);

        File templateDir = new File(tempServerRoot, "command_map");
        assertTrue(templateDir.mkdirs());
        File mapYml = new File(templateDir, "map.yml");
        try (FileWriter writer = new FileWriter(mapYml)) {
            writer.write("id: command_map\nname: Command Map\n");
        }

        discoveryService.discoverTemplates(tempServerRoot.toPath());

        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer("Operator1", playerUuid, loadedWorlds.get("hub"), true);

        CommandSourceStack stack = (CommandSourceStack) Proxy.newProxyInstance(
                CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class},
                (proxy, method, args) -> {
                    if ("getSender".equals(method.getName())) return player;
                    return null;
                }
        );

        // 1. /rga template load command_map
        rgaCommand.execute(stack, new String[]{"template", "load", "command_map"});
        assertTrue(stagingManager.isTemplateEditing("command_map"));

        // 2. /rga template tp command_map
        rgaCommand.execute(stack, new String[]{"template", "tp", "command_map"});
        assertEquals(GameMode.CREATIVE, player.getGameMode());

        // 3. /rga template save command_map
        rgaCommand.execute(stack, new String[]{"template", "save", "command_map"});

        // 4. /rga template list
        rgaCommand.execute(stack, new String[]{"template", "list"});

        // 5. /rga template unload command_map
        rgaCommand.execute(stack, new String[]{"template", "unload", "command_map"});
        assertFalse(stagingManager.isTemplateEditing("command_map"));
    }
}
