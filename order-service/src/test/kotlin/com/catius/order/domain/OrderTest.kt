package com.catius.order.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class OrderTest {

    private val now = Instant.parse("2026-04-26T00:00:00Z")
    private val later = now.plusSeconds(60)
    private val items = listOf(
        OrderItem(productId = 1001, quantity = 2),
        OrderItem(productId = 1002, quantity = 1),
    )

    @Nested
    @DisplayName("OrderItem 생성 검증")
    inner class OrderItemCreation {
        @Test
        fun `quantity 가 0 이면 예외`() {
            assertThatThrownBy { OrderItem(productId = 1, quantity = 0) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("수량은 1 이상")
        }

        @Test
        fun `quantity 가 음수면 예외`() {
            assertThatThrownBy { OrderItem(productId = 1, quantity = -3) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `quantity 가 1 이상이면 정상`() {
            val item = OrderItem(productId = 1, quantity = 1)
            assertThat(item.productId).isEqualTo(1L)
            assertThat(item.quantity).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Order.create 팩토리")
    inner class Create {
        @Test
        fun `정상 생성 시 PENDING 상태이고 id 는 null`() {
            val order = Order.create(customerId = 100L, items = items, now = now)

            assertThat(order.id).isNull()
            assertThat(order.status).isEqualTo(OrderStatus.PENDING)
            assertThat(order.customerId).isEqualTo(100L)
            assertThat(order.items).isEqualTo(items)
            assertThat(order.createdAt).isEqualTo(now)
            assertThat(order.updatedAt).isEqualTo(now)
        }

        @Test
        fun `items 가 비어있으면 예외`() {
            assertThatThrownBy {
                Order.create(customerId = 1L, items = emptyList(), now = now)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("주문 항목")
        }

        @Test
        fun `동일 productId 가 중복되면 예외`() {
            val duplicated = listOf(
                OrderItem(productId = 1, quantity = 1),
                OrderItem(productId = 1, quantity = 2),
            )

            assertThatThrownBy {
                Order.create(customerId = 1L, items = duplicated, now = now)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("중복")
        }
    }

    @Nested
    @DisplayName("totalQuantity")
    inner class TotalQuantity {
        @Test
        fun `여러 아이템의 수량을 합산한다`() {
            val order = Order.create(customerId = 1L, items = items, now = now)
            assertThat(order.totalQuantity).isEqualTo(3)
        }

        @Test
        fun `단일 아이템이면 그 수량을 반환한다`() {
            val single = listOf(OrderItem(productId = 1, quantity = 5))
            val order = Order.create(customerId = 1L, items = single, now = now)
            assertThat(order.totalQuantity).isEqualTo(5)
        }
    }

    @Nested
    @DisplayName("confirm 상태 전이")
    inner class Confirm {
        @Test
        fun `PENDING 에서 호출하면 CONFIRMED 로 전이하고 updatedAt 이 갱신된다`() {
            val order = Order.create(customerId = 1L, items = items, now = now)

            val confirmed = order.confirm(later)

            assertThat(confirmed.status).isEqualTo(OrderStatus.CONFIRMED)
            assertThat(confirmed.updatedAt).isEqualTo(later)
            assertThat(confirmed.createdAt).isEqualTo(now)
        }

        @Test
        fun `confirm 은 새 인스턴스를 반환하고 원본은 변경되지 않는다`() {
            val order = Order.create(customerId = 1L, items = items, now = now)

            val confirmed = order.confirm(later)

            assertThat(order.status).isEqualTo(OrderStatus.PENDING)
            assertThat(confirmed).isNotSameAs(order)
        }

        @Test
        fun `이미 CONFIRMED 상태면 예외`() {
            val confirmed = Order.create(customerId = 1L, items = items, now = now)
                .confirm(later)

            assertThatThrownBy { confirmed.confirm(later.plusSeconds(1)) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("PENDING")
        }

        @Test
        fun `FAILED 상태면 예외`() {
            val failed = Order.create(customerId = 1L, items = items, now = now)
                .fail(later)

            assertThatThrownBy { failed.confirm(later.plusSeconds(1)) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Nested
    @DisplayName("fail 상태 전이")
    inner class Fail {
        @Test
        fun `PENDING 에서 호출하면 FAILED 로 전이하고 updatedAt 이 갱신된다`() {
            val order = Order.create(customerId = 1L, items = items, now = now)

            val failed = order.fail(later)

            assertThat(failed.status).isEqualTo(OrderStatus.FAILED)
            assertThat(failed.updatedAt).isEqualTo(later)
            assertThat(failed.createdAt).isEqualTo(now)
        }

        @Test
        fun `CONFIRMED 상태면 예외`() {
            val confirmed = Order.create(customerId = 1L, items = items, now = now)
                .confirm(later)

            assertThatThrownBy { confirmed.fail(later.plusSeconds(1)) }
                .isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun `이미 FAILED 상태면 예외`() {
            val failed = Order.create(customerId = 1L, items = items, now = now)
                .fail(later)

            assertThatThrownBy { failed.fail(later.plusSeconds(1)) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }
}
