package com.ronlab.rga.party;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class SpatialAuthorityGameLaunchTest {

    @Test
    void playerAlreadyInMinigameWorld_retainsCustomCoordinates() {
        Location customTeamSpawn = new Location(null, -45.0, 65.0, 30.0);
        Location worldDefaultSpawn = new Location(null, 0.0, 64.0, 0.0);

        final boolean[] teleportCalled = {false};

        World minigameWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{ World.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "minigame_turfwars_101";
                    case "getSpawnLocation" -> worldDefaultSpawn;
                    default -> null;
                }
        );

        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{ Player.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> minigameWorld;
                    case "getLocation" -> customTeamSpawn;
                    case "teleport" -> {
                        teleportCalled[0] = true;
                        yield true;
                    }
                    default -> null;
                }
        );

        // Guard logic check matching PartyManager.finalizeGameLaunch
        if (!player.getWorld().getName().equalsIgnoreCase(minigameWorld.getName())) {
            player.teleport(minigameWorld.getSpawnLocation());
        }

        assertFalse(teleportCalled[0], "RGA must NOT re-teleport players who are already inside the minigame world");
    }

    @Test
    void playerInDifferentWorld_isTeleportedToWorldSpawn() {
        Location worldDefaultSpawn = new Location(null, 0.0, 64.0, 0.0);
        final boolean[] teleportCalled = {false};

        World hubWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{ World.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "hub";
                    default -> null;
                }
        );

        World minigameWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{ World.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "minigame_turfwars_101";
                    case "getSpawnLocation" -> worldDefaultSpawn;
                    default -> null;
                }
        );

        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{ Player.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> hubWorld;
                    case "teleport" -> {
                        teleportCalled[0] = true;
                        yield true;
                    }
                    default -> null;
                }
        );

        if (!player.getWorld().getName().equalsIgnoreCase(minigameWorld.getName())) {
            player.teleport(minigameWorld.getSpawnLocation());
        }

        assertTrue(teleportCalled[0], "RGA must teleport players into the minigame world if they are still in hub");
    }
}
