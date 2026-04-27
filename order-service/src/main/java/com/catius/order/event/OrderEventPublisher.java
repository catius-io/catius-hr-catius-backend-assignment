package com.catius.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private static final String TOPIC = "order-service.order.confirm";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        kafkaTemplate.send(TOPIC, event.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] 발행 실패 event={}" , event, ex);
                    } else {
                        log.info("[Kafka] 발행 성공 : {}", result.getRecordMetadata());
                    }
                });
    }
}

