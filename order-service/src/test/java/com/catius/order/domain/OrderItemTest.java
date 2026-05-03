package com.catius.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @Test
    void of_createsItem() {
        OrderItem item = OrderItem.of(1001L, 2);
        assertEquals(1001L, item.getProductId());
        assertEquals(2, item.getQuantity());
    }

    @Test
    void of_rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> OrderItem.of(1001L, 0));
    }

    @Test
    void of_rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> OrderItem.of(1001L, -1));
    }

    @Test
    void of_rejectsZeroProductId() {
        assertThrows(IllegalArgumentException.class, () -> OrderItem.of(0L, 1));
    }

    @Test
    void of_rejectsNegativeProductId() {
        assertThrows(IllegalArgumentException.class, () -> OrderItem.of(-1L, 1));
    }
}
