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
    void testAuditPurgesPhantomSessionDirectory(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        sessionsDir.mkdirs();

        // Create phantom session folder
        Path phantomDir = sessionsDir.toPath().resolve("session_phantom_100");
        Files.createDirectories(phantomDir);
        Files.writeString(phantomDir.resolve("level.dat"), "dummy");

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

        assertFalse(Files.exists(phantomDir), "Phantom session folder should be purged by auditor");
    }

    @Test
    void testAuditPreservesActiveSessionDirectory(@TempDir Path tempDir) throws Exception {
        File sessionsDir = tempDir.resolve("sessions").toFile();
        sessionsDir.mkdirs();

        Path activeDir = sessionsDir.toPath().resolve("session_active_200");
        Files.createDirectories(activeDir);

        SessionManager mockSessionManager = new SessionManager(null) {
            @Override
            public Set<String> getOrphanedSessionWorlds() {
                return Set.of("session_active_200");
            }
        };

        auditor = new RuntimeSessionAuditor(mockSessionManager, directoryDeleter, sessionsDir);
        auditor.performAudit();

        directoryDeleter.shutdown();

        assertTrue(Files.exists(activeDir), "Active session directory must be preserved");
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
