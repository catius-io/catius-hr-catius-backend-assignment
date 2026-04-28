package com.catius.inventory.domain.usecase.command

data class ReserveResult(
    val orderId: Long,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
