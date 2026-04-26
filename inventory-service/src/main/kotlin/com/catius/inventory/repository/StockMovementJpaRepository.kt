package com.catius.inventory.repository

import com.catius.inventory.domain.MovementType
import com.catius.inventory.repository.entity.StockMovementEntity
import org.springframework.data.jpa.repository.JpaRepository

interface StockMovementJpaRepository : JpaRepository<StockMovementEntity, Long> {

    fun findByOrderIdAndProductIdAndType(
        orderId: Long,
        productId: Long,
        type: MovementType,
    ): StockMovementEntity?
}
