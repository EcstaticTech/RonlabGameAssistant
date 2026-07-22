package com.ronlab.rga.session;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    @Test
    void persistShutdownRecoveryWritesRecoveryFileAndLoadsItOnStartup() throws Exception {
        File tempDir = Files.createTempDirectory("rga-session-test").toFile();

        // Set up mock Bukkit server to avoid NullPointerException on Bukkit.getPlayer()
        Server serverMock = (Server) Proxy.newProxyInstance(
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
        
        // Inject serverMock directly into Bukkit.server to bypass Paper's buildInfo checks in setServer()
        setStaticField(Bukkit.class, "server", serverMock);

        // Instantiate TestRGA without invoking JavaPlugin constructor to avoid InternalAPIBridge failures
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        TestRGA plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);
        
        // Inject final/private fields into JavaPlugin via reflection
        setPrivateField(plugin, "dataFolder", tempDir);
        setPrivateField(plugin, "logger", Logger.getLogger("RGA"));

        SessionManager sessionManager = new SessionManager(plugin);

        Minigame minigame = new Minigame(
                "test",
                "Test Minigame",
                Material.STONE,
                List.of(),
                2,
                2,
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

        Party party = new Party(UUID.fromString("11111111-1111-1111-1111-111111111111"), minigame);
        UUID member = UUID.fromString("22222222-2222-2222-2222-222222222222");
        party.addMember(member);
        party.setActiveWorldName("test-world");
        party.setPreGameGroup(member, "smp");
        party.setPreGameAdvancements(member, Map.of("advancement", List.of("minecraft:story/root")));

        sessionManager.saveSession(party, "test-world");

        File recoveryFile = new File(plugin.getDataFolder(), "sessions/test-world.yml");
        assertTrue(recoveryFile.exists(), "Recovery file should be written to disk");

        sessionManager.loadOrphanedSessions();

        assertTrue(sessionManager.hasPendingRecoveries());
        assertEquals(2, sessionManager.getPendingRecoveryCount());
    }

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
        // Subclassed to allow RGA type compatibility, fields are injected dynamically
    }
}
