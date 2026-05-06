package com.catius.order.controller;

import com.catius.order.controller.dto.request.OrderItemRequest;
import com.catius.order.controller.dto.request.OrderRequest;
import com.catius.order.controller.dto.response.OrderItemResponse;
import com.catius.order.controller.dto.response.OrderResponse;
import com.catius.order.domain.OrderStatus;
import com.catius.order.exception.OrderNotFoundException;
import com.catius.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("주문 생성 - 201 Created")
    void createOrder_201() throws Exception {
        given(orderService.createOrder(any()))
                .willReturn(new OrderResponse(1L, 1L, OrderStatus.PENDING, LocalDateTime.now(),
                        List.of(new OrderItemResponse(1001L, 2))));

        OrderRequest request = new OrderRequest(1L, List.of(new OrderItemRequest(1001L, 2)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].productId").value(1001))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("주문 조회 - 200 OK")
    void getOrder_200() throws Exception {
        given(orderService.findById(1L))
                .willReturn(new OrderResponse(1L, 1L, OrderStatus.CONFIRMED, LocalDateTime.now(),
                        List.of(new OrderItemResponse(1001L, 2))));

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("주문 조회 - 없는 주문 → 404")
    void getOrder_404() throws Exception {
        given(orderService.findById(999L))
                .willThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/api/v1/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found: 999"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/999"));
    }
}
