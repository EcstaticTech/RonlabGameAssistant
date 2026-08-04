package net.ronlab.rga.core.utils.io;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of {@link AsyncDirectoryDeleter} using a background
 * {@link ScheduledExecutorService} and non-blocking Java NIO file tree walks.
 */
public class DefaultAsyncDirectoryDeleter implements AsyncDirectoryDeleter {

    private static final Logger LOGGER = Logger.getLogger(DefaultAsyncDirectoryDeleter.class.getName());
    private static final int MAX_RETRIES = 5;
    private static final long[] RETRY_DELAYS_MS = {500L, 1000L, 2000L, 4000L};

    private final ScheduledExecutorService executor;

    public DefaultAsyncDirectoryDeleter() {
        this(Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "rga-async-deleter");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public DefaultAsyncDirectoryDeleter(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
    }

    @Override
    public CompletableFuture<Boolean> queueForDeletion(Path targetPath) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (targetPath == null) {
            future.complete(false);
            return future;
        }

        Path normalizedPath = targetPath.toAbsolutePath().normalize();

        if (!isSafePathForDeletion(normalizedPath)) {
            LOGGER.log(Level.FINE, "Refused deletion of unsafe path: {0}", normalizedPath);
            future.complete(false);
            return future;
        }

        if (!Files.exists(normalizedPath)) {
            future.complete(true);
            return future;
        }

        executeAttempt(normalizedPath, 1, future);
        return future;
    }

    private void executeAttempt(Path targetPath, int attemptNumber, CompletableFuture<Boolean> future) {
        executor.execute(() -> {
            boolean success = attemptDelete(targetPath);
            if (success || !Files.exists(targetPath)) {
                future.complete(true);
                return;
            }

            if (attemptNumber >= MAX_RETRIES) {
                LOGGER.log(Level.FINE, "Exhausted all retries deleting: {0}", targetPath);
                future.complete(false);
                return;
            }

            long delayMs = RETRY_DELAYS_MS[attemptNumber - 1];
            executor.schedule(() -> executeAttempt(targetPath, attemptNumber + 1, future), delayMs, TimeUnit.MILLISECONDS);
        });
    }

    private boolean attemptDelete(Path directory) {
        if (!Files.exists(directory)) {
            return true;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return !Files.exists(directory);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "File handle contention during deletion of {0}: {1}",
                    new Object[]{directory, e.getMessage()});
            return false;
        }
    }

    /**
     * Validates that targetPath resides inside a session or dynamic world directory
     * and is safe to purge. Protects system root, server root, plugins, and config folders.
     */
    public boolean isSafePathForDeletion(Path targetPath) {
        if (targetPath == null) return false;
        Path normalized = targetPath.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) return false; // System root

        String fileName = normalized.getFileName().toString().toLowerCase(java.util.Locale.ROOT);

        // Block root system directories or server configuration folders
        if (fileName.equals("plugins") || fileName.equals("config") || fileName.equals("logs")
                || fileName.equals("mods") || fileName.equals("cache")) {
            return false;
        }

        String normalizedStr = normalized.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);

        boolean matchesPattern = fileName.startsWith("session_") || fileName.startsWith("minigame_");
        boolean insideSubdir = normalizedStr.contains("/sessions/")
                || normalizedStr.contains("/dimensions/")
                || normalizedStr.contains("/minecraft/");

        boolean isCustomWorldFolder = fileName.contains("world");

        return matchesPattern || insideSubdir || isCustomWorldFolder;
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
