package com.catius.inventory.controller

import com.catius.inventory.controller.dto.*
import com.catius.inventory.domain.Inventory
import com.catius.inventory.domain.port.InventoryRepository
import com.catius.inventory.domain.usecase.FindInventoryUseCase
import com.catius.inventory.domain.usecase.ReleaseInventoryUseCase
import com.catius.inventory.domain.usecase.ReserveInventoryUseCase
import jakarta.validation.Valid
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController(
    private val reserveInventoryUseCase: ReserveInventoryUseCase,
    private val releaseInventoryUseCase: ReleaseInventoryUseCase,
    private val findInventoryUseCase: FindInventoryUseCase,
    private val inventoryRepository: InventoryRepository,
) {

    @PostMapping("/reserve")
    fun reserve(@RequestBody @Valid request: ReserveRequest): ReserveResponse {
        val result = reserveInventoryUseCase.reserve(request.toCommand())
        return ReserveResponse.from(result)
    }

    @PostMapping("/release")
    fun release(@RequestBody @Valid request: ReleaseRequest) {
        releaseInventoryUseCase.release(request.toCommand())
    }

    @PostMapping("/increase")
    @Transactional
    fun increase(@RequestBody @Valid request: IncreaseRequest) {
        val inventory = inventoryRepository.findByProductIdForUpdate(request.productId!!)
            ?: Inventory(request.productId!!, 0, Instant.now())
        val updated = inventory.release(request.quantity!!, Instant.now())
        inventoryRepository.save(updated)
    }

    @GetMapping("/{productId}")
    fun find(@PathVariable productId: Long): InventoryResponse {
        val result = findInventoryUseCase.findByProductId(productId)
        return InventoryResponse.from(result)
    }
}
