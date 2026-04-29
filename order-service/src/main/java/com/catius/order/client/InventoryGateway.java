package com.catius.order.client;

import com.catius.order.client.dto.ReleaseInventoryRequest;
import com.catius.order.client.dto.ReserveInventoryRequest;
import com.catius.order.client.exception.InventoryClientException;
import feign.FeignException;
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
 *   던지므로 호출자(Saga) 입장에선 도메인 예외 계층을 벗어남. wrapTransportErrors 가 단일 지점에서
 *   InventoryClientException 으로 wrap 해 일관된 예외 계층 보장.
 */
@Component
@RequiredArgsConstructor
public class InventoryGateway {

    private static final String CLIENT_NAME = "inventoryClient";

    private final InventoryClient client;

    @CircuitBreaker(name = CLIENT_NAME)
    @Retry(name = CLIENT_NAME)
    public void reserve(Long productId, int quantity) {
        wrapTransportErrors("reserve",
                () -> client.reserve(new ReserveInventoryRequest(productId, quantity)));
    }

    @CircuitBreaker(name = CLIENT_NAME)
    @Retry(name = CLIENT_NAME)
    public void release(Long productId, int quantity) {
        wrapTransportErrors("release",
                () -> client.release(new ReleaseInventoryRequest(productId, quantity)));
    }

    /**
     * Feign 의 transport 에러 (timeout / connection refused / 직렬화 실패 등) 를
     * InventoryClientException 으로 통일. 도메인 예외 (InventoryClientException 자손) 는
     * R4J retry/ignore 정책 매칭 보존을 위해 그대로 rethrow.
     *
     * catch 범위를 FeignException 으로 좁힌 이유: catch (RuntimeException) 으로 두면 우리 코드의
     * NPE/IllegalStateException 같은 *프로그래머 에러* 가 "inventory ... failed" 로 wrap 되어
     * 진단 메시지가 거짓말이 됨. Feign 이 던진 것만 인프라 실패로 간주.
     * (FeignException 은 RetryableException 의 부모 — timeout/connection refused 도 포함)
     *
     * 본 헬퍼가 단일 지점이므로, 향후 transport 에러 종류별 세분화(예: InventoryTimeoutException) 가
     * 필요해지면 이 메서드 한 곳만 확장하면 된다 (reserve/release 의 catch 중복 폭발 방지).
     */
    private void wrapTransportErrors(String op, Runnable call) {
        try {
            call.run();
        } catch (InventoryClientException e) {
            throw e;
        } catch (FeignException e) {
            throw new InventoryClientException("inventory " + op + " failed", e);
        }
    }
}
