package com.ronlab.rga.player;

import com.ronlab.rga.RGA;
import com.ronlab.rga.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class InventoryManagerHubEntryTest {

    private File tempDir;
    private TestRGA plugin;
    private ConfigManager configManagerMock;
    private InventoryManager inventoryManager;

    private boolean clearOnEntry = true;
    private boolean restoreOnReturn = false;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("rga-inv-test").toFile();

        Server serverMock = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getLogger")) {
                        return Logger.getLogger("Minecraft");
                    }
                    if (method.getName().equals("getPluginManager")) {
                        return Proxy.newProxyInstance(
                                Server.class.getClassLoader(),
                                new Class<?>[] { org.bukkit.plugin.PluginManager.class },
                                (p, m, a) -> null
                        );
                    }
                    return null;
                }
        );
        setStaticField(Bukkit.class, "server", serverMock);

        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        plugin = (TestRGA) unsafe.allocateInstance(TestRGA.class);

        setPrivateField(plugin, "dataFolder", tempDir);
        setPrivateField(plugin, "logger", Logger.getLogger("RGA"));

        YamlConfiguration config = new YamlConfiguration();
        config.set("hub-world", "Hub");
        config.set("inventory-groups.smp.worlds", List.of("world", "world_nether"));
        config.set("inventory-groups.creative.worlds", List.of("Creative"));
        plugin.config = config;

        TestConfigManager configMgr = (TestConfigManager) unsafe.allocateInstance(TestConfigManager.class);
        setPrivateField(configMgr, "plugin", plugin);
        setPrivateField(plugin, "configManager", configMgr);

        inventoryManager = new InventoryManager(plugin);
    }

    private class TestConfigManager extends ConfigManager {
        public TestConfigManager() { super(plugin); }
        @Override public String getHubWorld() { return "Hub"; }
        @Override public boolean isClearInventoryOnHubEntry() { return clearOnEntry; }
        @Override public boolean isRestoreInventoryOnHubReturn() { return restoreOnReturn; }
    }

    @Test
    void testDefaultBehavior_ClearsInventoryOnHubEntry_NoRestore() {
        clearOnEntry = true;
        restoreOnReturn = false;

        TestPlayerData playerData = new TestPlayerData(UUID.randomUUID(), "world");
        Player player = playerData.createProxy();
        playerData.contents[0] = createMockItem(Material.DIAMOND, 64);

        World fromWorld = createMockWorld("world");
        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, fromWorld);

        playerData.world = createMockWorld("Hub");
        inventoryManager.onWorldChange(event);

        assertNull(playerData.contents[0], "Inventory should be cleared when entering Hub");

        playerData.world = createMockWorld("world");
        PlayerChangedWorldEvent returnEvent = new PlayerChangedWorldEvent(player, createMockWorld("Hub"));
        inventoryManager.onWorldChange(returnEvent);

        assertNull(playerData.contents[0], "Inventory should not restore if restoreOnReturn is false");
    }

    @Test
    void testClearDisabled_PreservesInventoryOnHubEntry() {
        clearOnEntry = false;
        restoreOnReturn = false;

        TestPlayerData playerData = new TestPlayerData(UUID.randomUUID(), "world");
        Player player = playerData.createProxy();
        playerData.contents[0] = createMockItem(Material.DIAMOND, 64);

        World fromWorld = createMockWorld("world");
        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, fromWorld);

        playerData.world = createMockWorld("Hub");
        inventoryManager.onWorldChange(event);

        assertNotNull(playerData.contents[0], "Inventory should be preserved when clearOnEntry is false");
        assertEquals(Material.DIAMOND, playerData.contents[0].getType());
    }

    @Test
    void testRestoreOnReturn_RestoresWhenReturningToSameGroup() {
        clearOnEntry = true;
        restoreOnReturn = true;

        TestPlayerData playerData = new TestPlayerData(UUID.randomUUID(), "world");
        Player player = playerData.createProxy();
        playerData.contents[0] = createMockItem(Material.GOLD_INGOT, 32);

        World fromWorld = createMockWorld("world");
        PlayerChangedWorldEvent entryEvent = new PlayerChangedWorldEvent(player, fromWorld);

        playerData.world = createMockWorld("Hub");
        inventoryManager.onWorldChange(entryEvent);

        assertNull(playerData.contents[0], "Inventory should be cleared upon Hub entry");

        playerData.world = createMockWorld("world_nether"); // Same group ('smp')
        PlayerChangedWorldEvent returnEvent = new PlayerChangedWorldEvent(player, createMockWorld("Hub"));
        inventoryManager.onWorldChange(returnEvent);

        assertNotNull(playerData.contents[0], "Inventory should be restored when returning to same group");
        assertEquals(Material.GOLD_INGOT, playerData.contents[0].getType());
        assertEquals(32, playerData.contents[0].getAmount());
    }

    @Test
    void testRestoreOnReturn_DoesNotRestoreWhenChangingToDifferentGroup() {
        clearOnEntry = true;
        restoreOnReturn = true;

        TestPlayerData playerData = new TestPlayerData(UUID.randomUUID(), "world");
        Player player = playerData.createProxy();
        playerData.contents[0] = createMockItem(Material.GOLD_INGOT, 32);

        World fromWorld = createMockWorld("world");
        PlayerChangedWorldEvent entryEvent = new PlayerChangedWorldEvent(player, fromWorld);

        playerData.world = createMockWorld("Hub");
        inventoryManager.onWorldChange(entryEvent);

        playerData.world = createMockWorld("Creative"); // Different group ('creative')
        PlayerChangedWorldEvent exitEvent = new PlayerChangedWorldEvent(player, createMockWorld("Hub"));
        inventoryManager.onWorldChange(exitEvent);

        assertNull(playerData.contents[0], "Snapshot should not restore when entering a different group");
    }

    @Test
    void testPlayerQuit_RemovesSnapshot() {
        clearOnEntry = true;
        restoreOnReturn = true;

        TestPlayerData playerData = new TestPlayerData(UUID.randomUUID(), "world");
        Player player = playerData.createProxy();
        playerData.contents[0] = createMockItem(Material.EMERALD, 10);

        World fromWorld = createMockWorld("world");
        PlayerChangedWorldEvent entryEvent = new PlayerChangedWorldEvent(player, fromWorld);

        playerData.world = createMockWorld("Hub");
        inventoryManager.onWorldChange(entryEvent);

        net.kyori.adventure.text.Component quitMsg = null;
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, quitMsg);
        inventoryManager.onQuit(quitEvent);

        playerData.world = createMockWorld("world");
        PlayerChangedWorldEvent returnEvent = new PlayerChangedWorldEvent(player, createMockWorld("Hub"));
        inventoryManager.onWorldChange(returnEvent);

        assertNull(playerData.contents[0], "Snapshot should have been removed on quit");
    }

    private ItemStack createMockItem(Material mat, int amount) {
        try {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            ItemStack item = (ItemStack) unsafe.allocateInstance(ItemStack.class);
            setPrivateField(item, "type", mat);
            setPrivateField(item, "amount", amount);
            return item;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private World createMockWorld(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] { World.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    return null;
                }
        );
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field != null) {
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static class TestRGA extends RGA {
        FileConfiguration config;
        @Override
        public FileConfiguration getConfig() {
            return config;
        }
    }

    private static class TestPlayerData {
        final UUID uuid;
        World world;
        final ItemStack[] contents = new ItemStack[36];
        final ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;
        int heldSlot = 0;
        float exp = 0;
        int level = 0;
        int totalExp = 0;
        double health = 20.0;
        int foodLevel = 20;
        float saturation = 5.0f;

        TestPlayerData(UUID uuid, String worldName) {
            this.uuid = uuid;
            this.world = (World) Proxy.newProxyInstance(
                    World.class.getClassLoader(),
                    new Class<?>[] { World.class },
                    (proxy, method, args) -> method.getName().equals("getName") ? worldName : null
            );
        }

        Player createProxy() {
            PlayerInventory invProxy = (PlayerInventory) Proxy.newProxyInstance(
                    PlayerInventory.class.getClassLoader(),
                    new Class<?>[] { PlayerInventory.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getContents" -> contents;
                        case "setContents" -> {
                            System.arraycopy((ItemStack[]) args[0], 0, contents, 0, Math.min(contents.length, ((ItemStack[]) args[0]).length));
                            yield null;
                        }
                        case "getArmorContents" -> armor;
                        case "setArmorContents" -> {
                            System.arraycopy((ItemStack[]) args[0], 0, armor, 0, Math.min(armor.length, ((ItemStack[]) args[0]).length));
                            yield null;
                        }
                        case "getItemInOffHand" -> offhand;
                        case "setItemInOffHand" -> { offhand = (ItemStack) args[0]; yield null; }
                        case "getHeldItemSlot" -> heldSlot;
                        case "setHeldItemSlot" -> { heldSlot = (int) args[0]; yield null; }
                        case "clear" -> {
                            Arrays.fill(contents, null);
                            Arrays.fill(armor, null);
                            offhand = null;
                            yield null;
                        }
                        default -> null;
                    }
            );

            return (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[] { Player.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> uuid;
                        case "getWorld" -> world;
                        case "getName" -> "TestPlayer";
                        case "getInventory" -> invProxy;
                        case "getExp" -> exp;
                        case "setExp" -> { exp = (float) args[0]; yield null; }
                        case "getLevel" -> level;
                        case "setLevel" -> { level = (int) args[0]; yield null; }
                        case "getTotalExperience" -> totalExp;
                        case "setTotalExperience" -> { totalExp = (int) args[0]; yield null; }
                        case "getHealth" -> health;
                        case "setHealth" -> { health = (double) args[0]; yield null; }
                        case "getMaxHealth" -> 20.0;
                        case "getFoodLevel" -> foodLevel;
                        case "setFoodLevel" -> { foodLevel = (int) args[0]; yield null; }
                        case "getSaturation" -> saturation;
                        case "setSaturation" -> { saturation = (float) args[0]; yield null; }
                        case "isOnline" -> true;
                        default -> null;
                    }
            );
        }
    }
}
