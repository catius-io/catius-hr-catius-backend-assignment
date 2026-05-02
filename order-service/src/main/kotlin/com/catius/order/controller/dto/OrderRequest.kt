package com.catius.order.controller.dto

import com.catius.order.domain.usecase.command.OrderCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class CreateOrderRequest(
    @field:NotNull
    val customerId: Long?,

    @field:NotEmpty
    @field:Valid
    val items: List<OrderItemRequest>?,
) {
    fun toCommand(): OrderCommand.CreateOrderCommand {
        return OrderCommand.CreateOrderCommand(
            customerId = customerId!!,
            items = items!!.map { it.toCommand() }
        )
    }
}

data class OrderItemRequest(
    @field:NotNull
    val productId: Long?,

    @field:NotNull
    @field:Min(1)
    val quantity: Int?,
) {
    fun toCommand(): OrderCommand.CreateOrderCommand.Item {
        return OrderCommand.CreateOrderCommand.Item(
            productId = productId!!,
            quantity = quantity!!
        )
    }
}
