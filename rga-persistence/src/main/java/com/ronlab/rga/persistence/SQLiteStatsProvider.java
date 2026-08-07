package com.ronlab.rga.persistence;

import com.ronlab.rga.api.stats.PlayerMinigameStats;
import com.ronlab.rga.api.stats.RGAStatsProvider;
import org.flywaydb.core.Flyway;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * SQLite embedded database implementation of {@link RGAStatsProvider}.
 * Operates on a dedicated single-threaded worker executor with WAL mode enabled.
 */
public class SQLiteStatsProvider implements RGAStatsProvider {

    private final File dbFile;
    private final Logger logger;
    private final ThreadPoolExecutor dbExecutor;
    private Connection connection;
    private volatile boolean initialized = false;

    @FunctionalInterface
    public interface SQLSupplier<T> {
        T get() throws SQLException;
    }

    public SQLiteStatsProvider(File dbFile, Logger logger) {
        this.dbFile = Objects.requireNonNull(dbFile, "dbFile cannot be null");
        this.logger = logger;

        ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(2048);
        this.dbExecutor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                workQueue,
                runnable -> {
                    Thread thread = new Thread(runnable, "rga-db-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, executor) -> {
                    if (logger != null) {
                        logger.warning("[rga-persistence] DB task queue full (2048 capacity). Dropping write task!");
                    }
                }
        );
    }

    /**
     * Initializes the SQLite database, executes Flyway migrations, and configures WAL mode.
     *
     * @throws SQLException if schema migration or initial database setup fails
     */
    public void initialize() throws SQLException {
        if (initialized) return;

        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        // 1. Run Flyway Schema Migrations
        try {
            Flyway flyway = Flyway.configure(SQLiteStatsProvider.class.getClassLoader())
                    .dataSource(jdbcUrl, null, null)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
        } catch (Exception e) {
            if (logger != null) {
                logger.severe("[rga-persistence] Flyway migration failed for " + dbFile.getAbsolutePath() + ": " + e.getMessage());
            }
            throw new SQLException("Flyway schema migration failed", e);
        }

        // 2. Open Single Persistent Connection & Set PRAGMAs on dbExecutor thread
        CompletableFuture<Void> initFuture = new CompletableFuture<>();
        dbExecutor.execute(() -> {
            try {
                this.connection = DriverManager.getConnection(jdbcUrl);
                try (Statement stmt = this.connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA busy_timeout=5000;");
                    stmt.execute("PRAGMA synchronous=NORMAL;");
                }
                initialized = true;
                if (logger != null) {
                    logger.info("[rga-persistence] SQLite WAL persistence database initialized at " + dbFile.getAbsolutePath());
                }
                initFuture.complete(null);
            } catch (SQLException e) {
                if (logger != null) {
                    logger.severe("[rga-persistence] Failed to open connection to SQLite DB: " + e.getMessage());
                }
                initFuture.completeExceptionally(e);
            }
        });

        try {
            initFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new SQLException("Database connection initialization timed out", e);
        }
    }

