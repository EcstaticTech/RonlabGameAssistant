package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import com.ronlab.rga.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RGACommandTest {

    private TestRGA plugin;
    private TestConfigManager configManager;
    private CommandSender senderMock;
    private Command commandDummy;
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

        commandDummy = new TestCommand("rga");
        rgaCommand = new RGACommand(plugin);
    }

    @Test
    void testReloadConfig_WithPermission_InvokesPluginReloadAndSendsMessage() {
        permissions.add("rga.reload");

        boolean result = rgaCommand.onCommand(senderMock, commandDummy, "rga", new String[]{"reloadconfig"});

        assertTrue(result);
        assertTrue(plugin.reloaded, "plugin.reload() should be invoked when executing /rga reloadconfig");
        assertTrue(sentMessages.contains("Ronlab Game Assistant reloaded successfully."));
        assertFalse(sentMessages.stream().anyMatch(msg -> msg.contains("alias")), "No alias notice for primary /rga reloadconfig");
    }

    @Test
    void testReloadAlias_WithPermission_InvokesPluginReloadAndSendsAliasNotice() {
        permissions.add("rga.reload");

        boolean result = rgaCommand.onCommand(senderMock, commandDummy, "rga", new String[]{"reload"});

        assertTrue(result);
        assertTrue(plugin.reloaded, "plugin.reload() should be invoked when executing /rga reload");
        assertTrue(sentMessages.stream().anyMatch(msg -> msg.contains("alias")), "Alias notice should be sent when executing legacy /rga reload");
        assertTrue(sentMessages.contains("Ronlab Game Assistant reloaded successfully."));
    }

    @Test
    void testReloadConfig_WithoutPermission_RejectsExecution() {
        // No permissions added

        boolean result = rgaCommand.onCommand(senderMock, commandDummy, "rga", new String[]{"reloadconfig"});

        assertTrue(result);
        assertFalse(plugin.reloaded, "plugin.reload() should NOT be invoked without permission");
        assertTrue(sentMessages.contains("You do not have permission to do that."));
    }

    @Test
    void testTabComplete_IncludesReloadConfig() {
        permissions.add("rga.reload");

        List<String> completions = rgaCommand.onTabComplete(senderMock, commandDummy, "rga", new String[]{"reload"});

        assertNotNull(completions);
        assertTrue(completions.contains("reloadconfig"), "Tab completions should contain reloadconfig");
        assertTrue(completions.contains("reload"), "Tab completions should contain reload alias");
    }

    private static class TestCommand extends Command {
        protected TestCommand(String name) {
            super(name);
        }
        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
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
