package com.catius.order.client.exception;

/**
 * inventory-service 가 404 PRODUCT_NOT_FOUND 로 응답한 경우.
 * 영구적 실패 — Saga 는 보상 없이 즉시 FAILED 처리.
 */
public class ProductNotFoundException extends InventoryClientException {

    public ProductNotFoundException(String message) {
        super(message);
    }
}
