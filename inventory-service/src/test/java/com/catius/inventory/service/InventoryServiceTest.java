package com.catius.inventory.service;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.domain.exception.InsufficientStockException;
import com.catius.inventory.domain.exception.ProductNotFoundException;
import com.catius.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService — 단위 테스트 (Mockito)")
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 9001L;

    @Mock
    private InventoryRepository repository;

    @InjectMocks
    private InventoryService service;

    @Nested
    @DisplayName("getInventory")
    class GetInventory {

        @Test
        void 상품이_존재하면_Inventory_를_반환한다() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);
            when(repository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inventory));

            Inventory result = service.getInventory(PRODUCT_ID);

            assertThat(result).isSameAs(inventory);
        }

        @Test
        void 상품이_없으면_ProductNotFoundException() {
            when(repository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getInventory(PRODUCT_ID))
                    .isInstanceOf(ProductNotFoundException.class)
                    .satisfies(ex -> assertThat(((ProductNotFoundException) ex).getProductId())
                            .isEqualTo(PRODUCT_ID));
        }
    }

    @Nested
    @DisplayName("reserve")
    class Reserve {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        void quantity_가_0_이하이면_IllegalArgumentException(int quantity) {
            assertThatThrownBy(() -> service.reserve(PRODUCT_ID, quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quantity");

            // 검증 실패 시 repository 는 호출되지 않아야 함
            verify(repository, never()).decreaseStock(any(), anyInt());
        }

        @Test
        void decreaseStock_이_1_을_반환하면_성공하고_추가_조회는_없다() {
            when(repository.decreaseStock(PRODUCT_ID, 3)).thenReturn(1);

            service.reserve(PRODUCT_ID, 3);

            verify(repository, times(1)).decreaseStock(PRODUCT_ID, 3);
            // 성공 경로는 1-query 임이 핵심 — findByProductId 가 호출되면 안 됨
            verify(repository, never()).findByProductId(any());
        }

        @Test
        void decreaseStock_이_0_이고_상품이_존재하면_InsufficientStockException_을_정확한_정보로_던진다() {
            Inventory current = Inventory.create(PRODUCT_ID, 2);
            when(repository.decreaseStock(PRODUCT_ID, 5)).thenReturn(0);
            when(repository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(current));

            assertThatThrownBy(() -> service.reserve(PRODUCT_ID, 5))
                    .isInstanceOf(InsufficientStockException.class)
                    .satisfies(ex -> {
                        InsufficientStockException ise = (InsufficientStockException) ex;
                        assertThat(ise.getProductId()).isEqualTo(PRODUCT_ID);
                        assertThat(ise.getCurrentStock()).isEqualTo(2);
                        assertThat(ise.getRequestedQuantity()).isEqualTo(5);
                    });

            verify(repository, times(1)).decreaseStock(PRODUCT_ID, 5);
            verify(repository, times(1)).findByProductId(PRODUCT_ID);
        }

        @Test
        void decreaseStock_이_0_이고_상품도_없으면_ProductNotFoundException() {
            when(repository.decreaseStock(PRODUCT_ID, 1)).thenReturn(0);
            when(repository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reserve(PRODUCT_ID, 1))
                    .isInstanceOf(ProductNotFoundException.class)
                    .satisfies(ex -> assertThat(((ProductNotFoundException) ex).getProductId())
                            .isEqualTo(PRODUCT_ID));
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        void quantity_가_0_이하이면_IllegalArgumentException(int quantity) {
            assertThatThrownBy(() -> service.release(PRODUCT_ID, quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quantity");

            verify(repository, never()).increaseStock(any(), anyInt());
        }

        @Test
        void increaseStock_이_1_을_반환하면_정상_종료() {
            when(repository.increaseStock(PRODUCT_ID, 4)).thenReturn(1);

            service.release(PRODUCT_ID, 4);

            verify(repository, times(1)).increaseStock(PRODUCT_ID, 4);
            // release 는 실패 분기에서도 추가 조회를 하지 않으므로 findByProductId 는 호출되지 않음
            verify(repository, never()).findByProductId(any());
        }

        @Test
        void increaseStock_이_0_이면_ProductNotFoundException() {
            when(repository.increaseStock(PRODUCT_ID, 4)).thenReturn(0);

            assertThatThrownBy(() -> service.release(PRODUCT_ID, 4))
                    .isInstanceOf(ProductNotFoundException.class)
                    .satisfies(ex -> assertThat(((ProductNotFoundException) ex).getProductId())
                            .isEqualTo(PRODUCT_ID));

            verify(repository, times(1)).increaseStock(PRODUCT_ID, 4);
            verify(repository, never()).findByProductId(any());
        }
    }
}