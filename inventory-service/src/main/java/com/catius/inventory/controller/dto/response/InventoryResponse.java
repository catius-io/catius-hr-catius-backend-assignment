package com.catius.inventory.controller.dto.response;

public record InventoryResponse(
        String productId,
        String productName,
        int quantity
) {
}
