package com.catius.order.domain.port

import com.catius.order.domain.OrderDomainEvent

interface OrderEventPublisher {
    fun publish(event: OrderDomainEvent.Confirmed)
}
