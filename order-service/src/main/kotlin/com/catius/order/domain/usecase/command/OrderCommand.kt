package com.catius.order.domain.usecase.command

sealed interface OrderCommand {

    data class CreateOrderCommand(
        val customerId: Long,
        val items: List<Item>,
    ) : OrderCommand {
        data class Item(
            val productId: Long,
            val quantity: Int,
        )
    }
}
