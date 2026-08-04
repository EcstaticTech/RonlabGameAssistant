package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import com.ronlab.rga.world.FirstVisitSpawn;
import com.ronlab.rga.world.WorldManager;
import com.ronlab.rga.world.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldSpawnOptimizationTest {

    private WorldCopyManager worldCopyManager;
    private WorldManager mockWorldManager;
    private World mockWorld;
    private Location lastSetSpawn;

    @BeforeEach
    void setUp() throws Exception {
        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getLogger")) return Logger.getLogger("Minecraft");
                    return null;
                }
        );
        setStaticField(Bukkit.class, "server", serverMock);

        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        TestRGA plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);

        mockWorldManager = new WorldManager(plugin);
        setPrivateField(plugin, "worldManager", mockWorldManager);
        setPrivateField(plugin, "logger", Logger.getLogger("RGA"));

        worldCopyManager = new WorldCopyManager(plugin);

        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "test_game_world";
                    if (method.getName().equals("setSpawnLocation")) {
                        if (args[0] instanceof Location loc) {
                            lastSetSpawn = loc;
                        } else if (args.length >= 3 && args[0] instanceof Number x && args[1] instanceof Number y && args[2] instanceof Number z) {
                            lastSetSpawn = new Location(mockWorld, x.doubleValue(), y.doubleValue(), z.doubleValue());
                        }
                        return true;
                    }
                    if (method.getName().equals("getSpawnLocation")) {
                        return lastSetSpawn != null ? lastSetSpawn : new Location(mockWorld, 0, 64, 0);
                    }
                    return null;
                }
        );
    }

    @Test
    void applyDeterministicSpawn_defaultsToVector100_whenNoTemplateSpawnDefined() {
        worldCopyManager.applyDeterministicSpawn(mockWorld, "non_existent_template");

        assertEquals(0.5, lastSetSpawn.getX(), 0.001);
        assertEquals(100.0, lastSetSpawn.getY(), 0.001);
        assertEquals(0.5, lastSetSpawn.getZ(), 0.001);
    }

    @Test
    void applyDeterministicSpawn_usesConfiguredTemplateSpawn_whenPresentInWorldSettings() throws Exception {
        FirstVisitSpawn fvs = new FirstVisitSpawn(12.5, 75.0, -45.5, 90.0f, 0.0f);
        WorldSettings settings = new WorldSettings(
                GameMode.SURVIVAL, true, World.Environment.NORMAL,
                Difficulty.NORMAL, "arena_template", true, -1, false,
                false, false, Map.of(), fvs
        );

        Map<String, WorldSettings> settingsMap = Map.of("arena_template", settings);
        setPrivateField(mockWorldManager, "worldSettings", settingsMap);

        worldCopyManager.applyDeterministicSpawn(mockWorld, "arena_template");

        assertEquals(12.5, lastSetSpawn.getX(), 0.001);
        assertEquals(75.0, lastSetSpawn.getY(), 0.001);
        assertEquals(-45.5, lastSetSpawn.getZ(), 0.001);
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
