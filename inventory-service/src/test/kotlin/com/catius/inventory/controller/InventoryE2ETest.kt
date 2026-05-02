package com.catius.inventory.controller

import com.catius.inventory.controller.dto.InventoryResponse
import com.catius.inventory.controller.dto.ReserveRequest
import com.catius.inventory.controller.dto.ReserveResponse
import com.catius.inventory.domain.Inventory
import com.catius.inventory.domain.port.InventoryRepository
import com.catius.inventory.infrastructure.persistence.jpa.InventoryJpaRepository
import com.catius.inventory.infrastructure.persistence.jpa.StockMovementJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryE2ETest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var inventoryJpaRepository: InventoryJpaRepository

    @Autowired
    private lateinit var stockMovementJpaRepository: StockMovementJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setup() {
        transactionTemplate.execute {
            stockMovementJpaRepository.deleteAll()
            inventoryJpaRepository.deleteAll()
            inventoryRepository.save(
                Inventory(productId = 1001L, availableQuantity = 100, updatedAt = Instant.now()),
            )
        }
    }

    @Nested
    @DisplayName("정상 흐름 및 멱등성 테스트")
    inner class NormalFlow {

        @Test
        @DisplayName("재고 예약 성공 및 멱등성 확인")
        fun reserve_success_and_idempotency() {
            val request = ReserveRequest(
                orderId = 100L,
                items = listOf(ReserveRequest.ItemRequest(productId = 1001L, quantity = 10)),
            )

            // 첫 번째 요청
            val response = restTemplate.postForEntity("/api/v1/inventory/reserve", request, ReserveResponse::class.java)
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.orderId).isEqualTo(100L)

            val afterFirst = inventoryRepository.findByProductId(1001L)
            assertThat(afterFirst?.availableQuantity).isEqualTo(90)

            // 두 번째 요청 (멱등성)
            val secondResponse = restTemplate.postForEntity("/api/v1/inventory/reserve", request, ReserveResponse::class.java)
            assertThat(secondResponse.statusCode).isEqualTo(HttpStatus.OK)

            val afterSecond = inventoryRepository.findByProductId(1001L)
            assertThat(afterSecond?.availableQuantity).isEqualTo(90) // 추가 차감 없어야 함
        }

        @Test
        @DisplayName("재고 부족 시 409 Conflict")
        fun insufficient_stock() {
            val request = ReserveRequest(
                orderId = 200L,
                items = listOf(ReserveRequest.ItemRequest(productId = 1001L, quantity = 101)),
            )

            val response = restTemplate.postForEntity("/api/v1/inventory/reserve", request, Map::class.java)
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        @DisplayName("재고 단건 조회")
        fun find_inventory() {
            val response = restTemplate.getForEntity("/api/v1/inventory/1001", InventoryResponse::class.java)
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.productId).isEqualTo(1001L)
            assertThat(response.body?.availableQuantity).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    inner class Concurrency {

        @Test
        @DisplayName("동시 차감 요청 시 비관적 락에 의해 정확한 재고가 남아야 하며 oversell이 없어야 한다")
        fun concurrent_reserve() {
            val productId = 1001L
            val initialStock = 100
            transactionTemplate.execute {
                inventoryRepository.save(Inventory(productId, initialStock, Instant.now()))
            }

            val threadCount = 20
            val quantityPerRequest = 1
            val executorService = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            for (i in 1..threadCount) {
                val orderId = i.toLong() + 1000L // 겹치지 않는 orderId
                executorService.execute {
                    try {
                        val request = ReserveRequest(
                            orderId = orderId,
                            items = listOf(ReserveRequest.ItemRequest(productId = productId, quantity = quantityPerRequest)),
                        )
                        val response = restTemplate.postForEntity("/api/v1/inventory/reserve", request, ReserveResponse::class.java)
                        if (response.statusCode == HttpStatus.OK) {
                            successCount.incrementAndGet()
                        } else {
                            failCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executorService.shutdown()

            val finalInventory = inventoryRepository.findByProductId(productId)
            assertThat(finalInventory?.availableQuantity).isEqualTo(initialStock - successCount.get())
            assertThat(successCount.get()).isEqualTo(threadCount)
            assertThat(failCount.get()).isEqualTo(0)
        }

        @Test
        @DisplayName("재고보다 많은 동시 요청 시 가용 재고만큼만 성공해야 한다")
        fun concurrent_reserve_oversell_prevention() {
            val productId = 1001L
            val initialStock = 5
            transactionTemplate.execute {
                inventoryRepository.save(Inventory(productId, initialStock, Instant.now()))
            }

            val threadCount = 10
            val quantityPerRequest = 1
            val executorService = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val conflictCount = AtomicInteger(0)

            for (i in 1..threadCount) {
                val orderId = i.toLong() + 2000L
                executorService.execute {
                    try {
                        val request = ReserveRequest(
                            orderId = orderId,
                            items = listOf(ReserveRequest.ItemRequest(productId = productId, quantity = quantityPerRequest)),
                        )
                        val response = restTemplate.postForEntity("/api/v1/inventory/reserve", request, Map::class.java)
                        if (response.statusCode == HttpStatus.OK) {
                            successCount.incrementAndGet()
                        } else if (response.statusCode == HttpStatus.CONFLICT) {
                            conflictCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executorService.shutdown()

            val finalInventory = inventoryRepository.findByProductId(productId)
            assertThat(finalInventory?.availableQuantity).isEqualTo(0)
            assertThat(successCount.get()).isEqualTo(initialStock)
            assertThat(conflictCount.get()).isEqualTo(threadCount - initialStock)
        }
    }
}
