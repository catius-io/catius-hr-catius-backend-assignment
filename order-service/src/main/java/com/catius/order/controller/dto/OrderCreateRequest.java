package com.catius.order.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderCreateRequest(
        @NotNull(message = "productId must not be null") Long productId,
        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive") Integer quantity
) {
}
