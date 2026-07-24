package com.ronlab.rga.api;

import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.MinigameEvent;
import com.ronlab.rga.api.event.MinigameStartEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MinigameEventTest {

    @Test
    @DisplayName("MinigameStartEvent captures fields and respects cancellation state")
    void testMinigameStartEvent_FieldsAndCancellation() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        List<UUID> players = List.of(p1, p2);

        MinigameStartEvent event = new MinigameStartEvent(
                "block_shuffle",
                "Block Shuffle",
                "minigame_bs_123",
                players
        );

        assertEquals("block_shuffle", event.getMinigameId());
        assertEquals("Block Shuffle", event.getMinigameName());
        assertEquals("minigame_bs_123", event.getWorldName());
        assertEquals(2, event.getPlayerUuids().size());
        assertTrue(event.getPlayerUuids().contains(p1));
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("MinigameConcludeEvent allows score map mutation and cancellation")
    void testMinigameConcludeEvent_ScoresMutationAndCancellation() {
        UUID winner = UUID.randomUUID();
        UUID runnerUp = UUID.randomUUID();
        Map<UUID, Integer> initialScores = Map.of(winner, 10, runnerUp, 5);

        MinigameConcludeEvent event = new MinigameConcludeEvent(
                "block_shuffle",
                "Block Shuffle",
                "minigame_bs_123",
                List.of(winner, runnerUp),
                initialScores
        );

        assertEquals("block_shuffle", event.getMinigameId());
        assertEquals(2, event.getScores().size());
        assertEquals(10, event.getScores().get(winner));

        // Listener mutates scores map
        event.getScores().put(winner, 15);
        assertEquals(15, event.getScores().get(winner));

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }
}
