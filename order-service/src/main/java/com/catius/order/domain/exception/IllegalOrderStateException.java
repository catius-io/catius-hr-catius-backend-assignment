package com.catius.order.domain.exception;

import com.catius.order.domain.OrderStatus;
import lombok.Getter;

@Getter
public class IllegalOrderStateException extends RuntimeException {

    private final Long orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus attemptedTransition;

    public IllegalOrderStateException(Long orderId, OrderStatus currentStatus, OrderStatus attemptedTransition) {
        super("Order " + orderId + " cannot transition from " + currentStatus + " to " + attemptedTransition);
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.attemptedTransition = attemptedTransition;
    }
}
