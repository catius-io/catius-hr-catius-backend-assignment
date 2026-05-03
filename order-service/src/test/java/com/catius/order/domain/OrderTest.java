package com.catius.order.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @Test
    void confirmed_createsOrderInConfirmedStateWithItems() {
        Order order = Order.confirmed("order-1", 100L, List.of(
                OrderItem.of(1001L, 2),
                OrderItem.of(1002L, 3)
        ));

        assertEquals("order-1", order.getOrderId());
        assertEquals(100L, order.getCustomerId());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(2, order.getItems().size());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void confirmed_rejectsEmptyItems() {
        assertThrows(IllegalArgumentException.class, () ->
                Order.confirmed("order-1", 100L, List.of()));
    }

    @Test
    void confirmed_rejectsNullItems() {
        assertThrows(NullPointerException.class, () ->
                Order.confirmed("order-1", 100L, null));
    }

    @Test
    void confirmed_rejectsNullOrderId() {
        assertThrows(NullPointerException.class, () ->
                Order.confirmed(null, 100L, List.of(OrderItem.of(1001L, 2))));
    }

    @Test
    void confirmed_rejectsZeroCustomerId() {
        assertThrows(IllegalArgumentException.class, () ->
                Order.confirmed("order-1", 0L, List.of(OrderItem.of(1001L, 2))));
    }

    @Test
    void confirmed_rejectsNegativeCustomerId() {
        assertThrows(IllegalArgumentException.class, () ->
                Order.confirmed("order-1", -1L, List.of(OrderItem.of(1001L, 2))));
    }

    @Test
    void confirmed_rejectsDuplicateProductId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Order.confirmed("order-1", 100L, List.of(
                        OrderItem.of(1001L, 2),
                        OrderItem.of(1001L, 1)
                )));
        assertEquals("duplicate productId: 1001", ex.getMessage());
    }

    @Test
    void getItems_returnsUnmodifiableView() {
        Order order = Order.confirmed("order-1", 100L, List.of(OrderItem.of(1001L, 2)));
        assertThrows(UnsupportedOperationException.class, () ->
                order.getItems().add(OrderItem.of(1002L, 1)));
    }
}
