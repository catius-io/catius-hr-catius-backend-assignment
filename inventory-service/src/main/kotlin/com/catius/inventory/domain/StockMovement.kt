package com.catius.inventory.domain

import java.time.Instant

enum class MovementType { RESERVE, RELEASE }

data class StockMovement(
    val id: Long?,
    val orderId: Long,
    val productId: Long,
    val quantity: Int,
    val type: MovementType,
    val createdAt: Instant,
) {
    init {
        require(quantity > 0) { "수량은 1 이상이어야 합니다: $quantity" }
    }

    companion object {
        fun reserve(orderId: Long, productId: Long, quantity: Int, at: Instant) =
            StockMovement(
                id = null,
                orderId = orderId,
                productId = productId,
                quantity = quantity,
                type = MovementType.RESERVE,
                createdAt = at,
            )

        fun release(orderId: Long, productId: Long, quantity: Int, at: Instant) =
            StockMovement(
                id = null,
                orderId = orderId,
                productId = productId,
                quantity = quantity,
                type = MovementType.RELEASE,
                createdAt = at,
            )
    }
}
