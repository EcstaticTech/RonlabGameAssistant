package com.ronlab.rga.session.audit;

import com.ronlab.rga.session.SessionPhase;
import com.ronlab.rga.session.SessionSnapshot;
import com.ronlab.rga.session.SessionManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles power-loss recovery during plugin startup by scanning, replaying WAL files,
 * and quarantining corrupted session entries.
 */
public class PowerLossRecoveryHandler {

    private static final Logger LOGGER = Logger.getLogger(PowerLossRecoveryHandler.class.getName());
    private final File sessionsDir;
    private final File corruptedDir;

    public PowerLossRecoveryHandler(File sessionsDir) {
        this.sessionsDir = Objects.requireNonNull(sessionsDir, "sessionsDir cannot be null");
        this.corruptedDir = new File(sessionsDir, "corrupted");
        if (!corruptedDir.exists()) {
            corruptedDir.mkdirs();
        }
    }

    public void processPowerLossRecovery(SessionManager sessionManager) {
        if (!sessionsDir.exists() || !sessionsDir.isDirectory()) {
            return;
        }

        File[] files = sessionsDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.getName().equals("corrupted")) {
                continue;
            }

            if (file.getName().endsWith(".wal") || (file.isDirectory() && new File(file, "session_state.wal").exists())) {
                File walFile = file.isDirectory() ? new File(file, "session_state.wal") : file;
                try {
                    List<SessionSnapshot> snapshots = parseWalFile(walFile);
                    if (snapshots.isEmpty()) {
                        LOGGER.warning("[RGA RECOVERY] Empty or invalid WAL file detected: " + walFile.getName() + " — Quarantining.");
                        quarantineFileOrDirectory(file);
                    } else {
                        SessionSnapshot latest = snapshots.get(snapshots.size() - 1);
                        LOGGER.info(String.format("[RGA RECOVERY] Replayed WAL for session %s (Phase=%s, Minigame=%s, Players=%d)",
                                latest.sessionUuid(), latest.currentPhase(), latest.minigameId(), latest.activePlayers().size()));

                        if (latest.currentPhase() == SessionPhase.TEARDOWN) {
                            // Already tore down, safe to clean up WAL
                            walFile.delete();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "[RGA RECOVERY] Corruption detected in WAL file " + walFile.getName() + ": " + e.getMessage() + " — Quarantining.", e);
                    quarantineFileOrDirectory(file);
                }
            }
        }
    }

    public List<SessionSnapshot> parseWalFile(File walFile) throws IOException {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        List<String> lines = Files.readAllLines(walFile.toPath(), StandardCharsets.UTF_8);

        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            SessionSnapshot snapshot = parseWalLine(line.trim());
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    public SessionSnapshot parseWalLine(String line) {
        try {
            // Line format: [1770150144000] PHASE=IN_GAME UUID=a1b2c3d4-... MINIGAME=blockshuffle PLAYERS=uuid1,uuid2
            if (!line.startsWith("[")) return null;

            int closingBracketIndex = line.indexOf(']');
            if (closingBracketIndex == -1) return null;

            long epoch = Long.parseLong(line.substring(1, closingBracketIndex));
            String rest = line.substring(closingBracketIndex + 1).trim();

            String[] tokens = rest.split("\\s+");
            SessionPhase phase = SessionPhase.LOBBY;
            UUID uuid = null;
            String minigameId = "unknown";
            List<UUID> players = new ArrayList<>();

            for (String token : tokens) {
                if (token.startsWith("PHASE=")) {
                    phase = SessionPhase.valueOf(token.substring(6));
                } else if (token.startsWith("UUID=")) {
                    uuid = UUID.fromString(token.substring(5));
                } else if (token.startsWith("MINIGAME=")) {
                    minigameId = token.substring(9);
                } else if (token.startsWith("PLAYERS=")) {
                    String rawPlayers = token.substring(8);
                    if (!rawPlayers.isBlank()) {
                        for (String p : rawPlayers.split(",")) {
                            if (!p.isBlank()) {
                                players.add(UUID.fromString(p.trim()));
                            }
                        }
                    }
                }
            }

            if (uuid == null) return null;

            return new SessionSnapshot(uuid, minigameId, players, phase, epoch);
        } catch (Exception e) {
            return null; // Corrupted line
        }
    }

    public void quarantineFileOrDirectory(File target) {
        if (target == null || !target.exists()) return;
        Path targetPath = target.toPath();
        String timeSuffix = "_" + System.currentTimeMillis();
        Path destPath = corruptedDir.toPath().resolve(target.getName() + timeSuffix);

        try {
            Files.move(targetPath, destPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[RGA RECOVERY] Successfully quarantined corrupted item to: " + destPath.getFileName());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[RGA RECOVERY] Failed to quarantine corrupted item " + target.getName() + ": " + e.getMessage(), e);
        }
    }
}
