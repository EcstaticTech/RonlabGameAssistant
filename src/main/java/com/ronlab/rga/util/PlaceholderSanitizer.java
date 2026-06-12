package com.ronlab.rga.util;

import java.util.Map;
import java.util.regex.Pattern;

public final class PlaceholderSanitizer {

    // Control characters plus shell/command separators
    private static final Pattern UNSAFE_CHARS =
            Pattern.compile("[\\x00-\\x1F\\x7F;&|\\n\\r]");

    private PlaceholderSanitizer() {
    }

    /** Strips characters that could alter command boundaries. */
    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return UNSAFE_CHARS.matcher(value).replaceAll("");
    }

    /**
     * Replaces each {@code %key%} in template with a sanitized value
     * from the map. Placeholders not present in the map are left as-is.
     */
    public static String interpolate(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%",
                    sanitize(entry.getValue()));
        }
        return result;
    }

    /**
     * Final defense-in-depth check before dispatch. Returns false if any
     * command-boundary characters survived interpolation (e.g. from an
     * unmapped placeholder or template authoring mistake).
     */
    public static boolean isSafeToExecute(String fullyInterpolatedCommand) {
        return fullyInterpolatedCommand != null
                && !UNSAFE_CHARS.matcher(fullyInterpolatedCommand).find();
    }
}
