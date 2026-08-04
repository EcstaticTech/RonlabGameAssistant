package net.ronlab.rga.core.utils.io;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AsyncDirectoryDeleterTest {

    private DefaultAsyncDirectoryDeleter deleter;

    @BeforeEach
    void setUp() {
        deleter = new DefaultAsyncDirectoryDeleter();
    }

    @AfterEach
    void tearDown() {
        if (deleter != null) {
            deleter.shutdown();
        }
    }

    @Test
    void testSuccessfulAsyncDeletion(@TempDir Path tempDir) throws Exception {
        Path sessionDir = tempDir.resolve("session_minigame_1000_abcd1234");
        Files.createDirectories(sessionDir);
        Path childFile = sessionDir.resolve("region.mca");
        Files.writeString(childFile, "dummy region content");

        assertTrue(Files.exists(childFile));

        CompletableFuture<Boolean> future = deleter.queueForDeletion(sessionDir);
        Boolean success = future.get();

        assertTrue(success, "Expected deletion to succeed");
        assertFalse(Files.exists(sessionDir), "Directory should be deleted");
    }

    @Test
    void testPathSafetyValidation(@TempDir Path tempDir) {
        Path safeSessionDir = tempDir.resolve("session_map_123_45678");
        Path safeMinigameDir = tempDir.resolve("minigame_test_789");
        Path unsafePluginsDir = tempDir.resolve("plugins");
        Path unsafeConfigDir = tempDir.resolve("config");

        assertTrue(deleter.isSafePathForDeletion(safeSessionDir));
        assertTrue(deleter.isSafePathForDeletion(safeMinigameDir));
        assertFalse(deleter.isSafePathForDeletion(unsafePluginsDir));
        assertFalse(deleter.isSafePathForDeletion(unsafeConfigDir));
    }

    @Test
    void testUnsafePathRejection(@TempDir Path tempDir) throws Exception {
        Path unsafeDir = tempDir.resolve("plugins");
        Files.createDirectories(unsafeDir);

        CompletableFuture<Boolean> future = deleter.queueForDeletion(unsafeDir);
        Boolean success = future.get();

        assertFalse(success, "Unsafe directory deletion should be rejected");
        assertTrue(Files.exists(unsafeDir), "Unsafe directory should remain intact");
    }

    @Test
    void testNonExistentDirectoryHandling(@TempDir Path tempDir) throws Exception {
        Path nonExistent = tempDir.resolve("session_nonexistent_123");

        CompletableFuture<Boolean> future = deleter.queueForDeletion(nonExistent);
        Boolean success = future.get();

        assertTrue(success, "Non-existent path queueing should complete successfully");
    }
}
