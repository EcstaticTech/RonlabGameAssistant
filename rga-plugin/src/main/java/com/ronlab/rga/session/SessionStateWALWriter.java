package com.ronlab.rga.session;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Non-blocking Write-Ahead Log (WAL) writer for session state transitions.
 * Guarantees zero-tick overhead on the primary server thread.
 */
public class SessionStateWALWriter {

    private static final Logger LOGGER = Logger.getLogger(SessionStateWALWriter.class.getName());
    private final File sessionsDir;
    private final ExecutorService walExecutor;

    public SessionStateWALWriter(File sessionsDir) {
        this.sessionsDir = Objects.requireNonNull(sessionsDir, "sessionsDir cannot be null");
        if (!this.sessionsDir.exists()) {
            this.sessionsDir.mkdirs();
        }
        this.walExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rga-wal-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> appendTransitionAsync(String worldName, SessionSnapshot snapshot) {
        if (worldName == null || snapshot == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Path walFile = resolveWalFilePath(worldName);
                Path parent = walFile.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                String playersStr = snapshot.activePlayers().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));

                String entry = String.format(
                        "[%d] PHASE=%s UUID=%s MINIGAME=%s PLAYERS=%s%n",
                        snapshot.lastHeartbeatEpoch(),
                        snapshot.currentPhase().name(),
                        snapshot.sessionUuid().toString(),
                        snapshot.minigameId(),
                        playersStr
                );

                Files.writeString(
                        walFile,
                        entry,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to write WAL entry for world " + worldName + ": " + e.getMessage(), e);
            }
        }, walExecutor);
    }

    public Path resolveWalFilePath(String worldName) {
        File worldDir = new File(sessionsDir, worldName);
        if (worldDir.exists() && worldDir.isDirectory()) {
            return worldDir.toPath().resolve("session_state.wal");
        }
        return new File(sessionsDir, worldName + ".wal").toPath();
    }

    public CompletableFuture<Void> deleteWalFileAsync(String worldName) {
        if (worldName == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Path walFile = resolveWalFilePath(worldName);
                if (Files.exists(walFile)) {
                    boolean deleted = Files.deleteIfExists(walFile);
                    if (deleted) {
                        LOGGER.info("[RGA WAL] Successfully deleted WAL file: " + walFile.getFileName());
                    } else {
                        LOGGER.warning("[RGA WAL] Files.deleteIfExists returned false for WAL file: " + walFile.getFileName());
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to delete WAL file for world " + worldName + ": " + e.getMessage(), e);
            }
        }, walExecutor);
    }

    public void shutdown() {
        walExecutor.shutdown();
        try {
            if (!walExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                walExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            walExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
