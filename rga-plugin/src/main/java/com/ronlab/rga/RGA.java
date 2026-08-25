package com.ronlab.rga;

import com.ronlab.rga.api.command.RGACommandRouter;
import com.ronlab.rga.command.DefaultRGACommandRouter;
import com.ronlab.rga.command.HubCommand;
import com.ronlab.rga.command.RGACommand;
import com.ronlab.rga.compass.CompassListener;
import com.ronlab.rga.compass.HubListener;
import com.ronlab.rga.config.ConfigManager;
import com.ronlab.rga.core.template.TemplateDiscoveryService;
import com.ronlab.rga.gui.MenuListener;
import com.ronlab.rga.gui.MenuManager;
import com.ronlab.rga.gui.PaginatedMapMenu;
import com.ronlab.rga.minigame.MinigameManager;
import com.ronlab.rga.minigame.MinigameWorldListener;
import com.ronlab.rga.party.LobbyGui;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.party.PartyManager;
import com.ronlab.rga.player.AdvancementManager;
import com.ronlab.rga.player.InventoryManager;
import com.ronlab.rga.player.LocationTracker;
import com.ronlab.rga.world.PortalBlockListener;
import com.ronlab.rga.world.WorldConfigManager;
import com.ronlab.rga.world.WorldEnforcementListener;
import com.ronlab.rga.world.WorldManager;
import com.ronlab.rga.social.BrowsePartiesGui;
import com.ronlab.rga.social.SocialItem;
import com.ronlab.rga.social.SocialListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import com.ronlab.rga.util.VersionGuard;

import com.ronlab.rga.api.event.ConcludeResult;
import com.ronlab.rga.api.RGASessionControl;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import com.ronlab.rga.session.SessionManager;
import com.ronlab.rga.session.audit.PowerLossRecoveryHandler;
import com.ronlab.rga.session.audit.RuntimeSessionAuditor;
import com.ronlab.rga.api.stats.RGAStatsProvider;
import com.ronlab.rga.persistence.SQLiteStatsProvider;
import org.bukkit.plugin.ServicePriority;

public class RGA extends JavaPlugin implements RGASessionControl {

    private static RGA instance;

    private ConfigManager configManager;
    private WorldConfigManager worldConfigManager;
    private WorldManager worldManager;
    private MenuManager menuManager;
    private PaginatedMapMenu paginatedMapMenu;
    private TemplateDiscoveryService templateDiscoveryService;
    private RGACommandRouter commandRouter;
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
    private RuntimeSessionAuditor runtimeSessionAuditor;
    private PowerLossRecoveryHandler powerLossRecoveryHandler;
    private SQLiteStatsProvider sqliteStatsProvider;
    private RGAStatsProvider statsProvider;

    @Override
    public void onEnable() {
        if (!VersionGuard.check(this)) {
            return;
        }

        instance = this;

        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        saveResourceIfNotExists("config.yml");
        saveResourceIfNotExists("worlds.yml");
        saveResourceIfNotExists("menus.yml");
        saveResourceIfNotExists("minigames.yml");
        saveResourceIfNotExists("paper-world-defaults.yml");

        configManager = new ConfigManager(this);
        worldConfigManager = new WorldConfigManager(this);
        locationTracker = new LocationTracker(this);
        inventoryManager = new InventoryManager(this);
        advancementManager = new AdvancementManager(this);
        sessionManager = new SessionManager(this);

        File sessionsDir = new File(getDataFolder(), "sessions");
        powerLossRecoveryHandler = new PowerLossRecoveryHandler(sessionsDir);
        powerLossRecoveryHandler.processPowerLossRecovery(sessionManager);

        sessionManager.loadOrphanedSessions();

        File dbFile = new File(new File(getDataFolder(), "data"), "rga.db");
        sqliteStatsProvider = new SQLiteStatsProvider(dbFile, getLogger());
        try {
            sqliteStatsProvider.initialize();
            statsProvider = sqliteStatsProvider;
            getServer().getServicesManager().register(RGAStatsProvider.class, statsProvider, this, ServicePriority.Normal);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize SQLite persistence engine: " + e.getMessage());
        }

        worldManager = new WorldManager(this);
        runtimeSessionAuditor = new RuntimeSessionAuditor(
                this,
                sessionManager,
                worldManager.getAsyncDirectoryDeleter(),
                sessionsDir
        );
        runtimeSessionAuditor.startAuditing(30);

        menuManager = new MenuManager(this);
        paginatedMapMenu = new PaginatedMapMenu(this);
        templateDiscoveryService = new TemplateDiscoveryService(this);
        commandRouter = new DefaultRGACommandRouter(this);
        minigameManager = new MinigameManager(this);
        lobbyGui = new LobbyGui(this);
        partyManager = new PartyManager(this);

        // Async template discovery walk on enable
        templateDiscoveryService.discoverTemplatesAsync();

        worldManager.loadConfiguredWorlds();
        worldManager.purgeOrphanedSessionFolders();

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
        getServer().getPluginManager().registerEvents(new com.ronlab.rga.listener.CorePlayerDeathListener(sessionManager), this);

        RGACommand rgaCommand = new RGACommand(this);
        HubCommand hubCommand = new HubCommand(this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("rga", "Ronlab Game Assistant main command", List.of("ronlab"), rgaCommand);
            event.registrar().register("hub", "Teleport to the server hub world", List.of(), hubCommand);
        });

