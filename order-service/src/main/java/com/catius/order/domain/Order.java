package com.catius.order.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    private String orderId;

    private long customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = new ArrayList<>();

    private Instant createdAt;

    /**
     * Order 생성에 필요한 입력 불변식만 검증 — Saga 시작 전 service 경계에서 재사용 가능.
     * orderId 검사는 Saga 내부 책임이므로 본 메서드는 customerId / items만 본다.
     */
    public static void validateOrderInput(long customerId, List<OrderItem> items) {
        Objects.requireNonNull(items, "items");
        if (customerId <= 0) {
            throw new IllegalArgumentException("customerId must be positive: " + customerId);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        Set<Long> seenProductIds = new HashSet<>();
        for (OrderItem item : items) {
            Objects.requireNonNull(item, "items must not contain null");
            if (!seenProductIds.add(item.getProductId())) {
                throw new IllegalArgumentException("duplicate productId: " + item.getProductId());
            }
        }
    }

    public static Order confirmed(String orderId, long customerId, List<OrderItem> items) {
        Objects.requireNonNull(orderId, "orderId");
        validateOrderInput(customerId, items);

        Order order = new Order();
        order.orderId = orderId;
        order.customerId = customerId;
        order.items = new ArrayList<>(items);
        order.status = OrderStatus.CONFIRMED;
        order.createdAt = Instant.now();
        return order;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
