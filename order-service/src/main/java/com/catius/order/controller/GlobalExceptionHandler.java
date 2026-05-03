package com.catius.order.controller;

import com.catius.order.client.exception.InsufficientStockException;
import com.catius.order.client.exception.InventoryClientException;
import com.catius.order.client.exception.ProductNotFoundException;
import com.catius.order.domain.exception.IllegalOrderStateException;
import com.catius.order.controller.dto.ErrorResponse;
import com.catius.order.messaging.OrderEventPublishException;
import com.catius.order.service.exception.OrderNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * order-service 의 도메인/Feign/R4J/인프라 예외 → HTTP 응답 매핑.
 *
 * 매핑 원칙:
 * - 4xx: 호출자가 요청을 고치면 해결 가능한 종류 (검증 실패 / 충돌 / not-found).
 * - 5xx: 호출자가 어찌할 수 없는 인프라/서버측 실패 (downstream 장애 / publish 실패 / DB 실패).
 *
 * Saga 보상 동작과의 관계:
 * - reserve 단계 실패 ({@link InsufficientStockException} / {@link ProductNotFoundException}) →
 *   stock 차감 자체가 안 일어났으므로 보상 불필요. 즉시 4xx.
 * - confirm/publish 단계 실패 ({@link OrderEventPublishException} / {@link DataAccessException}) →
 *   OrderService 가 보상(release + COMPENSATED) 후 throw. 사용자에겐 5xx 가 적절 (요청 미완료).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 4xx — 호출자 측 정정 가능 ─────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ErrorResponse("VALIDATION_FAILED", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException ex) {
        return new ErrorResponse("MALFORMED_REQUEST", "Request body is malformed or missing");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        return new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderNotFound(OrderNotFoundException ex) {
        return new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInsufficientStock(InsufficientStockException ex) {
        return new ErrorResponse("INSUFFICIENT_STOCK", ex.getMessage());
    }

    @ExceptionHandler(IllegalOrderStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalOrderState(IllegalOrderStateException ex) {
        return new ErrorResponse("ILLEGAL_ORDER_STATE", ex.getMessage());
    }

    // ── 5xx — 인프라/서버 측 ─────────────────────────────────────────────────

    /**
     * R4J CircuitBreaker 가 OPEN 상태라 호출 자체가 차단된 경우.
     * 짧은 시간 후 자동 half-open 으로 복구되므로 503 + Retry-After 의도.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("inventory circuit breaker OPEN: {}", ex.getMessage());
        return new ErrorResponse("INVENTORY_CIRCUIT_OPEN", "Inventory service is temporarily unavailable");
    }

    /**
     * 4xx (Insufficient/ProductNotFound) 가 아닌 모든 inventory-service 호출 실패.
     * (5xx, 네트워크 장애, timeout, 알 수 없는 4xx 등 transport-level 문제)
     */
    @ExceptionHandler(InventoryClientException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleInventoryClient(InventoryClientException ex) {
        log.warn("inventory call failed: {}", ex.toString());
        return new ErrorResponse("INVENTORY_UNAVAILABLE", ex.getMessage());
    }

    /**
     * Saga 가 reserve 성공 후 publish 실패 → 보상(release + COMPENSATED) 까지 마친 상태.
     * 호출자 입장에선 주문이 성립하지 않았으므로 5xx 로 명확히 알린다.
     */
    @ExceptionHandler(OrderEventPublishException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handlePublishFailure(OrderEventPublishException ex) {
        log.error("order event publish failed (compensation already attempted)", ex);
        return new ErrorResponse("ORDER_PUBLISH_FAILED",
                "Order could not be published; reservation has been compensated");
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleDataAccess(DataAccessException ex) {
        log.error("data access failure", ex);
        return new ErrorResponse("DATA_ACCESS_FAILURE", "Internal data access error");
    }
}
