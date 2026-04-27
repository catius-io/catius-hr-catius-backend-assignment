package com.catius.inventory.infrastructure.persistence

import com.catius.inventory.domain.Inventory
import com.catius.inventory.domain.port.InventoryRepository
import com.catius.inventory.infrastructure.persistence.jpa.InventoryJpaRepository
import com.catius.inventory.infrastructure.persistence.mapper.InventoryMapper
import org.springframework.stereotype.Component

@Component
class InventoryRepositoryAdapter(
    private val inventoryJpaRepository: InventoryJpaRepository,
) : InventoryRepository {

    override fun save(inventory: Inventory): Inventory {
        val existing = inventoryJpaRepository.findByProductIdForUpdate(inventory.productId)
        return if (existing != null) {
            InventoryMapper.applyTo(existing, inventory)
            InventoryMapper.toDomain(inventoryJpaRepository.save(existing))
        } else {
            InventoryMapper.toDomain(inventoryJpaRepository.save(InventoryMapper.toNewEntity(inventory)))
        }
    }

    override fun findByProductId(productId: Long): Inventory? {
        return inventoryJpaRepository.findByProductId(productId)?.let { InventoryMapper.toDomain(it) }
    }

    override fun findByProductIdForUpdate(productId: Long): Inventory? {
        return inventoryJpaRepository.findByProductIdForUpdate(productId)?.let { InventoryMapper.toDomain(it) }
    }
}
