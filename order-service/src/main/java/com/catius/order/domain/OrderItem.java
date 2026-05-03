package com.catius.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private int quantity;

    private OrderItem(long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public static OrderItem of(long productId, int quantity) {
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive: " + productId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        return new OrderItem(productId, quantity);
    }
}
