package com.catius.order.controller.dto.response;

import com.catius.order.domain.OrderItem;

public record OrderItemResponse(
        Long productId,
        int quantity
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getQuantity());
    }
}
