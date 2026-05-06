package com.catius.order.service;

import com.catius.order.controller.dto.request.OrderRequest;
import com.catius.order.controller.dto.response.OrderResponse;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.event.OrderSagaOrchestrator;
import com.catius.order.exception.OrderNotFoundException;
import com.catius.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    public OrderResponse createOrder(OrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(item -> OrderItem.of(item.productId(), item.quantity()))
                .toList();
        Order order = orderRepository.save(Order.create(request.customerId(), items));
        orderSagaOrchestrator.execute(order);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
