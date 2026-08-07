package com.ronlab.rga.api.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMinigameStatsTest {

    @Test
    @DisplayName("PlayerMinigameStats record creates valid instance and handles defaults")
    void testPlayerMinigameStatsRecord() {
        UUID uuid = UUID.randomUUID();
        PlayerMinigameStats stats = new PlayerMinigameStats(
                uuid,
                "sumo",
                5,
                2,
                10,
                4,
                "{\"elo\":1200}",
                Instant.ofEpochMilli(1000000L)
        );

        assertEquals(uuid, stats.playerUuid());
        assertEquals("sumo", stats.minigameId());
        assertEquals(5, stats.wins());
        assertEquals(2, stats.losses());
        assertEquals(10, stats.kills());
        assertEquals(4, stats.deaths());
        assertEquals("{\"elo\":1200}", stats.metadata());
        assertEquals(Instant.ofEpochMilli(1000000L), stats.lastUpdated());
    }

    @Test
    @DisplayName("PlayerMinigameStats rejects null playerUuid or minigameId")
    void testNullGuards() {
        assertThrows(NullPointerException.class, () ->
                new PlayerMinigameStats(null, "sumo", 0, 0, 0, 0, null, null)
        );
        assertThrows(NullPointerException.class, () ->
                new PlayerMinigameStats(UUID.randomUUID(), null, 0, 0, 0, 0, null, null)
        );
    }
}
