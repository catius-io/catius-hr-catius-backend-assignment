package com.catius.inventory.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReleaseRequest(
        @NotBlank String orderId,
        @Positive long productId
) {
}
