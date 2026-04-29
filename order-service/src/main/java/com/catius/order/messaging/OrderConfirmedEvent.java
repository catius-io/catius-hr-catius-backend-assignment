package com.catius.order.messaging;

import java.time.Instant;

/**
 * order.order-confirmed.v1 토픽에 발행되는 이벤트.
 * 컨슈머 측 멱등성을 위해 eventId 를 unique key 로 사용 가능 (Kafka at-least-once 대응).
 */
public record OrderConfirmedEvent(
        String eventId,
        Long orderId,
        Long productId,
        int quantity,
        Instant occurredAt
) {
}
