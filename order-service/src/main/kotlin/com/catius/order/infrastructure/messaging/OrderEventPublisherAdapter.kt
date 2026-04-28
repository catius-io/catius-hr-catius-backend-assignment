package com.catius.order.infrastructure.messaging

import com.catius.order.domain.OrderDomainEvent
import com.catius.order.domain.port.OrderEventPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class OrderEventPublisherAdapter(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${catius.kafka.topics.order-confirmed}")
    private val topic: String,
) : OrderEventPublisher {

    override fun publish(event: OrderDomainEvent.Confirmed) {
        kafkaTemplate.send(topic, event.orderId.toString(), event)
    }
}
