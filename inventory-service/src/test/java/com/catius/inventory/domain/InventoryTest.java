package com.catius.inventory.domain;

import com.catius.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    private static final Long PRODUCT_ID = 1001L;

    @Nested
    @DisplayName("생성자")
    class Constructor {

        @Test
        void productId_와_stock_으로_정상_생성된다() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);

            assertThat(inventory.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(inventory.getStock()).isEqualTo(10);
        }

        @Test
        void stock_이_0_이어도_생성_가능하다() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 0);

            assertThat(inventory.getStock()).isZero();
        }

        @Test
        void productId_가_null_이면_예외() {
            assertThatThrownBy(() -> Inventory.create(null, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("productId");
        }

        @Test
        void stock_이_음수면_예외() {
            assertThatThrownBy(() -> Inventory.create(PRODUCT_ID, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stock");
        }
    }

    @Nested
    @DisplayName("reserve")
    class Reserve {

        @Test
        void 재고가_충분하면_차감된다() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);

            inventory.reserve(3);

            assertThat(inventory.getStock()).isEqualTo(7);
        }

        @Test
        void 재고와_같은_수량을_차감하면_0_이_된다() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);

            inventory.reserve(5);

            assertThat(inventory.getStock()).isZero();
        }

        @Test
        void 재고보다_많이_차감_시도하면_InsufficientStockException() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 2);

            assertThatThrownBy(() -> inventory.reserve(3))
                    .isInstanceOf(InsufficientStockException.class)
                    .satisfies(ex -> {
                        InsufficientStockException ise = (InsufficientStockException) ex;
                        assertThat(ise.getProductId()).isEqualTo(PRODUCT_ID);
                        assertThat(ise.getCurrentStock()).isEqualTo(2);
                        assertThat(ise.getRequestedQuantity()).isEqualTo(3);
                    });
        }

        @Test
        void 차감_실패_시_재고는_변경되지_않는다() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 2);

            try {
                inventory.reserve(3);
            } catch (InsufficientStockException ignored) {
            }

            assertThat(inventory.getStock()).isEqualTo(2);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        void quantity_가_0_이하이면_예외(int quantity) {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);

            assertThatThrownBy(() -> inventory.reserve(quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quantity");
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        void 재고가_정상적으로_복원된다() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);

            inventory.release(3);

            assertThat(inventory.getStock()).isEqualTo(8);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        void quantity_가_0_이하이면_예외(int quantity) {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);

            assertThatThrownBy(() -> inventory.release(quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quantity");
        }
    }
}
