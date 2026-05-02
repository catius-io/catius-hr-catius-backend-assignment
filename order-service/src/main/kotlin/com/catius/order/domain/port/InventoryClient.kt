package com.catius.order.domain.port

import com.catius.order.domain.OrderItem

interface InventoryClient {
    fun reserve(orderId: Long, items: List<OrderItem>): ReserveOutcome
    fun release(orderId: Long, items: List<OrderItem>)
}
