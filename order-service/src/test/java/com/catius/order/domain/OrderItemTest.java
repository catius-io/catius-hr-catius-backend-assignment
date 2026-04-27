package com.catius.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {

    @Test
    @DisplayName("정상 수량으로 OrderItem을 생성하면 입력값이 그대로 기록된다.")
    void create_order_item() {
        OrderItem item = OrderItem.of(1L, 1001L, 2);

        assertEquals(1L, item.getOrderId());
        assertEquals(1001L, item.getProductId());
        assertEquals(2, item.getQuantity());
    }

    @Test
    @DisplayName("수량이 0이면 예외가 발생한다.")
    void create_order_item_with_zero_quantity() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderItem.of(1L, 1001L, 0));
    }


    @Test
    @DisplayName("수량이 음수이면 예외가 발생한다.")
    void create_order_item_with_negative_quantity() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderItem.of(1L, 1001L, -1));
    }

}