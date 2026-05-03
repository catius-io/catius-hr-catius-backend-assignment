package com.catius.order.service.kafka;

import com.catius.order.service.OrderEventPublisher;
import com.catius.order.service.event.InventoryReleaseRequestedEvent;
import com.catius.order.service.event.OrderConfirmedEvent;
import com.catius.order.service.exception.EventPublishException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${catius.kafka.topics.order-confirmed}")
    private String confirmedTopic;

    @Value("${catius.kafka.topics.inventory-release-requested}")
    private String releaseRequestedTopic;

    @Value("${catius.kafka.publish-timeout-seconds:5}")
    private long publishTimeoutSeconds;

    @Override
    public void publishConfirmed(OrderConfirmedEvent event) {
        sendSync(confirmedTopic, event.orderId(), event);
    }

    @Override
    public void publishCompensation(InventoryReleaseRequestedEvent event) {
        sendSync(releaseRequestedTopic, event.orderId(), event);
    }

    private void sendSync(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).get(publishTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("publish interrupted: topic=" + topic, e);
        } catch (ExecutionException e) {
            throw new EventPublishException("publish failed: topic=" + topic, e.getCause());
        } catch (TimeoutException e) {
            throw new EventPublishException("publish timed out: topic=" + topic, e);
        }
    }
}
