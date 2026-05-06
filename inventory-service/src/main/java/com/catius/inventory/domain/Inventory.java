package com.catius.inventory.domain;


import com.catius.inventory.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table
@Getter
@NoArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Inventory create(Long productId, String productName, int quantity) {
        Inventory inventory = new Inventory();
        inventory.productId = productId;
        inventory.productName = productName;
        inventory.quantity = quantity;
        inventory.createdAt = LocalDateTime.now();
        inventory.updatedAt = LocalDateTime.now();
        return inventory;
    }

    public void deduct(int quantity) {
        if (this.quantity < quantity) {
            throw new InsufficientStockException(productId.toString(), this.quantity, quantity);
        }
        this.quantity -= quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public void restore(int quantity) {
        this.quantity += quantity;
        this.updatedAt = LocalDateTime.now();
    }
}
