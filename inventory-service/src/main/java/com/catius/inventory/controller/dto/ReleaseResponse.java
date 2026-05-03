package com.catius.inventory.controller.dto;

import com.catius.inventory.service.ReleaseOutcome;

public record ReleaseResponse(
        String orderId,
        long productId,
        String outcome
) {

    public static ReleaseResponse of(String orderId, long productId, ReleaseOutcome outcome) {
        return new ReleaseResponse(orderId, productId, outcome.name());
    }
}
