package com.catius.order.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.create("PRODUCT-001", 3);
    }

    @Test
    @DisplayName("주문 생성 - 초기 상태는 PENDING")
    void create_초기상태_PENDING() {
        assertThat(order.getProductId()).isEqualTo("PRODUCT-001");
        assertThat(order.getQuantity()).isEqualTo(3);
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
