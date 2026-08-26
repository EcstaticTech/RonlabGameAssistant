package com.ronlab.rga.api.template;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record representing metadata descriptor for a map template.
 */
public record MapTemplateMetadata(
        String id,
        String category,
        String minigameId,
        Component displayName,
        Material icon,
        List<Component> lore,
        String difficulty,
        int minPlayers,
        int maxPlayers,
        List<Vector> spawnVectors,
        double fallThresholdY,
        Path templatePath
) {
    public MapTemplateMetadata {
        Objects.requireNonNull(id, "Map template ID cannot be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Map template ID cannot be blank");
        }

        category = (category == null || category.isBlank()) ? "minigames" : category.toLowerCase().trim();
        if (minigameId == null || minigameId.isBlank()) {
            minigameId = category.equalsIgnoreCase("parkour") ? "parkour" : id;
        } else {
            minigameId = minigameId.trim();
        }

        displayName = (displayName == null) ? Component.text(id) : displayName;
        icon = (icon == null) ? Material.BARRIER : icon;
        lore = (lore == null) ? List.of() : List.copyOf(lore);
        difficulty = (difficulty == null || difficulty.isBlank()) ? "NORMAL" : difficulty.toUpperCase().trim();
        if (minPlayers < 0) minPlayers = 1;
        if (maxPlayers < minPlayers) maxPlayers = Math.max(minPlayers, 1);
        spawnVectors = (spawnVectors == null) ? List.of() : List.copyOf(spawnVectors);
    }

    public MapTemplateMetadata(
            String id,
            String category,
            Component displayName,
            Material icon,
            List<Component> lore,
            String difficulty,
            int minPlayers,
            int maxPlayers,
            List<Vector> spawnVectors,
            double fallThresholdY,
            Path templatePath
    ) {
        this(id, category, null, displayName, icon, lore, difficulty, minPlayers, maxPlayers, spawnVectors, fallThresholdY, templatePath);
    }

    public String resolveEngineId() {
        return this.minigameId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String category = "minigames";
        private String minigameId;
        private Component displayName;
        private Material icon = Material.BARRIER;
        private List<Component> lore = new ArrayList<>();
        private String difficulty = "NORMAL";
        private int minPlayers = 1;
        private int maxPlayers = 16;
        private List<Vector> spawnVectors = new ArrayList<>();
        private double fallThresholdY = 0.0;
        private Path templatePath;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder minigameId(String minigameId) {
            this.minigameId = minigameId;
            return this;
        }

        public Builder displayName(Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = icon;
            return this;
        }

        public Builder lore(List<Component> lore) {
            this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
            return this;
        }

        public Builder addLore(Component line) {
            if (line != null) this.lore.add(line);
            return this;
        }

        public Builder difficulty(String difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder minPlayers(int minPlayers) {
            this.minPlayers = minPlayers;
            return this;
        }

        public Builder maxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
            return this;
        }

        public Builder spawnVectors(List<Vector> spawnVectors) {
            this.spawnVectors = spawnVectors != null ? new ArrayList<>(spawnVectors) : new ArrayList<>();
            return this;
        }

        public Builder addSpawnVector(Vector spawnVector) {
            if (spawnVector != null) this.spawnVectors.add(spawnVector);
            return this;
        }

        public Builder fallThresholdY(double fallThresholdY) {
            this.fallThresholdY = fallThresholdY;
            return this;
        }

        public Builder templatePath(Path templatePath) {
            this.templatePath = templatePath;
            return this;
        }

        public MapTemplateMetadata build() {
            return new MapTemplateMetadata(
                    id, category, minigameId, displayName, icon, lore, difficulty,
                    minPlayers, maxPlayers, spawnVectors, fallThresholdY, templatePath
            );
        }
    }
}
