package com.catius.inventory.service.exception;

import lombok.Getter;

@Getter
public class AlreadyCompensatedException extends RuntimeException {

    private final String orderId;
    private final long productId;

    public AlreadyCompensatedException(String orderId, long productId) {
        super("reservation already released (tombstone): orderId=" + orderId + ", productId=" + productId);
        this.orderId = orderId;
        this.productId = productId;
    }
}
