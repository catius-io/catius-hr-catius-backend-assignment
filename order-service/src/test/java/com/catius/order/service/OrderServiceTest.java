package com.catius.order.service;

import com.catius.order.controller.dto.request.OrderRequest;
import com.catius.order.controller.dto.response.OrderResponse;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderStatus;
import com.catius.order.event.OrderSagaOrchestrator;
import com.catius.order.exception.OrderNotFoundException;
import com.catius.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderSagaOrchestrator orderSagaOrchestrator;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("주문 생성 - 성공")
    void createOrder_성공() {
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> {
            Order saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        willDoNothing().given(orderSagaOrchestrator).execute(any(Order.class));

        OrderResponse response = orderService.createOrder(new OrderRequest("PRODUCT-001", 2));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.productId()).isEqualTo("PRODUCT-001");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        then(orderSagaOrchestrator).should().execute(any(Order.class));
    }

    @Test
    @DisplayName("주문 조회 - 성공")
    void findById_성공() {
        Order order = Order.create("PRODUCT-001", 2);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        OrderResponse response = orderService.findById(1L);

        assertThat(response.productId()).isEqualTo("PRODUCT-001");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 조회 - 없는 주문 → 404")
    void findById_없는주문_404() {
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 999");
    }
}
