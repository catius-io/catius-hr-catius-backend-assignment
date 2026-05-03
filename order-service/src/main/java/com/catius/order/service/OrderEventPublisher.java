package com.catius.order.service;

import com.catius.order.service.event.InventoryReleaseRequestedEvent;
import com.catius.order.service.event.OrderConfirmedEvent;

/**
 * outbound 이벤트 발행 포트 (ADR-001). 구현체는 service/kafka/ 서브패키지의 KafkaOrderEventPublisher.
 *
 * <p>두 이벤트의 발행 실패 정책이 다르다 (ADR-007):
 * <ul>
 *   <li>{@link #publishConfirmed}: 발행 실패 시 상위 호출자가 swallow + log/metric (사용자 응답 201 유지)</li>
 *   <li>{@link #publishCompensation}: 발행 실패는 pending_compensations.status=DISPATCH_FAILED로 영속화되고 부팅 시 재시도</li>
 * </ul>
 */
public interface OrderEventPublisher {

    void publishConfirmed(OrderConfirmedEvent event);

    void publishCompensation(InventoryReleaseRequestedEvent event);
}
