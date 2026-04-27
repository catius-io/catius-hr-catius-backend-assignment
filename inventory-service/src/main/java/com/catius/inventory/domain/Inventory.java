package com.catius.inventory.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "inventory")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    public static Inventory of(Long productId, Integer quantity) {
        if (quantity < 0) throw new IllegalArgumentException("재고는 음수일 수 없습니다.");
        Inventory inventory = new Inventory();
        inventory.productId = productId;
        inventory.quantity = quantity;
        return inventory;
    }

    public void decrease(Integer quantity) {
        if (this.quantity < quantity) throw new IllegalStateException("재고가 부족합니다.");
        this.quantity -= quantity;
    }

    public void increase(Integer quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("수량은 0보다 커야 합니다.");
        this.quantity += quantity;
    }
}


