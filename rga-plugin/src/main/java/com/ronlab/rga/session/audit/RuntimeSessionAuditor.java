package com.ronlab.rga.session.audit;

import com.ronlab.rga.RGA;
import com.ronlab.rga.session.SessionManager;
import net.ronlab.rga.core.utils.io.AsyncDirectoryDeleter;
import org.bukkit.Bukkit;

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
    private static final long GRACE_PERIOD_MS = 60_000L; // 60-second grace window

    private final RGA plugin;
    private final SessionManager sessionManager;
    private final AsyncDirectoryDeleter directoryDeleter;
    private final File sessionsDir;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> auditTaskFuture;

    public RuntimeSessionAuditor(RGA plugin, AsyncDirectoryDeleter directoryDeleter, File sessionsDir) {
        this(plugin, plugin != null ? plugin.getSessionManager() : null, directoryDeleter, sessionsDir);
    }

    public RuntimeSessionAuditor(SessionManager sessionManager, AsyncDirectoryDeleter directoryDeleter, File sessionsDir) {
        this(null, sessionManager, directoryDeleter, sessionsDir);
    }

    public RuntimeSessionAuditor(RGA plugin, SessionManager sessionManager, AsyncDirectoryDeleter directoryDeleter, File sessionsDir) {
        this.plugin = plugin;
        this.sessionManager = sessionManager != null ? sessionManager : (plugin != null ? plugin.getSessionManager() : null);
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

        File[] files = sessionsDir.listFiles();
        if (files == null) return;

        long now = System.currentTimeMillis();

        for (File file : files) {
            if (file.getName().equalsIgnoreCase("corrupted")) {
                continue;
            }

            // 60-Second Grace Window Buffer: Skip files/folders modified within the last 60 seconds
            long lastModified = file.lastModified();
            if (now - lastModified < GRACE_PERIOD_MS) {
                continue;
            }

            String sessionId = extractSessionId(file);
            if (sessionId == null || sessionId.isBlank()) continue;

            // Triple-Lock Active Memory Verification: Skip active sessions
            if (isSessionActiveInMemory(sessionId)) {
                continue;
            }

            // Phantom session detected — queue async deletion
            LOGGER.info("[RGA AUDITOR] Detected phantom session file/directory on disk: " + file.getName() + " — Queueing async purge");
            directoryDeleter.queueForDeletion(file.toPath());
        }
    }

    public boolean isSessionActiveInMemory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return true;

        // Check 1: SessionManager orphaned recovery / registered session worlds
        if (sessionManager != null) {
            Set<String> orphanWorlds = sessionManager.getOrphanedSessionWorlds();
            if (orphanWorlds != null && orphanWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(sessionId))) {
                return true;
            }
        }

        // Check 2: Bukkit loaded world instances
        try {
            if (Bukkit.getWorld(sessionId) != null
                    || Bukkit.getWorld(sessionId + "_the_nether") != null
                    || Bukkit.getWorld(sessionId + "_the_end") != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // Uninitialized Bukkit server in unit test environment
        }

        // Check 3: PartyManager active in-game sessions
        if (plugin != null && plugin.getPartyManager() != null) {
            boolean activeInParty = plugin.getPartyManager().getActiveParties().values().stream().anyMatch(party -> {
                String activeWorld = party.getActiveWorldName();
                return activeWorld != null && (
                        activeWorld.equalsIgnoreCase(sessionId) ||
                        activeWorld.equalsIgnoreCase(sessionId + "_the_nether") ||
                        activeWorld.equalsIgnoreCase(sessionId + "_the_end")
                );
            });
            if (activeInParty) {
                return true;
            }
        }

        return false;
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

    public String extractSessionId(Path path) {
        if (path == null) return null;
        return extractSessionId(path.toFile());
    }

    public String extractSessionId(File file) {
        if (file == null) return null;
        String name = file.getName();
        if (name.endsWith(".yml") || name.endsWith(".wal") || name.endsWith(".json")) {
            int lastDot = name.lastIndexOf('.');
            return name.substring(0, lastDot);
        }
        return name;
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
