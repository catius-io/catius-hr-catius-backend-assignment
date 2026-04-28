package com.catius.order.controller

import com.catius.order.controller.dto.CreateOrderRequest
import com.catius.order.controller.dto.OrderResponse
import com.catius.order.domain.usecase.CreateOrderUseCase
import com.catius.order.domain.usecase.FindOrderUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase,
    private val findOrderUseCase: FindOrderUseCase,
) {
    @PostMapping
    fun create(@RequestBody @Valid request: CreateOrderRequest): OrderResponse {
        val result = createOrderUseCase.create(request.toCommand())
        return OrderResponse.from(result)
    }

    @GetMapping("/{orderId}")
    fun find(@PathVariable orderId: Long): OrderResponse {
        val result = findOrderUseCase.findById(orderId)
        return OrderResponse.from(result)
    }
}
