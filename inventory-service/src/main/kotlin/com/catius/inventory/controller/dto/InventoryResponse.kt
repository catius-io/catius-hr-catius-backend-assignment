package com.catius.inventory.controller.dto

import com.catius.inventory.domain.usecase.command.InventoryResult
import com.catius.inventory.domain.usecase.command.ReserveResult
import java.time.Instant

data class ReserveResponse(
    val orderId: Long,
    val items: List<ItemResponse>,
) {
    data class ItemResponse(
        val productId: Long,
        val quantity: Int,
    )

    companion object {
        fun from(result: ReserveResult): ReserveResponse = ReserveResponse(
            orderId = result.orderId,
            items = result.items.map { ItemResponse(it.productId, it.quantity) },
        )
    }
}

data class InventoryResponse(
    val productId: Long,
    val availableQuantity: Int,
    val updatedAt: Instant,
) {
    companion object {
        fun from(result: InventoryResult): InventoryResponse = InventoryResponse(
            productId = result.productId,
            availableQuantity = result.availableQuantity,
            updatedAt = result.updatedAt,
        )
    }
}
