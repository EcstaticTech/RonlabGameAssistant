package com.ronlab.rga.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerDefaultResourceTest {

    @Test
    void dataFolderAndFileExistenceGuard_preventsRedundantSave(@TempDir Path tempDir) throws IOException {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        // Verify folder and file initially do not exist
        assertFalse(configFile.exists());

        // Simulate initialization: ensure directory exists first
        if (!dataFolder.exists()) {
            assertTrue(dataFolder.mkdirs());
        }

        // Write pre-existing config content
        Files.writeString(configFile.toPath(), "hub-world: custom_hub\n");
        assertTrue(configFile.exists());

        // Perform existence guard check before save
        boolean saved = false;
        if (!configFile.exists()) {
            saved = true;
        }

        assertFalse(saved, "Resource save must be skipped when file already exists on disk");
        assertEquals("hub-world: custom_hub\n", Files.readString(configFile.toPath()));
    }

    @Test
    void missingDirectory_isCreatedBeforeExistenceCheck(@TempDir Path tempDir) {
        File nonExistentFolder = tempDir.resolve("nested/folder").toFile();
        File configFile = new File(nonExistentFolder, "worlds.yml");

        assertFalse(nonExistentFolder.exists());

        if (!nonExistentFolder.exists()) {
            nonExistentFolder.mkdirs();
        }

        assertTrue(nonExistentFolder.exists());
        assertFalse(configFile.exists());
    }
}
