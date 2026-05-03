package com.catius.inventory.service.kafka;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.domain.Reservation;
import com.catius.inventory.domain.ReservationState;
import com.catius.inventory.repository.InventoryRepository;
import com.catius.inventory.repository.ReservationRepository;
import com.catius.inventory.service.InventoryReservationService;
import com.catius.inventory.service.event.InventoryReleaseRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        topics = {"inventory.release-requested.v1"},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
class InventoryReleaseRequestedListenerTest {

    private static final String TOPIC = "inventory.release-requested.v1";

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    InventoryRepository inventoryRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    InventoryReservationService service;

    @Value("${catius.kafka.topics.inventory-release-requested}")
    String topic;

    @BeforeEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        inventoryRepository.deleteAllInBatch();
    }

    @Test
    void releaseEvent_restoresInventory_whenReservationExists() {
        inventoryRepository.save(Inventory.of(1001L, 100));
        service.reserve("order-1", 1001L, 3);
        assertEquals(97, inventoryRepository.findById(1001L).orElseThrow().getQuantity());

        InventoryReleaseRequestedEvent event = new InventoryReleaseRequestedEvent(
                "order-1",
                List.of(new InventoryReleaseRequestedEvent.Item(1001L, 3)),
                "EXPLICIT_FAILURE"
        );
        kafkaTemplate.send(topic, "order-1", event);

        awaitInventoryQuantity(1001L, 100);
        Reservation r = reservationRepository.findByOrderIdAndProductId("order-1", 1001L).orElseThrow();
        assertEquals(ReservationState.RELEASED, r.getState());
    }

    @Test
    void releaseEvent_createsTombstone_whenReservationAbsent() {
        // release-before-reserve race 시뮬레이션 (reserve 호출 안 함)
        inventoryRepository.save(Inventory.of(1001L, 100));

        InventoryReleaseRequestedEvent event = new InventoryReleaseRequestedEvent(
                "order-1",
                List.of(new InventoryReleaseRequestedEvent.Item(1001L, 0)),
                "AMBIGUOUS_FAILURE"
        );
        kafkaTemplate.send(topic, "order-1", event);

        awaitTombstone("order-1", 1001L);
        // 재고는 변하지 않음
        assertEquals(100, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
    }

    @Test
    void releaseEvent_handlesMultipleItems_inOneEvent() {
        inventoryRepository.save(Inventory.of(1001L, 100));
        inventoryRepository.save(Inventory.of(1002L, 50));
        service.reserve("order-1", 1001L, 3);
        service.reserve("order-1", 1002L, 5);

        InventoryReleaseRequestedEvent event = new InventoryReleaseRequestedEvent(
                "order-1",
                List.of(
                        new InventoryReleaseRequestedEvent.Item(1001L, 3),
                        new InventoryReleaseRequestedEvent.Item(1002L, 5)
                ),
                "PERSIST_FAILURE"
        );
        kafkaTemplate.send(topic, "order-1", event);

        awaitInventoryQuantity(1001L, 100);
        awaitInventoryQuantity(1002L, 50);
    }

    private void awaitInventoryQuantity(long productId, int expected) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<Inventory> inv = inventoryRepository.findById(productId);
            if (inv.isPresent() && inv.get().getQuantity() == expected) return;
            sleep();
        }
        throw new AssertionError("inventory.quantity for " + productId + " did not reach " + expected
                + " (current=" + inventoryRepository.findById(productId).map(Inventory::getQuantity).orElse(-1) + ")");
    }

    private void awaitTombstone(String orderId, long productId) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<Reservation> r = reservationRepository.findByOrderIdAndProductId(orderId, productId);
            if (r.isPresent() && r.get().getState() == ReservationState.RELEASED) return;
            sleep();
        }
        throw new AssertionError("tombstone not created for orderId=" + orderId + ", productId=" + productId);
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
