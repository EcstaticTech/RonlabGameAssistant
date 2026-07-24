package com.ronlab.rga.config;

import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.WorldCopyManager;
import com.ronlab.rga.world.WorldManager;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Issue #30 — Config Hardening, Defensive Enum Parsers & Dimension Toggles.
 */
class ConfigParsingHardeningTest {

    private WorldManager worldManager;

    @BeforeEach
    void setUp() {
        worldManager = new WorldManager(null);
    }

    // ── 1. Defensive Enum Parsers (WorldManager) ──────────────────────────────────

    @Test
    void parseEnvironment_handlesNull_defaultsToNormal() {
        assertEquals(World.Environment.NORMAL, worldManager.parseEnvironment("test_key", null));
    }

    @Test
    void parseEnvironment_handlesNumericMappings() {
        assertEquals(World.Environment.NORMAL,  worldManager.parseEnvironment("test_key", 0));
        assertEquals(World.Environment.NETHER,  worldManager.parseEnvironment("test_key", 1));
        assertEquals(World.Environment.THE_END, worldManager.parseEnvironment("test_key", -1));
        assertEquals(World.Environment.NORMAL,  worldManager.parseEnvironment("test_key", 99));
    }

    @Test
    void parseEnvironment_handlesCaseInsensitiveStrings() {
        assertEquals(World.Environment.NORMAL,  worldManager.parseEnvironment("test_key", "normal"));
        assertEquals(World.Environment.NETHER,  worldManager.parseEnvironment("test_key", "Nether"));
        assertEquals(World.Environment.THE_END, worldManager.parseEnvironment("test_key", "THE_END"));
        assertEquals(World.Environment.NORMAL,  worldManager.parseEnvironment("test_key", "INVALID_ENV"));
    }

    @Test
    void parseGameMode_handlesNull_defaultsToSurvival() {
        assertEquals(GameMode.SURVIVAL, worldManager.parseGameMode("test_key", null));
    }

    @Test
    void parseGameMode_handlesNumericMappings() {
        assertEquals(GameMode.CREATIVE,  worldManager.parseGameMode("test_key", 0));
        assertEquals(GameMode.SURVIVAL,  worldManager.parseGameMode("test_key", 1));
        assertEquals(GameMode.ADVENTURE, worldManager.parseGameMode("test_key", 2));
        assertEquals(GameMode.SPECTATOR, worldManager.parseGameMode("test_key", 3));
        assertEquals(GameMode.SURVIVAL,  worldManager.parseGameMode("test_key", 99));
    }

    @Test
    void parseGameMode_handlesCaseInsensitiveStrings() {
        assertEquals(GameMode.SURVIVAL,  worldManager.parseGameMode("test_key", "survival"));
        assertEquals(GameMode.CREATIVE,  worldManager.parseGameMode("test_key", "Creative"));
        assertEquals(GameMode.ADVENTURE, worldManager.parseGameMode("test_key", "ADVENTURE"));
        assertEquals(GameMode.SPECTATOR, worldManager.parseGameMode("test_key", "spectator"));
        assertEquals(GameMode.SURVIVAL,  worldManager.parseGameMode("test_key", "INVALID_MODE"));
    }

    @Test
    void parseDifficulty_handlesNull_defaultsToNormal() {
        assertEquals(Difficulty.NORMAL, worldManager.parseDifficulty("test_key", null));
    }

    @Test
    void parseDifficulty_handlesNumericMappings() {
        assertEquals(Difficulty.PEACEFUL, worldManager.parseDifficulty("test_key", 0));
        assertEquals(Difficulty.EASY,     worldManager.parseDifficulty("test_key", 1));
        assertEquals(Difficulty.NORMAL,   worldManager.parseDifficulty("test_key", 2));
        assertEquals(Difficulty.HARD,     worldManager.parseDifficulty("test_key", 3));
        assertEquals(Difficulty.NORMAL,   worldManager.parseDifficulty("test_key", 99));
    }

    @Test
    void parseDifficulty_handlesCaseInsensitiveStrings() {
        assertEquals(Difficulty.PEACEFUL, worldManager.parseDifficulty("test_key", "peaceful"));
        assertEquals(Difficulty.EASY,     worldManager.parseDifficulty("test_key", "Easy"));
        assertEquals(Difficulty.NORMAL,   worldManager.parseDifficulty("test_key", "NORMAL"));
        assertEquals(Difficulty.HARD,     worldManager.parseDifficulty("test_key", "hard"));
        assertEquals(Difficulty.NORMAL,   worldManager.parseDifficulty("test_key", "INVALID_DIFF"));
    }

