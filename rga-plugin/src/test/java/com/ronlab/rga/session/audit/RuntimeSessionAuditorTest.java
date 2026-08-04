package com.ronlab.rga.session.audit;

import com.ronlab.rga.session.SessionManager;
import net.ronlab.rga.core.utils.io.DefaultAsyncDirectoryDeleter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeSessionAuditorTest {

    private DefaultAsyncDirectoryDeleter directoryDeleter;
    private RuntimeSessionAuditor auditor;

    @BeforeEach
    void setUp() {
        directoryDeleter = new DefaultAsyncDirectoryDeleter();
    }

    @AfterEach
    void tearDown() {
        if (auditor != null) {
            auditor.stopAuditing();
        }
        if (directoryDeleter != null) {
            directoryDeleter.shutdown();
        }
    }

    @Test
    void testExtractSessionIdNormalizesExtensions() {
        SessionManager mockSessionManager = new SessionManager(null);
        auditor = new RuntimeSessionAuditor(mockSessionManager, directoryDeleter, new File("."));

        assertEquals("minigame_deathrace_4df5d090", auditor.extractSessionId(Path.of("minigame_deathrace_4df5d090.wal")));
        assertEquals("session_blockshuffle_100", auditor.extractSessionId(Path.of("session_blockshuffle_100.yml")));
        assertEquals("session_tag_200", auditor.extractSessionId(Path.of("session_tag_200.json")));
        assertEquals("session_folder_300", auditor.extractSessionId(Path.of("session_folder_300")));
    }

    @Test
    void testAuditPurgesOldPhantomSessionDirectory(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        sessionsDir.mkdirs();

        // Create phantom session folder modified 5 minutes ago
        Path phantomDir = sessionsDir.toPath().resolve("session_phantom_100");
        Files.createDirectories(phantomDir);
        Files.writeString(phantomDir.resolve("level.dat"), "dummy");
        Files.setLastModifiedTime(phantomDir, FileTime.from(Instant.now().minus(5, ChronoUnit.MINUTES)));

        assertTrue(Files.exists(phantomDir));

        SessionManager mockSessionManager = new SessionManager(null) {
            @Override
            public Set<String> getOrphanedSessionWorlds() {
                return Set.of(); // No active sessions
            }
        };

        auditor = new RuntimeSessionAuditor(mockSessionManager, directoryDeleter, sessionsDir);
        auditor.performAudit();

        // Wait for deleter background execution
        directoryDeleter.shutdown();

        assertFalse(Files.exists(phantomDir), "Old phantom session folder should be purged by auditor");
    }

    @Test
    void testAuditIgnoresFilesModifiedWithin60Seconds(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        sessionsDir.mkdirs();

        // Create recent phantom session file modified 10 seconds ago
        Path recentPhantomFile = sessionsDir.toPath().resolve("minigame_deathrace_999.wal");
        Files.writeString(recentPhantomFile, "[1000] PHASE=IN_GAME");
        Files.setLastModifiedTime(recentPhantomFile, FileTime.from(Instant.now().minus(10, ChronoUnit.SECONDS)));

        SessionManager mockSessionManager = new SessionManager(null) {
            @Override
            public Set<String> getOrphanedSessionWorlds() {
                return Set.of(); // Empty memory state
            }
        };

        auditor = new RuntimeSessionAuditor(mockSessionManager, directoryDeleter, sessionsDir);
        auditor.performAudit();

        directoryDeleter.shutdown();

        assertTrue(Files.exists(recentPhantomFile), "File modified within 60 seconds must be protected by grace period");
    }

    @Test
    void testAuditPreservesActiveWalAndYmlFiles(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        sessionsDir.mkdirs();

        Path activeWal = sessionsDir.toPath().resolve("minigame_active_200.wal");
        Path activeYml = sessionsDir.toPath().resolve("minigame_active_200.yml");
        Files.writeString(activeWal, "[1000] PHASE=IN_GAME");
        Files.writeString(activeYml, "world-name: minigame_active_200");

        // Set timestamps to 5 minutes ago so grace period doesn't apply
        FileTime oldTime = FileTime.from(Instant.now().minus(5, ChronoUnit.MINUTES));
        Files.setLastModifiedTime(activeWal, oldTime);
        Files.setLastModifiedTime(activeYml, oldTime);

        SessionManager mockSessionManager = new SessionManager(null) {
            @Override
            public Set<String> getOrphanedSessionWorlds() {
                return Set.of("minigame_active_200");
            }
        };

        auditor = new RuntimeSessionAuditor(mockSessionManager, directoryDeleter, sessionsDir);
        auditor.performAudit();

        directoryDeleter.shutdown();

        assertTrue(Files.exists(activeWal), "Active session WAL file must be preserved");
        assertTrue(Files.exists(activeYml), "Active session YML file must be preserved");
    }

    @Test
    void testCleanCorruptedDirectoryPurgesFilesOlderThan7Days(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        File corruptedDir = new File(sessionsDir, "corrupted");
        corruptedDir.mkdirs();

        Path oldCorruptedFile = corruptedDir.toPath().resolve("corrupted_session_888.yml");
        Files.writeString(oldCorruptedFile, "bad data");

        // Set timestamp to 8 days ago
        Instant eightDaysAgo = Instant.now().minus(8, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldCorruptedFile, FileTime.from(eightDaysAgo));

        Path recentCorruptedFile = corruptedDir.toPath().resolve("recent_corrupted_999.yml");
        Files.writeString(recentCorruptedFile, "recent bad data");

        SessionManager mockSessionManager = new SessionManager(null);
        auditor = new RuntimeSessionAuditor(mockSessionManager, directoryDeleter, sessionsDir);

        auditor.cleanCorruptedDirectory();
        directoryDeleter.shutdown();

        assertFalse(Files.exists(oldCorruptedFile), "Corrupted file older than 7 days should be purged");
        assertTrue(Files.exists(recentCorruptedFile), "Recent corrupted file should be retained");
    }
}
