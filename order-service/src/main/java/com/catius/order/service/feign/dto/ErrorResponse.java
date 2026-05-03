package com.catius.order.service.feign.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
