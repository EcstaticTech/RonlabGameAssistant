package com.ronlab.rga.config;

import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.world.WorldSettings;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConfigImmutabilityTest {

    @Test
    void minigame_collectionsAreUnmodifiable() {
        List<String> mutableLore = new ArrayList<>(List.of("Lore 1"));
        List<String> mutableStart = new ArrayList<>(List.of("console: start"));
        List<String> mutableConclude = new ArrayList<>(List.of("console: conclude"));
        Map<String, String> mutableRules = new HashMap<>(Map.of("keepInventory", "true"));

        Minigame minigame = new Minigame(
                "test", "Test Minigame", Material.STONE, mutableLore,
                4, 1, Minigame.WorldType.VANILLA, null,
                mutableStart, mutableConclude,
                GameMode.SURVIVAL, false, Difficulty.NORMAL, mutableRules,
                false, false, true, true, true, 4
        );

        // Verify UnsupportedOperationException on list/map mutations
        assertThrows(UnsupportedOperationException.class, () -> minigame.getDisplayLore().add("Extra Lore"));
        assertThrows(UnsupportedOperationException.class, () -> minigame.getStartCommands().add("console: extra"));
        assertThrows(UnsupportedOperationException.class, () -> minigame.getConcludeCommands().clear());
        assertThrows(UnsupportedOperationException.class, () -> minigame.getGamerules().put("doDaylightCycle", "false"));

        // Mutating original caller collections must not mutate Minigame internal state
        mutableStart.add("console: mutated");
        assertEquals(1, minigame.getStartCommands().size());
        assertFalse(minigame.getStartCommands().contains("console: mutated"));
    }

    @Test
    void worldSettings_gamerulesMapIsUnmodifiable() {
        Map<String, String> mutableRules = new HashMap<>(Map.of("doMobSpawning", "false"));
        WorldSettings settings = new WorldSettings(
                GameMode.SURVIVAL, true, World.Environment.NORMAL, Difficulty.NORMAL,
                "smp", false, -1, false, false, false, mutableRules
        );

        assertThrows(UnsupportedOperationException.class, () -> settings.getGamerules().put("pvp", "true"));

        mutableRules.put("keepInventory", "true");
        assertEquals(1, settings.getGamerules().size());
        assertFalse(settings.getGamerules().containsKey("keepInventory"));
    }

    @Test
    void partySession_retainsLaunchMinigameSnapshotAcrossManagerReload() {
        List<String> initialConcludeCommands = List.of("console: announce game over");
        Minigame initialMinigame = new Minigame(
                "speedrun", "Speedrun v1", Material.DIAMOND_SWORD, List.of(),
                2, 1, Minigame.WorldType.VANILLA, null,
                List.of("console: start"), initialConcludeCommands,
                GameMode.SURVIVAL, true, Difficulty.HARD, Map.of("keepInventory", "false"),
                false, false, true, true, false, 0
        );

        Party activeParty = new Party(UUID.randomUUID(), initialMinigame);
        assertEquals("Speedrun v1", activeParty.getMinigame().getName());

        // Simulate config reload producing a new Minigame instance for future sessions
        Minigame reloadedMinigame = new Minigame(
                "speedrun", "Speedrun v2 (Updated)", Material.DIAMOND_SWORD, List.of("New Description"),
                4, 2, Minigame.WorldType.VANILLA, null,
                List.of("console: start v2"), List.of("console: announce new winner"),
                GameMode.ADVENTURE, false, Difficulty.EASY, Map.of("keepInventory", "true"),
                true, true, true, true, true, 8
        );

        Map<String, Minigame> registry = new HashMap<>();
        registry.put("speedrun", reloadedMinigame);

        // Verify active party retains launch snapshot (v1)
        assertEquals("Speedrun v1", activeParty.getMinigame().getName());
        assertEquals(Difficulty.HARD, activeParty.getMinigame().getDifficulty());
        assertEquals("console: announce game over", activeParty.getMinigame().getConcludeCommands().get(0));

        // Verify new registry returns updated config (v2) for future sessions
        assertEquals("Speedrun v2 (Updated)", registry.get("speedrun").getName());
        assertEquals(Difficulty.EASY, registry.get("speedrun").getDifficulty());
    }
}
