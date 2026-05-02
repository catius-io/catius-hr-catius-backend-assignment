package com.catius.inventory.infrastructure.persistence.mapper

import com.catius.inventory.domain.StockMovement
import com.catius.inventory.infrastructure.persistence.entity.StockMovementEntity

object StockMovementMapper {

    fun toDomain(entity: StockMovementEntity): StockMovement = StockMovement(
        id = entity.id,
        orderId = entity.orderId,
        productId = entity.productId,
        quantity = entity.quantity,
        type = entity.type,
        createdAt = entity.createdAt,
    )

    fun toNewEntity(domain: StockMovement): StockMovementEntity = StockMovementEntity(
        id = domain.id,
        orderId = domain.orderId,
        productId = domain.productId,
        quantity = domain.quantity,
        type = domain.type,
        createdAt = domain.createdAt,
    )
}
