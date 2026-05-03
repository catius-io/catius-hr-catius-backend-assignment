package com.catius.inventory.controller.dto;

import com.catius.inventory.domain.Inventory;

public record InventoryResponse(
        long productId,
        int quantity
) {

    public static InventoryResponse from(Inventory inv) {
        return new InventoryResponse(inv.getProductId(), inv.getQuantity());
    }
}
