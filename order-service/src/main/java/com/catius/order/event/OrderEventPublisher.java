package com.catius.order.event;

import com.catius.order.exception.KafkaPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    @Value("${catius.kafka.topics.order-confirmed}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        try {
            var result = kafkaTemplate
                    .send(topic, event.getOrderId().toString(), event)
                    .get(1_500, TimeUnit.MILLISECONDS);
            log.info("[Kafka] 발행 성공 orderId={} offset={}",
                    event.getOrderId(), result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException("[Kafka] 발행 인터럽트 orderId=" + event.getOrderId(), e);
        } catch (ExecutionException e) {
            log.error("[Kafka] 발행 실패 orderId={}", event.getOrderId(), e.getCause());
            throw new KafkaPublishException("[Kafka] 발행 실패 orderId=" + event.getOrderId(), e.getCause());
        } catch (TimeoutException e) {
            log.error("[Kafka] 발행 타임아웃 orderId={}", event.getOrderId());
            throw new KafkaPublishException("[Kafka] 발행 타임아웃 orderId=" + event.getOrderId(), e);
        }
    }
}
