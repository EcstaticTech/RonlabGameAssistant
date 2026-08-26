package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.MinigameManager;
import com.ronlab.rga.party.PartyManager;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RGACommandRouterTest {

    private static class TestPartyManager extends PartyManager {
        public TestPartyManager(RGA plugin) {
            super(plugin);
        }

        @Override
        public boolean isPlayerSpectating(UUID uuid) {
            return false;
        }

        @Override
        public void joinMinigame(Player player, String minigameId, String templateId) {
            // mock no-op
        }
    }

    private static class TestRGA extends RGA {
        PartyManager partyManager;
        MinigameManager minigameManager;
        @Override
        public PartyManager getPartyManager() {
            return partyManager;
        }
        @Override
        public MinigameManager getMinigameManager() {
            return minigameManager;
        }
    }

    private TestRGA plugin;
    private TestPartyManager partyManager;
    private Player player;
    private DefaultRGACommandRouter router;

    @BeforeEach
    void setUp() throws Exception {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);

        BukkitScheduler schedulerMock = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    if ("runTaskLater".equals(method.getName()) || "runTask".equals(method.getName())) {
                        Runnable r = (Runnable) args[1];
                        r.run();
                        return null;
                    }
                    return null;
                }
        );

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getScheduler".equals(method.getName())) return schedulerMock;
                    return null;
                }
        );

        plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);
        partyManager = (TestPartyManager) unsafe.allocateInstance(TestPartyManager.class);
        plugin.partyManager = partyManager;
        setPrivateField(partyManager, "plugin", plugin);

        setPrivateField(plugin, "server", serverMock);

        player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("hasPermission".equals(name) && args != null && args.length == 1) {
                        String perm = (String) args[0];
                        if ("rga.admin".equals(perm)) return false;
                        if ("rga.user.join".equals(perm)) return false;
                        if ("rga.tp".equals(perm)) return false;
                    }
                    if ("getUniqueId".equals(name)) {
                        return UUID.randomUUID();
                    }
                    return null;
                }
        );

        router = new DefaultRGACommandRouter(plugin);
    }

    @Test
    void testPermissionCheckForJoin_DeniedWithoutPermission() {
        assertFalse(router.canExecuteJoin(player, "manhunt"));
    }

    @Test
    void testPermissionCheckForJoin_AllowedWithUserJoinPermission() {
        Player playerWithJoin = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("hasPermission".equals(name) && args != null && args.length == 1) {
                        String perm = (String) args[0];
                        return "rga.user.join".equals(perm);
                    }
                    if ("getUniqueId".equals(name)) {
                        return UUID.randomUUID();
                    }
                    return null;
                }
        );
        assertTrue(router.canExecuteJoin(playerWithJoin, "manhunt"));
    }

    @Test
    void testPermissionCheckForJoin_AllowedWithAdminBypass() {
        Player adminPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("hasPermission".equals(name) && args != null && args.length == 1) {
                        String perm = (String) args[0];
                        return "rga.admin".equals(perm);
                    }
                    if ("getUniqueId".equals(name)) {
                        return UUID.randomUUID();
                    }
                    return null;
                }
        );
        assertTrue(router.canExecuteJoin(adminPlayer, "manhunt"));
    }

    @Test
    void testDispatchAction_JoinMinigameWithTemplate() {
        Player adminPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("hasPermission".equals(name)) return true;
                    if ("getUniqueId".equals(name)) return UUID.randomUUID();
                    if ("closeInventory".equals(name)) return null;
                    return null;
                }
        );
        assertTrue(router.dispatchAction(adminPlayer, "rga:join_minigame parkour 1000blocks"));
    }

    @Test
    void testPermissionCheckForTeleport_DeniedWithoutPermission() {
        assertFalse(router.canExecuteTeleport(player, "Creative"));
    }

    @Test
    void testPermissionCheckForTeleport_AllowedWithTpPermission() {
        Player playerWithTp = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("hasPermission".equals(name) && args != null && args.length == 1) {
                        String perm = (String) args[0];
                        return "rga.tp".equals(perm);
                    }
                    if ("getUniqueId".equals(name)) {
                        return UUID.randomUUID();
                    }
                    return null;
                }
        );
        assertTrue(router.canExecuteTeleport(playerWithTp, "Creative"));
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
