package com.ronlab.rga.core.template;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TemplateDiscoveryServiceTest {

    private static class TestRGA extends RGA {
        Logger logger = Logger.getLogger("TemplateDiscoveryServiceTest");

        @Override
        public Logger getLogger() {
            return logger;
        }
    }

    private TestRGA plugin;

    @BeforeEach
    void setUp() throws Exception {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);
        plugin.logger = Logger.getLogger("TemplateDiscoveryServiceTest");
    }

    @Test
    void testDiscoveryWithValidMapDescriptor(@TempDir Path tempDir) throws IOException {
        Path templatesDir = tempDir.resolve("templates");
        Files.createDirectories(templatesDir);

        Path mapFolder = templatesDir.resolve("parkour_alpha");
        Files.createDirectories(mapFolder);

        String mapYmlContent = """
                id: parkour_alpha
                category: parkour
                display-name: "&aParkour Alpha"
                icon: GOLD_BLOCK
                difficulty: HARD
                min-players: 1
                max-players: 4
                fall-threshold-y: 10.0
                lore:
                  - "&7First alpha map"
                """;
        Files.writeString(mapFolder.resolve("map.yml"), mapYmlContent);

        TemplateDiscoveryService service = new TemplateDiscoveryService(plugin);
        service.discoverTemplates(templatesDir);

        MapTemplateMetadata metadata = service.getTemplate("parkour_alpha");
        assertNotNull(metadata);
        assertEquals("parkour_alpha", metadata.id());
        assertEquals("parkour", metadata.category());
        assertEquals(Material.GOLD_BLOCK, metadata.icon());
        assertEquals("HARD", metadata.difficulty());
        assertEquals(1, metadata.minPlayers());
        assertEquals(4, metadata.maxPlayers());
        assertEquals(10.0, metadata.fallThresholdY());
    }

    @Test
    void testDiscoveryNestedCategorySubfolders(@TempDir Path tempDir) throws IOException {
        Path templatesDir = tempDir.resolve("templates");
        Path parkourCategoryDir = templatesDir.resolve("parkour");
        Path map1000Dir = parkourCategoryDir.resolve("1000blocks");
        Files.createDirectories(map1000Dir);

        String map1000Yml = """
                id: 1000blocks
                category: parkour
                display-name: "&e1000 Blocks Parkour"
                icon: GOLD_BLOCK
                difficulty: PEACEFUL
                min-players: 1
                max-players: 8
                fall-threshold-y: 10.0
                """;
        Files.writeString(map1000Dir.resolve("map.yml"), map1000Yml);

        Path minigamesCategoryDir = templatesDir.resolve("minigames");
        Path sumoDir = minigamesCategoryDir.resolve("sumo");
        Files.createDirectories(sumoDir);

        String sumoYml = """
                id: sumo
                category: minigames
                display-name: "&cSumo Arena"
                icon: SLIME_BLOCK
                difficulty: PEACEFUL
                min-players: 2
                max-players: 8
                fall-threshold-y: 50.0
                """;
        Files.writeString(sumoDir.resolve("map.yml"), sumoYml);

        TemplateDiscoveryService service = new TemplateDiscoveryService(plugin);
        service.discoverTemplates(templatesDir);

        assertEquals(2, service.getRegistry().size(), "Must discover maps nested inside category folders without registering category BARRIER items");
        assertNotNull(service.getTemplate("1000blocks"));
        assertEquals("parkour", service.getTemplate("1000blocks").category());
        assertNotNull(service.getTemplate("sumo"));
        assertEquals("minigames", service.getTemplate("sumo").category());
    }

    @Test
    void testDiscoveryFallbackWithMissingMapYml(@TempDir Path tempDir) throws IOException {
        Path templatesDir = tempDir.resolve("templates");
        Files.createDirectories(templatesDir);

        // A direct map folder (no category subfolder) missing map.yml
        Path mapFolder = templatesDir.resolve("corrupt_folder");
        Files.createDirectories(mapFolder); // missing map.yml

        TemplateDiscoveryService service = new TemplateDiscoveryService(plugin);
        service.discoverTemplates(templatesDir);

        MapTemplateMetadata fallback = service.getTemplate("corrupt_folder");
        assertNotNull(fallback, "Missing map.yml in a leaf folder must register fallback BARRIER item");
        assertEquals("corrupt_folder", fallback.id());
        assertEquals(Material.BARRIER, fallback.icon());
    }

    @Test
    void testRegisterMinigamesFromMinigameManager() {
        TemplateDiscoveryService service = new TemplateDiscoveryService(plugin);
        com.ronlab.rga.minigame.Minigame bingo = new com.ronlab.rga.minigame.Minigame(
                "bingo", "Bingo", Material.FILLED_MAP, List.of("&7Bingo lore"),
                8, 2, com.ronlab.rga.minigame.Minigame.WorldType.VANILLA, null,
                List.of(), List.of(), org.bukkit.GameMode.SURVIVAL, true,
                org.bukkit.Difficulty.EASY, Map.of(), false, false, true, true, true, 4
        );

        service.registerMinigames(List.of(bingo));

        MapTemplateMetadata meta = service.getTemplate("bingo");
        assertNotNull(meta, "Minigame bingo must be registered into template registry");
        assertEquals("bingo", meta.id());
        assertEquals("minigames", meta.category());
        assertEquals(Material.FILLED_MAP, meta.icon());
        assertEquals("EASY", meta.difficulty());
    }
}
