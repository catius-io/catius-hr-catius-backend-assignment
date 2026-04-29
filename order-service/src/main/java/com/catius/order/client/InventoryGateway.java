package com.catius.order.client;

import com.catius.order.client.dto.ReleaseInventoryRequest;
import com.catius.order.client.dto.ReserveInventoryRequest;
import com.catius.order.client.exception.InventoryClientException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * InventoryClient (Feign) 를 Resilience4j 로 감싼 facade. Saga 는 본 Gateway 만 사용.
 *
 * 정책:
 * - @Retry: 영구 실패(InsufficientStock / ProductNotFound) 는 yml ignore-exceptions 로 재시도 안 함.
 *           그 외(5xx / 네트워크 / timeout) 는 max-attempts 만큼 재시도.
 * - @CircuitBreaker: 영구 실패는 CB failure 카운트에서 제외 — CB 는 inventory 의 *가용성* 만 반영.
 * - Transport 에러 (timeout, connection refused 등) 는 Feign 이 RetryableException/FeignException 으로
 *   던지므로 호출자(Saga) 입장에선 도메인 예외 계층을 벗어남. Gateway 가 catch 후 InventoryClientException
 *   으로 wrap 해 일관된 예외 계층 보장 — Saga 가 catch (InventoryClientException) 한 번이면 충분.
 */
@Component
@RequiredArgsConstructor
public class InventoryGateway {

    private static final String CLIENT_NAME = "inventoryClient";

    private final InventoryClient client;

    @CircuitBreaker(name = CLIENT_NAME)
    @Retry(name = CLIENT_NAME)
    public void reserve(Long productId, int quantity) {
        try {
            client.reserve(new ReserveInventoryRequest(productId, quantity));
        } catch (InventoryClientException e) {
            throw e;  // ErrorDecoder 가 만든 도메인 예외는 그대로 — retry/ignore 정책이 정확히 매칭되도록
        } catch (RuntimeException e) {
            throw new InventoryClientException("inventory reserve failed", e);
        }
    }

    @CircuitBreaker(name = CLIENT_NAME)
    @Retry(name = CLIENT_NAME)
    public void release(Long productId, int quantity) {
        try {
            client.release(new ReleaseInventoryRequest(productId, quantity));
        } catch (InventoryClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InventoryClientException("inventory release failed", e);
        }
    }
}
