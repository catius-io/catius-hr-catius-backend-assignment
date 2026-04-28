package com.catius.inventory.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class StockMovementTest {

    private val now = Instant.parse("2026-04-26T00:00:00Z")

    @Nested
    @DisplayName("StockMovement 생성 검증")
    inner class Creation {
        @Test
        fun `quantity 가 0 이면 예외`() {
            assertThatThrownBy {
                StockMovement(
                    id = null,
                    orderId = 1L,
                    productId = 1001L,
                    quantity = 0,
                    type = MovementType.RESERVE,
                    createdAt = now,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("1 이상")
        }

        @Test
        fun `quantity 가 음수면 예외`() {
            assertThatThrownBy {
                StockMovement(
                    id = null,
                    orderId = 1L,
                    productId = 1001L,
                    quantity = -3,
                    type = MovementType.RELEASE,
                    createdAt = now,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("reserve 팩토리")
    inner class ReserveFactory {
        @Test
        fun `RESERVE 타입으로 생성되며 id 는 null`() {
            val movement = StockMovement.reserve(
                orderId = 42L,
                productId = 1001L,
                quantity = 2,
                at = now,
            )

            assertThat(movement.id).isNull()
            assertThat(movement.orderId).isEqualTo(42L)
            assertThat(movement.productId).isEqualTo(1001L)
            assertThat(movement.quantity).isEqualTo(2)
            assertThat(movement.type).isEqualTo(MovementType.RESERVE)
            assertThat(movement.createdAt).isEqualTo(now)
        }

        @Test
        fun `quantity 가 0 이면 예외 (팩토리 경유 시에도 검증)`() {
            assertThatThrownBy {
                StockMovement.reserve(orderId = 1L, productId = 1L, quantity = 0, at = now)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("release 팩토리")
    inner class ReleaseFactory {
        @Test
        fun `RELEASE 타입으로 생성되며 id 는 null`() {
            val movement = StockMovement.release(
                orderId = 42L,
                productId = 1001L,
                quantity = 2,
                at = now,
            )

            assertThat(movement.id).isNull()
            assertThat(movement.orderId).isEqualTo(42L)
            assertThat(movement.productId).isEqualTo(1001L)
            assertThat(movement.quantity).isEqualTo(2)
            assertThat(movement.type).isEqualTo(MovementType.RELEASE)
            assertThat(movement.createdAt).isEqualTo(now)
        }

        @Test
        fun `quantity 가 0 이면 예외 (팩토리 경유 시에도 검증)`() {
            assertThatThrownBy {
                StockMovement.release(orderId = 1L, productId = 1L, quantity = 0, at = now)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
