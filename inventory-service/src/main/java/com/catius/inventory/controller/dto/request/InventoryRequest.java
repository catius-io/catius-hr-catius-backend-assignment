package com.catius.inventory.controller.dto.request;


public record InventoryRequest(
        String productId,
        int quantity
) {
}
