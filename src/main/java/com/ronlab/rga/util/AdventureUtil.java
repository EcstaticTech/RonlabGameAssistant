package com.ronlab.rga.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for Adventure API component construction and
 * registry-safe material lookups.
 */
public final class AdventureUtil {

    private static final LegacyComponentSerializer AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    private AdventureUtil() {}

    // ── Colour / chat ─────────────────────────────────────────────

    /**
     * Describes a string that may contain legacy {@code &} colour codes
     * (e.g. {@code &c}, {@code &6}, {@code &l}) into an Adventure
     * {@link Component}.
     *
     * <p>Config files with familiar Bukkit colour codes continue to
     * work without changes.</p>
     */
    public static Component color(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        return AMPERSAND.deserialize(legacy);
    }

    /**
     * Convenience: converts every line in a list of legacy-colour
     * strings into Adventure {@link Component components}.
     */
    public static List<Component> color(List<String> legacyLines) {
        if (legacyLines == null) return List.of();
        List<Component> result = new ArrayList<>(legacyLines.size());
        for (String line : legacyLines) {
            result.add(color(line));
        }
        return result;
    }

    // ── Material lookup ───────────────────────────────────────────

    /**
     * Looks up a {@link Material} by its string name using the
     * registry-safe {@link Registry#MATERIAL} API.
     *
     * @param name     the material name (case-insensitive)
     * @param fallback material returned when the name is null or invalid
     * @return the matched material, or {@code fallback}
     */
    public static Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase().trim());
        Material material = Registry.MATERIAL.get(key);
        return material != null ? material : fallback;
    }
}