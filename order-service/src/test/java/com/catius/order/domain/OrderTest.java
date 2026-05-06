package com.catius.order.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.create(1L, List.of(OrderItem.of(1001L, 3)));
    }

    @Test
    @DisplayName("주문 생성 - 초기 상태는 PENDING, 아이템 포함")
    void create_초기상태_PENDING() {
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getProductId()).isEqualTo(1001L);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 확정 - 상태가 CONFIRMED로 변경")
    void confirm_상태변경() {
        order.confirm();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("주문 취소 - 상태가 CANCELLED로 변경")
    void cancel_상태변경() {
        order.cancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
