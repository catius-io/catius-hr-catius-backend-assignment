package com.catius.order.client;

import com.catius.order.client.dto.ReleaseInventoryRequest;
import com.catius.order.client.dto.ReserveInventoryRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * inventory-service 의 REST contract 에 1:1 매핑되는 Feign 인터페이스.
 *
 * Resilience4j 의 CB / retry / timeout 적용은 다음 PR 의 InventoryGateway 가 wrapper 로 담당.
 * 본 인터페이스는 순수 HTTP 매핑 + ErrorDecoder 를 통한 4xx/5xx → 도메인 예외 변환만.
 */
@FeignClient(
        name = "inventoryClient",
        url = "${inventory.base-url}",
        configuration = InventoryClientConfig.class
)
public interface InventoryClient {

    @PostMapping("/api/v1/inventory/reserve")
    void reserve(@RequestBody ReserveInventoryRequest request);

    @PostMapping("/api/v1/inventory/release")
    void release(@RequestBody ReleaseInventoryRequest request);
}
