package com.catius.inventory.controller.dto;

import com.catius.inventory.domain.Reservation;

public record ReservationResponse(
        String orderId,
        long productId,
        int quantity,
        String state
) {

    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getOrderId(),
                r.getProductId(),
                r.getQuantity(),
                r.getState().name()
        );
    }
}
