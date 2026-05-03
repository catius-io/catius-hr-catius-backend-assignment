package com.catius.order.service.feign.dto;

public record ReservationResponse(
        String orderId,
        long productId,
        int quantity,
        String state
) {
}
