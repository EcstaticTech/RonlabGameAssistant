package com.ronlab.rga.session.audit;

import com.ronlab.rga.session.SessionManager;
import net.ronlab.rga.core.utils.io.AsyncDirectoryDeleter;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically audits session directories to detect phantom folders and purge corrupted logs.
 */
public class RuntimeSessionAuditor {

    private static final Logger LOGGER = Logger.getLogger(RuntimeSessionAuditor.class.getName());
    private static final long SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L;

    private final SessionManager sessionManager;
    private final AsyncDirectoryDeleter directoryDeleter;
    private final File sessionsDir;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> auditTaskFuture;

    public RuntimeSessionAuditor(SessionManager sessionManager, AsyncDirectoryDeleter directoryDeleter, File sessionsDir) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager cannot be null");
        this.directoryDeleter = Objects.requireNonNull(directoryDeleter, "directoryDeleter cannot be null");
        this.sessionsDir = Objects.requireNonNull(sessionsDir, "sessionsDir cannot be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rga-session-auditor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void startAuditing(long periodSeconds) {
        if (auditTaskFuture != null && !auditTaskFuture.isCancelled()) {
            return;
        }
        auditTaskFuture = scheduler.scheduleAtFixedRate(this::performAudit, periodSeconds, periodSeconds, TimeUnit.SECONDS);
        LOGGER.info("[RGA AUDITOR] Started session auditor with period: " + periodSeconds + "s");
    }

    public void performAudit() {
        try {
            auditPhantomSessionDirectories();
            cleanCorruptedDirectory();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[RGA AUDITOR] Unexpected error during audit execution: " + e.getMessage(), e);
        }
    }

    public void auditPhantomSessionDirectories() {
        if (!sessionsDir.exists() || !sessionsDir.isDirectory()) {
            return;
        }

        Set<String> activeOrphanWorlds = sessionManager.getOrphanedSessionWorlds();

        File[] files = sessionsDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.getName().equals("corrupted")) {
                continue;
            }

            String worldName = extractWorldName(file);
            if (worldName == null) continue;

            // If session directory on disk is not in active or orphaned recovery lists, it's a phantom folder
            if (!activeOrphanWorlds.contains(worldName)) {
                LOGGER.info("[RGA AUDITOR] Detected phantom session directory on disk: " + file.getName() + " — Queueing async purge");
                directoryDeleter.queueForDeletion(file.toPath());
            }
        }
    }

    public void cleanCorruptedDirectory() {
        File corruptedDir = new File(sessionsDir, "corrupted");
        if (!corruptedDir.exists() || !corruptedDir.isDirectory()) {
            return;
        }

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(corruptedDir.toPath())) {
            for (Path path : stream) {
                try {
                    FileTime lastModified = Files.getLastModifiedTime(path);
                    if (lastModified.toInstant().isBefore(sevenDaysAgo)) {
                        LOGGER.info("[RGA AUDITOR] Auto-purging corrupted session item older than 7 days: " + path.getFileName());
                        directoryDeleter.queueForDeletion(path);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "[RGA AUDITOR] Failed to check timestamp for corrupted file: " + path, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[RGA AUDITOR] Error reading corrupted directory: " + e.getMessage(), e);
        }
    }

    private String extractWorldName(File file) {
        String name = file.getName();
        if (name.endsWith(".yml")) {
            return name.substring(0, name.length() - 4);
        } else if (name.endsWith(".wal")) {
            return name.substring(0, name.length() - 4);
        } else if (file.isDirectory()) {
            return name;
        }
        return null;
    }

    public void stopAuditing() {
        if (auditTaskFuture != null) {
            auditTaskFuture.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
