package com.catius.inventory.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReserveRequest(
        @NotBlank String orderId,
        @Positive long productId,
        @Positive int quantity
) {
}
