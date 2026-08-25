package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import com.ronlab.rga.party.PartyManager;
import org.bukkit.entity.Player;
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
    }

    private static class TestRGA extends RGA {
        PartyManager partyManager;
        @Override
        public PartyManager getPartyManager() {
            return partyManager;
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

        plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);
        partyManager = (TestPartyManager) unsafe.allocateInstance(TestPartyManager.class);
        plugin.partyManager = partyManager;

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
}
