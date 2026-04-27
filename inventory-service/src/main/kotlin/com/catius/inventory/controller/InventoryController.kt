package com.catius.inventory.controller

import com.catius.inventory.controller.dto.InventoryResponse
import com.catius.inventory.controller.dto.ReleaseRequest
import com.catius.inventory.controller.dto.ReserveRequest
import com.catius.inventory.controller.dto.ReserveResponse
import com.catius.inventory.domain.usecase.FindInventoryUseCase
import com.catius.inventory.domain.usecase.ReleaseInventoryUseCase
import com.catius.inventory.domain.usecase.ReserveInventoryUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController(
    private val reserveInventoryUseCase: ReserveInventoryUseCase,
    private val releaseInventoryUseCase: ReleaseInventoryUseCase,
    private val findInventoryUseCase: FindInventoryUseCase,
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

    @GetMapping("/{productId}")
    fun find(@PathVariable productId: Long): InventoryResponse {
        val result = findInventoryUseCase.findByProductId(productId)
        return InventoryResponse.from(result)
    }
}