    /**
     * Executes a SQL operation wrapping execution in a 3-try exponential backoff loop for SQLITE_BUSY locks.
     */
    public <T> T executeWithRetry(SQLSupplier<T> supplier) throws SQLException {
        int maxRetries = 3;
        long waitMs = 50;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return supplier.get();
            } catch (SQLException e) {
                boolean isBusy = e.getErrorCode() == 5 // SQLITE_BUSY
                        || (e.getMessage() != null && (e.getMessage().contains("BUSY") || e.getMessage().contains("locked")));
                if (isBusy && attempt < maxRetries) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    waitMs *= 2;
                } else {
                    throw e;
                }
            }
        }
        throw new SQLException("SQLite operation failed after retries");
    }

    @Override
    public void recordMatchResult(UUID playerUuid, String minigameId, boolean won, int kills, int deaths, String metadataJson) {
        if (playerUuid == null || minigameId == null) return;
        String meta = (metadataJson != null && !metadataJson.isBlank()) ? metadataJson : "{}";
        int addWins = won ? 1 : 0;
        int addLosses = won ? 0 : 1;

        Runnable writeTask = () -> {
            try {
                executeWithRetry(() -> {
                    String sql = """
                        INSERT INTO player_stats (player_uuid, minigame_id, wins, losses, kills, deaths, metadata, last_updated)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT(player_uuid, minigame_id) DO UPDATE SET
                            wins = wins + excluded.wins,
                            losses = losses + excluded.losses,
                            kills = kills + excluded.kills,
                            deaths = deaths + excluded.deaths,
                            metadata = excluded.metadata,
                            last_updated = CURRENT_TIMESTAMP;
                        """;
                    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                        pstmt.setString(1, playerUuid.toString());
                        pstmt.setString(2, minigameId);
                        pstmt.setInt(3, addWins);
                        pstmt.setInt(4, addLosses);
                        pstmt.setInt(5, kills);
                        pstmt.setInt(6, deaths);
                        pstmt.setString(7, meta);
                        pstmt.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                if (logger != null) {
                    logger.warning("[rga-persistence] Failed to record match result for player " + playerUuid + ": " + e.getMessage());
                }
            }
        };

        try {
            dbExecutor.execute(writeTask);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("[rga-persistence] Rejected match result task for player " + playerUuid + ": " + e.getMessage());
            }
        }
    }

    @Override
    public CompletableFuture<Optional<PlayerMinigameStats>> getPlayerStats(UUID playerUuid, String minigameId) {
        CompletableFuture<Optional<PlayerMinigameStats>> future = new CompletableFuture<>();
        if (playerUuid == null || minigameId == null) {
            future.complete(Optional.empty());
            return future;
        }

        dbExecutor.execute(() -> {
            try {
                Optional<PlayerMinigameStats> stats = executeWithRetry(() -> {
                    String sql = "SELECT player_uuid, minigame_id, wins, losses, kills, deaths, metadata, last_updated FROM player_stats WHERE player_uuid = ? AND minigame_id = ?";
                    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                        pstmt.setString(1, playerUuid.toString());
                        pstmt.setString(2, minigameId);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                String uuidStr = rs.getString("player_uuid");
                                String gameId = rs.getString("minigame_id");
                                int wins = rs.getInt("wins");
                                int losses = rs.getInt("losses");
                                int kills = rs.getInt("kills");
                                int deaths = rs.getInt("deaths");
                                String meta = rs.getString("metadata");
                                Timestamp ts = rs.getTimestamp("last_updated");
                                Instant updated = ts != null ? ts.toInstant() : Instant.now();
                                return Optional.of(new PlayerMinigameStats(UUID.fromString(uuidStr), gameId, wins, losses, kills, deaths, meta, updated));
                            }
                        }
                    }
                    return Optional.empty();
                });
                future.complete(stats);
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[rga-persistence] Query error for player " + playerUuid + ": " + e.getMessage());
                }
                future.complete(Optional.empty());
            }
        });

        return future;
    }

    @Override
    public CompletableFuture<List<PlayerMinigameStats>> getTopPlayers(String minigameId, int limit) {
        CompletableFuture<List<PlayerMinigameStats>> future = new CompletableFuture<>();
        if (minigameId == null || limit <= 0) {
            future.complete(List.of());
            return future;
        }

        dbExecutor.execute(() -> {
            try {
                List<PlayerMinigameStats> list = executeWithRetry(() -> {
                    List<PlayerMinigameStats> results = new ArrayList<>();
                    String sql = "SELECT player_uuid, minigame_id, wins, losses, kills, deaths, metadata, last_updated FROM player_stats WHERE minigame_id = ? ORDER BY wins DESC LIMIT ?";
                    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                        pstmt.setString(1, minigameId);
                        pstmt.setInt(2, limit);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {
                                String uuidStr = rs.getString("player_uuid");
                                String gameId = rs.getString("minigame_id");
                                int wins = rs.getInt("wins");
                                int losses = rs.getInt("losses");
                                int kills = rs.getInt("kills");
                                int deaths = rs.getInt("deaths");
                                String meta = rs.getString("metadata");
                                Timestamp ts = rs.getTimestamp("last_updated");
                                Instant updated = ts != null ? ts.toInstant() : Instant.now();
                                results.add(new PlayerMinigameStats(UUID.fromString(uuidStr), gameId, wins, losses, kills, deaths, meta, updated));
                            }
                        }
                    }
                    return results;
                });
                future.complete(list);
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[rga-persistence] Query top players error for minigame " + minigameId + ": " + e.getMessage());
                }
                future.complete(List.of());
            }
        });

        return future;
    }

    /**
     * Closes the connection cleanly and stops the dbExecutor worker.
     */
    public void shutdown() {
        if (!dbExecutor.isShutdown()) {
            CompletableFuture<Void> closeFuture = new CompletableFuture<>();
            dbExecutor.execute(() -> {
                if (connection != null) {
                    try {
                        connection.close();
                        if (logger != null) {
                            logger.info("[rga-persistence] SQLite database connection closed cleanly.");
                        }
                    } catch (SQLException e) {
                        if (logger != null) {
                            logger.warning("[rga-persistence] Exception closing SQLite connection: " + e.getMessage());
                        }
                    }
                }
                closeFuture.complete(null);
            });
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dbExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public ThreadPoolExecutor getDbExecutor() {
        return dbExecutor;
    }
}
