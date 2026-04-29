package com.catius.order.domain;

import com.catius.order.domain.exception.IllegalOrderStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Saga 의 시작점이자 종착점. 상태 전이는 confirm / markFailed / markCompensated 로 일원화되며
 * 각 메서드는 PENDING 에서만 호출 가능 (종착 상태에서의 호출은 IllegalOrderStateException).
 *
 * 테이블명을 'orders' (복수) 로 둔 이유: 'order' 는 SQL 예약어라 일부 DB/도구에서 인용 부호 없이는 충돌.
 */
@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Order(Long productId, int quantity) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        this.productId = productId;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public static Order create(Long productId, int quantity) {
        return new Order(productId, quantity);
    }

    public void confirm() {
        transition(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    public void markFailed() {
        transition(OrderStatus.PENDING, OrderStatus.FAILED);
    }

    public void markCompensated() {
        transition(OrderStatus.PENDING, OrderStatus.COMPENSATED);
    }

    private void transition(OrderStatus expected, OrderStatus next) {
        if (this.status != expected) {
            throw new IllegalOrderStateException(this.id, this.status, next);
        }
        this.status = next;
    }
}