        getLogger().info("Ronlab Game Assistant enabled.");
    }

    @Override
    public void onDisable() {
        if (runtimeSessionAuditor != null) {
            runtimeSessionAuditor.stopAuditing();
        }

        if (locationTracker != null) locationTracker.saveAll();

        // Clean up active game sessions - preserve session files for player recovery
        if (partyManager != null) {
            partyManager.cleanupAllActiveParties();
        }

        // Report recovery data status
        if (sessionManager != null && sessionManager.hasPendingRecoveries()) {
            getLogger().warning("Plugin shutdown with " + sessionManager.getPendingRecoveryCount()
                    + " player(s) pending recovery in " + sessionManager.getOrphanedSessionWorlds().size()
                    + " session(s). Recovery data will be available on next startup.");
        } else if (partyManager != null) {
            // Check if there are active parties with sessions that were preserved
            int activeSessions = (int) partyManager.getActiveParties().values().stream()
                    .filter(p -> p.getState() == Party.State.IN_GAME)
                    .count();
            int preservedCount = (int) partyManager.getActiveParties().values().stream()
                    .filter(p -> p.getState() == Party.State.IN_GAME)
                    .mapToInt(p -> p.getMemberCount())
                    .sum();
            if (activeSessions > 0) {
                getLogger().warning("Plugin shutdown with " + preservedCount + " player(s) in " + activeSessions
                        + " active session(s). Recovery data preserved for next startup.");
            }
        }

        if (sessionManager != null) {
            sessionManager.shutdown();
        }

        if (worldManager != null) {
            worldManager.shutdown();
        }

        if (sqliteStatsProvider != null) {
            sqliteStatsProvider.shutdown();
        }

        getLogger().info("Ronlab Game Assistant disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        if (worldConfigManager != null) worldConfigManager.reload();
        menuManager.reload();
        inventoryManager.reload();
        minigameManager.reload();
        if (templateDiscoveryService != null) templateDiscoveryService.discoverTemplatesAsync();
        worldManager.loadConfiguredWorlds();
        getLogger().info("Ronlab Game Assistant configuration and world definitions reloaded.");
    }

    public ConcludeResult requestSessionConclude(String worldName, String reason, Map<UUID, ? extends Number> scores) {
        if (partyManager == null) return ConcludeResult.ERROR;
        return partyManager.requestSessionConclude(worldName, reason, scores);
    }

    // ── RGASessionControl (JIT Spectator API) ────────────────────

    @Override
    public void setSpectator(org.bukkit.entity.Player player, boolean isSpectator) {
        if (partyManager == null) return;
        partyManager.setSpectator(player, isSpectator);
    }

    @Override
    public boolean isSpectator(org.bukkit.entity.Player player) {
        if (partyManager == null) return false;
        return partyManager.isPlayerSpectating(player.getUniqueId());
    }

    public void saveResourceIfNotExists(String resourcePath) {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File file = new File(dataFolder, resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
            getLogger().info("Generated default configuration: " + resourcePath);
        }
    }

    public static RGA getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public WorldConfigManager getWorldConfigManager() { return worldConfigManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public MenuManager getMenuManager() { return menuManager; }
    public PaginatedMapMenu getPaginatedMapMenu() { return paginatedMapMenu; }
    public TemplateDiscoveryService getTemplateDiscoveryService() { return templateDiscoveryService; }
    public RGACommandRouter getCommandRouter() { return commandRouter; }
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
    public RuntimeSessionAuditor getRuntimeSessionAuditor() { return runtimeSessionAuditor; }
    public PowerLossRecoveryHandler getPowerLossRecoveryHandler() { return powerLossRecoveryHandler; }
    public RGAStatsProvider getStatsProvider() { return statsProvider; }
}
