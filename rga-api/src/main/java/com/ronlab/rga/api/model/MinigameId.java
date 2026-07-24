package com.ronlab.rga.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pure Java record representing a namespaced minigame identifier.
 * Completely decoupled from Bukkit server instances and plugin objects.
 * 
 * Example usage:
 * <pre>{@code
 * MinigameId id = MinigameId.of("blockshuffle"); // "rga:blockshuffle"
 * MinigameId customId = MinigameId.of("ronlab", "deathrace"); // "ronlab:deathrace"
 * }</pre>
 */
public record MinigameId(String namespace, String key) {

    public static final String DEFAULT_NAMESPACE = "rga";
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-z0-9_.-]+$");

    public MinigameId {
        if (namespace == null || namespace.isBlank()) {
            namespace = DEFAULT_NAMESPACE;
        }
        namespace = namespace.toLowerCase().trim();

        Objects.requireNonNull(key, "Minigame key cannot be null");
        key = key.toLowerCase().trim();

        if (key.isBlank()) {
            throw new IllegalArgumentException("Minigame key cannot be blank");
        }

        if (!VALID_IDENTIFIER.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace pattern: '" + namespace + "'. Must match [a-z0-9_.-]+");
        }
        if (!VALID_IDENTIFIER.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid minigame key pattern: '" + key + "'. Must match [a-z0-9_.-]+");
        }
    }

    /**
     * Create a MinigameId with explicit namespace and key.
     */
    public static MinigameId of(String namespace, String key) {
        return new MinigameId(namespace, key);
    }

    /**
     * Create a MinigameId under the default "rga" namespace.
     */
    public static MinigameId of(String key) {
        return new MinigameId(DEFAULT_NAMESPACE, key);
    }

    /**
     * Parse a string formatted as "namespace:key" or "key".
     */
    public static MinigameId parse(String input) {
        Objects.requireNonNull(input, "Input string cannot be null");
        String trimmed = input.trim();
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex == -1) {
            return of(trimmed);
        }
        String namespace = trimmed.substring(0, colonIndex);
        String key = trimmed.substring(colonIndex + 1);
        return of(namespace, key);
    }

    /**
     * Returns formatted identifier string: "namespace:key"
     */
    public String asString() {
        return namespace + ":" + key;
    }

    @Override
    public String toString() {
        return asString();
    }
}
