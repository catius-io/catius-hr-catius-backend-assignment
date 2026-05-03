package com.catius.order.service.exception;

/**
 * inventory-service가 4xx INSUFFICIENT_STOCK으로 명시 거부 — 차감이 발생하지 않았음 확정.
 * Saga에서 보상 대상에서 제외하는 신호 (ADR-003 explicit failure 분기).
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
