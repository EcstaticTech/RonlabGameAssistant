package com.ronlab.rga.api;

import com.ronlab.rga.api.event.PlayerRGANavigateEvent;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRGANavigateEventTest {

    @SuppressWarnings("unchecked")
    private <T> T mockInterface(Class<T> clazz, String name) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{clazz},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("PlayerRGANavigateEvent initializes fields correctly and handles cancellation")
    void testPlayerRGANavigateEvent() {
        Player player = mockInterface(Player.class, "Player1");
        World origin = mockInterface(World.class, "world");
        World target = mockInterface(World.class, "smp_world");

        PlayerRGANavigateEvent event = new PlayerRGANavigateEvent(
                player,
                origin,
                target,
                "smp_world",
                PlayerRGANavigateEvent.NavigationType.COMPASS_MENU
        );

        assertSame(player, event.getPlayer());
        assertSame(origin, event.getOriginWorld());
        assertSame(target, event.getTargetWorld());
        assertEquals("smp_world", event.getTargetWorldName());
        assertEquals(PlayerRGANavigateEvent.NavigationType.COMPASS_MENU, event.getNavigationType());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("PlayerRGANavigateEvent auto-populates targetWorldName from targetWorld when omitted")
    void testPlayerRGANavigateEvent4ArgConstructor() {
        Player player = mockInterface(Player.class, "Player1");
        World origin = mockInterface(World.class, "world");
        World target = mockInterface(World.class, "hub");

        PlayerRGANavigateEvent event = new PlayerRGANavigateEvent(
                player,
                origin,
                target,
                PlayerRGANavigateEvent.NavigationType.COMMAND_HUB
        );

        assertEquals("hub", event.getTargetWorldName());
    }
}
