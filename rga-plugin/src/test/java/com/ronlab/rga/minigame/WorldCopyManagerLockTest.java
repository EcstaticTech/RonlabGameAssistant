package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class WorldCopyManagerLockTest {

    private static File tempServerRoot;
    private static Server serverMock;

    @BeforeAll
    static void setupBukkitServerMock() throws Exception {
        tempServerRoot = Files.createTempDirectory("rga-worldcopy-lock-test").toFile();

        BukkitScheduler schedulerMock = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[] { BukkitScheduler.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("runTask")) {
                        Runnable r = (Runnable) args[1];
                        r.run();
                        return null;
                    }
                    return null;
                }
        );

        UnsafeValues unsafeMock = (UnsafeValues) Proxy.newProxyInstance(
                UnsafeValues.class.getClassLoader(),
                new Class<?>[] { UnsafeValues.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getMainLevelName")) {
                        return "world";
                    }
                    return null;
                }
        );

        serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getWorldContainer")) {
                        return tempServerRoot;
                    }
                    if (method.getName().equals("getLogger")) {
                        return Logger.getLogger("Minecraft");
                    }
                    if (method.getName().equals("getScheduler")) {
                        return schedulerMock;
                    }
                    if (method.getName().equals("getUnsafe")) {
                        return unsafeMock;
                    }
                    if (method.getName().equals("createWorld")) {
                        return null; // Stub world creation in unit test environment
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

    private static RGA createUnsafePluginInstance() throws Exception {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        RGA plugin = (RGA) unsafe.allocateInstance(RGA.class);

        Field loggerField = RGA.class.getSuperclass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(plugin, Logger.getLogger("RGA"));

        Field serverField = RGA.class.getSuperclass().getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(plugin, serverMock);

        return plugin;
    }

    @Test
    void templateLocks_returnsSameLockInstanceForSameTemplateName() throws Exception {
        RGA plugin = createUnsafePluginInstance();
        WorldCopyManager manager = new WorldCopyManager(plugin);

        ReentrantLock lock1 = manager.getTemplateLock("template_skyblock");
        ReentrantLock lock2 = manager.getTemplateLock("template_skyblock");
        ReentrantLock lockOther = manager.getTemplateLock("template_pvp");

        assertNotNull(lock1, "Lock should not be null");
        assertSame(lock1, lock2, "Should return identical lock instance for same template name");
        assertNotSame(lock1, lockOther, "Should return distinct lock instances for different template names");
    }

    @Test
    void copyTemplateWorld_acquiresAndReleasesLockDuringCopy() throws Exception {
        RGA plugin = createUnsafePluginInstance();
        WorldCopyManager manager = new WorldCopyManager(plugin);

        String templateName = "template_lock_test";
        File templateDir = new File(tempServerRoot, templateName);
        assertTrue(templateDir.mkdirs());

        File dummyFile = new File(templateDir, "level.dat");
        try (FileWriter writer = new FileWriter(dummyFile)) {
            writer.write("dummy world data");
        }

        Minigame minigame = new Minigame(
                "parkour", "Parkour Map", Material.GRASS_BLOCK, List.of(),
                1, 4, Minigame.WorldType.TEMPLATE, templateName,
                List.of(), List.of(), GameMode.SURVIVAL, false,
                Difficulty.NORMAL, Map.of(), false, false,
                false, false, false, 0
        );

        ReentrantLock lock = manager.getTemplateLock(templateName);
        assertFalse(lock.isLocked(), "Lock should not be locked initially");

        CompletableFuture<String> copyFuture = manager.copyTemplateWorld(minigame);
        copyFuture.get(5, TimeUnit.SECONDS);

        assertFalse(lock.isLocked(), "Lock should be released post copy");
    }

    @Test
    void copyTemplateWorld_releasesLockOnNonExistentTemplateFolder() throws Exception {
        RGA plugin = createUnsafePluginInstance();
        WorldCopyManager manager = new WorldCopyManager(plugin);

        String nonExistentTemplate = "template_non_existent";
        ReentrantLock lock = manager.getTemplateLock(nonExistentTemplate);

        Minigame minigame = new Minigame(
                "missing", "Missing Map", Material.STONE, List.of(),
                1, 4, Minigame.WorldType.TEMPLATE, nonExistentTemplate,
                List.of(), List.of(), GameMode.SURVIVAL, false,
                Difficulty.NORMAL, Map.of(), false, false,
                false, false, false, 0
        );

        CompletableFuture<String> copyFuture = manager.copyTemplateWorld(minigame);
        String result = copyFuture.get(2, TimeUnit.SECONDS);

        assertNull(result, "Copy should return null for missing template folder");
        assertFalse(lock.isLocked(), "Lock must remain unlocked when template folder does not exist");
    }

    @Test
    void templateLocks_executesSequentialLocksForConcurrentTemplateCopies() throws Exception {
        RGA plugin = createUnsafePluginInstance();
        WorldCopyManager manager = new WorldCopyManager(plugin);

        String templateName = "template_concurrent_test";
        File templateDir = new File(tempServerRoot, templateName);
        assertTrue(templateDir.mkdirs());

        File dummyFile = new File(templateDir, "level.dat");
        try (FileWriter writer = new FileWriter(dummyFile)) {
            writer.write("concurrent map content");
        }

        Minigame minigame = new Minigame(
                "arena", "Arena", Material.IRON_SWORD, List.of(),
                1, 4, Minigame.WorldType.TEMPLATE, templateName,
                List.of(), List.of(), GameMode.SURVIVAL, false,
                Difficulty.NORMAL, Map.of(), false, false,
                false, false, false, 0
        );

        ReentrantLock lock = manager.getTemplateLock(templateName);

        CompletableFuture<String> future1 = manager.copyTemplateWorld(minigame);
        CompletableFuture<String> future2 = manager.copyTemplateWorld(minigame);

        CompletableFuture.allOf(future1, future2).get(5, TimeUnit.SECONDS);

        assertFalse(lock.isLocked(), "Lock must be unlocked after both concurrent copies complete");
    }
}
