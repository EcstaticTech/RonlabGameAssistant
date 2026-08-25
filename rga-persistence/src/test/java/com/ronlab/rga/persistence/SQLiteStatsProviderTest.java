package com.ronlab.rga.persistence;

import com.ronlab.rga.api.stats.PlayerMinigameStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteStatsProviderTest {

    @TempDir
    Path tempDir;

    private SQLiteStatsProvider provider;
    private File dbFile;

    @BeforeEach
    void setUp() throws SQLException {
        dbFile = tempDir.resolve("test_rga.db").toFile();
        provider = new SQLiteStatsProvider(dbFile, Logger.getLogger("SQLiteStatsProviderTest"));
        provider.initialize();
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.shutdown();
        }
    }

    @Test
    @DisplayName("Initialization runs Flyway migrations and creates player_stats table")
    void testInitialize() {
        assertTrue(provider.isInitialized());
        assertTrue(dbFile.exists());
    }

    @Test
    @DisplayName("recordMatchResult inserts and updates match stats asynchronously")
    void testRecordMatchResultAndQuery() throws ExecutionException, InterruptedException {
        UUID playerUuid = UUID.randomUUID();
        String minigameId = "sumo";

        provider.recordMatchResult(playerUuid, minigameId, true, 3, 1, "{\"note\":\"test\"}");

        // Wait for async dbExecutor processing
        Optional<PlayerMinigameStats> statsOpt = provider.getPlayerStats(playerUuid, minigameId).get();
        assertTrue(statsOpt.isPresent());

        PlayerMinigameStats stats = statsOpt.get();
        assertEquals(playerUuid, stats.playerUuid());
        assertEquals("sumo", stats.minigameId());
        assertEquals(1, stats.wins());
        assertEquals(0, stats.losses());
        assertEquals(3, stats.kills());
        assertEquals(1, stats.deaths());
        assertEquals("{\"note\":\"test\"}", stats.metadata());

        // Update stats with another match
        provider.recordMatchResult(playerUuid, minigameId, false, 2, 2, "{\"note\":\"test2\"}");
        PlayerMinigameStats updated = provider.getPlayerStats(playerUuid, minigameId).get().orElseThrow();
        assertEquals(1, updated.wins());
        assertEquals(1, updated.losses());
        assertEquals(5, updated.kills());
        assertEquals(3, updated.deaths());
    }

    @Test
    @DisplayName("getTopPlayers returns ranked leaderboard sorted by wins descending")
    void testGetTopPlayers() throws ExecutionException, InterruptedException {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        provider.recordMatchResult(p1, "tag", true, 1, 0, null);
        provider.recordMatchResult(p2, "tag", true, 2, 0, null);
        provider.recordMatchResult(p2, "tag", true, 2, 0, null); // p2 has 2 wins
        provider.recordMatchResult(p3, "tag", false, 0, 1, null);

        List<PlayerMinigameStats> top = provider.getTopPlayers("tag", 10).get();
        assertEquals(3, top.size());
        assertEquals(p2, top.get(0).playerUuid());
        assertEquals(2, top.get(0).wins());
        assertEquals(p1, top.get(1).playerUuid());
        assertEquals(1, top.get(1).wins());
        assertEquals(p3, top.get(2).playerUuid());
        assertEquals(0, top.get(2).wins());
    }

    @Test
    @DisplayName("PERSIST-1: Dynamic map key validation accepts long map identifiers without truncation")
    void testDynamicMapKeyValidation() throws ExecutionException, InterruptedException {
        UUID playerUuid = UUID.randomUUID();
        // Dynamic map template ID exceeding 32 characters (46 chars)
        String longDynamicMapId = "parkour_alpha_super_extended_challenge_stage_99";

        provider.recordMatchResult(playerUuid, longDynamicMapId, true, 12, 2, "{\"map\":\"parkour_alpha_v99\"}");

        // Query stats for dynamic map key
        Optional<PlayerMinigameStats> statsOpt = provider.getPlayerStats(playerUuid, longDynamicMapId).get();
        assertTrue(statsOpt.isPresent(), "Match result for dynamic map ID must be recorded and retrievable");

        PlayerMinigameStats stats = statsOpt.get();
        assertEquals(playerUuid, stats.playerUuid());
        assertEquals(longDynamicMapId, stats.minigameId(), "Dynamic minigameId must match full string without truncation");
        assertEquals(1, stats.wins());
        assertEquals(0, stats.losses());
        assertEquals(12, stats.kills());
        assertEquals(2, stats.deaths());
        assertEquals("{\"map\":\"parkour_alpha_v99\"}", stats.metadata());

        // Verify top players for dynamic map key
        List<PlayerMinigameStats> topList = provider.getTopPlayers(longDynamicMapId, 5).get();
        assertEquals(1, topList.size());
        assertEquals(longDynamicMapId, topList.get(0).minigameId());
    }
}
