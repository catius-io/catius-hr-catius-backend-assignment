package com.catius.order.service.feign;

import com.catius.order.service.feign.dto.InventoryResponse;
import com.catius.order.service.feign.dto.ReleaseRequest;
import com.catius.order.service.feign.dto.ReleaseResponse;
import com.catius.order.service.feign.dto.ReservationResponse;
import com.catius.order.service.feign.dto.ReserveRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * inventory-service의 HTTP contract를 그대로 노출한 Feign 인터페이스 (어댑터의 내부 표면).
 * Resilience4j 적용은 {@link InventoryClientAdapter}의 메서드에서 annotation으로 수행.
 */
@FeignClient(
        name = "inventoryClient",
        url = "${inventory.base-url}",
        configuration = InventoryFeignConfig.class
)
public interface FeignInventoryClient {

    @GetMapping("/api/v1/inventory/{productId}")
    InventoryResponse getInventory(@PathVariable("productId") long productId);

    @PostMapping("/api/v1/inventory/reserve")
    ReservationResponse reserve(@RequestBody ReserveRequest request);

    @PostMapping("/api/v1/inventory/release")
    ReleaseResponse release(@RequestBody ReleaseRequest request);
}
