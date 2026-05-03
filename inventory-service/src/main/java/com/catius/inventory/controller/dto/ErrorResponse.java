package com.catius.inventory.controller.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
