package com.catius.order.service.exception;

/**
 * inventory-service 호출에서 5xx, 타임아웃, 또는 connection 실패가 발생 — 차감 여부 불명확.
 * Saga의 ambiguous failure 분기 트리거 (ADR-003) — at-least-once 보상 발행.
 * Resilience4j circuit breaker 및 retry의 record 대상.
 */
public class AmbiguousInventoryException extends RuntimeException {

    public AmbiguousInventoryException(String message) {
        super(message);
    }

    public AmbiguousInventoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
