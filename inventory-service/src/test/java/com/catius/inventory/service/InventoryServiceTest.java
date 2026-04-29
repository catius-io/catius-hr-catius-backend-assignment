package com.catius.inventory.service;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.domain.Inventory;
import com.catius.inventory.exception.StockNotFoundException;
import com.catius.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory createInventory(String productId, String productName, int quantity) {
        return Inventory.create(productId, productName, quantity);
    }

    // ── 재고 조회 ────────────────────────────────────────────────

    @Test
    @DisplayName("재고 조회 - 성공")
    void findByProductId_성공() {
        given(inventoryRepository.findByProductId("PRODUCT-001"))
                .willReturn(Optional.of(createInventory("PRODUCT-001", "테스트 상품", 10)));

        InventoryResponse response = inventoryService.findByProductId("PRODUCT-001");

        assertThat(response.productId()).isEqualTo("PRODUCT-001");
        assertThat(response.productName()).isEqualTo("테스트 상품");
        assertThat(response.quantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 조회 - 없는 상품 → 404")
    void findByProductId_없는상품_404() {
        given(inventoryRepository.findByProductId("PRODUCT-999"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.findByProductId("PRODUCT-999"))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("Stock not found: PRODUCT-999");
    }

    // ── 재고 예약(차감) ───────────────────────────────────────────

    @Test
    @DisplayName("재고 예약 - 성공")
    void reserve_성공() {
        given(inventoryRepository.findByProductId("PRODUCT-001"))
                .willReturn(Optional.of(createInventory("PRODUCT-001", "테스트 상품", 10)));

        InventoryResponse response = inventoryService.reserve(new InventoryRequest("PRODUCT-001", 5));

        assertThat(response.productId()).isEqualTo("PRODUCT-001");
        assertThat(response.quantity()).isEqualTo(5); // 10 - 5 = 5
    }

    @Test
    @DisplayName("재고 예약 - 재고 부족 → 예외")
    void reserve_재고부족_예외() {
        given(inventoryRepository.findByProductId("PRODUCT-001"))
                .willReturn(Optional.of(createInventory("PRODUCT-001", "테스트 상품", 3)));

        assertThatThrownBy(() -> inventoryService.reserve(new InventoryRequest("PRODUCT-001", 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고 부족");
    }

    @Test
    @DisplayName("재고 예약 - 없는 상품 → 404")
    void reserve_없는상품_404() {
        given(inventoryRepository.findByProductId("PRODUCT-999"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserve(new InventoryRequest("PRODUCT-999", 1)))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("Stock not found: PRODUCT-999");
    }

    // ── 재고 복구 ────────────────────────────────────────────────

    @Test
    @DisplayName("재고 복구 - 성공")
    void release_성공() {
        given(inventoryRepository.findByProductId("PRODUCT-001"))
                .willReturn(Optional.of(createInventory("PRODUCT-001", "테스트 상품", 5)));

        InventoryResponse response = inventoryService.release(new InventoryRequest("PRODUCT-001", 3));

        assertThat(response.productId()).isEqualTo("PRODUCT-001");
        assertThat(response.quantity()).isEqualTo(8); // 5 + 3 = 8
    }

    @Test
    @DisplayName("재고 복구 - 없는 상품 → 404")
    void release_없는상품_404() {
        given(inventoryRepository.findByProductId("PRODUCT-999"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.release(new InventoryRequest("PRODUCT-999", 1)))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("Stock not found: PRODUCT-999");
    }
}
