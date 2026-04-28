package com.catius.inventory.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String productId, int currentQuantity, int requestedQuantity) {
        super(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK",
                "Insufficient stock: productId=" + productId
                        + ", current=" + currentQuantity
                        + ", requested=" + requestedQuantity
        );
    }
}
