package com.ronlab.rga.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusReportFormatterTest {

    @Test
    void formatsStatusSections() {
        List<String> lines = StatusReportFormatter.buildLines(
                List.of(new StatusReportFormatter.SessionEntry("survival", "arena_1", 2)),
                List.of(new StatusReportFormatter.WorldEntry("hub", "LOADED", 0, "SURVIVAL", true)),
                true,
                1,
                Set.of("arena_1")
        );

        assertTrue(lines.contains("Active sessions:"));
        assertTrue(lines.contains(" - survival | World: arena_1 | Status: [ACTIVE] | Players: 2"));
        assertTrue(lines.contains("Loaded managed worlds:"));

        assertTrue(lines.contains(" - hub | State: LOADED | Players: 0 | Gamemode: SURVIVAL | PVP: on"));
        assertTrue(lines.contains(" - Pending orphaned recovery data: yes (1 player(s))"));
    }
}
