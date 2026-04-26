package com.catius.order.client;

import com.catius.order.client.dto.request.InventoryRequest;
import com.catius.order.client.dto.response.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", url = "${inventory.base-url}")
public interface InventoryClient {

    @PostMapping("/api/v1/inventory/reserve")
    InventoryResponse reserve(@RequestBody InventoryRequest request);

    @PostMapping("/api/v1/inventory/release")
    InventoryResponse release(@RequestBody InventoryRequest request);
}
