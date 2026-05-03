package com.catius.order.service.feign.dto;

public record ReserveRequest(
        String orderId,
        long productId,
        int quantity
) {
}
