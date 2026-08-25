package com.ronlab.rga.api.command;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Interface defining contract for central command routing, action sanitization,
 * party state validation, and permission enforcement for RGA navigation and minigame actions.
 */
public interface RGACommandRouter {

    /**
     * Sanitizes and dispatches a single action string (e.g. "rga:join_minigame manhunt" or "rga:tp Creative").
     *
     * @param player player initiating action
     * @param action raw action string
     * @return true if action was successfully executed/dispatched, false otherwise
     */
    boolean dispatchAction(Player player, String action);

    /**
     * Sanitizes and dispatches a sequence of action strings.
     *
     * @param player  player initiating actions
     * @param actions list of action strings
     * @return true if all actions were processed without security block, false otherwise
     */
    boolean dispatchActions(Player player, List<String> actions);

    /**
     * Validates party state and permissions before attempting to join a minigame.
     * Checks permission node 'rga.user.join' (or 'rga.admin' bypass).
     *
     * @param player     player attempting to join
     * @param minigameId target minigame identifier
     * @return true if execution is permitted and party state valid
     */
    boolean canExecuteJoin(Player player, String minigameId);

    /**
     * Validates permissions before attempting to teleport to a target world.
     * Checks permission node 'rga.tp' (or 'rga.admin' bypass).
     *
     * @param player      player attempting teleport
     * @param targetWorld target world name
     * @return true if execution is permitted
     */
    boolean canExecuteTeleport(Player player, String targetWorld);

    /**
     * Executes minigame join after sanitization, party validation, and permission checks.
     *
     * @param player     player initiating join
     * @param minigameId target minigame identifier
     * @return true if join was executed successfully
     */
    boolean executeJoinMinigame(Player player, String minigameId);

    /**
     * Executes world teleport after sanitization and permission checks.
     *
     * @param player      player initiating teleport
     * @param targetWorld target world name
     * @return true if teleport was executed successfully
     */
    boolean executeTeleport(Player player, String targetWorld);
}
