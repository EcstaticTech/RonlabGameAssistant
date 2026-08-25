-- Migration V2: Expand minigame_id length for dynamic map template keys
-- SQLite dynamically types VARCHAR columns, but explicit declaration documents dynamic map key support.
SELECT 1;
