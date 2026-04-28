package com.catius.inventory.domain.usecase.command

sealed interface InventoryCommand {

    data class Item(
        val productId: Long,
        val quantity: Int,
    )

    data class ReserveCommand(
        val orderId: Long,
        val items: List<Item>,
    ) : InventoryCommand

    data class ReleaseCommand(
        val orderId: Long,
        val items: List<Item>,
    ) : InventoryCommand
}
