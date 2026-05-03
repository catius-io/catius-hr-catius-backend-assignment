package com.catius.order.service.feign.dto;

public record ReleaseResponse(
        String orderId,
        long productId,
        String outcome
) {
}
