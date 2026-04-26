package com.catius.inventory.domain.port

import com.catius.inventory.domain.MovementType
import com.catius.inventory.domain.StockMovement

interface StockMovementRepository {
    fun save(movement: StockMovement): StockMovement

    /** 멱등성 사전 조회 — 같은 (orderId, productId, type) 의 movement 가 이미 있는지. */
    fun findByOrderIdAndProductIdAndType(
        orderId: Long,
        productId: Long,
        type: MovementType,
    ): StockMovement?
}
