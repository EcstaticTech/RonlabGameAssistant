package com.ronlab.rga.session;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.util.StatusReportFormatter;
import com.ronlab.rga.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class OrphanSessionDetectionTest {

    private static Server serverMock;

    @BeforeAll
    static void setupServerMock() throws Exception {
        serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getPlayer")) {
                        return null;
                    }
                    if (method.getName().equals("getLogger")) {
                        return Logger.getLogger("Minecraft");
                    }
                    return null;
                }
        );

        setStaticField(Bukkit.class, "server", serverMock);
    }

    private static void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static RGA createUnsafePluginInstance(File dataFolder) throws Exception {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        RGA plugin = (RGA) unsafe.allocateInstance(RGA.class);

        Field loggerField = RGA.class.getSuperclass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(plugin, Logger.getLogger("RGA"));

        Field dataFolderField = RGA.class.getSuperclass().getDeclaredField("dataFolder");
        dataFolderField.setAccessible(true);
        dataFolderField.set(plugin, dataFolder);

        Field serverField = RGA.class.getSuperclass().getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(plugin, serverMock);

        return plugin;
    }

    @Test
    void loadOrphanedSessions_detectsAndFlagsOrphanedSessions() throws Exception {
        File tempDir = Files.createTempDirectory("rga-orphan-detection-test").toFile();
        RGA plugin = createUnsafePluginInstance(tempDir);

        SessionManager sessionManager = new SessionManager(plugin);

        Minigame minigame = new Minigame(
                "parkour", "Parkour Challenge", Material.FEATHER, List.of(),
                4, 1, Minigame.WorldType.TEMPLATE, "ParkourMap",
                List.of(), List.of(), GameMode.SURVIVAL, false,
                Difficulty.NORMAL, Map.of(), false, false,
                true, true, false, 0
        );

        Party party = new Party(UUID.randomUUID(), minigame);
        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();
        party.addMember(member1);
        party.addMember(member2);

        String orphanedWorldName = "minigame_parkour_99999999";
        sessionManager.saveSession(party, orphanedWorldName);

        File sessionFile = new File(tempDir, "sessions/" + orphanedWorldName + ".yml");
        assertTrue(sessionFile.exists(), "Session persistence file should be created");

        sessionManager.loadOrphanedSessions();

        assertTrue(sessionManager.isOrphanedSession(orphanedWorldName), "World should be flagged as orphaned");
        assertTrue(sessionManager.getOrphanedSessionWorlds().contains(orphanedWorldName));
        assertTrue(sessionManager.isPendingRecovery(member1));
        assertTrue(sessionManager.isPendingRecovery(member2));
    }

    @Test
    void statusReportFormatter_rendersOrphanedStatusTag() {
        StatusReportFormatter.SessionEntry activeSession = new StatusReportFormatter.SessionEntry("parkour", "minigame_parkour_1", 2, "[ACTIVE]");
        StatusReportFormatter.SessionEntry orphanSession = new StatusReportFormatter.SessionEntry("arena", "minigame_arena_2", 0, "[ORPHANED]");

        List<String> lines = StatusReportFormatter.buildLines(
                List.of(activeSession, orphanSession),
                List.of(),
                true,
                2,
                List.of("minigame_arena_2")
        );

        assertTrue(lines.stream().anyMatch(l -> l.contains("Status: [ACTIVE]")), "Formatter should output [ACTIVE] tag");
        assertTrue(lines.stream().anyMatch(l -> l.contains("Status: [ORPHANED]")), "Formatter should output [ORPHANED] tag");
        assertTrue(lines.stream().anyMatch(l -> l.contains("Pending orphaned recovery data: yes")));
    }

    @Test
    void deleteSession_purgesOrphanedStatusAndFiles() throws Exception {
        File tempDir = Files.createTempDirectory("rga-orphan-delete-test").toFile();
        RGA plugin = createUnsafePluginInstance(tempDir);

        SessionManager sessionManager = new SessionManager(plugin);

        Minigame minigame = new Minigame(
                "pvp", "PVP Arena", Material.DIAMOND_SWORD, List.of(),
                2, 1, Minigame.WorldType.VANILLA, null,
                List.of(), List.of(), GameMode.SURVIVAL, true,
                Difficulty.HARD, Map.of(), false, false,
                true, true, false, 0
        );

        Party party = new Party(UUID.randomUUID(), minigame);
        String targetWorld = "minigame_pvp_12345";
        sessionManager.saveSession(party, targetWorld);
        sessionManager.loadOrphanedSessions();

        assertTrue(sessionManager.isOrphanedSession(targetWorld));

        // Simulate /rga cleanupsession targetWorld
        sessionManager.deleteSession(targetWorld);

        assertFalse(sessionManager.isOrphanedSession(targetWorld), "Orphan status should be purged");
        assertFalse(new File(tempDir, "sessions/" + targetWorld + ".yml").exists(), "Session file should be deleted");
    }
}
