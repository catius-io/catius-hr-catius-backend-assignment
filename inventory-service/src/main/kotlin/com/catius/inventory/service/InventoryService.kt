package com.catius.inventory.service

import com.catius.inventory.domain.MovementType
import com.catius.inventory.domain.StockMovement
import com.catius.inventory.domain.exception.InventoryNotFoundException
import com.catius.inventory.domain.port.InventoryRepository
import com.catius.inventory.domain.port.StockMovementRepository
import com.catius.inventory.domain.usecase.FindInventoryUseCase
import com.catius.inventory.domain.usecase.ReleaseInventoryUseCase
import com.catius.inventory.domain.usecase.ReserveInventoryUseCase
import com.catius.inventory.domain.usecase.command.InventoryCommand
import com.catius.inventory.domain.usecase.command.InventoryResult
import com.catius.inventory.domain.usecase.command.ReserveResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository,
    private val stockMovementApplier: StockMovementApplier,
    private val clock: Clock,
) : ReserveInventoryUseCase, ReleaseInventoryUseCase, FindInventoryUseCase {

    @Transactional
    override fun reserve(command: InventoryCommand.ReserveCommand): ReserveResult {
        val now = Instant.now(clock)
        command.items.forEach { item ->
            stockMovementApplier.apply(
                orderId = command.orderId,
                productId = item.productId,
                quantity = item.quantity,
                type = MovementType.RESERVE,
                now = now,
            )
        }

        return ReserveResult(
            orderId = command.orderId,
            items = command.items.map {
                ReserveResult.Item(productId = it.productId, quantity = it.quantity)
            },
        )
    }

    @Transactional
    override fun release(command: InventoryCommand.ReleaseCommand) {
        val now = Instant.now(clock)
        command.items.forEach { item ->
            stockMovementApplier.apply(
                orderId = command.orderId,
                productId = item.productId,
                quantity = item.quantity,
                type = MovementType.RELEASE,
                now = now,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun findByProductId(productId: Long): InventoryResult {
        val inventory = inventoryRepository.findByProductId(productId)
            ?: throw InventoryNotFoundException(productId)
        return InventoryResult(
            productId = inventory.productId,
            availableQuantity = inventory.availableQuantity,
            updatedAt = inventory.updatedAt,
        )
    }
}
