package com.ronlab.rga.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {

    private FileUtils() {
        // Utility class
    }

    /**
     * Recursively deletes a directory with retry logic to handle OS file lock contention (e.g. Windows region files).
     *
     * @param directory The directory to delete.
     * @param maxRetries Maximum number of deletion attempts.
     * @param delayMillis Delay between attempts in milliseconds.
     * @return true if the directory no longer exists; false otherwise.
     */
    public static boolean deleteDirectoryWithRetry(File directory, int maxRetries, long delayMillis) {
        if (directory == null || !directory.exists()) return true;

        for (int i = 0; i < maxRetries; i++) {
            if (deleteDirectory(directory)) {
                return true;
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return !directory.exists();
    }

    /**
     * Recursively deletes a directory using NIO Files.walkFileTree.
     *
     * @param directory The directory to delete.
     * @return true if deletion succeeded and directory no longer exists; false otherwise.
     */
    public static boolean deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) return true;
        Path path = directory.toPath();
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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
            return !directory.exists();
        } catch (IOException e) {
            return false;
        }
    }
}
