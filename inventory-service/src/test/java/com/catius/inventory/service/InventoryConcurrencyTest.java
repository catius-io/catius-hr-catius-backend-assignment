package com.catius.inventory.service;

import com.catius.inventory.domain.Inventory;
import com.catius.inventory.domain.exception.InsufficientStockException;
import com.catius.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Inventory 동시성 통합 테스트 — 조건부 atomic UPDATE 가 재고 음수를 방지하는지 검증")
class InventoryConcurrencyTest {

    private static final Long PRODUCT_ID = 9001L;

    @Autowired
    private InventoryService service;

    @Autowired
    private InventoryRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @RepeatedTest(value = 10, name = "{displayName} [{currentRepetition}/{totalRepetitions}]")
    @DisplayName("재고 100 동시 100건 1개 차감 → 모두 성공 + stock 0")
    void reserve_동시_요청이_재고와_같으면_모두_성공한다() throws InterruptedException {
        repository.saveAndFlush(Inventory.create(PRODUCT_ID, 100));

        int threads = 100;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        runConcurrentReserve(threads, 1, success, failure);

        Inventory after = repository.findByProductId(PRODUCT_ID).orElseThrow();

        assertThat(after.getStock()).isZero();
        assertThat(after.getStock()).isGreaterThanOrEqualTo(0);

        assertThat(success.get()).isEqualTo(100);
        assertThat(failure.get()).isZero();
    }

    @RepeatedTest(value = 10, name = "{displayName} [{currentRepetition}/{totalRepetitions}]")
    @DisplayName("재고 10 동시 50건 1개 차감 → 10건 성공 + 40건 실패 + stock 0 (음수 X)")
    void reserve_재고초과_요청은_부족분만_실패하고_나머지는_성공한다() throws InterruptedException {
        repository.saveAndFlush(Inventory.create(PRODUCT_ID, 10));

        int threads = 50;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        runConcurrentReserve(threads, 1, success, failure);

        Inventory after = repository.findByProductId(PRODUCT_ID).orElseThrow();

        assertThat(after.getStock()).isZero();
        assertThat(after.getStock()).isGreaterThanOrEqualTo(0);

        assertThat(success.get()).isEqualTo(10);
        assertThat(failure.get()).isEqualTo(40);
    }

    private void runConcurrentReserve(int threads,
        int qtyPerThread,
        AtomicInteger success,
        AtomicInteger failure) throws InterruptedException {

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();

                        service.reserve(PRODUCT_ID, qtyPerThread);
                        success.incrementAndGet();

                    } catch (InsufficientStockException e) {
                        failure.incrementAndGet();

                    } catch (Throwable t) {
                        // atomic UPDATE 경로에서는 InsufficientStockException 외의 실패가 나오면 안 된다.
                        // 락 timeout / SQLITE_BUSY 등 인프라성 예외도 여기로 떨어지면 즉시 테스트 실패.
                        unexpected.add(t);

                    } finally {
                        done.countDown();
                    }
                });
            }

            // 모든 스레드 준비 완료 확인
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            assertThat(allReady)
                .as("모든 스레드가 준비되어야 함")
                .isTrue();

            // 동시에 시작
            start.countDown();

            // 모든 작업 완료 대기
            boolean finished = done.await(60, TimeUnit.SECONDS);
            assertThat(finished)
                .as("모든 동시 요청이 60초 안에 완료되어야 함")
                .isTrue();
        }

        assertThat(List.copyOf(unexpected))
            .as("예상 외 예외가 발생하면 atomic UPDATE 모델이 깨진 것 — %s", unexpected)
            .isEmpty();
    }
}
