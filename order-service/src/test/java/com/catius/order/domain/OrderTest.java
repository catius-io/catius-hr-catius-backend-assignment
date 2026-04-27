package com.catius.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {


    @Test
    @DisplayName("주문 생성 시 customerId가 기록되고 초기 상태는 PENDING이다.")
    void create_order() {
        Order order = Order.create(1L);

        assertEquals(1L, order.getCustomerId());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    @DisplayName("PENDING 상태의 주문을 확정하면 CONFIRMED가 된다.")
    void confirm_order() {
        Order order = Order.create(1L);

        order.confirm();

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    @DisplayName("PENDING 상태의 주문을 취소하면 CANCELLED가 된다.")
    void cancel_order() {
        Order order = Order.create(1L);

        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("CONFIRMED 상태의 주문을 다시 확정하면 예외가 발생한다.")
    void confirm_already_confirmed_order() {
        Order order = Order.create(1L);
        order.confirm();

        assertThrows(IllegalStateException.class, order::confirm);
    }

    @Test
    @DisplayName("CANCELLED 상태의 주문을 취소하면 예외가 발생한다.")
    void cancel_already_cancelled_order() {
        Order order = Order.create(1L);
        order.cancel();

        assertThrows(IllegalStateException.class, order::cancel);
    }
}