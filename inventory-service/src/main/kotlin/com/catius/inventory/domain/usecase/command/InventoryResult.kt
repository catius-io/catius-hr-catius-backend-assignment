package com.catius.inventory.domain.usecase.command

import java.time.Instant

data class InventoryResult(
    val productId: Long,
    val availableQuantity: Int,
    val updatedAt: Instant,
)
