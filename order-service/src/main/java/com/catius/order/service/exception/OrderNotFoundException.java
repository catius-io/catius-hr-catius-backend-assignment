package com.catius.order.service.exception;

import lombok.Getter;

@Getter
public class OrderNotFoundException extends RuntimeException {

    private final Long orderId;

    public OrderNotFoundException(Long orderId) {
        super("Order not found: id=" + orderId);
        this.orderId = orderId;
    }
}
