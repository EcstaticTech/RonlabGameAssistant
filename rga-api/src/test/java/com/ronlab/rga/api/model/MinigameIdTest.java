package com.ronlab.rga.api.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinigameIdTest {

    @Test
    void testDefaultNamespace() {
        MinigameId id = MinigameId.of("manhunt");
        assertEquals("rga", id.namespace());
        assertEquals("manhunt", id.key());
        assertEquals("rga:manhunt", id.asString());
        assertEquals("rga:manhunt", id.toString());
    }

    @Test
    void testCustomNamespace() {
        MinigameId id = MinigameId.of("ronlab", "deathrace");
        assertEquals("ronlab", id.namespace());
        assertEquals("deathrace", id.key());
        assertEquals("ronlab:deathrace", id.asString());
    }

    @Test
    void testParseWithColon() {
        MinigameId id = MinigameId.parse("custom:blockshuffle");
        assertEquals("custom", id.namespace());
        assertEquals("blockshuffle", id.key());
        assertEquals("custom:blockshuffle", id.asString());
    }

    @Test
    void testParseWithoutColon() {
        MinigameId id = MinigameId.parse("parkour");
        assertEquals("rga", id.namespace());
        assertEquals("parkour", id.key());
        assertEquals("rga:parkour", id.asString());
    }

    @Test
    void testCaseInsensitivityAndTrimming() {
        MinigameId id = MinigameId.of("  RONLAB  ", "  DeathRace  ");
        assertEquals("ronlab", id.namespace());
        assertEquals("deathrace", id.key());
        assertEquals("ronlab:deathrace", id.asString());
    }

    @Test
    void testInvalidCharactersThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> MinigameId.of("Invalid Namespace!", "game"));
        assertThrows(IllegalArgumentException.class, () -> MinigameId.of("rga", "game with spaces"));
        assertThrows(IllegalArgumentException.class, () -> MinigameId.of("rga", ""));
    }

    @Test
    void testEquality() {
        MinigameId id1 = MinigameId.of("rga", "manhunt");
        MinigameId id2 = MinigameId.of("manhunt");
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
