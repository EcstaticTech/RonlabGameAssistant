package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HubCommand implements BasicCommand {

    private final RGA plugin;

    public HubCommand(RGA plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("rga.hub")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        String currentWorld = player.getWorld().getName();
        String hubWorld = plugin.getConfigManager().getHubWorld();

        // Don't teleport if already in Hub
        if (currentWorld.equalsIgnoreCase(hubWorld)) {
            player.sendMessage(Component.text("You are already in the Hub!", NamedTextColor.YELLOW));
            return;
        }

        // If the player is a spectator, restore their advancements and remove them from spectator mode
        if (plugin.getPartyManager().isSpectator(player.getUniqueId())) {
            plugin.getPartyManager().leaveSpectatorMode(player);
            // leaveSpectatorMode already teleports to hub, so we're done
            return;
        }

        // If leaving an SMP world, save the player's current location first
        if (plugin.getConfigManager().getSmpWorlds().contains(currentWorld)) {
            plugin.getLocationTracker().saveLocation(player, player.getLocation());
        }

        // Teleport to Hub
        plugin.getWorldManager().teleportToWorld(player, hubWorld, com.ronlab.rga.api.event.PlayerRGANavigateEvent.NavigationType.COMMAND_HUB);
    }
}