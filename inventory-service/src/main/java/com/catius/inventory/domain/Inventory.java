package com.catius.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    private long productId;

    private int quantity;

    private Inventory(long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public static Inventory of(long productId, int quantity) {
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive: " + productId);
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be non-negative: " + quantity);
        }
        return new Inventory(productId, quantity);
    }
}
