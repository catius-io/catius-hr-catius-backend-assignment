package com.catius.order.controller;

import com.catius.order.client.exception.InsufficientStockException;
import com.catius.order.client.exception.InventoryClientException;
import com.catius.order.client.exception.ProductNotFoundException;
import com.catius.order.domain.Order;
import com.catius.order.messaging.OrderEventPublishException;
import com.catius.order.service.OrderService;
import com.catius.order.service.exception.OrderNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderController + GlobalExceptionHandler 의 REST 계약 검증.
 * @WebMvcTest 로 OrderService 만 MockBean 으로 격리해 컨트롤러 + 직렬화 + 예외 매핑까지 한 번에 본다.
 */
@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderController — REST contract & 예외 매핑 (MockMvc)")
class OrderControllerTest {

    private static final Long PRODUCT_ID = 9001L;
    private static final Long ORDER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService service;

    private Order persistedOrder() {
        Order order = Order.create(PRODUCT_ID, 3);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-04-29T10:00:00Z"));
        order.confirm();
        return order;
    }

    @Nested
    @DisplayName("POST /api/v1/orders — 주문 생성")
    class Create {

        @Test
        void 정상_요청은_201_과_Location_헤더_및_body() throws Exception {
            given(service.createOrder(eq(PRODUCT_ID), eq(3))).willReturn(persistedOrder());

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 3))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.endsWith("/api/v1/orders/" + ORDER_ID)))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(ORDER_ID))
                    .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
                    .andExpect(jsonPath("$.quantity").value(3))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        void 재고_부족은_409_와_INSUFFICIENT_STOCK() throws Exception {
            given(service.createOrder(eq(PRODUCT_ID), eq(3)))
                    .willThrow(new InsufficientStockException("insufficient stock"));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 3))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        }

        @Test
        void 없는_상품은_404_와_PRODUCT_NOT_FOUND() throws Exception {
            given(service.createOrder(eq(PRODUCT_ID), eq(3)))
                    .willThrow(new ProductNotFoundException("product not found"));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 3))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }

        @Test
        void inventory_transport_실패는_503_과_INVENTORY_UNAVAILABLE() throws Exception {
            given(service.createOrder(eq(PRODUCT_ID), eq(3)))
                    .willThrow(new InventoryClientException("connection refused"));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 3))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("INVENTORY_UNAVAILABLE"));
        }

        @Test
        void CB_OPEN_은_503_과_INVENTORY_CIRCUIT_OPEN() throws Exception {
            CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
            cb.transitionToOpenState();
            CallNotPermittedException cbOpen = CallNotPermittedException.createCallNotPermittedException(cb);
            given(service.createOrder(eq(PRODUCT_ID), eq(3))).willThrow(cbOpen);

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 3))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("INVENTORY_CIRCUIT_OPEN"));
        }

        @Test
        void publish_실패_보상완료는_500_과_ORDER_PUBLISH_FAILED() throws Exception {
            given(service.createOrder(eq(PRODUCT_ID), eq(3)))
                    .willThrow(new OrderEventPublishException("kafka send failed", new RuntimeException()));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 3))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("ORDER_PUBLISH_FAILED"));
        }

        @Test
        void DB_실패는_500_과_DATA_ACCESS_FAILURE() throws Exception {
            given(service.createOrder(eq(PRODUCT_ID), eq(3)))
                    .willThrow(new DataAccessResourceFailureException("db down"));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 3))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("DATA_ACCESS_FAILURE"));
        }

        @Test
        void quantity_가_0_이면_400_과_VALIDATION_FAILED() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("productId", PRODUCT_ID, "quantity", 0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("quantity")));
        }

        @Test
        void productId_가_누락되면_400() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("quantity", 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void 잘못된_JSON_은_400_과_MALFORMED_REQUEST() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not-json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{id} — 주문 조회")
    class Get {

        @Test
        void 존재하는_주문은_200_과_body() throws Exception {
            given(service.getOrder(ORDER_ID)).willReturn(persistedOrder());

            mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ORDER_ID))
                    .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
                    .andExpect(jsonPath("$.quantity").value(3))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        void 없는_주문은_404_와_ORDER_NOT_FOUND() throws Exception {
            given(service.getOrder(ORDER_ID)).willThrow(new OrderNotFoundException(ORDER_ID));

            mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }
    }
}
