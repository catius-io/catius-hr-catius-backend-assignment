package com.catius.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reservations_order_product",
                columnNames = {"order_id", "product_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationState state;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant releasedAt;

    private Reservation(String orderId, long productId, int quantity, ReservationState state) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.state = state;
        this.createdAt = Instant.now();
        if (state == ReservationState.RELEASED) {
            this.releasedAt = this.createdAt;
        }
    }

    public static Reservation reserved(String orderId, long productId, int quantity) {
        Objects.requireNonNull(orderId, "orderId");
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive: " + productId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        return new Reservation(orderId, productId, quantity, ReservationState.RESERVED);
    }

    public static Reservation tombstone(String orderId, long productId) {
        Objects.requireNonNull(orderId, "orderId");
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive: " + productId);
        }
        return new Reservation(orderId, productId, 0, ReservationState.RELEASED);
    }

    public boolean release() {
        if (state == ReservationState.RELEASED) {
            return false;
        }
        state = ReservationState.RELEASED;
        releasedAt = Instant.now();
        return true;
    }
}
