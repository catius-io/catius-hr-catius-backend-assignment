package com.catius.order.controller;

import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.repository.OrderRepository;
import com.catius.order.service.OrderSagaService;
import com.catius.order.service.exception.AlreadyCompensatedException;
import com.catius.order.service.exception.AmbiguousInventoryException;
import com.catius.order.service.exception.InsufficientStockException;
import com.catius.order.service.exception.ReservationConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    OrderSagaService sagaService;

    @MockBean
    OrderRepository orderRepository;

    @Test
    void create_returns201WithOrderResponse() throws Exception {
        Order persisted = Order.confirmed("order-1", 100L, List.of(OrderItem.of(1001L, 2)));
        when(sagaService.createOrder(anyLong(), anyList())).thenReturn(persisted);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(Map.of("productId", 1001, "quantity", 2))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void create_returns400OnDuplicateProductId() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(
                                        Map.of("productId", 1001, "quantity", 2),
                                        Map.of("productId", 1001, "quantity", 1))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("duplicate productId")));
    }

    @Test
    void create_returns400OnEmptyItems() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_returns400OnZeroQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(Map.of("productId", 1001, "quantity", 0))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_returns400OnZeroProductId() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(Map.of("productId", 0, "quantity", 1))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_returns400OnZeroCustomerId() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 0,
                                "items", List.of(Map.of("productId", 1001, "quantity", 1))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_returns400OnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void create_returns409OnInsufficientStock() throws Exception {
        when(sagaService.createOrder(anyLong(), anyList()))
                .thenThrow(new InsufficientStockException("low stock"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(Map.of("productId", 1001, "quantity", 5))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void create_returns409OnAlreadyCompensated() throws Exception {
        when(sagaService.createOrder(anyLong(), anyList()))
                .thenThrow(new AlreadyCompensatedException("tombstone"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(Map.of("productId", 1001, "quantity", 1))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_COMPENSATED"));
    }

    @Test
    void create_returns409OnReservationConflict() throws Exception {
        when(sagaService.createOrder(anyLong(), anyList()))
                .thenThrow(new ReservationConflictException("drift"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(Map.of("productId", 1001, "quantity", 5))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
    }

    @Test
    void create_returns503OnAmbiguous() throws Exception {
        when(sagaService.createOrder(anyLong(), anyList()))
                .thenThrow(new AmbiguousInventoryException("inventory unreachable"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", 100,
                                "items", List.of(Map.of("productId", 1001, "quantity", 1))))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVENTORY_UNAVAILABLE"));
    }

    @Test
    void get_returns200WithOrder() throws Exception {
        Order order = Order.confirmed("order-1", 100L, List.of(OrderItem.of(1001L, 2)));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/v1/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.customerId").value(100));
    }

    @Test
    void get_returns404WhenOrderMissing() throws Exception {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
