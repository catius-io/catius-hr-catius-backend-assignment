package com.catius.inventory.service.exception;

import lombok.Getter;

@Getter
public class ProductNotFoundException extends RuntimeException {

    private final long productId;

    public ProductNotFoundException(long productId) {
        super("product not found: productId=" + productId);
        this.productId = productId;
    }
}
