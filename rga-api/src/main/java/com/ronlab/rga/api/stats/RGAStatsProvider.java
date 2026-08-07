package com.ronlab.rga.api.stats;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public contract interface for non-blocking statistics persistence and querying across rga-core and companion plugins.
 */
public interface RGAStatsProvider {

    /**
     * Dispatches a match result write to the async dbExecutor queue.
     *
     * @param playerUuid   the player's unique identifier
     * @param minigameId   the unique minigame identifier
     * @param won          true if the player won the match, false otherwise
     * @param kills        number of kills in the match
     * @param deaths       number of deaths in the match
     * @param metadataJson optional JSON metadata payload (e.g. companion metrics)
     */
    void recordMatchResult(UUID playerUuid, String minigameId, boolean won, int kills, int deaths, String metadataJson);

    /**
     * Asynchronously fetches aggregated player statistics for a specific minigame.
     *
     * @param playerUuid the player's unique identifier
     * @param minigameId the minigame identifier
     * @return a CompletableFuture containing an Optional with player stats if present
     */
    CompletableFuture<Optional<PlayerMinigameStats>> getPlayerStats(UUID playerUuid, String minigameId);

    /**
     * Asynchronously fetches top leaderboard stats for a minigame sorted by wins descending.
     *
     * @param minigameId the minigame identifier
     * @param limit      maximum number of top records to return
     * @return a CompletableFuture containing the list of top player stats
     */
    CompletableFuture<List<PlayerMinigameStats>> getTopPlayers(String minigameId, int limit);
}
