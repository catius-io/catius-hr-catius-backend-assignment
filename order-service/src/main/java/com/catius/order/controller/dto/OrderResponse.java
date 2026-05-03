package com.catius.order.controller.dto;

import com.catius.order.domain.Order;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        long customerId,
        String status,
        List<OrderItemResponse> items,
        Instant createdAt
) {

    public record OrderItemResponse(long productId, int quantity) {
    }

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getQuantity()))
                .toList();
        return new OrderResponse(
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus().name(),
                items,
                order.getCreatedAt()
        );
    }
}
