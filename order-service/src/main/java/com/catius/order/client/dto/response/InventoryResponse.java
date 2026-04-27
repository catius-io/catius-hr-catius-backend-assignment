package com.catius.order.client.dto.response;


public record InventoryResponse(
        String productId,
        String productName,
        int quantity
) {

}
