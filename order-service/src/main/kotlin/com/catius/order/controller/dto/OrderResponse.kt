package com.catius.order.controller.dto

import com.catius.order.domain.usecase.command.OrderResult
import java.time.Instant

data class OrderResponse(
    val id: Long,
    val customerId: Long,
    val items: List<OrderItemResponse>,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(result: OrderResult): OrderResponse {
            return OrderResponse(
                id = result.id,
                customerId = result.customerId,
                items = result.items.map { OrderItemResponse.from(it) },
                status = result.status.name,
                createdAt = result.createdAt,
                updatedAt = result.updatedAt
            )
        }
    }
}

data class OrderItemResponse(
    val productId: Long,
    val quantity: Int,
) {
    companion object {
        fun from(item: OrderResult.Item): OrderItemResponse {
            return OrderItemResponse(
                productId = item.productId,
                quantity = item.quantity
            )
        }
    }
}
