package com.catius.order.service.exception;

/**
 * Kafka 발행 시도가 transient retry 한계를 넘어 실패. Saga 흐름에서는
 * pending_compensations.status=DISPATCH_FAILED 마킹 후 부팅 재시도 (ADR-007).
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
