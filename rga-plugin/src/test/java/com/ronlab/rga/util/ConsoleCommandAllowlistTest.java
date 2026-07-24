package com.ronlab.rga.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleCommandAllowlistTest {

    @Test
    void allowsConfiguredCommandAndArguments() {
        assertTrue(ConsoleCommandAllowlist.isAllowed(List.of("say", "whitelist"), "say hello"));
        assertTrue(ConsoleCommandAllowlist.isAllowed(List.of("say", "whitelist add"), "whitelist add bob"));
    }

    @Test
    void rejectsCommandsNotOnAllowlist() {
        assertFalse(ConsoleCommandAllowlist.isAllowed(List.of("say"), "ban Steve"));
        assertFalse(ConsoleCommandAllowlist.isAllowed(List.of("say", "whitelist add"), "whitelist remove bob"));
    }

    @Test
    void normalizesLeadingSlashAndCase() {
        assertTrue(ConsoleCommandAllowlist.isAllowed(List.of("SAY"), "/say hello"));
    }
}
