package com.catius.order.service.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("order not found: orderId=" + orderId);
    }
}
