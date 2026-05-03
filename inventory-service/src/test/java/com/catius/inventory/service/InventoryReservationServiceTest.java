package com.catius.inventory.service;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.domain.Reservation;
import com.catius.inventory.domain.ReservationState;
import com.catius.inventory.repository.InventoryRepository;
import com.catius.inventory.repository.ReservationRepository;
import com.catius.inventory.service.exception.AlreadyCompensatedException;
import com.catius.inventory.service.exception.InsufficientStockException;
import com.catius.inventory.service.exception.ReservationConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InventoryReservationServiceTest {

    @Autowired
    InventoryReservationService service;

    @Autowired
    InventoryRepository inventoryRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @BeforeEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        inventoryRepository.deleteAllInBatch();
    }

    @Test
    void reserve_decrementsInventoryAndPersistsReservation() {
        inventoryRepository.save(Inventory.of(1001L, 100));

        Reservation r = service.reserve("order-1", 1001L, 3);

        assertEquals("order-1", r.getOrderId());
        assertEquals(1001L, r.getProductId());
        assertEquals(3, r.getQuantity());
        assertEquals(ReservationState.RESERVED, r.getState());
        assertEquals(97, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
    }

    @Test
    void reserve_isIdempotentForSameOrderAndProduct() {
        inventoryRepository.save(Inventory.of(1001L, 100));

        Reservation r1 = service.reserve("order-1", 1001L, 3);
        Reservation r2 = service.reserve("order-1", 1001L, 3);

        assertEquals(r1.getId(), r2.getId());
        assertEquals(97, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(1, reservationRepository.count());
    }

    @Test
    void reserve_differentProductsForSameOrder_succeed() {
        inventoryRepository.save(Inventory.of(1001L, 10));
        inventoryRepository.save(Inventory.of(1002L, 10));

        service.reserve("order-1", 1001L, 1);
        service.reserve("order-1", 1002L, 1);

        assertEquals(2, reservationRepository.count());
        assertEquals(9, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(9, inventoryRepository.findById(1002L).orElseThrow().getQuantity());
    }

    @Test
    void reserve_throwsInsufficientStock_andRollsBackReservation() {
        inventoryRepository.save(Inventory.of(1001L, 2));

        assertThrows(InsufficientStockException.class,
                () -> service.reserve("order-1", 1001L, 5));

        assertEquals(2, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(0, reservationRepository.count());
    }

    @Test
    void reserve_throwsInsufficientStock_whenProductNotFound() {
        assertThrows(InsufficientStockException.class,
                () -> service.reserve("order-1", 9999L, 1));

        assertEquals(0, reservationRepository.count());
    }

    @Test
    void release_restoresQuantityAndTransitionsToReleased() {
        inventoryRepository.save(Inventory.of(1001L, 10));
        service.reserve("order-1", 1001L, 3);

        ReleaseOutcome outcome = service.release("order-1", 1001L);

        assertEquals(ReleaseOutcome.RELEASED, outcome);
        assertEquals(10, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        Reservation r = reservationRepository.findByOrderIdAndProductId("order-1", 1001L).orElseThrow();
        assertEquals(ReservationState.RELEASED, r.getState());
        assertNotNull(r.getReleasedAt());
    }

    @Test
    void release_isIdempotent() {
        inventoryRepository.save(Inventory.of(1001L, 10));
        service.reserve("order-1", 1001L, 3);
        service.release("order-1", 1001L);

        ReleaseOutcome outcome = service.release("order-1", 1001L);

        assertEquals(ReleaseOutcome.ALREADY_RELEASED, outcome);
        assertEquals(10, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
    }

    @Test
    void reserve_throwsOnQuantityDrift_idempotencyKeyPayloadMismatch() {
        inventoryRepository.save(Inventory.of(1001L, 100));
        service.reserve("order-1", 1001L, 3);

        ReservationConflictException ex = assertThrows(ReservationConflictException.class,
                () -> service.reserve("order-1", 1001L, 5));

        assertEquals(3, ex.getExistingQuantity());
        assertEquals(5, ex.getRequestedQuantity());
        // 첫 reserve의 차감만 반영, drift 시도는 차감 없음
        assertEquals(97, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(1, reservationRepository.count());
    }

    @Test
    void reserve_rejectsZeroQuantity() {
        inventoryRepository.save(Inventory.of(1001L, 100));

        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("order-1", 1001L, 0));

        assertEquals(100, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(0, reservationRepository.count());
    }

    @Test
    void reserve_rejectsNegativeQuantity_doesNotIncreaseInventory() {
        // 회귀 방어: 음수 quantity가 atomic UPDATE에서 재고를 증가시키는 것을 차단.
        inventoryRepository.save(Inventory.of(1001L, 100));

        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("order-1", 1001L, -5));

        assertEquals(100, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(0, reservationRepository.count());
    }

    @Test
    void reserve_rejectsZeroProductId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("order-1", 0L, 1));
    }

    @Test
    void reserve_rejectsNegativeProductId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("order-1", -1L, 1));
    }

    @Test
    void reserve_rejectsNullOrderId() {
        assertThrows(NullPointerException.class,
                () -> service.reserve(null, 1001L, 1));
    }

    @Test
    void release_rejectsNullOrderId() {
        assertThrows(NullPointerException.class,
                () -> service.release(null, 1001L));
    }

    @Test
    void release_rejectsZeroProductId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.release("order-1", 0L));
    }

    @Test
    void release_rejectsNegativeProductId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.release("order-1", -1L));
    }

    @Test
    void release_beforeReserve_createsTombstoneAndBlocksLateReserve() {
        inventoryRepository.save(Inventory.of(1001L, 10));

        ReleaseOutcome outcome = service.release("order-1", 1001L);
        assertEquals(ReleaseOutcome.TOMBSTONED, outcome);

        Reservation tomb = reservationRepository.findByOrderIdAndProductId("order-1", 1001L).orElseThrow();
        assertEquals(ReservationState.RELEASED, tomb.getState());
        assertEquals(0, tomb.getQuantity());

        assertThrows(AlreadyCompensatedException.class,
                () -> service.reserve("order-1", 1001L, 3));

        assertEquals(10, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
    }

    @Test
    void concurrentReserves_sufficientStock_allSucceed() throws Exception {
        inventoryRepository.save(Inventory.of(1001L, 1000));

        int taskCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                try {
                    start.await();
                    service.reserve("order-" + idx, 1001L, 1);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(100, successes.get());
        assertEquals(0, failures.get());
        assertEquals(900, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(100, reservationRepository.count());
    }

    @Test
    void concurrentReserves_limitedStock_partialSuccessExact() throws Exception {
        inventoryRepository.save(Inventory.of(1001L, 5));

        int taskCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                try {
                    start.await();
                    service.reserve("order-" + idx, 1001L, 1);
                    successes.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficient.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(5, successes.get());
        assertEquals(15, insufficient.get());
        assertEquals(0, other.get());
        assertEquals(0, inventoryRepository.findById(1001L).orElseThrow().getQuantity());
        assertEquals(5, reservationRepository.count());
    }
}
