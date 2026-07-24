package com.ronlab.rga.api.event;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the outcome of attempting to conclude a minigame session.
 */
@NullMarked
public enum ConcludeResult {
    /**
     * The game was successfully concluded.
     */
    SUCCESS,

    /**
     * Conclusion was cancelled by an event listener (and active online members were remaining).
     */
    CANCELLED,

    /**
     * No active party/session was found for the specified world.
     */
    NOT_FOUND
}
