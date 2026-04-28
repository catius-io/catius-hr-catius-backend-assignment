package com.catius.order.exception;

import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(Long orderId) {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found: " + orderId);
    }
}
