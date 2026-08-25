package com.ronlab.rga.api.template;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapTemplateMetadataTest {

    @Test
    void testRecordConstructorValidationAndDefaults() {
        MapTemplateMetadata metadata = new MapTemplateMetadata(
                "test_map",
                null,
                null,
                null,
                null,
                null,
                -1,
                0,
                null,
                -10.0,
                Path.of("templates/test_map")
        );

        assertEquals("test_map", metadata.id());
        assertEquals("minigames", metadata.category());
        assertNotNull(metadata.displayName());
        assertEquals(Material.BARRIER, metadata.icon());
        assertTrue(metadata.lore().isEmpty());
        assertEquals("NORMAL", metadata.difficulty());
        assertEquals(1, metadata.minPlayers());
        assertEquals(1, metadata.maxPlayers());
        assertTrue(metadata.spawnVectors().isEmpty());
        assertEquals(-10.0, metadata.fallThresholdY());
    }

    @Test
    void testBuilderPattern() {
        MapTemplateMetadata metadata = MapTemplateMetadata.builder()
                .id("parkour_1")
                .category("parkour")
                .displayName(Component.text("Parkour One"))
                .icon(Material.GOLD_BLOCK)
                .addLore(Component.text("Difficulty: Hard"))
                .difficulty("HARD")
                .minPlayers(1)
                .maxPlayers(4)
                .addSpawnVector(new Vector(0, 64, 0))
                .fallThresholdY(40.0)
                .templatePath(Path.of("templates/parkour_1"))
                .build();

        assertEquals("parkour_1", metadata.id());
        assertEquals("parkour", metadata.category());
        assertEquals(Material.GOLD_BLOCK, metadata.icon());
        assertEquals(1, metadata.lore().size());
        assertEquals("HARD", metadata.difficulty());
        assertEquals(1, metadata.minPlayers());
        assertEquals(4, metadata.maxPlayers());
        assertEquals(1, metadata.spawnVectors().size());
        assertEquals(40.0, metadata.fallThresholdY());
    }

    @Test
    void testBlankIdThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new MapTemplateMetadata("", "cat", Component.text("A"), Material.STONE, List.of(), "EASY", 1, 2, List.of(), 0.0, null)
        );
    }
}
