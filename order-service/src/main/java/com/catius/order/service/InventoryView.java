package com.catius.order.service;

/**
 * inventory-service가 노출하는 재고 상태의 읽기 전용 뷰.
 * order-service에는 Inventory 도메인 엔티티가 없으므로 record로 표현.
 */
public record InventoryView(long productId, int quantity) {
}
