package com.catius.order.controller.dto;

import jakarta.validation.constraints.Positive;

public record OrderItemDto(
        @Positive long productId,
        @Positive int quantity
) {
}
