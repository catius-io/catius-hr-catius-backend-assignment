package com.catius.order.service.event;

import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;

import java.time.Instant;
import java.util.List;

/**
 * order.order-confirmed.v1 토픽 페이로드 (ADR-005). 멱등 키는 orderId.
 */
public record OrderConfirmedEvent(
        String orderId,
        long customerId,
        List<Item> items,
        Instant confirmedAt
) {

    public record Item(long productId, int quantity) {
        static Item from(OrderItem item) {
            return new Item(item.getProductId(), item.getQuantity());
        }
    }

    public static OrderConfirmedEvent from(Order order) {
        return new OrderConfirmedEvent(
                order.getOrderId(),
                order.getCustomerId(),
                order.getItems().stream().map(Item::from).toList(),
                order.getCreatedAt()
        );
    }
}
