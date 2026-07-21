package com.ronlab.rga;

import com.ronlab.rga.command.HubCommand;
import com.ronlab.rga.command.RGACommand;
import com.ronlab.rga.compass.CompassListener;
import com.ronlab.rga.compass.HubListener;
import com.ronlab.rga.config.ConfigManager;
import com.ronlab.rga.gui.MenuListener;
import com.ronlab.rga.gui.MenuManager;
import com.ronlab.rga.minigame.MinigameManager;
import com.ronlab.rga.minigame.MinigameWorldListener;
import com.ronlab.rga.party.LobbyGui;
import com.ronlab.rga.party.PartyManager;
import com.ronlab.rga.player.AdvancementManager;
import com.ronlab.rga.player.InventoryManager;
import com.ronlab.rga.player.LocationTracker;
import com.ronlab.rga.world.PortalBlockListener;
import com.ronlab.rga.world.WorldEnforcementListener;
import com.ronlab.rga.world.WorldManager;
import com.ronlab.rga.social.BrowsePartiesGui;
import com.ronlab.rga.social.SocialItem;
import com.ronlab.rga.social.SocialListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import com.ronlab.rga.util.VersionGuard;

import com.ronlab.rga.session.SessionManager;

public class RGA extends JavaPlugin {

    private static RGA instance;

    private ConfigManager configManager;
    private WorldManager worldManager;
    private MenuManager menuManager;
    private LocationTracker locationTracker;
    private InventoryManager inventoryManager;
    private AdvancementManager advancementManager;
    private SessionManager sessionManager;
    private MinigameManager minigameManager;
    private PartyManager partyManager;
    private LobbyGui lobbyGui;
    private HubListener hubListener;
    private SocialItem socialItem;
    private BrowsePartiesGui browsePartiesGui;

    @Override
    public void onEnable() {
        if (!VersionGuard.check(this)) {
            return;
        }

        instance = this;

        saveDefaultConfig();
        saveResource("worlds.yml", false);
        saveResource("menus.yml", false);
        saveResource("minigames.yml", false);

        configManager = new ConfigManager(this);
        locationTracker = new LocationTracker(this);
        inventoryManager = new InventoryManager(this);
        advancementManager = new AdvancementManager(this);
        sessionManager = new SessionManager(this);
        sessionManager.loadOrphanedSessions();

        worldManager = new WorldManager(this);
        menuManager = new MenuManager(this);
        minigameManager = new MinigameManager(this);
        lobbyGui = new LobbyGui(this);
        partyManager = new PartyManager(this);

        worldManager.loadConfiguredWorlds();

        socialItem = new SocialItem(this);
        browsePartiesGui = new BrowsePartiesGui(this);

        hubListener = new HubListener(this);
        getServer().getPluginManager().registerEvents(hubListener, this);
        getServer().getPluginManager().registerEvents(new CompassListener(this, menuManager, hubListener), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, menuManager), this);
        getServer().getPluginManager().registerEvents(new WorldEnforcementListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new MinigameWorldListener(this), this);
        getServer().getPluginManager().registerEvents(new SocialListener(this), this);

        RGACommand rgaCommand = new RGACommand(this);
        PluginCommand rga = getCommand("rga");
        if (rga != null) {
            rga.setExecutor(rgaCommand);
            rga.setTabCompleter(rgaCommand);
        }

        PluginCommand hub = getCommand("hub");
        if (hub != null) hub.setExecutor(new HubCommand(this));

        getLogger().info("Ronlab Game Assistant enabled.");
    }

    @Override
    public void onDisable() {
        if (locationTracker != null) locationTracker.saveAll();
        getLogger().info("Ronlab Game Assistant disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        menuManager.reload();
        inventoryManager.reload();
        minigameManager.reload();
        worldManager.loadConfiguredWorlds();
        getLogger().info("Ronlab Game Assistant reloaded.");
    }

    public static RGA getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public MenuManager getMenuManager() { return menuManager; }
    public LocationTracker getLocationTracker() { return locationTracker; }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public AdvancementManager getAdvancementManager() { return advancementManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public MinigameManager getMinigameManager() { return minigameManager; }
    public PartyManager getPartyManager() { return partyManager; }
    public LobbyGui getLobbyGui() { return lobbyGui; }
    public HubListener getHubListener() { return hubListener; }
    public SocialItem getSocialItem() { return socialItem; }
    public BrowsePartiesGui getBrowsePartiesGui() { return browsePartiesGui; }
}
