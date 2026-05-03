package com.catius.inventory.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryTest {

    @Test
    void of_createsInventory() {
        Inventory inv = Inventory.of(1001L, 100);
        assertEquals(1001L, inv.getProductId());
        assertEquals(100, inv.getQuantity());
    }

    @Test
    void of_allowsZeroQuantity() {
        Inventory inv = Inventory.of(1001L, 0);
        assertEquals(0, inv.getQuantity());
    }

    @Test
    void of_rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> Inventory.of(1001L, -1));
    }

    @Test
    void of_rejectsZeroProductId() {
        assertThrows(IllegalArgumentException.class, () -> Inventory.of(0L, 100));
    }

    @Test
    void of_rejectsNegativeProductId() {
        assertThrows(IllegalArgumentException.class, () -> Inventory.of(-1L, 100));
    }
}