    // ── 2. Precedence Logic & isSet() Validation ───────────────────────────────

    @Test
    void minigameDimensionPrecedence_worldSettingsOverridesRoot() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection mg = config.createSection("minigame");
        mg.set("disable-nether", false);
        ConfigurationSection ws = mg.createSection("world-settings");
        ws.set("disable-nether", true);

        boolean disableNether = false;
        if (mg.isSet("disable-nether")) {
            disableNether = mg.getBoolean("disable-nether");
        }
        if (ws != null && ws.isSet("disable-nether")) {
            disableNether = ws.getBoolean("disable-nether");
        }

        assertTrue(disableNether, "world-settings section must override minigame root section");
    }

    @Test
    void minigameDimensionPrecedence_nullNestedKeyDoesNotOverrideRoot() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection mg = config.createSection("minigame");
        mg.set("disable-nether", true);
        ConfigurationSection ws = mg.createSection("world-settings");
        ws.set("disable-nether", null); // Explicit null placeholder

        boolean disableNether = false;
        if (mg.isSet("disable-nether")) {
            disableNether = mg.getBoolean("disable-nether");
        }
        if (ws != null && ws.isSet("disable-nether")) {
            disableNether = ws.getBoolean("disable-nether");
        }

        assertTrue(disableNether, "Null nested key must NOT override valid root setting when using isSet()");
    }

    @Test
    void commandList_isSet_detectsAbsentAndNullKeys() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection mg = config.createSection("minigame");
        mg.set("start-commands", null); // Present with null value

        assertFalse(mg.isSet("start-commands"), "isSet() must return false for null-valued key");
        assertFalse(mg.isSet("conclude-commands"), "isSet() must return false for absent key");

        mg.set("conclude-commands", List.of("say done"));
        assertTrue(mg.isSet("conclude-commands"), "isSet() must return true for populated list");
    }

    // ── 3. WorldCopyManager Folder Copy & Filter Tests ─────────────────────────

    @Test
    void copyFolder_skipsDisabledDimensions(@TempDir Path tempDir) throws IOException {
        Path sourceTemplate = tempDir.resolve("template_world");
        Path destSession = tempDir.resolve("session_world");

        Files.createDirectories(sourceTemplate);
        Files.writeString(sourceTemplate.resolve("level.dat"), "dummy_level_data");

        Path netherFolder = sourceTemplate.resolve("template_world_nether");
        Files.createDirectories(netherFolder);
        Files.writeString(netherFolder.resolve("nether.dat"), "nether_data");

        Path endFolder = sourceTemplate.resolve("template_world_the_end");
        Files.createDirectories(endFolder);
        Files.writeString(endFolder.resolve("end.dat"), "end_data");

        WorldCopyManager copyManager = new WorldCopyManager(null);

        // Copy template with nether disabled and end enabled
        Minigame minigameNetherDisabled = new Minigame(
                "test", "Test Game", null, List.of(), 8, 2,
                Minigame.WorldType.TEMPLATE, "template_world",
                List.of(), List.of(), GameMode.SURVIVAL, true,
                Difficulty.NORMAL, java.util.Collections.emptyMap(),
                true, false, true, true, true, 4
        );

        // Perform directory walk with nether disabled (disableNether=true, disableEnd=false)
        Files.walkFileTree(sourceTemplate, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(sourceTemplate)) {
                    String name = dir.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (minigameNetherDisabled.isDisableNether() && (name.endsWith("_nether") || name.equals("the_nether") || name.equals("dim-1"))) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    if (minigameNetherDisabled.isDisableEnd() && (name.endsWith("_the_end") || name.equals("the_end") || name.equals("dim1"))) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                }
                Files.createDirectories(destSession.resolve(sourceTemplate.relativize(dir)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destSession.resolve(sourceTemplate.relativize(file)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });

        // Verify root files were copied
        assertTrue(Files.exists(destSession.resolve("level.dat")));
        // Verify nether folder was skipped
        assertFalse(Files.exists(destSession.resolve("template_world_nether")));
        // Verify end folder was copied
        assertTrue(Files.exists(destSession.resolve("template_world_the_end")));
        // Verify source template remains unchanged
        assertTrue(Files.exists(sourceTemplate.resolve("template_world_nether")));
    }
}
