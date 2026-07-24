package com.ronlab.rga.util;

import java.util.regex.Pattern;

public final class WorldNameValidator {

    private static final Pattern SAFE_NAME =
            Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");

    private WorldNameValidator() {
    }

    /**
     * Filesystem/identifier-safe check only. Does not check whether the
     * world is actually loaded or configured — see callers for registry
     * lookups where relevant (e.g. /rga conclude).
     */
    public static boolean isValid(String worldName) {
        if (worldName == null || worldName.isEmpty() || worldName.contains("..") || worldName.contains("/") || worldName.contains("\\")) {
            return false;
        }
        return SAFE_NAME.matcher(worldName).matches();
    }
}
