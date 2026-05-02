package com.catius.inventory.domain

import java.time.Instant

class InsufficientStockException(
    val productId: Long,
    val requested: Int,
    val available: Int,
) : RuntimeException(
    "재고 부족: product=$productId, 요청=$requested, 가용=$available",
)

data class Inventory(
    val productId: Long,
    val availableQuantity: Int,
    val updatedAt: Instant,
) {
    init {
        require(availableQuantity >= 0) {
            "가용 재고는 음수일 수 없습니다: $availableQuantity"
        }
    }

    fun reserve(quantity: Int, at: Instant): Inventory {
        require(quantity > 0) { "차감 수량은 1 이상이어야 합니다: $quantity" }
        if (availableQuantity < quantity) {
            throw InsufficientStockException(productId, quantity, availableQuantity)
        }
        return copy(availableQuantity = availableQuantity - quantity, updatedAt = at)
    }

    fun release(quantity: Int, at: Instant): Inventory {
        require(quantity > 0) { "복원 수량은 1 이상이어야 합니다: $quantity" }
        return copy(availableQuantity = availableQuantity + quantity, updatedAt = at)
    }
}
