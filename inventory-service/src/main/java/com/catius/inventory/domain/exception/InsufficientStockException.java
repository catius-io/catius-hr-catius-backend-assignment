package com.catius.inventory.domain.exception;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final Long productId;
    private final int currentStock;
    private final int requestedQuantity;

    public InsufficientStockException(Long productId, int currentStock, int requestedQuantity) {
        super("Insufficient stock for product " + productId
                + ": current=" + currentStock + ", requested=" + requestedQuantity);
        this.productId = productId;
        this.currentStock = currentStock;
        this.requestedQuantity = requestedQuantity;
    }
}