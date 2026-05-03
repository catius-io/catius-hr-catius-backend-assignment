package com.catius.order.service.exception;

/**
 * 동일 (orderId, productId) 멱등 키로 재호출했지만 quantity가 다른 경우 (payload drift).
 * 클라이언트 또는 boundary outbox 재발행 측의 결함을 시그널 — Saga에서는 explicit failure.
 */
public class ReservationConflictException extends RuntimeException {

    public ReservationConflictException(String message) {
        super(message);
    }
}
