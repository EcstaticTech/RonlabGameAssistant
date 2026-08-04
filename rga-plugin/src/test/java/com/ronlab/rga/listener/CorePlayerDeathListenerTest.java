package com.ronlab.rga.listener;

import com.ronlab.rga.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorePlayerDeathListenerTest {

    private SessionManager sessionManagerMock;
    private TestCorePlayerDeathListener listener;
    private final UUID activePlayerUuid = UUID.randomUUID();
    private final UUID nonActivePlayerUuid = UUID.randomUUID();

    private static class TestCorePlayerDeathListener extends CorePlayerDeathListener {
        private final List<Player> handledHubDeaths = new ArrayList<>();

        public TestCorePlayerDeathListener(SessionManager sessionManager) {
            super(sessionManager);
        }

        @Override
        public void handleHubDeathSequence(Player player, PlayerDeathEvent event) {
            handledHubDeaths.add(player);
        }

        public List<Player> getHandledHubDeaths() {
            return handledHubDeaths;
        }
    }

    private static Player createPlayerMock(UUID uuid) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "equals" -> args != null && args.length > 0 && args[0] == proxy;
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                }
        );
    }

    private static PlayerDeathEvent createPlayerDeathEvent(Player player) throws Exception {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        PlayerDeathEvent event = (PlayerDeathEvent) unsafe.allocateInstance(PlayerDeathEvent.class);

        // Set entity field on EntityEvent superclass
        Field entityField = org.bukkit.event.entity.EntityEvent.class.getDeclaredField("entity");
        entityField.setAccessible(true);
        entityField.set(event, player);

        return event;
    }

    @BeforeEach
    void setUp() {
        sessionManagerMock = new SessionManager(null) {
            @Override
            public boolean isPlayerInActiveSession(UUID playerUuid) {
                return activePlayerUuid.equals(playerUuid);
            }
        };

        listener = new TestCorePlayerDeathListener(sessionManagerMock);
    }

    @Test
    void testActiveSessionPlayerDeathYieldsControl() throws Exception {
        Player activePlayer = createPlayerMock(activePlayerUuid);
        PlayerDeathEvent event = createPlayerDeathEvent(activePlayer);

        listener.onPlayerDeath(event);

        assertTrue(listener.getHandledHubDeaths().isEmpty(),
                "CorePlayerDeathListener must yield 100% control and skip hub death sequence for active session players");
    }

    @Test
    void testNonSessionPlayerDeathExecutesHubSequence() throws Exception {
        Player nonActivePlayer = createPlayerMock(nonActivePlayerUuid);
        PlayerDeathEvent event = createPlayerDeathEvent(nonActivePlayer);

        listener.onPlayerDeath(event);

        assertEquals(1, listener.getHandledHubDeaths().size());
        assertSame(nonActivePlayer, listener.getHandledHubDeaths().get(0),
                "CorePlayerDeathListener must execute hub death sequence for non-session players");
    }
}
