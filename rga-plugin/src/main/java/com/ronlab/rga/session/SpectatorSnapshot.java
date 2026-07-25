package com.ronlab.rga.session;

import org.bukkit.inventory.ItemStack;

/**
 * Immutable in-memory snapshot of a player's inventory and vital stats captured
 * at the moment they enter spectator mode in an active RGA minigame session.
 * <p>
 * This snapshot is held in {@code PartyManager}'s {@code spectatorSnapshots} map
 * (keyed by player UUID) and is consumed — then discarded — when the player leaves
 * spectator mode via {@code PartyManager#setSpectator(Player, false)} or when the
 * session is concluded.
 * <p>
 * The snapshot intentionally covers the full player state that RGA manages:
 * inventory contents, armour slots, offhand, held slot, experience, level, total XP,
 * health, food level, and saturation. This mirrors the {@code HubSnapshot} design
 * in {@code InventoryManager} but is decoupled from it so spectator state is
 * independent of world-change events.
 *
 * @param contents   the 36-slot main inventory contents (may contain nulls for empty slots)
 * @param armor      the 4-slot armour contents (may contain nulls)
 * @param offhand    the offhand item, or {@code null} if empty
 * @param heldSlot   the hotbar slot index the player had selected
 * @param exp        the fractional experience bar progress [0.0, 1.0]
 * @param level      the experience level
 * @param totalExp   the total accumulated experience points
 * @param health     the health value at snapshot time (clamped to maxHealth on restore)
 * @param foodLevel  the food level [0, 20]
 * @param saturation the saturation value
 */
public record SpectatorSnapshot(
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offhand,
        int heldSlot,
        float exp,
        int level,
        int totalExp,
        double health,
        int foodLevel,
        float saturation
) {}
