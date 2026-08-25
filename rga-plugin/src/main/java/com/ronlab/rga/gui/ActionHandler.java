package com.ronlab.rga.gui;

import com.ronlab.rga.RGA;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handler delegating menu action dispatches to RGACommandRouter.
 */
public class ActionHandler {

    private final RGA plugin;

    public ActionHandler(RGA plugin) {
        this.plugin = plugin;
    }

    public void handle(Player player, List<String> actions) {
        if (actions == null || actions.isEmpty()) return;

        for (String action : actions) {
            plugin.getCommandRouter().dispatchAction(player, action);
        }
    }
}
