package com.ronlab.rga.api;

import com.ronlab.rga.api.event.ConcludeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcludeResultTest {

    @Test
    @DisplayName("ConcludeResult enum contains expected values and supports valueOf")
    void testConcludeResultEnumValues() {
        assertEquals(3, ConcludeResult.values().length);
        assertEquals(ConcludeResult.SUCCESS, ConcludeResult.valueOf("SUCCESS"));
        assertEquals(ConcludeResult.CANCELLED, ConcludeResult.valueOf("CANCELLED"));
        assertEquals(ConcludeResult.NOT_FOUND, ConcludeResult.valueOf("NOT_FOUND"));
    }
}
