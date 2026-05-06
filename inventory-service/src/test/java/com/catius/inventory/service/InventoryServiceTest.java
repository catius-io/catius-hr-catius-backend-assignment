package com.catius.inventory.service;

import com.catius.inventory.controller.dto.request.InventoryRequest;
import com.catius.inventory.controller.dto.response.InventoryResponse;
import com.catius.inventory.domain.Inventory;
import com.catius.inventory.exception.InsufficientStockException;
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

    private Inventory createInventory(Long productId, String productName, int quantity) {
        return Inventory.create(productId, productName, quantity);
    }

    // ── 재고 조회 ────────────────────────────────────────────────

    @Test
    @DisplayName("재고 조회 - 성공")
    void findByProductId_성공() {
        given(inventoryRepository.findByProductId(1001L))
                .willReturn(Optional.of(createInventory(1001L, "테스트 상품", 10)));

        InventoryResponse response = inventoryService.findByProductId(1001L);

        assertThat(response.productId()).isEqualTo(1001L);
        assertThat(response.productName()).isEqualTo("테스트 상품");
        assertThat(response.quantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 조회 - 없는 상품 → 404")
    void findByProductId_없는상품_404() {
        given(inventoryRepository.findByProductId(9999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.findByProductId(9999L))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("Stock not found: 9999");
    }

    // ── 재고 예약(차감) ───────────────────────────────────────────

    @Test
    @DisplayName("재고 예약 - 성공")
    void reserve_성공() {
        given(inventoryRepository.findByProductId(1001L))
                .willReturn(Optional.of(createInventory(1001L, "테스트 상품", 10)));

        InventoryResponse response = inventoryService.reserve(new InventoryRequest(1001L, 5));

        assertThat(response.productId()).isEqualTo(1001L);
        assertThat(response.quantity()).isEqualTo(5); // 10 - 5 = 5
    }

    @Test
    @DisplayName("재고 예약 - 재고 부족 → 예외")
    void reserve_재고부족_예외() {
        given(inventoryRepository.findByProductId(1001L))
                .willReturn(Optional.of(createInventory(1001L, "테스트 상품", 3)));

        assertThatThrownBy(() -> inventoryService.reserve(new InventoryRequest(1001L, 5)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("재고 예약 - 없는 상품 → 404")
    void reserve_없는상품_404() {
        given(inventoryRepository.findByProductId(9999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserve(new InventoryRequest(9999L, 1)))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("Stock not found: 9999");
    }

    // ── 재고 복구 ────────────────────────────────────────────────

    @Test
    @DisplayName("재고 복구 - 성공")
    void release_성공() {
        given(inventoryRepository.findByProductId(1001L))
                .willReturn(Optional.of(createInventory(1001L, "테스트 상품", 5)));

        InventoryResponse response = inventoryService.release(new InventoryRequest(1001L, 3));

        assertThat(response.productId()).isEqualTo(1001L);
        assertThat(response.quantity()).isEqualTo(8); // 5 + 3 = 8
    }

    @Test
    @DisplayName("재고 복구 - 없는 상품 → 404")
    void release_없는상품_404() {
        given(inventoryRepository.findByProductId(9999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.release(new InventoryRequest(9999L, 1)))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("Stock not found: 9999");
    }
}
