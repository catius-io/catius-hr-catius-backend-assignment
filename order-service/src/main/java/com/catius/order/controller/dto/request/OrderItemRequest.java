package com.catius.order.controller.dto.request;

public record OrderItemRequest(
        String productId,
        int quantity
) {
}
