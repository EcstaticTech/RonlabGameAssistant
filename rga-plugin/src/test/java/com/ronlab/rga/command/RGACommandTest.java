package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import com.ronlab.rga.config.ConfigManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RGACommandTest {

    private TestRGA plugin;
    private TestConfigManager configManager;
    private CommandSender senderMock;
    private CommandSourceStack stackMock;
    private RGACommand rgaCommand;

    private List<String> sentMessages;
    private Set<String> permissions;

    @BeforeEach
    void setUp() throws Exception {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);

        configManager = new TestConfigManager(plugin);
        plugin.configManager = configManager;

        sentMessages = new ArrayList<>();
        permissions = new HashSet<>();

        senderMock = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) {
                        Component comp = (Component) args[0];
                        sentMessages.add(PlainTextComponentSerializer.plainText().serialize(comp));
                        return null;
                    }
                    if (method.getName().equals("hasPermission")) {
                        String perm = (String) args[0];
                        return permissions.contains(perm);
                    }
                    return null;
                }
        );

        stackMock = (CommandSourceStack) Proxy.newProxyInstance(
                CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSender")) {
                        return senderMock;
                    }
                    return null;
                }
        );

        rgaCommand = new RGACommand(plugin);
    }

    @Test
    void testReloadConfig_WithPermission_InvokesPluginReloadAndSendsMessage() {
        permissions.add("rga.reload");

        rgaCommand.execute(stackMock, new String[]{"reloadconfig"});

        assertTrue(plugin.reloaded, "plugin.reload() should be invoked when executing /rga reloadconfig");
        assertTrue(sentMessages.contains("Ronlab Game Assistant reloaded successfully."));
        assertFalse(sentMessages.stream().anyMatch(msg -> msg.contains("alias")), "No alias notice for primary /rga reloadconfig");
    }

    @Test
    void testReloadAlias_WithPermission_InvokesPluginReloadAndSendsAliasNotice() {
        permissions.add("rga.reload");

        rgaCommand.execute(stackMock, new String[]{"reload"});

        assertTrue(plugin.reloaded, "plugin.reload() should be invoked when executing /rga reload");
        assertTrue(sentMessages.stream().anyMatch(msg -> msg.contains("alias")), "Alias notice should be sent when executing legacy /rga reload");
        assertTrue(sentMessages.contains("Ronlab Game Assistant reloaded successfully."));
    }

    @Test
    void testReloadConfig_WithoutPermission_RejectsExecution() {
        // No permissions added

        rgaCommand.execute(stackMock, new String[]{"reloadconfig"});

        assertFalse(plugin.reloaded, "plugin.reload() should NOT be invoked without permission");
        assertTrue(sentMessages.contains("You do not have permission to do that."));
    }

    @Test
    void testTabComplete_IncludesReloadConfig() {
        permissions.add("rga.reload");

        Collection<String> completions = rgaCommand.suggest(stackMock, new String[]{"reload"});

        assertNotNull(completions);
        assertTrue(completions.contains("reloadconfig"), "Tab completions should contain reloadconfig");
        assertTrue(completions.contains("reload"), "Tab completions should contain reload alias");
    }

    @Test
    void testTabComplete_EmptyOrNullArgs_ReturnsSubcommandsWithoutException() {
        permissions.add("rga.reload");

        assertDoesNotThrow(() -> {
            Collection<String> emptyArgsCompletions = rgaCommand.suggest(stackMock, new String[0]);
            assertNotNull(emptyArgsCompletions);
            assertTrue(emptyArgsCompletions.contains("reloadconfig"));

            Collection<String> nullArgsCompletions = rgaCommand.suggest(stackMock, null);
            assertNotNull(nullArgsCompletions);
            assertTrue(nullArgsCompletions.contains("reloadconfig"));
        });
    }

    private static class TestRGA extends RGA {
        boolean reloaded = false;
        ConfigManager configManager;

        @Override
        public void reload() {
            reloaded = true;
        }

        @Override
        public ConfigManager getConfigManager() {
            return configManager;
        }
    }

    private static class TestConfigManager extends ConfigManager {
        public TestConfigManager(RGA plugin) { super(plugin); }
        @Override
        public void reload() {
            // No-op to avoid uninitialized JavaPlugin file operations in unit test
        }
        @Override
        public Component getMessage(String key) {
            if ("reloaded".equals(key)) return Component.text("Ronlab Game Assistant reloaded successfully.");
            if ("no-permission".equals(key)) return Component.text("You do not have permission to do that.");
            return Component.empty();
        }
    }
}
