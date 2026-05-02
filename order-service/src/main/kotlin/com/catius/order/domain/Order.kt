package com.catius.order.domain

import java.time.Instant

enum class OrderStatus { PENDING, CONFIRMED, FAILED }

data class OrderItem(
    val productId: Long,
    val quantity: Int,
) {
    init { require(quantity > 0) { "수량은 1 이상이어야 합니다: $quantity" } }
}

data class Order(
    val id: Long?,
    val customerId: Long,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(items.isNotEmpty()) { "주문 항목이 비어 있습니다" }
        require(items.distinctBy { it.productId }.size == items.size) {
            "동일 상품이 중복으로 포함될 수 없습니다"
        }
    }

    val totalQuantity: Int get() = items.sumOf { it.quantity }

    fun confirm(at: Instant): Order {
        check(status == OrderStatus.PENDING) { "PENDING 에서만 확정 가능: 현재=$status" }
        return copy(status = OrderStatus.CONFIRMED, updatedAt = at)
    }

    fun fail(at: Instant): Order {
        check(status == OrderStatus.PENDING) { "PENDING 에서만 실패 처리 가능: 현재=$status" }
        return copy(status = OrderStatus.FAILED, updatedAt = at)
    }

    companion object {
        fun create(customerId: Long, items: List<OrderItem>, now: Instant) = Order(
            id = null,
            customerId = customerId,
            items = items,
            status = OrderStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
    }
}
