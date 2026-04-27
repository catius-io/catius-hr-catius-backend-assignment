package com.catius.order.service;

import com.catius.order.controller.dto.request.OrderRequest;
import com.catius.order.controller.dto.response.OrderResponse;
import com.catius.order.domain.Order;
import com.catius.order.event.OrderEventPublisher;
import com.catius.order.event.OrderSagaOrchestrator;
import com.catius.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Order order = orderRepository.save(Order.create(request.productId(), request.quantity()));
        orderSagaOrchestrator.execute(order);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));

    }
}
