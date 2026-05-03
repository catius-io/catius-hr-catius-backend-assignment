package com.catius.order.service.feign;

import com.catius.order.service.exception.AlreadyCompensatedException;
import com.catius.order.service.exception.AmbiguousInventoryException;
import com.catius.order.service.exception.InsufficientStockException;
import com.catius.order.service.exception.ProductNotFoundException;
import com.catius.order.service.exception.ReservationConflictException;
import com.catius.order.service.feign.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * inventory-service의 4xx/5xx 응답을 order-service 도메인 예외로 변환.
 *
 * <p>매핑 (ADR-003 explicit vs ambiguous failure 분기 기반):
 * <ul>
 *   <li>4xx + body code 인식 → 명시적 도메인 예외 (Saga에서 explicit 분기 → 차감 없음 확정)</li>
 *   <li>4xx + body code 미인식 → {@link AmbiguousInventoryException} (보수적으로 ambiguous 처리)</li>
 *   <li>5xx → {@link AmbiguousInventoryException} (Saga에서 ambiguous 분기 → at-least-once 보상)</li>
 * </ul>
 *
 * <p>네트워크 예외(connection refused, timeout 등)는 ErrorDecoder가 아니라 Feign client 자체에서
 * 발생하므로 본 클래스가 처리하지 않는다 — {@link InventoryClientAdapter}의 catch 블록에서 변환.
 */
@Slf4j
@RequiredArgsConstructor
public class InventoryErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;
    private final ErrorDecoder fallback = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();

        if (status >= 500) {
            return new AmbiguousInventoryException(
                    "inventory server error: status=" + status + ", method=" + methodKey);
        }

        if (status >= 400) {
            ErrorResponse body = parseBody(response);
            if (body == null) {
                return new AmbiguousInventoryException(
                        "inventory client error with unparseable body: status=" + status);
            }
            return mapByCode(body);
        }

        return fallback.decode(methodKey, response);
    }

    private RuntimeException mapByCode(ErrorResponse body) {
        return switch (body.code()) {
            case "INSUFFICIENT_STOCK" -> new InsufficientStockException(body.message());
            case "ALREADY_COMPENSATED" -> new AlreadyCompensatedException(body.message());
            case "RESERVATION_CONFLICT" -> new ReservationConflictException(body.message());
            case "PRODUCT_NOT_FOUND" -> new ProductNotFoundException(body.message());
            default -> new AmbiguousInventoryException(
                    "unknown inventory error code: " + body.code() + " — " + body.message());
        };
    }

    private ErrorResponse parseBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try {
            byte[] bytes = Util.toByteArray(response.body().asInputStream());
            if (bytes.length == 0) {
                return null;
            }
            return objectMapper.readValue(bytes, ErrorResponse.class);
        } catch (IOException e) {
            log.warn("failed to parse inventory error body", e);
            return null;
        }
    }
}
