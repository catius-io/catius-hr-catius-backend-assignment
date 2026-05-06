package com.catius.order.client.dto.response;


public record InventoryResponse(
        Long productId,
        String productName,
        int quantity
) {

}
