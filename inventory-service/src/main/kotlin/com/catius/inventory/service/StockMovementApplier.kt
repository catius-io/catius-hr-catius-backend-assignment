package com.catius.inventory.service

import com.catius.inventory.domain.MovementType
import com.catius.inventory.domain.StockMovement
import com.catius.inventory.domain.exception.InventoryNotFoundException
import com.catius.inventory.domain.port.InventoryRepository
import com.catius.inventory.domain.port.StockMovementRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class StockMovementApplier(
    private val inventoryRepository: InventoryRepository,
    private val stockMovementRepository: StockMovementRepository,
) {
    fun apply(
        orderId: Long,
        productId: Long,
        quantity: Int,
        type: MovementType,
        now: Instant,
    ) {
        // 멱등성: 같은 (orderId, productId, type) 이미 처리됐으면 스킵
        val existing = stockMovementRepository.findByOrderIdAndProductIdAndType(
            orderId = orderId,
            productId = productId,
            type = type,
        )
        if (existing != null) return

        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException(productId)

        val updated = when (type) {
            MovementType.RESERVE -> inventory.reserve(quantity, now)
            MovementType.RELEASE -> inventory.release(quantity, now)
        }
        inventoryRepository.save(updated)

        stockMovementRepository.save(
            when (type) {
                MovementType.RESERVE -> StockMovement.reserve(orderId, productId, quantity, now)
                MovementType.RELEASE -> StockMovement.release(orderId, productId, quantity, now)
            },
        )
    }
}
