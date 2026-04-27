package com.catius.order.controller.dto.request;

public record OrderRequest (
        String productId,
        int quantity
){
}
