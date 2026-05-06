package com.catius.inventory.exception;

import org.springframework.http.HttpStatus;

public class StockNotFoundException extends BusinessException {

    public StockNotFoundException(Long productId) {
        super(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "Stock not found: " + productId);
    }
}
