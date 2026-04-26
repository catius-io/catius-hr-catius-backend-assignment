package com.catius.order.domain

import java.time.Instant

sealed interface OrderDomainEvent {
    val orderId: Long
    val occurredAt: Instant

    data class Created(
        override val orderId: Long,
        val customerId: Long,
        val items: List<OrderItem>,
        override val occurredAt: Instant,
    ) : OrderDomainEvent

    data class Confirmed(
        override val orderId: Long,
        val customerId: Long,
        val items: List<OrderItem>,
        override val occurredAt: Instant,
    ) : OrderDomainEvent

    data class Failed(
        override val orderId: Long,
        val reason: String,
        override val occurredAt: Instant,
    ) : OrderDomainEvent
}
