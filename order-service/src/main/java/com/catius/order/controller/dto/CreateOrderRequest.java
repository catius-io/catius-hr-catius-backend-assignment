package com.catius.order.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @Positive long customerId,
        @NotEmpty @Valid List<OrderItemDto> items
) {
}
