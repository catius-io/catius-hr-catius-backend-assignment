package com.catius.order.repository;

import com.catius.order.domain.Order;
import com.catius.order.domain.OrderItem;
import com.catius.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void persistsOrderWithItems() {
        Order order = Order.confirmed("order-1", 100L, List.of(
                OrderItem.of(1001L, 2),
                OrderItem.of(1002L, 3)
        ));

        orderRepository.saveAndFlush(order);
        entityManager.clear();

        Order loaded = orderRepository.findById("order-1").orElseThrow();
        assertEquals("order-1", loaded.getOrderId());
        assertEquals(100L, loaded.getCustomerId());
        assertEquals(OrderStatus.CONFIRMED, loaded.getStatus());
        assertEquals(2, loaded.getItems().size());
        assertTrue(loaded.getItems().stream().anyMatch(i -> i.getProductId() == 1001L && i.getQuantity() == 2));
        assertTrue(loaded.getItems().stream().anyMatch(i -> i.getProductId() == 1002L && i.getQuantity() == 3));
    }

    @Test
    void findByIdReturnsEmptyWhenAbsent() {
        assertTrue(orderRepository.findById("missing").isEmpty());
    }
}
