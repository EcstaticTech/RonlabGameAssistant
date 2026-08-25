package com.ronlab.rga.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaginatedMapMenuTest {

    @Test
    void testInnerGridSlotLayout() {
        assertEquals(21, PaginatedMapMenu.INNER_GRID_SLOTS.length);
        assertEquals(10, PaginatedMapMenu.INNER_GRID_SLOTS[0]);
        assertEquals(16, PaginatedMapMenu.INNER_GRID_SLOTS[6]);
        assertEquals(19, PaginatedMapMenu.INNER_GRID_SLOTS[7]);
        assertEquals(25, PaginatedMapMenu.INNER_GRID_SLOTS[13]);
        assertEquals(28, PaginatedMapMenu.INNER_GRID_SLOTS[14]);
        assertEquals(34, PaginatedMapMenu.INNER_GRID_SLOTS[20]);
    }

    @Test
    void testNavigationSlots() {
        assertEquals(48, PaginatedMapMenu.PREV_PAGE_SLOT);
        assertEquals(49, PaginatedMapMenu.BACK_BUTTON_SLOT);
        assertEquals(50, PaginatedMapMenu.NEXT_PAGE_SLOT);
    }

    @Test
    void testTitleParser() {
        PaginatedMapMenu menu = new PaginatedMapMenu(null);

        PaginatedMapMenu.ParsedTitle parsed1 = menu.parseTitle("§8§lParkour Maps §7(Page 1/3)");
        assertNotNull(parsed1);
        assertEquals("parkour", parsed1.category());
        assertEquals(0, parsed1.page());

        PaginatedMapMenu.ParsedTitle parsed2 = menu.parseTitle("Minigames Maps (Page 2/4)");
        assertNotNull(parsed2);
        assertEquals("minigames", parsed2.category());
        assertEquals(1, parsed2.page());
    }
}
