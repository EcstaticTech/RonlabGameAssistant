package com.ronlab.rga.world;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.Map;

public class WorldSettings {

    private final GameMode gamemode;
    private final boolean pvp;
    private final World.Environment environment;
    private final Difficulty difficulty;
    private final String alias;
    private final boolean template;
    private final long timeLock;
    private final boolean weatherLock;
    private final boolean disableNether;
    private final boolean disableEnd;
    private final Map<String, String> gamerules;
    /** Optional first-visit spawn; null means fall back to world spawn. */
    private final FirstVisitSpawn firstVisitSpawn;

    public WorldSettings(GameMode gamemode, boolean pvp, World.Environment environment,
                         Difficulty difficulty, String alias, boolean template,
                         long timeLock, boolean weatherLock,
                         boolean disableNether, boolean disableEnd,
                         Map<String, String> gamerules) {
        this(gamemode, pvp, environment, difficulty, alias, template,
                timeLock, weatherLock, disableNether, disableEnd, gamerules, null);
    }

    public WorldSettings(GameMode gamemode, boolean pvp, World.Environment environment,
                         Difficulty difficulty, String alias, boolean template,
                         long timeLock, boolean weatherLock,
                         boolean disableNether, boolean disableEnd,
                         Map<String, String> gamerules, FirstVisitSpawn firstVisitSpawn) {
        this.gamemode = gamemode;
        this.pvp = pvp;
        this.environment = environment;
        this.difficulty = difficulty;
        this.alias = alias;
        this.template = template;
        this.timeLock = timeLock;
        this.weatherLock = weatherLock;
        this.disableNether = disableNether;
        this.disableEnd = disableEnd;
        this.gamerules = gamerules != null ? gamerules : Collections.emptyMap();
        this.firstVisitSpawn = firstVisitSpawn;
    }

    public GameMode getGamemode() { return gamemode; }
    public boolean isPvp() { return pvp; }
    public World.Environment getEnvironment() { return environment; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getAlias() { return alias; }
    public boolean isTemplate() { return template; }
    public long getTimeLock() { return timeLock; }
    public boolean isWeatherLock() { return weatherLock; }
    public boolean isDisableNether() { return disableNether; }
    public boolean isDisableEnd() { return disableEnd; }
    public Map<String, String> getGamerules() { return gamerules; }

    /** Returns the configured first-visit spawn, or {@code null} if none. */
    public FirstVisitSpawn getFirstVisitSpawn() { return firstVisitSpawn; }

    /**
     * Returns the spawn location a first-time (or untracked) visitor should be sent to.
     * Uses the configured {@link FirstVisitSpawn} when present; falls back to
     * {@link World#getSpawnLocation()} otherwise.
     *
     * @param world the (already-loaded) world; must not be null
     * @return the resolved {@link Location}
     */
    public Location getSpawnLocation(World world) {
        if (firstVisitSpawn != null) {
            return firstVisitSpawn.toLocation(world);
        }
        return world.getSpawnLocation();
    }
}
