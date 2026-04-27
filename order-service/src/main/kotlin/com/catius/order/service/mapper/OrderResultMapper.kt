package com.catius.order.service.mapper

import com.catius.order.domain.Order
import com.catius.order.domain.usecase.command.OrderResult

object OrderResultMapper {
    fun toResult(order: Order): OrderResult = OrderResult(
        id = order.id!!,
        customerId = order.customerId,
        items = order.items.map { OrderResult.Item(productId = it.productId, quantity = it.quantity) },
        status = order.status,
        createdAt = order.createdAt,
        updatedAt = order.updatedAt,
    )
}
