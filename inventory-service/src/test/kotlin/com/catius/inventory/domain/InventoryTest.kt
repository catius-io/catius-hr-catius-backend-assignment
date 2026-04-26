package com.catius.inventory.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class InventoryTest {

    private val now = Instant.parse("2026-04-26T00:00:00Z")
    private val later = now.plusSeconds(60)

    private fun inventory(available: Int) = Inventory(
        productId = 1001L,
        availableQuantity = available,
        updatedAt = now,
    )

    @Nested
    @DisplayName("Inventory 생성 검증")
    inner class Creation {
        @Test
        fun `availableQuantity 가 0 이면 정상`() {
            val inv = inventory(available = 0)
            assertThat(inv.availableQuantity).isEqualTo(0)
        }

        @Test
        fun `availableQuantity 가 양수면 정상`() {
            val inv = inventory(available = 100)
            assertThat(inv.availableQuantity).isEqualTo(100)
        }

        @Test
        fun `availableQuantity 가 음수면 예외`() {
            assertThatThrownBy { inventory(available = -1) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("음수")
        }
    }

    @Nested
    @DisplayName("reserve 차감")
    inner class Reserve {
        @Test
        fun `가용 재고 내에서 차감하면 잔여 재고가 줄고 updatedAt 이 갱신된다`() {
            val inv = inventory(available = 10)

            val reserved = inv.reserve(quantity = 3, at = later)

            assertThat(reserved.availableQuantity).isEqualTo(7)
            assertThat(reserved.updatedAt).isEqualTo(later)
            assertThat(reserved.productId).isEqualTo(1001L)
        }

        @Test
        fun `정확히 가용 재고 만큼 차감하면 0 이 된다 (경계값)`() {
            val inv = inventory(available = 5)

            val reserved = inv.reserve(quantity = 5, at = later)

            assertThat(reserved.availableQuantity).isEqualTo(0)
        }

        @Test
        fun `가용 재고를 초과 차감하면 InsufficientStockException`() {
            val inv = inventory(available = 5)

            assertThatThrownBy { inv.reserve(quantity = 6, at = later) }
                .isInstanceOf(InsufficientStockException::class.java)
                .extracting("productId", "requested", "available")
                .containsExactly(1001L, 6, 5)
        }

        @Test
        fun `가용 재고 0 에서 1 차감 시도해도 InsufficientStockException`() {
            val inv = inventory(available = 0)

            assertThatThrownBy { inv.reserve(quantity = 1, at = later) }
                .isInstanceOf(InsufficientStockException::class.java)
        }

        @Test
        fun `quantity 가 0 이면 예외`() {
            val inv = inventory(available = 10)

            assertThatThrownBy { inv.reserve(quantity = 0, at = later) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("1 이상")
        }

        @Test
        fun `quantity 가 음수면 예외`() {
            val inv = inventory(available = 10)

            assertThatThrownBy { inv.reserve(quantity = -1, at = later) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `reserve 는 새 인스턴스를 반환하고 원본은 변경되지 않는다`() {
            val inv = inventory(available = 10)

            val reserved = inv.reserve(quantity = 3, at = later)

            assertThat(inv.availableQuantity).isEqualTo(10)
            assertThat(reserved).isNotSameAs(inv)
        }
    }

    @Nested
    @DisplayName("release 복원")
    inner class Release {
        @Test
        fun `복원 시 잔여 재고가 늘고 updatedAt 이 갱신된다`() {
            val inv = inventory(available = 5)

            val released = inv.release(quantity = 3, at = later)

            assertThat(released.availableQuantity).isEqualTo(8)
            assertThat(released.updatedAt).isEqualTo(later)
        }

        @Test
        fun `0 인 재고에도 복원 가능 (Saga 보상은 항상 성공해야 함)`() {
            val inv = inventory(available = 0)

            val released = inv.release(quantity = 5, at = later)

            assertThat(released.availableQuantity).isEqualTo(5)
        }

        @Test
        fun `quantity 가 0 이면 예외`() {
            val inv = inventory(available = 10)

            assertThatThrownBy { inv.release(quantity = 0, at = later) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("1 이상")
        }

        @Test
        fun `quantity 가 음수면 예외`() {
            val inv = inventory(available = 10)

            assertThatThrownBy { inv.release(quantity = -1, at = later) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `release 는 새 인스턴스를 반환하고 원본은 변경되지 않는다`() {
            val inv = inventory(available = 5)

            val released = inv.release(quantity = 3, at = later)

            assertThat(inv.availableQuantity).isEqualTo(5)
            assertThat(released).isNotSameAs(inv)
        }
    }
}
