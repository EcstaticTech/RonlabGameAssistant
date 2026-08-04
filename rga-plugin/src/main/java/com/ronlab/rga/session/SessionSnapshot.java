package com.ronlab.rga.session;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record capturing point-in-time session state.
 * Used for safe thread boundary transfers to background workers.
 */
public record SessionSnapshot(
        UUID sessionUuid,
        String minigameId,
        List<UUID> activePlayers,
        SessionPhase currentPhase,
        long lastHeartbeatEpoch
) {
    public SessionSnapshot {
        Objects.requireNonNull(sessionUuid, "sessionUuid cannot be null");
        Objects.requireNonNull(minigameId, "minigameId cannot be null");
        Objects.requireNonNull(currentPhase, "currentPhase cannot be null");
        activePlayers = activePlayers != null ? List.copyOf(activePlayers) : List.of();
    }
}
