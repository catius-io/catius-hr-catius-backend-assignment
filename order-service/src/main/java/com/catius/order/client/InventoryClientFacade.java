package com.catius.order.client;

import com.catius.order.client.dto.response.InventoryResponse;
import com.catius.order.client.dto.request.InventoryRequest;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryClientFacade {

    private static final String RESERVE_CB = "inventoryReserve";
    private static final String RELEASE_CB  = "inventoryRelease";

    private final InventoryClient inventoryClient;

    @CircuitBreaker(name = RESERVE_CB, fallbackMethod = "reserveFallback")
    public InventoryResponse reserve(InventoryRequest request) {
        return inventoryClient.reserve(request);
    }

    @CircuitBreaker(name = RELEASE_CB, fallbackMethod = "releaseFallback")
    @Retry(name = RELEASE_CB)
    public InventoryResponse release(InventoryRequest request) {
        return inventoryClient.release(request);
    }

    private InventoryResponse reserveFallback(InventoryRequest request, Exception e) {
        if (e instanceof FeignException.NotFound || e instanceof FeignException.Conflict) {
            throw (FeignException) e;
        }

        log.error("[RESERVE_CB-Fallback] reserve 불가 productId={}, cause={}", request.productId(), e.getMessage());
        throw new RuntimeException("inventory-service 일시적으로 사용 불가 (circuit open): " + e.getMessage(), e);
    }

    private InventoryResponse releaseFallback(InventoryRequest request, Exception e) {
        if (e instanceof FeignException.NotFound) {
            throw (FeignException) e;
        }

        log.error("[RELEASE_CB-Fallback] release 불가 productId={}, cause={}",
                request.productId(), e.getMessage());
        throw new RuntimeException("inventory-service 일시적으로 사용 불가, 재고 복원 실패: " + e.getMessage(), e);
    }
}
