package net.ronlab.rga.core.utils.io;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for non-blocking asynchronous file system deletions.
 * Designed to replace synchronous System.gc() workarounds on Windows OS.
 */
public interface AsyncDirectoryDeleter {

    /**
     * Enqueues a target directory path for asynchronous deletion.
     * 
     * @param targetPath The root Path of the directory to purge.
     * @return A CompletableFuture completing true on success, false on exhaustion of retries.
     */
    CompletableFuture<Boolean> queueForDeletion(Path targetPath);

    /**
     * Shuts down the background executor service gracefully.
     * Flushes remaining pending deletions before server thread stops.
     */
    void shutdown();
}
