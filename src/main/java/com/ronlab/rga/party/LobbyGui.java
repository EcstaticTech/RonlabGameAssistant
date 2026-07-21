package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class LobbyGui implements Listener {

    private final RGA plugin;
    private static final int GUI_SIZE = 54;

    // Players currently in the lobby GUI
    private final Set<UUID> openLobbyPlayers = new HashSet<>();

    // Players being refreshed — suppress close removal during refresh
    private final Set<UUID> refreshing = new HashSet<>();

    public LobbyGui(RGA plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openLobby(Player player, Party party) {
        // Mark as refreshing so onInventoryClose doesn't remove from openLobbyPlayers
        refreshing.add(player.getUniqueId());

        Minigame minigame = party.getMinigame();
        String title = "§8§l" + minigame.getName() + " Lobby";
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // ── Border ───────────────────────────────────────────────
        ItemStack border = makeBorder();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border);  inv.setItem(17, border);
        inv.setItem(18, border); inv.setItem(26, border);
        inv.setItem(27, border); inv.setItem(35, border);
        inv.setItem(36, border); inv.setItem(44, border);

        // ── Game info item (top center) ──────────────────────────
        ItemStack info = new ItemStack(minigame.getDisplayItem());
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text(minigame.getName(), NamedTextColor.GOLD, TextDecoration.BOLD));
        List<Component> infoLore = new ArrayList<>(AdventureUtil.color(minigame.getDisplayLore()));
        infoLore.add(Component.empty());
        infoLore.add(Component.text()
                .append(Component.text("Players: ", NamedTextColor.GRAY))
                .append(Component.text(party.getMemberCount() + "/" + minigame.getMaxPlayers(), NamedTextColor.WHITE))
                .build());
        infoLore.add(Component.text()
                .append(Component.text("Min to start: ", NamedTextColor.GRAY))
                .append(Component.text(minigame.getMinPlayers(), NamedTextColor.WHITE))
                .build());
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // ── Player slots ─────────────────────────────────────────
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19};
        List<UUID> members = party.getMembers();

        for (int i = 0; i < slots.length; i++) {
            if (i < members.size()) {
                UUID memberUuid = members.get(i);
                Player member = Bukkit.getPlayer(memberUuid);
                boolean isReady = party.isReady(memberUuid);
                boolean isLeader = memberUuid.equals(party.getLeaderUuid());
                boolean isSelf = memberUuid.equals(player.getUniqueId());

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
                if (member != null) skullMeta.setOwningPlayer(member);

                NamedTextColor nameColor = isReady ? NamedTextColor.GREEN : NamedTextColor.RED;
                String nameStr = member != null ? member.getName() : "Unknown";
                Component displayName = Component.text().append(Component.text(nameStr, nameColor))
                        .append(isLeader ? Component.text(" ★", NamedTextColor.GOLD) : Component.empty())
                        .build();
                skullMeta.displayName(displayName);

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(isReady ? "✔ Ready" : "✘ Not Ready", nameColor));
                if (isSelf) {
                    lore.add(Component.empty());
                    lore.add(isReady
                            ? Component.text("Click to unready", NamedTextColor.YELLOW)
                            : Component.text("Click to ready up", NamedTextColor.YELLOW));
                }
                skullMeta.lore(lore);
                head.setItemMeta(skullMeta);
                inv.setItem(slots[i], head);

            } else {
                ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta emptyMeta = empty.getItemMeta();
                emptyMeta.displayName(Component.text("Empty Slot", NamedTextColor.GRAY));
                empty.setItemMeta(emptyMeta);
                inv.setItem(slots[i], empty);
            }
        }

        // ── Leave party button ────────────────────────────────────
        ItemStack leave = new ItemStack(Material.RED_BED);
        ItemMeta leaveMeta = leave.getItemMeta();
        leaveMeta.displayName(Component.text("Leave Party", NamedTextColor.RED, TextDecoration.BOLD));
        leaveMeta.lore(List.of(Component.text("Click to leave this party.", NamedTextColor.GRAY)));
        leave.setItemMeta(leaveMeta);
        inv.setItem(49, leave);

        // ── Status bar ───────────────────────────────────────────
        ItemStack status;
        ItemMeta statusMeta;

        if (party.getMemberCount() < minigame.getMinPlayers()) {
            status = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            statusMeta = status.getItemMeta();
            statusMeta.displayName(Component.text("Waiting for more players...", NamedTextColor.RED));
            statusMeta.lore(List.of(
                    Component.text("Need at least ", NamedTextColor.GRAY)
                            .append(Component.text(minigame.getMinPlayers(), NamedTextColor.WHITE))
                            .append(Component.text(" to start.", NamedTextColor.GRAY))
            ));
        } else if (party.allReady()) {
            status = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            statusMeta = status.getItemMeta();
            statusMeta.displayName(Component.text("Starting game...", NamedTextColor.GREEN, TextDecoration.BOLD));
            statusMeta.lore(List.of());
        } else {
            int notReady = party.getMemberCount() - party.getReadyPlayers().size();
            status = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
            statusMeta = status.getItemMeta();
            statusMeta.displayName(Component.text("Waiting for players to ready up...", NamedTextColor.YELLOW));
            statusMeta.lore(List.of(
                    Component.text(notReady + " player(s) not ready.", NamedTextColor.GRAY)
            ));
        }
        status.setItemMeta(statusMeta);
        inv.setItem(45, status);

        openLobbyPlayers.add(player.getUniqueId());
        player.openInventory(inv);

        // Done refreshing — remove flag on next tick after inventory opens
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> refreshing.remove(player.getUniqueId()), 1L);
    }

    // ── Event Handlers ───────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openLobbyPlayers.contains(player.getUniqueId())) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().isAir()) return;

        int slot = event.getSlot();

        // Leave party button
        if (slot == 49) {
            plugin.getPartyManager().leaveParty(player);
            return;
        }

        // Clicking a player head — toggle ready if it's the player's own head
        if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                if (skullMeta.getOwningPlayer() != null &&
                        skullMeta.getOwningPlayer().getUniqueId().equals(player.getUniqueId())) {
                    plugin.getPartyManager().toggleReady(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Don't remove from set if we're refreshing the lobby
        if (!refreshing.contains(player.getUniqueId())) {
            openLobbyPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openLobbyPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }
}