package com.catius.order.service.feign;

import com.catius.order.service.InventoryClient;
import com.catius.order.service.InventoryView;
import com.catius.order.service.exception.AlreadyCompensatedException;
import com.catius.order.service.exception.AmbiguousInventoryException;
import com.catius.order.service.exception.InsufficientStockException;
import com.catius.order.service.exception.ProductNotFoundException;
import com.catius.order.service.exception.ReservationConflictException;
import com.catius.order.service.feign.dto.InventoryResponse;
import com.catius.order.service.feign.dto.ReleaseRequest;
import com.catius.order.service.feign.dto.ReserveRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link InventoryClient} 포트의 어댑터 — Feign 호출에 Resilience4j 서킷·재시도를 적용한다.
 *
 * <p>Resilience4j 인스턴스 이름 {@code inventoryClient}는 application.yml의 설정 키와 일치.
 * 명시적 4xx 도메인 예외는 application.yml의 {@code ignore-exceptions}로 CB·retry 모두에서 제외 —
 * "차감 없음 확정" 시그널이 일시적 장애와 섞이지 않도록 보장 (ADR-003).
 *
 * <p><b>Ambiguous failure 변환 정책</b>: explicit 도메인 예외 4종 + {@link AmbiguousInventoryException}
 * 본인을 제외한 모든 {@link RuntimeException}은 어댑터 경계에서 ambiguous로 감싼다. 이는
 * (1) 네트워크 layer 실패(timeout, connection refused — Feign의 {@code RetryableException}),
 * (2) 응답 디코드 실패(서버는 차감을 commit했으나 body가 깨진 경우 — {@code DecodeException}),
 * (3) 그 외 예측 못한 RuntimeException 모두를 ADR-003 ambiguous 분기로 흘려보내기 위함이다.
 * 차감 여부 불명확 시 at-least-once 보상이 release의 멱등성으로 안전하게 처리되는 설계.
 */
@Component
@RequiredArgsConstructor
public class InventoryClientAdapter implements InventoryClient {

    private final FeignInventoryClient feign;

    @Override
    @CircuitBreaker(name = "inventoryClient")
    @Retry(name = "inventoryClient")
    public InventoryView getInventory(long productId) {
        try {
            InventoryResponse r = feign.getInventory(productId);
            return new InventoryView(r.productId(), r.quantity());
        } catch (InsufficientStockException | AlreadyCompensatedException
                | ReservationConflictException | ProductNotFoundException
                | AmbiguousInventoryException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AmbiguousInventoryException(
                    "inventory transport or decode failure on getInventory: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "inventoryClient")
    @Retry(name = "inventoryClient")
    public void reserve(String orderId, long productId, int quantity) {
        try {
            feign.reserve(new ReserveRequest(orderId, productId, quantity));
        } catch (InsufficientStockException | AlreadyCompensatedException
                | ReservationConflictException | ProductNotFoundException
                | AmbiguousInventoryException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AmbiguousInventoryException(
                    "inventory transport or decode failure on reserve: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "inventoryClient")
    @Retry(name = "inventoryClient")
    public void release(String orderId, long productId) {
        try {
            feign.release(new ReleaseRequest(orderId, productId));
        } catch (InsufficientStockException | AlreadyCompensatedException
                | ReservationConflictException | ProductNotFoundException
                | AmbiguousInventoryException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AmbiguousInventoryException(
                    "inventory transport or decode failure on release: " + e.getMessage(), e);
        }
    }
}
