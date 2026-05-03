package com.catius.inventory.service.exception;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final long productId;
    private final int requestedQuantity;

    public InsufficientStockException(long productId, int requestedQuantity) {
        super("insufficient stock or product not found: productId=" + productId + ", quantity=" + requestedQuantity);
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
    }
}
