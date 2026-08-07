package com.ronlab.rga.api.stats;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable data record representing a player's aggregated statistics for a specific minigame.
 */
public record PlayerMinigameStats(
        UUID playerUuid,
        String minigameId,
        int wins,
        int losses,
        int kills,
        int deaths,
        String metadata,
        Instant lastUpdated
) {
    public PlayerMinigameStats {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(minigameId, "minigameId cannot be null");
        if (metadata == null) {
            metadata = "{}";
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }
}
