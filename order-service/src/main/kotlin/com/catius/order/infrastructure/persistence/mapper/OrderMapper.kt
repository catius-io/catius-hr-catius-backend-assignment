package com.catius.order.infrastructure.persistence.mapper

import com.catius.order.domain.Order
import com.catius.order.domain.OrderItem
import com.catius.order.infrastructure.persistence.entity.OrderEntity
import com.catius.order.infrastructure.persistence.entity.OrderItemEntity

object OrderMapper {

    fun toDomain(entity: OrderEntity): Order = Order(
        id = entity.id,
        customerId = entity.customerId,
        items = entity.items.map { OrderItem(productId = it.productId, quantity = it.quantity) },
        status = entity.status,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    fun toNewEntity(domain: Order): OrderEntity {
        val entity = OrderEntity(
            id = domain.id,
            customerId = domain.customerId,
            status = domain.status,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
        entity.items.addAll(
            domain.items.map { OrderItemEntity(productId = it.productId, quantity = it.quantity) },
        )
        return entity
    }

    fun applyTo(entity: OrderEntity, domain: Order) {
        entity.status = domain.status
        entity.updatedAt = domain.updatedAt
    }
}
