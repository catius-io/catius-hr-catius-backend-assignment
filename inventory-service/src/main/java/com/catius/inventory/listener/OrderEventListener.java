package com.catius.inventory.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventListener {

    @KafkaListener(
            topics = "order-service.order.confirm",
            groupId = "inventory-service"
    )
    public void handleOrderConfirmed(String message) {
        log.info("Order confirmed event received: {}", message);
    }
}
