package com.catius.inventory.domain;

import com.catius.inventory.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class InventoryTest {

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new Inventory();
        ReflectionTestUtils.setField(inventory, "productId", "PRODUCT-001");
        ReflectionTestUtils.setField(inventory, "productName", "테스트 상품");
        ReflectionTestUtils.setField(inventory, "quantity", 10);
        ReflectionTestUtils.setField(inventory, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(inventory, "updatedAt", LocalDateTime.now());
    }

    @Test
    @DisplayName("재고 차감 - 정상")
    void deduct_정상차감() {
        inventory.deduct(3);
        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 차감 - 정확히 동일 수량")
    void deduct_전체수량_차감() {
        inventory.deduct(10);
        assertThat(inventory.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 차감 - 재고 부족 시 예외 발생")
    void deduct_재고부족_예외() {
        assertThatThrownBy(() -> inventory.deduct(11))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("재고 복구 - 정상")
    void restore_정상복구() {
        inventory.restore(5);
        assertThat(inventory.getQuantity()).isEqualTo(15);
    }
}
