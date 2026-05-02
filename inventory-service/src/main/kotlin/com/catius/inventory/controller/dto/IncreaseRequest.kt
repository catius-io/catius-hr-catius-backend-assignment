package com.catius.inventory.controller.dto

import com.catius.inventory.domain.usecase.command.InventoryCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class IncreaseRequest(
    @field:NotNull
    val productId: Long?,
    @field:NotNull
    @field:Min(1)
    val quantity: Int?
)
