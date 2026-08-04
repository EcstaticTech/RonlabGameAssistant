package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class WorldCopyManagerDatapackStripTest {

    private static File tempServerRoot;
    private static Server serverMock;
    private static World dummyWorld;

    @BeforeAll
    static void setupBukkitServerMock() throws Exception {
        tempServerRoot = Files.createTempDirectory("rga-datapack-strip-test").toFile();

        dummyWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName": return "test_world";
                        case "getPlayers": return Collections.emptyList();
                        case "getSpawnLocation": return new Location(null, 0, 64, 0);
                        case "setSpawnLocation": return true;
                        case "setPVP": case "setDifficulty": case "setGameRule": return null;
                        default:
                            Class<?> returnType = method.getReturnType();
                            if (returnType.equals(boolean.class)) return Boolean.FALSE;
                            if (returnType.equals(int.class)) return 0;
                            if (returnType.equals(long.class)) return 0L;
                            if (returnType.equals(double.class)) return 0.0;
                            if (returnType.equals(float.class)) return 0.0f;
                            return null;
                    }
                }
        );

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
                        return dummyWorld;
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
    void copyTemplateWorld_stripsDatapacksDirectoryFromDestinationWorld() throws Exception {
        RGA plugin = createUnsafePluginInstance();
        WorldCopyManager manager = new WorldCopyManager(plugin);

        String templateName = "template_datapack_test";
        File templateDir = new File(tempServerRoot, templateName);
        assertTrue(templateDir.mkdirs());

        // Create standard world files
        File levelDat = new File(templateDir, "level.dat");
        try (FileWriter writer = new FileWriter(levelDat)) { writer.write("dummy level data"); }

        File regionDir = new File(templateDir, "region");
        assertTrue(regionDir.mkdirs());
        File chunkFile = new File(regionDir, "r.0.0.mca");
        try (FileWriter writer = new FileWriter(chunkFile)) { writer.write("dummy region data"); }

        // Create datapacks directory with nested files
        File datapacksDir = new File(templateDir, "datapacks/custom_pack/data");
        assertTrue(datapacksDir.mkdirs());
        File mcmeta = new File(templateDir, "datapacks/custom_pack/pack.mcmeta");
        try (FileWriter writer = new FileWriter(mcmeta)) { writer.write("dummy datapack mcmeta"); }

        Minigame minigame = new Minigame(
                "datapack_test", "Datapack Test", Material.GRASS_BLOCK, List.of(),
                1, 4, Minigame.WorldType.TEMPLATE, templateName,
                List.of(), List.of(), GameMode.SURVIVAL, false,
                Difficulty.NORMAL, Map.of(), false, false,
                false, false, false, 0
        );

        CompletableFuture<String> copyFuture = manager.copyTemplateWorld(minigame);
        String copiedWorldName = copyFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(copiedWorldName, "Copied world name should not be null");

        File destDir = new File(tempServerRoot, copiedWorldName);
        assertTrue(destDir.exists(), "Destination world directory should exist");
        assertTrue(new File(destDir, "level.dat").exists(), "level.dat should be preserved");
        assertTrue(new File(destDir, "region/r.0.0.mca").exists(), "Region files should be copied");

        File destDatapacksDir = new File(destDir, "datapacks");
        assertFalse(destDatapacksDir.exists(), "datapacks directory MUST be stripped from destination world");
    }

    @Test
    void copyTemplateWorld_preservesPaperNestedDimensionsWhileStrippingDatapacks() throws Exception {
        RGA plugin = createUnsafePluginInstance();
        WorldCopyManager manager = new WorldCopyManager(plugin);

        String templateName = "template_nested_dim_test";
        File templateDir = new File(tempServerRoot, templateName);
        assertTrue(templateDir.mkdirs());

        // Create Paper 26.1 nested dimension folders
        File netherDim = new File(templateDir, "dimensions/minecraft/the_nether/region");
        assertTrue(netherDim.mkdirs());
        File netherRegion = new File(netherDim, "r.0.0.mca");
        try (FileWriter writer = new FileWriter(netherRegion)) { writer.write("nether region data"); }

        // Create datapacks subfolder
        File datapacksDir = new File(templateDir, "datapacks");
        assertTrue(datapacksDir.mkdirs());

        Minigame minigame = new Minigame(
                "dim_test", "Nested Dim Test", Material.NETHERRACK, List.of(),
                1, 4, Minigame.WorldType.TEMPLATE, templateName,
                List.of(), List.of(), GameMode.SURVIVAL, false,
                Difficulty.NORMAL, Map.of(), false, false,
                false, false, false, 0
        );

        CompletableFuture<String> copyFuture = manager.copyTemplateWorld(minigame);
        String copiedWorldName = copyFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(copiedWorldName);
        File destDir = new File(tempServerRoot, copiedWorldName);
        assertTrue(new File(destDir, "dimensions/minecraft/the_nether/region/r.0.0.mca").exists(),
                "Nested dimensions must be preserved");
        assertFalse(new File(destDir, "datapacks").exists(),
                "datapacks directory MUST be stripped");
    }
}
