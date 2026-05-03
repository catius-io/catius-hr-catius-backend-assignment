package com.catius.order.service.exception;

/**
 * GET /inventory/{productId} 조회 시 존재하지 않는 product. 4xx.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String message) {
        super(message);
    }
}
