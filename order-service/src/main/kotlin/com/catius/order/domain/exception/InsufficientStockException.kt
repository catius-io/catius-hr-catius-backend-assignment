package com.catius.order.domain.exception

class InsufficientStockException(
    val productId: Long,
    override val message: String? = null
) : RuntimeException(message ?: "Insufficient stock for product: $productId")
