package com.ronlab.rga.util;

import java.util.List;
import java.util.Locale;

public final class ConsoleCommandAllowlist {

    private ConsoleCommandAllowlist() {}

    public static boolean isAllowed(List<String> allowlist, String command) {
        if (allowlist == null || allowlist.isEmpty()) {
            return true;
        }

        String normalizedCommand = normalize(command);
        if (normalizedCommand.isEmpty()) {
            return false;
        }

        for (String entry : allowlist) {
            if (entry == null) {
                continue;
            }

            String normalizedEntry = normalize(entry);
            if (normalizedEntry.isEmpty()) {
                continue;
            }

            if (normalizedCommand.equals(normalizedEntry)
                    || normalizedCommand.startsWith(normalizedEntry + " ")) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
