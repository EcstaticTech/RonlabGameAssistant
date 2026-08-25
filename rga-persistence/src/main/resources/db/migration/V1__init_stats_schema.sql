CREATE TABLE IF NOT EXISTS player_stats (
    player_uuid VARCHAR(36) NOT NULL,
    minigame_id VARCHAR(64) NOT NULL,
    wins INTEGER DEFAULT 0,
    losses INTEGER DEFAULT 0,
    kills INTEGER DEFAULT 0,
    deaths INTEGER DEFAULT 0,
    metadata TEXT,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, minigame_id)
);

CREATE INDEX IF NOT EXISTS idx_minigame_wins ON player_stats (minigame_id, wins DESC);
