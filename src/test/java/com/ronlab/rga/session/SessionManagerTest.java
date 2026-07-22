package com.ronlab.rga.session;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.party.Party;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    @Test
    void persistShutdownRecoveryWritesRecoveryFileAndLoadsItOnStartup() throws Exception {
        File tempDir = Files.createTempDirectory("rga-session-test").toFile();
        RGA plugin = new TestRGA(tempDir);
        SessionManager sessionManager = new SessionManager(plugin);

        Minigame minigame = new Minigame(
                "test",
                "Test Minigame",
                Material.STONE,
                List.of(),
                2,
                2,
                Minigame.WorldType.VANILLA,
                null,
                List.of(),
                List.of(),
                GameMode.SURVIVAL,
                false,
                Difficulty.NORMAL,
                Map.of(),
                false,
                false
        );

        Party party = new Party(UUID.fromString("11111111-1111-1111-1111-111111111111"), minigame);
        UUID member = UUID.fromString("22222222-2222-2222-2222-222222222222");
        party.addMember(member);
        party.setActiveWorldName("test-world");
        party.setPreGameGroup(member, "smp");
        party.setPreGameAdvancements(member, Map.of("advancement", List.of("minecraft:story/root")));

        sessionManager.persistShutdownRecovery(party, "test-world");

        File recoveryFile = new File(plugin.getDataFolder(), "recovery/test-world.yml");
        assertTrue(recoveryFile.exists(), "Recovery file should be written to disk");

        sessionManager.loadOrphanedSessions();

        assertTrue(sessionManager.hasPendingRecoveries());
        assertEquals(1, sessionManager.getPendingRecoveryCount());
    }

    private static class TestRGA extends RGA {
        private final File dataFolder;

        private TestRGA(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }
    }
}
