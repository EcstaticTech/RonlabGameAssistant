package com.ronlab.rga.util;

import java.util.regex.Pattern;

public final class WorldNameValidator {

    private static final Pattern SAFE_NAME =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private WorldNameValidator() {
    }

    /**
     * Filesystem/identifier-safe check only. Does not check whether the
     * world is actually loaded or configured — see callers for registry
     * lookups where relevant (e.g. /rga conclude).
     */
    public static boolean isValid(String worldName) {
        return worldName != null && SAFE_NAME.matcher(worldName).matches();
    }
}
