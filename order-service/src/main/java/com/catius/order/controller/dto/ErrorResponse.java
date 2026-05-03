package com.catius.order.controller.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
