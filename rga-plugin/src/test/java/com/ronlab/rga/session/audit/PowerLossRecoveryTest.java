package com.ronlab.rga.session.audit;

import com.ronlab.rga.session.SessionPhase;
import com.ronlab.rga.session.SessionSnapshot;
import com.ronlab.rga.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PowerLossRecoveryTest {

    @Test
    void testReplayValidWalFile(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        sessionsDir.mkdirs();

        File walFile = new File(sessionsDir, "session_test_100.wal");
        UUID sessionUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String line = String.format("[%d] PHASE=IN_GAME UUID=%s MINIGAME=blockshuffle PLAYERS=%s%n",
                System.currentTimeMillis(), sessionUuid, playerUuid);
        Files.writeString(walFile.toPath(), line);

        PowerLossRecoveryHandler handler = new PowerLossRecoveryHandler(sessionsDir);
        List<SessionSnapshot> snapshots = handler.parseWalFile(walFile);

        assertEquals(1, snapshots.size());
        SessionSnapshot snapshot = snapshots.get(0);
        assertEquals(sessionUuid, snapshot.sessionUuid());
        assertEquals("blockshuffle", snapshot.minigameId());
        assertEquals(SessionPhase.IN_GAME, snapshot.currentPhase());
        assertEquals(1, snapshot.activePlayers().size());
        assertEquals(playerUuid, snapshot.activePlayers().get(0));
    }

    @Test
    void testQuarantineCorruptedWalFile(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        sessionsDir.mkdirs();

        File corruptedWal = new File(sessionsDir, "session_corrupted_999.wal");
        Files.writeString(corruptedWal.toPath(), "INVALID CORRUPTED WAL DATA");

        PowerLossRecoveryHandler handler = new PowerLossRecoveryHandler(sessionsDir);
        SessionManager sessionManager = new SessionManager(null);

        handler.processPowerLossRecovery(sessionManager);

        assertFalse(corruptedWal.exists(), "Corrupted WAL file should be moved out of sessions root");

        File corruptedFolder = new File(sessionsDir, "corrupted");
        assertTrue(corruptedFolder.exists() && corruptedFolder.isDirectory());

        File[] files = corruptedFolder.listFiles((dir, name) -> name.startsWith("session_corrupted_999.wal"));
        assertNotNull(files);
        assertTrue(files.length > 0, "Corrupted file should be quarantined into /sessions/corrupted/");
    }
}
