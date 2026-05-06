package com.catius.order.controller.dto.request;

public record OrderItemRequest(
        Long productId,
        int quantity
) {
}
