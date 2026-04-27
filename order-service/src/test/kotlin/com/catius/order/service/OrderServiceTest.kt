package com.catius.order.service

import com.catius.order.domain.Order
import com.catius.order.domain.OrderDomainEvent
import com.catius.order.domain.OrderItem
import com.catius.order.domain.OrderStatus
import com.catius.order.domain.port.InventoryClient
import com.catius.order.domain.port.OrderEventPublisher
import com.catius.order.domain.port.OrderRepository
import com.catius.order.domain.port.ReserveOutcome
import com.catius.order.domain.usecase.command.OrderCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OrderServiceTest {

    private val now = Instant.parse("2026-04-28T00:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var orderRepository: OrderRepository
    private lateinit var inventoryClient: InventoryClient
    private lateinit var orderEventPublisher: OrderEventPublisher
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var service: OrderService

    private fun <T> anyK(): T {
        any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @BeforeEach
    fun setup() {
        orderRepository = mock(OrderRepository::class.java)
        inventoryClient = mock(InventoryClient::class.java)
        orderEventPublisher = mock(OrderEventPublisher::class.java)
        transactionTemplate = mock(TransactionTemplate::class.java)
        service = OrderService(orderRepository, inventoryClient, orderEventPublisher, fixedClock, transactionTemplate)

        // Mock transactionTemplate.execute to call the callback immediately
        given(transactionTemplate.execute(anyK<TransactionCallback<Any>>())).willAnswer {
            val callback = it.getArgument<TransactionCallback<Any>>(0)
            callback.doInTransaction(mock(TransactionStatus::class.java))
        }
    }

    @Nested
    @DisplayName("주문 생성 (CreateOrder)")
    inner class CreateOrder {

        @Test
        @DisplayName("재고 예약 성공 시 주문이 CONFIRMED 가 되고 이벤트를 발행한다")
        fun create_success() {
            // given
            val command = OrderCommand.CreateOrderCommand(
                customerId = 1L,
                items = listOf(OrderCommand.CreateOrderCommand.Item(productId = 1001L, quantity = 2)),
            )
            val pendingOrder = Order.create(1L, listOf(OrderItem(1001L, 2)), now).copy(id = 42L)
            val confirmedOrder = pendingOrder.confirm(now)

            given(orderRepository.save(anyK())).willReturn(pendingOrder, confirmedOrder)
            given(orderRepository.findById(42L)).willReturn(pendingOrder)
            given(inventoryClient.reserve(eq(42L), anyK())).willReturn(ReserveOutcome.Success)

            // when
            val result = service.create(command)

            // then
            assertThat(result.id).isEqualTo(42L)
            assertThat(result.status).isEqualTo(OrderStatus.CONFIRMED)

            verify(orderRepository, times(2)).save(anyK())
            verify(orderRepository).findById(42L)
            verify(inventoryClient).reserve(eq(42L), anyK())
            verify(orderEventPublisher).publish(anyK())
        }

        @Test
        @DisplayName("재고 부족 시 주문이 FAILED 가 되고 release 는 호출하지 않는다")
        fun create_insufficient_stock() {
            // given
            val command = OrderCommand.CreateOrderCommand(
                customerId = 1L,
                items = listOf(OrderCommand.CreateOrderCommand.Item(productId = 1001L, quantity = 2)),
            )
            val pendingOrder = Order.create(1L, listOf(OrderItem(1001L, 2)), now).copy(id = 42L)
            val failedOrder = pendingOrder.fail(now)

            given(orderRepository.save(anyK())).willReturn(pendingOrder, failedOrder)
            given(orderRepository.findById(42L)).willReturn(pendingOrder)
            given(inventoryClient.reserve(eq(42L), anyK())).willReturn(ReserveOutcome.InsufficientStock(1001L))

            // when
            val result = service.create(command)

            // then
            assertThat(result.status).isEqualTo(OrderStatus.FAILED)
            verify(inventoryClient, never()).release(any(Long::class.java), anyK())
            verify(orderEventPublisher, never()).publish(anyK())
        }

        @Test
        @DisplayName("재고 서버 장애(Unavailable) 시 release 를 호출하고 주문이 FAILED 가 된다")
        fun create_unavailable() {
            // given
            val command = OrderCommand.CreateOrderCommand(
                customerId = 1L,
                items = listOf(OrderCommand.CreateOrderCommand.Item(productId = 1001L, quantity = 2)),
            )
            val pendingOrder = Order.create(1L, listOf(OrderItem(1001L, 2)), now).copy(id = 42L)
            val failedOrder = pendingOrder.fail(now)

            given(orderRepository.save(anyK())).willReturn(pendingOrder, failedOrder)
            given(orderRepository.findById(42L)).willReturn(pendingOrder)
            given(inventoryClient.reserve(eq(42L), anyK())).willReturn(ReserveOutcome.Unavailable)

            // when
            val result = service.create(command)

            // then
            assertThat(result.status).isEqualTo(OrderStatus.FAILED)
            verify(inventoryClient).release(eq(42L), anyK())
            verify(orderEventPublisher, never()).publish(anyK())
        }
    }
}
