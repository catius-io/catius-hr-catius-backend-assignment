package com.catius.order.controller;

import com.catius.order.controller.dto.CreateOrderRequest;
import com.catius.order.controller.dto.OrderItemDto;
import com.catius.order.controller.dto.OrderResponse;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.repository.OrderRepository;
import com.catius.order.service.OrderSagaService;
import com.catius.order.service.exception.OrderNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderSagaService sagaService;
    private final OrderRepository orderRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        // 입력 불변식 (ADR-001) — 같은 productId 중복 금지. Saga 시작 전에 차단.
        validateNoDuplicateProductIds(request.items());

        List<OrderItem> domainItems = request.items().stream()
                .map(i -> OrderItem.of(i.productId(), i.quantity()))
                .toList();

        Order order = sagaService.createOrder(request.customerId(), domainItems);
        return OrderResponse.from(order);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.from(order);
    }

    private static void validateNoDuplicateProductIds(List<OrderItemDto> items) {
        Set<Long> seen = new HashSet<>();
        for (OrderItemDto item : items) {
            if (!seen.add(item.productId())) {
                throw new IllegalArgumentException("duplicate productId: " + item.productId());
            }
        }
    }
}
