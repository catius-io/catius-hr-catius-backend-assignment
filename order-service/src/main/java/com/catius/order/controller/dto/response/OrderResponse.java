package com.catius.order.controller.dto.response;

import com.catius.order.domain.Order;
import com.catius.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String productId,
        int quantity,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}