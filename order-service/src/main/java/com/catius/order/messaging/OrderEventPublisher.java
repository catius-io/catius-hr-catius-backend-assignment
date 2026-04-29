package com.catius.order.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Saga 의 *마지막 단계* — order 확정 이벤트 발행.
 *
 * 본 publisher 의 send 가 실패하면 Saga 가 catch 해 보상(release) 트리거.
 * 동기 send 는 의도적 — KafkaTemplate.send(...).get() 로 broker ack 까지 확인해야
 * 발행 성공/실패가 결정적으로 판단됨. 비동기로 두면 Saga 가 *publish 실패를 늦게 알게 되어*
 * 보상 시점이 흔들림.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderConfirmedEvent> kafkaTemplate;

    @Value("${catius.kafka.topics.order-confirmed}")
    private String orderConfirmedTopic;

    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        try {
            // 동기 대기 — broker ack 확인 후 진행. timeout 은 KafkaTemplate 의 default 사용.
            kafkaTemplate.send(orderConfirmedTopic, event.orderId().toString(), event).get();
            log.info("published order-confirmed: orderId={} eventId={}", event.orderId(), event.eventId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderEventPublishException("interrupted while publishing order-confirmed", e);
        } catch (Exception e) {
            throw new OrderEventPublishException("failed to publish order-confirmed", e);
        }
    }
}
