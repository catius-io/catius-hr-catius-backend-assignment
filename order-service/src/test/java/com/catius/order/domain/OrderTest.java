package com.catius.order.domain;

import com.catius.order.domain.exception.IllegalOrderStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order 도메인 — 상태 전이 invariant")
class OrderTest {

    private static final Long PRODUCT_ID = 9001L;

    @Nested
    @DisplayName("생성자 / 정적 팩토리")
    class Constructor {

        @Test
        void productId_와_quantity_로_생성되며_초기_상태는_PENDING() {
            Order order = Order.create(PRODUCT_ID, 3);

            assertThat(order.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(order.getQuantity()).isEqualTo(3);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getCreatedAt()).isNotNull();
            assertThat(order.getId()).isNull(); // 영속화 전이라 id 없음
        }

        @Test
        void productId_가_null_이면_IllegalArgumentException() {
            assertThatThrownBy(() -> Order.create(null, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("productId");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        void quantity_가_0_이하이면_IllegalArgumentException(int quantity) {
            assertThatThrownBy(() -> Order.create(PRODUCT_ID, quantity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quantity");
        }
    }

    @Nested
    @DisplayName("confirm — Saga 정상 흐름 (PENDING → CONFIRMED)")
    class Confirm {

        @Test
        void PENDING_에서_CONFIRMED_로_전이() {
            Order order = Order.create(PRODUCT_ID, 1);

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        void 이미_CONFIRMED_이면_IllegalOrderStateException() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.confirm();

            assertThatThrownBy(order::confirm)
                    .isInstanceOf(IllegalOrderStateException.class)
                    .satisfies(ex -> {
                        IllegalOrderStateException e = (IllegalOrderStateException) ex;
                        assertThat(e.getCurrentStatus()).isEqualTo(OrderStatus.CONFIRMED);
                        assertThat(e.getAttemptedTransition()).isEqualTo(OrderStatus.CONFIRMED);
                    });
        }

        @Test
        void FAILED_에서_confirm_시도하면_예외() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.fail();

            assertThatThrownBy(order::confirm)
                    .isInstanceOf(IllegalOrderStateException.class);
        }

        @Test
        void COMPENSATED_에서_confirm_시도하면_예외() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.confirm();      // PENDING → CONFIRMED
            order.compensate();   // CONFIRMED → COMPENSATED

            assertThatThrownBy(order::confirm)
                    .isInstanceOf(IllegalOrderStateException.class);
        }
    }

    @Nested
    @DisplayName("fail — reserve 자체 실패 (PENDING → FAILED, 보상 불필요)")
    class Fail {

        @Test
        void PENDING_에서_FAILED_로_전이() {
            Order order = Order.create(PRODUCT_ID, 1);

            order.fail();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @Test
        void 이미_FAILED_이면_예외() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.fail();

            assertThatThrownBy(order::fail)
                    .isInstanceOf(IllegalOrderStateException.class);
        }

        @Test
        void CONFIRMED_에서_fail_시도하면_예외() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.confirm();

            assertThatThrownBy(order::fail)
                    .isInstanceOf(IllegalOrderStateException.class);
        }
    }

    @Nested
    @DisplayName("compensate — reserve 후 실패 → 보상 완료 (CONFIRMED → COMPENSATED)")
    class Compensate {

        @Test
        void CONFIRMED_에서_COMPENSATED_로_전이() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.confirm();

            order.compensate();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPENSATED);
        }

        @Test
        void PENDING_에서_compensate_시도하면_예외() {
            Order order = Order.create(PRODUCT_ID, 1);

            assertThatThrownBy(order::compensate)
                    .isInstanceOf(IllegalOrderStateException.class)
                    .satisfies(ex -> {
                        IllegalOrderStateException e = (IllegalOrderStateException) ex;
                        assertThat(e.getCurrentStatus()).isEqualTo(OrderStatus.PENDING);
                        assertThat(e.getAttemptedTransition()).isEqualTo(OrderStatus.COMPENSATED);
                    });
        }

        @Test
        void 이미_COMPENSATED_이면_예외() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.confirm();
            order.compensate();

            assertThatThrownBy(order::compensate)
                    .isInstanceOf(IllegalOrderStateException.class);
        }

        @Test
        void FAILED_에서_compensate_시도하면_예외() {
            Order order = Order.create(PRODUCT_ID, 1);
            order.fail();

            assertThatThrownBy(order::compensate)
                    .isInstanceOf(IllegalOrderStateException.class);
        }
    }

    @Nested
    @DisplayName("OrderStatus.isTerminal — 종착 상태 식별")
    class Terminal {

        @Test
        void FAILED_와_COMPENSATED_는_종착이고_PENDING_과_CONFIRMED_는_아니다() {
            assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
            assertThat(OrderStatus.CONFIRMED.isTerminal()).isFalse();
            assertThat(OrderStatus.FAILED.isTerminal()).isTrue();
            assertThat(OrderStatus.COMPENSATED.isTerminal()).isTrue();
        }
    }
}
