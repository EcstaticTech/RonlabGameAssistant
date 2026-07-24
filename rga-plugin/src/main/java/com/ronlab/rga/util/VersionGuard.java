package com.ronlab.rga.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the running server's Paper API version against RGA's
 * minimum supported version before the plugin finishes enabling.
 *
 * <p>This is a pre-flight check only: it decides whether RGA should
 * start at all. It does not probe for specific API surfaces.
 *
 * <p>Paper now reports its API version via {@code Bukkit.getBukkitVersion()}
 * in a "X.Y.Z..." shape, e.g. {@code 26.1.2.build.69-stable} or
 * {@code 26.1.2-R0.1-SNAPSHOT} depending on build. This class only relies
 * on the leading X.Y.Z and ignores everything after it.
 */
public final class VersionGuard {

    // Minimum supported version: Paper API 26.1.2
    private static final int MIN_MAJOR = 26;
    private static final int MIN_MINOR = 1;
    private static final int MIN_PATCH = 2;

    // Matches the leading "26.1.2" out of strings like
    // "26.1.2.build.69-stable" or "26.1.2-R0.1-SNAPSHOT"
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private VersionGuard() {
    }

    public static boolean check(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();
        String rawVersion = Bukkit.getBukkitVersion();

        Matcher matcher = VERSION_PATTERN.matcher(rawVersion);
        if (!matcher.find()) {
            logger.warning("Could not parse server API version string '"
                    + rawVersion + "'. Skipping version check.");
            return true;
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null
                ? Integer.parseInt(matcher.group(3))
                : 0;

        if (isOlderThanMinimum(major, minor, patch)) {
            logger.severe("==========================================");
            logger.severe("RGA requires Paper API " + MIN_MAJOR + "."
                    + MIN_MINOR + "." + MIN_PATCH + " or newer.");
            logger.severe("Detected server API version: " + rawVersion);
            logger.severe("RGA will now disable itself.");
            logger.severe("==========================================");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return false;
        }

        if (isNewerThanTested(major, minor)) {
            logger.warning("RGA has not been tested on this server "
                    + "API version (" + rawVersion + "). "
                    + "Continuing, but please report any issues.");
        }

        return true;
    }

    private static boolean isOlderThanMinimum(int major, int minor, int patch) {
        if (major != MIN_MAJOR) {
            return major < MIN_MAJOR;
        }
        if (minor != MIN_MINOR) {
            return minor < MIN_MINOR;
        }
        return patch < MIN_PATCH;
    }

    private static boolean isNewerThanTested(int major, int minor) {
        return major > MIN_MAJOR || minor > MIN_MINOR;
    }
}
