package com.catius.inventory.infrastructure.persistence

import com.catius.inventory.domain.MovementType
import com.catius.inventory.domain.StockMovement
import com.catius.inventory.domain.port.StockMovementRepository
import com.catius.inventory.infrastructure.persistence.jpa.StockMovementJpaRepository
import com.catius.inventory.infrastructure.persistence.mapper.StockMovementMapper
import org.springframework.stereotype.Component

@Component
class StockMovementRepositoryAdapter(
    private val stockMovementJpaRepository: StockMovementJpaRepository,
) : StockMovementRepository {

    override fun save(movement: StockMovement): StockMovement {
        val entity = StockMovementMapper.toNewEntity(movement)
        return StockMovementMapper.toDomain(stockMovementJpaRepository.save(entity))
    }

    override fun findByOrderIdAndProductIdAndType(
        orderId: Long,
        productId: Long,
        type: MovementType,
    ): StockMovement? {
        return stockMovementJpaRepository.findByOrderIdAndProductIdAndType(orderId, productId, type)
            ?.let { StockMovementMapper.toDomain(it) }
    }
}
