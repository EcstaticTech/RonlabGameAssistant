package com.ronlab.rga.social;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.party.Party;
import com.ronlab.rga.util.AdventureUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public class BrowsePartiesGui implements Listener {

    private static final String GUI_TITLE = "§8§lOpen Parties";
    private static final int GUI_SIZE = 54;

    // Maps each open browser's UUID → the ordered list of minigame IDs shown
    // (index = slot position, used to resolve clicks back to a party)
    private final Map<UUID, List<String>> openBrowsers = new HashMap<>();

    private final RGA plugin;

    public BrowsePartiesGui(RGA plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Open ─────────────────────────────────────────────────────

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // ── Border ───────────────────────────────────────────────
        ItemStack border = makeBorder();
        for (int i = 0; i < 9; i++)  inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9,  border); inv.setItem(17, border);
        inv.setItem(18, border); inv.setItem(26, border);
        inv.setItem(27, border); inv.setItem(35, border);
        inv.setItem(36, border); inv.setItem(44, border);

        // ── Party slots (inner 4×7 = 28 slots) ──────────────────
        int[] partySlots = buildInnerSlots();

        Map<String, Party> active = plugin.getPartyManager().getActiveParties();
        List<String> slotMinigameIds = new ArrayList<>();

        int idx = 0;
        for (Map.Entry<String, Party> entry : active.entrySet()) {
            if (idx >= partySlots.length) break;

            Party party = entry.getValue();
            // Only show parties that are still in the lobby
            if (party.getState() != Party.State.LOBBY) continue;

            String minigameId = entry.getKey();
            Minigame minigame = plugin.getMinigameManager().getMinigame(minigameId);
            if (minigame == null) continue;

            inv.setItem(partySlots[idx], buildPartyItem(party, minigame));
            slotMinigameIds.add(minigameId);
            idx++;
        }

        // Pad remaining inner slots with a "no party" placeholder if empty
        if (idx == 0) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta m = empty.getItemMeta();
            m.displayName(Component.text("No open parties right now.", NamedTextColor.GRAY));
            m.lore(List.of(
                    Component.text("Join a minigame from the", NamedTextColor.DARK_GRAY),
                    Component.text("navigator to create one!", NamedTextColor.DARK_GRAY)
            ));
            empty.setItemMeta(m);
            inv.setItem(partySlots[0], empty);
        }

        openBrowsers.put(player.getUniqueId(), slotMinigameIds);
        player.openInventory(inv);
    }

    // ── Event Handlers ───────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openBrowsers.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (clicked.getType() != Material.PLAYER_HEAD) return;

        // Map the clicked slot back to a minigame ID
        int[] partySlots = buildInnerSlots();
        List<String> slotIds = openBrowsers.get(player.getUniqueId());

        int partyIndex = -1;
        for (int i = 0; i < partySlots.length; i++) {
            if (partySlots[i] == event.getSlot()) {
                partyIndex = i;
                break;
            }
        }

        if (partyIndex < 0 || partyIndex >= slotIds.size()) return;

        String minigameId = slotIds.get(partyIndex);
        player.closeInventory();

        // Schedule join on next tick so inventory close completes first
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getPartyManager().joinMinigame(player, minigameId), 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openBrowsers.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openBrowsers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Item builders ────────────────────────────────────────────

    private ItemStack buildPartyItem(Party party, Minigame minigame) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        Player leader = Bukkit.getPlayer(party.getLeaderUuid());
        if (leader != null) meta.setOwningPlayer(leader);

        String leaderName = leader != null ? leader.getName() : "Unknown";
        meta.displayName(Component.text(minigame.getName(), NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text()
                .append(Component.text("Party leader: ", NamedTextColor.GRAY))
                .append(Component.text(leaderName, NamedTextColor.WHITE))
                .build());
        lore.add(Component.text()
                .append(Component.text("Players: ", NamedTextColor.GRAY))
                .append(Component.text(party.getMemberCount() + "/" + minigame.getMaxPlayers(), NamedTextColor.WHITE))
                .build());
        lore.add(Component.empty());

        int readyCount = party.getReadyPlayers().size();
        lore.add(Component.text()
                .append(Component.text("Ready: ", NamedTextColor.GRAY))
                .append(Component.text(readyCount + "/" + party.getMemberCount(), NamedTextColor.WHITE))
                .build());
        lore.add(Component.empty());

        if (party.isFull()) {
            lore.add(Component.text("Party Full", NamedTextColor.RED, TextDecoration.BOLD));
        } else {
            lore.add(Component.text("Click to Join", NamedTextColor.GREEN, TextDecoration.BOLD));
        }

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    /**
     * Returns the 28 inner slot indices of a 6-row (54-slot) GUI,
     * leaving a 1-slot border all the way around.
     */
    private int[] buildInnerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}