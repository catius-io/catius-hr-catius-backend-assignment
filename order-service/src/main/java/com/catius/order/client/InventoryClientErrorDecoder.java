package com.catius.order.client;

import com.catius.order.client.exception.InsufficientStockException;
import com.catius.order.client.exception.InventoryClientException;
import com.catius.order.client.exception.ProductNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * inventory-service 의 4xx/5xx 응답을 도메인 예외로 변환한다.
 *
 * 본 디코더의 책임은 "어떤 HTTP 상태가 어떤 의미인가" 만 — Saga 흐름의 보상/재시도 정책은
 * InventoryGateway (다음 PR) 의 R4J 설정과 catch 로직이 결정한다.
 */
@Slf4j
@RequiredArgsConstructor
public class InventoryClientErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        String message = extractMessage(body, response);

        return switch (response.status()) {
            case 404 -> new ProductNotFoundException(message);
            case 409 -> new InsufficientStockException(message);
            default  -> new InventoryClientException(
                    "inventory call failed: status=" + response.status() + " body=" + body);
        };
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try (InputStream in = response.body().asInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("failed to read Feign response body", e);
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessage(String body, Response response) {
        if (body.isBlank()) {
            return "inventory call failed: status=" + response.status();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            Object msg = parsed.get("message");
            return msg != null ? msg.toString() : body;
        } catch (IOException e) {
            return body;
        }
    }
}
