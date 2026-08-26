package com.ronlab.rga.core.template;

import com.ronlab.rga.RGA;
import com.ronlab.rga.api.template.MapTemplateMetadata;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administrative manager for dynamic loading, hot-saving, clean unloading,
 * and session collision prevention of raw map templates.
 */
public class TemplateStagingManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final RGA plugin;
    private final Set<String> activeEditingTemplates = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> templateEditors = new ConcurrentHashMap<>();

    public TemplateStagingManager(RGA plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a template ID or world folder name is currently in an active EDITING state.
     */
    public boolean isTemplateEditing(String idOrWorldName) {
        if (idOrWorldName == null || idOrWorldName.isBlank()) return false;
        if (activeEditingTemplates.contains(idOrWorldName)) return true;

        TemplateDiscoveryService discovery = plugin.getTemplateDiscoveryService();
        if (discovery == null) return false;

        for (String activeId : activeEditingTemplates) {
            if (activeId.equalsIgnoreCase(idOrWorldName)) return true;
            MapTemplateMetadata meta = discovery.get(activeId);
            if (meta != null) {
                if (meta.id().equalsIgnoreCase(idOrWorldName)) return true;
                if (meta.templatePath() != null) {
                    String folderName = meta.templatePath().getFileName().toString();
                    if (folderName.equalsIgnoreCase(idOrWorldName)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns an unmodifiable set of currently active editing template IDs.
     */
    public Set<String> getActiveEditingTemplates() {
        return Collections.unmodifiableSet(activeEditingTemplates);
    }

    /**
     * Resolves the template ID being edited by world name or alias.
     */
    public String getEditingTemplateId(String idOrWorldName) {
        if (idOrWorldName == null || idOrWorldName.isBlank()) return null;
        for (String activeId : activeEditingTemplates) {
            if (activeId.equalsIgnoreCase(idOrWorldName)) return activeId;
            if (plugin.getTemplateDiscoveryService() != null) {
                MapTemplateMetadata meta = plugin.getTemplateDiscoveryService().get(activeId);
                if (meta != null) {
                    if (meta.id().equalsIgnoreCase(idOrWorldName)) return meta.id();
                    if (meta.templatePath() != null) {
                        String folderName = meta.templatePath().getFileName().toString();
                        if (folderName.equalsIgnoreCase(idOrWorldName)) return meta.id();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Dynamically loads a root template world from disk via Bukkit.createWorld,
     * switches the player to CREATIVE mode, and teleports them to the template spawn.
     */
    public void loadTemplateForEditing(Player player, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            player.sendMessage(MM.deserialize("<red>Please specify a template ID."));
            return;
        }

        TemplateDiscoveryService discovery = plugin.getTemplateDiscoveryService();
        MapTemplateMetadata meta = discovery != null ? discovery.get(templateId) : null;
        if (meta == null) {
            player.sendMessage(MM.deserialize("<red>Unknown template ID: " + templateId));
            return;
        }

        Path templatePath = meta.templatePath();
        if (templatePath == null) {
            player.sendMessage(MM.deserialize("<red>Template has no disk folder path: " + templateId));
            return;
        }

        String worldFolder = templatePath.getFileName().toString();
        World world = Bukkit.getWorld(worldFolder);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(worldFolder));
        }

        if (world == null) {
            player.sendMessage(MM.deserialize("<red>Failed to load template world from disk: " + worldFolder));
            return;
        }

        activeEditingTemplates.add(meta.id());
        templateEditors.put(meta.id(), player.getUniqueId());

        player.setGameMode(GameMode.CREATIVE);
        player.teleportAsync(world.getSpawnLocation());
        player.sendMessage(MM.deserialize("<green>Loaded & teleported to template: " + meta.id()));
    }

    /**
     * Teleports an operator into an already loaded (or dynamically loaded) template world in CREATIVE mode.
     */
    public void teleportToTemplate(Player player, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            player.sendMessage(MM.deserialize("<red>Please specify a template ID."));
            return;
        }

        TemplateDiscoveryService discovery = plugin.getTemplateDiscoveryService();
        MapTemplateMetadata meta = discovery != null ? discovery.get(templateId) : null;
        if (meta == null) {
            player.sendMessage(MM.deserialize("<red>Unknown template ID: " + templateId));
            return;
        }

        Path templatePath = meta.templatePath();
        if (templatePath == null) {
            player.sendMessage(MM.deserialize("<red>Template has no disk folder path: " + templateId));
            return;
        }

        String worldFolder = templatePath.getFileName().toString();
        World world = Bukkit.getWorld(worldFolder);
        if (world == null) {
            // Load if not yet loaded
            loadTemplateForEditing(player, meta.id());
            return;
        }

        activeEditingTemplates.add(meta.id());
        templateEditors.put(meta.id(), player.getUniqueId());

        player.setGameMode(GameMode.CREATIVE);
        player.teleportAsync(world.getSpawnLocation());
        player.sendMessage(MM.deserialize("<green>Teleported to template: " + meta.id()));
    }

    /**
     * Executes world.save() on the specified template world to flush chunk changes directly to disk.
     */
    public void saveTemplate(CommandSender sender, String templateId) {
        TemplateDiscoveryService discovery = plugin.getTemplateDiscoveryService();

        MapTemplateMetadata meta = null;
        if (templateId != null && !templateId.isBlank() && discovery != null) {
            meta = discovery.get(templateId);
        } else if (sender instanceof Player p && discovery != null) {
            String currentWorldName = p.getWorld().getName();
            meta = discovery.get(currentWorldName);
        }

        if (meta == null) {
            sender.sendMessage(MM.deserialize("<red>Unknown template ID or not currently inside a template world."));
            return;
        }

        Path templatePath = meta.templatePath();
        String worldFolder = templatePath != null ? templatePath.getFileName().toString() : meta.id();
        World world = Bukkit.getWorld(worldFolder);
        if (world == null) {
            sender.sendMessage(MM.deserialize("<red>Template world is not loaded: " + worldFolder));
            return;
        }

        world.save();
        sender.sendMessage(MM.deserialize("<green>Saved chunk changes for template: " + meta.id()));
    }

    /**
     * Teleports players back to Hub and cleanly unloads the template world with save=true.
     */
    public void unloadTemplate(CommandSender sender, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            if (sender != null) sender.sendMessage(MM.deserialize("<red>Please specify a template ID to unload."));
            return;
        }

        TemplateDiscoveryService discovery = plugin.getTemplateDiscoveryService();
        MapTemplateMetadata meta = discovery != null ? discovery.get(templateId) : null;
        if (meta == null) {
            if (sender != null) sender.sendMessage(MM.deserialize("<red>Unknown template ID: " + templateId));
            return;
        }

        Path templatePath = meta.templatePath();
        String worldFolder = templatePath != null ? templatePath.getFileName().toString() : meta.id();
        World world = Bukkit.getWorld(worldFolder);

        if (world != null) {
            String hubName = plugin.getConfigManager() != null ? plugin.getConfigManager().getHubWorld() : "world";
            World hub = Bukkit.getWorld(hubName);
            Location hubSpawn = hub != null ? hub.getSpawnLocation() : null;

            for (Player p : world.getPlayers()) {
                if (hubSpawn != null) {
                    p.teleportAsync(hubSpawn);
                }
                p.sendMessage(MM.deserialize("<yellow>Template world is unloading. Teleported to Hub."));
            }
            Bukkit.unloadWorld(world, true);
        }

        activeEditingTemplates.remove(meta.id());
        templateEditors.remove(meta.id());

        if (sender != null) {
            sender.sendMessage(MM.deserialize("<green>Template unloaded and saved to disk: " + meta.id()));
        }
    }

    /**
     * Unloads all active editing templates with save=true (e.g. on plugin shutdown).
     */
    public void unloadAllTemplates() {
        Set<String> activeCopy = new HashSet<>(activeEditingTemplates);
        for (String id : activeCopy) {
            unloadTemplate(null, id);
        }
    }

    /**
     * Lists active editing templates and available discovered templates.
     */
    public void listTemplates(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><b>======= Map Templates =======</b></gold>"));
        
        TemplateDiscoveryService discovery = plugin.getTemplateDiscoveryService();
        Map<String, MapTemplateMetadata> registry = discovery != null ? discovery.getRegistry() : Map.of();

        if (registry.isEmpty()) {
            sender.sendMessage(MM.deserialize("<gray>No template descriptors registered.</gray>"));
        } else {
            for (MapTemplateMetadata meta : registry.values()) {
                boolean isEditing = activeEditingTemplates.contains(meta.id());
                String status = isEditing ? "<green>[EDITING / LOADED]</green>" : "<gray>[UNLOADED]</gray>";
                sender.sendMessage(MM.deserialize(" <yellow>•</yellow> <white>" + meta.id() + "</white> " + status + " <dark_gray>(" + meta.category() + ")</dark_gray>"));
            }
        }

        sender.sendMessage(MM.deserialize("<gold><b>=============================</b></gold>"));
    }

    /**
     * Auto-unloads any template if its editing operator disconnects to prevent memory leaks.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        List<String> toUnload = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : templateEditors.entrySet()) {
            if (entry.getValue().equals(playerUuid)) {
                toUnload.add(entry.getKey());
            }
        }

        // Also check if quitting player was the last player inside an active editing world
        TemplateDiscoveryService discovery = plugin.getTemplateDiscoveryService();
        if (discovery != null) {
            for (String templateId : activeEditingTemplates) {
                MapTemplateMetadata meta = discovery.get(templateId);
                if (meta != null && meta.templatePath() != null) {
                    World world = Bukkit.getWorld(meta.templatePath().getFileName().toString());
                    if (world != null && world.getPlayers().contains(player) && world.getPlayers().size() <= 1) {
                        if (!toUnload.contains(templateId)) {
                            toUnload.add(templateId);
                        }
                    }
                }
            }
        }

        for (String templateId : toUnload) {
            plugin.getLogger().info("[RGA] Operator " + player.getName() + " disconnected. Auto-unloading template: " + templateId);
            unloadTemplate(null, templateId);
        }
    }
}
