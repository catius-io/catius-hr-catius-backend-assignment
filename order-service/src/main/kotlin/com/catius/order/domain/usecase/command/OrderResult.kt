package com.catius.order.domain.usecase.command

import com.catius.order.domain.OrderStatus
import java.time.Instant

data class OrderResult(
    val id: Long,
    val customerId: Long,
    val items: List<Item>,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
