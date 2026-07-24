package com.ronlab.rga.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldNameValidatorTest {

    @Test
    void rejectsPathTraversalSequences() {
        assertFalse(WorldNameValidator.validate("../world", false).isValid());
        assertFalse(WorldNameValidator.validate("..\\world", false).isValid());
        assertFalse(WorldNameValidator.validate("..", false).isValid());
        assertFalse(WorldNameValidator.validate(".", false).isValid());
        assertFalse(WorldNameValidator.validate("../../etc/passwd", false).isValid());

        var res = WorldNameValidator.validate("../world", false);
        assertTrue(res.getErrorMessage().startsWith("Security Violation"));
    }

    @Test
    void rejectsOSIllegalCharacters() {
        assertFalse(WorldNameValidator.validate("world:name", false).isValid());
        assertFalse(WorldNameValidator.validate("world?", false).isValid());
        assertFalse(WorldNameValidator.validate("world*", false).isValid());
        assertFalse(WorldNameValidator.validate("<dir>", false).isValid());
        assertFalse(WorldNameValidator.validate("world|pipe", false).isValid());
        assertFalse(WorldNameValidator.validate("world\"quote", false).isValid());
        assertFalse(WorldNameValidator.validate("world/sub", false).isValid());
        assertFalse(WorldNameValidator.validate("world\\sub", false).isValid());

        var res = WorldNameValidator.validate("world:name", false);
        assertTrue(res.getErrorMessage().startsWith("Security Violation"));
    }

    @Test
    void acceptsStandardMinecraftNamesWithCosmeticWarnings() {
        var spawnRes = WorldNameValidator.validate("Spawn World", false);
        assertTrue(spawnRes.isValid());
        assertTrue(spawnRes.hasWarnings());
        assertTrue(spawnRes.getWarnings().stream().anyMatch(w -> w.contains("spaces")));

        var dotRes = WorldNameValidator.validate(".test_world", false);
        assertTrue(dotRes.isValid());
        assertTrue(dotRes.hasWarnings());
        assertTrue(dotRes.getWarnings().stream().anyMatch(w -> w.contains("dot")));

        var normalRes = WorldNameValidator.validate("world_nether", false);
        assertTrue(normalRes.isValid());
        assertFalse(normalRes.hasWarnings());
    }

    @Test
    void enforcesStrictModeWhitelisting() {
        var nonStrict = WorldNameValidator.validate("Spawn World", false);
        assertTrue(nonStrict.isValid());

        var strict = WorldNameValidator.validate("Spawn World", true);
        assertFalse(strict.isValid());
        assertTrue(strict.getErrorMessage().startsWith("Format Violation"));

        var validStrict = WorldNameValidator.validate("world-123.map", true);
        assertTrue(validStrict.isValid());
    }

    @Test
    void verifiesBoundaryLengths() {
        String name255 = "a".repeat(255);
        assertTrue(WorldNameValidator.validate(name255, false).isValid());

        String name256 = "a".repeat(256);
        var res = WorldNameValidator.validate(name256, false);
        assertFalse(res.isValid());
        assertEquals("World name exceeds maximum length (255 characters).", res.getErrorMessage());
    }

    @Test
    void rejectsNullOrEmpty() {
        assertFalse(WorldNameValidator.validate(null, false).isValid());
        assertFalse(WorldNameValidator.validate("", false).isValid());
        assertFalse(WorldNameValidator.validate("   ", false).isValid());
    }

    @Test
    void sanitizesForFilesystemPreservingDotsAndHyphens() {
        assertEquals("Block.Shuffle_2", WorldNameValidator.sanitizeForFilesystem("Block.Shuffle 2"));
        assertEquals("manhunt_v1.0", WorldNameValidator.sanitizeForFilesystem("manhunt_v1.0"));
        assertEquals("unnamed", WorldNameValidator.sanitizeForFilesystem(null));
        assertEquals("test_world_name", WorldNameValidator.sanitizeForFilesystem("test:world?name"));
    }
}
