package com.catius.order.event;

import com.catius.order.client.InventoryClientFacade;
import com.catius.order.client.dto.request.InventoryRequest;
import com.catius.order.client.dto.response.InventoryResponse;
import com.catius.order.domain.Order;
import com.catius.order.domain.OrderStatus;
import com.catius.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private InventoryClientFacade inventoryClientFacade;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderSagaOrchestrator orchestrator;

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.create(1L, "PRODUCT-001", 2);
    }

    // ── 정상 흐름 ─────────────────────────────────────────────────

    @Test
    @DisplayName("정상 흐름 - 재고 차감 성공 시 주문 확정 및 이벤트 발행")
    void execute_성공_주문확정_이벤트발행() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 8));
        willDoNothing().given(orderEventPublisher).publishOrderConfirmed(any());

        orchestrator.execute(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        then(inventoryClientFacade).should().reserve(any());
        then(orderEventPublisher).should().publishOrderConfirmed(any());
        then(inventoryClientFacade).should(never()).release(any());
    }

    // ── 보상 트랜잭션 ─────────────────────────────────────────────

    @Test
    @DisplayName("보상 트랜잭션 - 재고 차감 실패 시 release 호출하지 않음 (차감된 재고 없음)")
    void execute_재고차감_실패_release_미호출() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willThrow(new RuntimeException("재고 부족"));

        orchestrator.execute(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(inventoryClientFacade).should().reserve(any());
        then(inventoryClientFacade).should(never()).release(any());
        then(orderEventPublisher).should(never()).publishOrderConfirmed(any());
    }

    @Test
    @DisplayName("보상 트랜잭션 - 이벤트 발행 실패 시 주문 취소 및 재고 복구")
    void execute_이벤트발행_실패_보상트랜잭션() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 8));
        willThrow(new RuntimeException("Kafka 연결 실패"))
                .given(orderEventPublisher).publishOrderConfirmed(any());

        orchestrator.execute(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(inventoryClientFacade).should().reserve(any());
        then(inventoryClientFacade).should().release(any());
    }

    @Test
    @DisplayName("보상 트랜잭션 - 이벤트 발행 실패 시 release에 올바른 파라미터 전달")
    void execute_보상트랜잭션_release_파라미터_검증() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 8));
        willThrow(new RuntimeException("Kafka 연결 실패"))
                .given(orderEventPublisher).publishOrderConfirmed(any());

        orchestrator.execute(order);

        then(inventoryClientFacade).should().release(new InventoryRequest("PRODUCT-001", 2));
    }

    @Test
    @DisplayName("보상 트랜잭션 - release 실패해도 예외가 전파되지 않음")
    void execute_release_실패_예외_전파_안됨() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 8));
        willThrow(new RuntimeException("Kafka 연결 실패"))
                .given(orderEventPublisher).publishOrderConfirmed(any());
        willThrow(new RuntimeException("inventory-service 장애"))
                .given(inventoryClientFacade).release(any());

        // release 실패해도 예외가 밖으로 나오지 않아야 함
        orchestrator.execute(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("주문 상태 저장 - SQLITE_BUSY 발생 시 재시도 후 성공")
    void execute_주문상태저장_lock_재시도_성공() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 8));
        given(orderRepository.save(any(Order.class)))
                .willThrow(new CannotAcquireLockException("database is locked"))
                .willReturn(order);

        orchestrator.execute(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        then(orderRepository).should(times(2)).save(any(Order.class));
        then(orderEventPublisher).should().publishOrderConfirmed(any());
    }

    @Test
    @DisplayName("보상 트랜잭션 - 주문 취소 저장 실패해도 재고 복구는 시도")
    void execute_주문취소저장_lock_재고복구_시도() {
        given(inventoryClientFacade.reserve(any(InventoryRequest.class)))
                .willReturn(new InventoryResponse("PRODUCT-001", "테스트 상품", 8));
        willThrow(new RuntimeException("Kafka 연결 실패"))
                .given(orderEventPublisher).publishOrderConfirmed(any());
        given(orderRepository.save(any(Order.class)))
                .willReturn(order)
                .willThrow(new CannotAcquireLockException("database is locked"));

        orchestrator.execute(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(inventoryClientFacade).should().release(any());
    }
}
