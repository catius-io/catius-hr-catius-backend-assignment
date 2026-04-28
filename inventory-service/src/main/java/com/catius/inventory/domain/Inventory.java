package com.catius.inventory.domain;

import com.catius.inventory.domain.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_product_id", columnNames = "product_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int stock;

    private Inventory(Long productId, int stock) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("stock must not be negative: " + stock);
        }
        this.productId = productId;
        this.stock = stock;
    }

    public static Inventory create(Long productId, int initialStock) {
        return new Inventory(productId, initialStock);
    }

    public void reserve(int quantity) {
        validatePositive(quantity);
        if (stock < quantity) {
            throw new InsufficientStockException(productId, stock, quantity);
        }
        this.stock -= quantity;
    }

    public void release(int quantity) {
        validatePositive(quantity);
        this.stock += quantity;
    }

    private static void validatePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
    }
}