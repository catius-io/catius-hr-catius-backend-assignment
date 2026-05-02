package com.catius.inventory.domain

import java.time.Instant

sealed interface InventoryDomainEvent {
    val orderId: Long
    val productId: Long
    val occurredAt: Instant

    data class Reserved(
        override val orderId: Long,
        override val productId: Long,
        val quantity: Int,
        override val occurredAt: Instant,
    ) : InventoryDomainEvent

    data class Released(
        override val orderId: Long,
        override val productId: Long,
        val quantity: Int,
        override val occurredAt: Instant,
    ) : InventoryDomainEvent
}
