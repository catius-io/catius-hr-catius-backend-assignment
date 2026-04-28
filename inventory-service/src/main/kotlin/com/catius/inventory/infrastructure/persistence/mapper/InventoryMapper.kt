package com.catius.inventory.infrastructure.persistence.mapper

import com.catius.inventory.domain.Inventory
import com.catius.inventory.infrastructure.persistence.entity.InventoryEntity

object InventoryMapper {

    fun toDomain(entity: InventoryEntity): Inventory = Inventory(
        productId = entity.productId,
        availableQuantity = entity.availableQuantity,
        updatedAt = entity.updatedAt,
    )

    fun toNewEntity(domain: Inventory): InventoryEntity = InventoryEntity(
        productId = domain.productId,
        availableQuantity = domain.availableQuantity,
        updatedAt = domain.updatedAt,
    )

    fun applyTo(entity: InventoryEntity, domain: Inventory) {
        entity.availableQuantity = domain.availableQuantity
        entity.updatedAt = domain.updatedAt
    }
}
