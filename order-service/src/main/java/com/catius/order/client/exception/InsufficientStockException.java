package com.catius.order.client.exception;

/**
 * inventory-service 가 409 INSUFFICIENT_STOCK 으로 응답한 경우.
 * 영구적 실패 — Saga 는 보상 없이 즉시 FAILED 처리.
 */
public class InsufficientStockException extends InventoryClientException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
