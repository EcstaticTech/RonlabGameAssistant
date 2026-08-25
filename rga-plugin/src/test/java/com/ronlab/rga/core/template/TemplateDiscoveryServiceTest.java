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
    void testDiscoveryFallbackWithMissingMapYml(@TempDir Path tempDir) throws IOException {
        Path templatesDir = tempDir.resolve("templates");
        Files.createDirectories(templatesDir);

        Path mapFolder = templatesDir.resolve("corrupt_folder");
        Files.createDirectories(mapFolder); // missing map.yml

        TemplateDiscoveryService service = new TemplateDiscoveryService(plugin);
        service.discoverTemplates(templatesDir);

        MapTemplateMetadata fallback = service.getTemplate("corrupt_folder");
        assertNotNull(fallback, "Missing map.yml must register fallback BARRIER item");
        assertEquals("corrupt_folder", fallback.id());
        assertEquals(Material.BARRIER, fallback.icon());
    }
}
