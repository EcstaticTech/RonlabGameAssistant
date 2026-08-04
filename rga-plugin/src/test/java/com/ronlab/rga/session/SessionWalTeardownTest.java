package com.ronlab.rga.session;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.session.audit.RuntimeSessionAuditor;
import net.ronlab.rga.core.utils.io.DefaultAsyncDirectoryDeleter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionWalTeardownTest {

    private File tempDir;
    private SessionManager sessionManager;
    private DefaultAsyncDirectoryDeleter directoryDeleter;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("rga-session-wal-test").toFile();

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getPlayer")) return null;
                    if (method.getName().equals("getLogger")) return Logger.getLogger("Minecraft");
                    return null;
                }
        );
        setStaticField(Bukkit.class, "server", serverMock);

        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        TestRGA plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);

        setPrivateField(plugin, "dataFolder", tempDir);
        setPrivateField(plugin, "logger", Logger.getLogger("RGA"));

        sessionManager = new SessionManager(plugin);
        directoryDeleter = new DefaultAsyncDirectoryDeleter();
    }

    @AfterEach
    void tearDown() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (directoryDeleter != null) {
            directoryDeleter.shutdown();
        }
    }

    @Test
    void deleteSession_removesBothYmlAndWalFiles() throws Exception {
        Minigame minigame = new Minigame(
                "test", "Test Minigame", Material.STONE, List.of(), 2, 2,
                Minigame.WorldType.VANILLA, null, List.of(), List.of(),
                GameMode.SURVIVAL, false, Difficulty.NORMAL, Map.of(),
                false, false, true, true, true, 4
        );

        Party party = new Party(UUID.randomUUID(), minigame);
        UUID member = UUID.randomUUID();
        party.addMember(member);
        party.setActiveWorldName("test-wal-world");

        sessionManager.saveSession(party, "test-wal-world");

        File sessionsDir = new File(tempDir, "sessions");
        File ymlFile = new File(sessionsDir, "test-wal-world.yml");
        File walFile = new File(sessionsDir, "test-wal-world.wal");

        assertTrue(ymlFile.exists(), "Session snapshot .yml file should exist");

        // Execute session deletion
        sessionManager.deleteSession("test-wal-world");

        // Wait up to 3 seconds for async NIO file deletion
        long deadline = System.currentTimeMillis() + 3000;
        while ((ymlFile.exists() || walFile.exists()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertFalse(ymlFile.exists(), "Session snapshot .yml file should be deleted");
        assertFalse(walFile.exists(), "Session .wal log file should be deleted");

        // Verify RuntimeSessionAuditor sees no phantom files
        RuntimeSessionAuditor auditor = new RuntimeSessionAuditor(sessionManager, directoryDeleter, sessionsDir);
        auditor.performAudit();
    }

    private static void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
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

    private static class TestRGA extends RGA {}
}
