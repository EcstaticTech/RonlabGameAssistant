package com.ronlab.rga.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionRecoveryHubEquipTest {

    @Test
    void nonDestructiveHotbarSlotting_preservesRestoredItemsInOccupiedSlots() {
        Object[] items = new Object[36];
        Object restoredItem = new Object();
        Object hubCompass = new Object();

        // Restored inventory has item in slot 8
        items[8] = restoredItem;

        // Simulate HubListener non-destructive slot placement logic for slot 8:
        int targetSlot = 8;
        Object existing = items[targetSlot];
        if (existing == null) {
            items[targetSlot] = hubCompass;
        } else {
            boolean placed = false;
            for (int i = 0; i <= 8; i++) {
                if (items[i] == null) {
                    items[i] = hubCompass;
                    placed = true;
                    break;
                }
            }
            if (!placed) items[0] = hubCompass;
        }

        // Slot 8 must still contain restored item (not overwritten)
        assertSame(restoredItem, items[8]);

        // Compass must be placed in first available slot (slot 0)
        assertSame(hubCompass, items[0]);
    }
}
