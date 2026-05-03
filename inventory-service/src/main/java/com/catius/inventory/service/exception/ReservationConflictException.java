package com.catius.inventory.service.exception;

import lombok.Getter;

/**
 * 동일 (orderId, productId) 멱등 키로 재호출됐을 때 quantity가 기존 reservation과 다른 경우.
 * 멱등 키 payload drift — 클라이언트 측 중복 발행 + 변형 가능성. 409 Conflict 성격.
 */
@Getter
public class ReservationConflictException extends RuntimeException {

    private final String orderId;
    private final long productId;
    private final int existingQuantity;
    private final int requestedQuantity;

    public ReservationConflictException(String orderId, long productId,
                                        int existingQuantity, int requestedQuantity) {
        super("reservation quantity drift on idempotent retry: orderId=" + orderId
                + ", productId=" + productId
                + ", existing=" + existingQuantity
                + ", requested=" + requestedQuantity);
        this.orderId = orderId;
        this.productId = productId;
        this.existingQuantity = existingQuantity;
        this.requestedQuantity = requestedQuantity;
    }
}
