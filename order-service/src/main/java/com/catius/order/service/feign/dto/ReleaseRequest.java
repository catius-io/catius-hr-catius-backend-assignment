package com.catius.order.service.feign.dto;

public record ReleaseRequest(
        String orderId,
        long productId
) {
}
