package com.catius.order.controller

import com.catius.order.controller.advice.OrderControllerAdvice
import com.catius.order.controller.dto.CreateOrderRequest
import com.catius.order.controller.dto.OrderItemRequest
import com.catius.order.domain.OrderStatus
import com.catius.order.domain.exception.InsufficientStockException
import com.catius.order.domain.exception.OrderNotFoundException
import com.catius.order.domain.usecase.CreateOrderUseCase
import com.catius.order.domain.usecase.FindOrderUseCase
import com.catius.order.domain.usecase.command.OrderResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(OrderController::class, OrderControllerAdvice::class)
class OrderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var createOrderUseCase: CreateOrderUseCase

    @MockBean
    private lateinit var findOrderUseCase: FindOrderUseCase

    private fun <T> anyK(): T {
        any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @Test
    @DisplayName("주문 생성 성공")
    fun create_order_success() {
        val request = CreateOrderRequest(
            customerId = 1L,
            items = listOf(OrderItemRequest(productId = 101L, quantity = 2))
        )
        val result = OrderResult(
            id = 1L,
            customerId = 1L,
            items = listOf(OrderResult.Item(productId = 101L, quantity = 2)),
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        given(createOrderUseCase.create(anyK())).willReturn(result)

        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.customerId").value(1L))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    @DisplayName("주문 조회 성공")
    fun find_order_success() {
        val result = OrderResult(
            id = 1L,
            customerId = 1L,
            items = listOf(OrderResult.Item(productId = 101L, quantity = 2)),
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        given(findOrderUseCase.findById(1L)).willReturn(result)

        mockMvc.perform(get("/api/v1/orders/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.customerId").value(1L))
    }

    @Test
    @DisplayName("주문 조회 실패 - 존재하지 않는 주문")
    fun find_order_not_found() {
        given(findOrderUseCase.findById(1L)).willThrow(OrderNotFoundException(1L))

        mockMvc.perform(get("/api/v1/orders/1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("주문을 찾을 수 없습니다: id=1"))
    }

    @Test
    @DisplayName("주문 생성 실패 - 재고 부족")
    fun create_order_insufficient_stock() {
        val request = CreateOrderRequest(
            customerId = 1L,
            items = listOf(OrderItemRequest(productId = 101L, quantity = 2))
        )
        given(createOrderUseCase.create(anyK())).willThrow(InsufficientStockException(101L, "재고 부족"))

        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("재고 부족"))
    }

    @Test
    @DisplayName("주문 생성 실패 - 잘못된 요청 (customerId 누락)")
    fun create_order_bad_request() {
        val request = mapOf(
            "items" to listOf(mapOf("productId" to 101L, "quantity" to 2))
        )

        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }
}
