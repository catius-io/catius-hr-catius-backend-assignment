package com.catius.inventory.controller.dto

import com.catius.inventory.domain.usecase.command.InventoryCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class ReserveRequest(
    @field:NotNull
    val orderId: Long?,

    @field:NotEmpty
    @field:Valid
    val items: List<ItemRequest>?,
) {
    data class ItemRequest(
        @field:NotNull
        val productId: Long?,

        @field:NotNull
        @field:Min(1)
        val quantity: Int?,
    )

    fun toCommand(): InventoryCommand.ReserveCommand = InventoryCommand.ReserveCommand(
        orderId = orderId!!,
        items = items!!.map { InventoryCommand.Item(it.productId!!, it.quantity!!) },
    )
}

data class ReleaseRequest(
    @field:NotNull
    val orderId: Long?,

    @field:NotEmpty
    @field:Valid
    val items: List<ItemRequest>?,
) {
    data class ItemRequest(
        @field:NotNull
        val productId: Long?,

        @field:NotNull
        @field:Min(1)
        val quantity: Int?,
    )

    fun toCommand(): InventoryCommand.ReleaseCommand = InventoryCommand.ReleaseCommand(
        orderId = orderId!!,
        items = items!!.map { InventoryCommand.Item(it.productId!!, it.quantity!!) },
    )
}
