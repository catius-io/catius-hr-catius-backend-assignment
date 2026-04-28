package com.catius.order.domain.port

import com.catius.order.domain.Order

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
}
