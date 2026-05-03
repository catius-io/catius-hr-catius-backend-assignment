package com.catius.order.domain;

/**
 * Saga 진행 중 inventory.reserve를 시도한 단일 item.
 * pending_compensations.attempted_items_json에 직렬화되어 영속화 (ADR-007).
 */
public record AttemptedItem(long productId, int quantity) {

    public AttemptedItem {
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive: " + productId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
    }

    public static AttemptedItem from(OrderItem item) {
        return new AttemptedItem(item.getProductId(), item.getQuantity());
    }
}
