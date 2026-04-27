package com.catius.inventory.service

import com.catius.inventory.domain.InsufficientStockException
import com.catius.inventory.domain.Inventory
import com.catius.inventory.domain.MovementType
import com.catius.inventory.domain.StockMovement
import com.catius.inventory.domain.exception.InventoryNotFoundException
import com.catius.inventory.domain.port.InventoryRepository
import com.catius.inventory.domain.port.StockMovementRepository
import com.catius.inventory.domain.usecase.command.InventoryCommand
import com.catius.inventory.domain.usecase.command.ReserveResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InventoryServiceTest {

    private val now = Instant.parse("2026-04-26T00:00:00Z")
    private val fixedClock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var inventories: FakeInventoryRepository
    private lateinit var movements: FakeStockMovementRepository
    private lateinit var service: InventoryService

    @BeforeEach
    fun setup() {
        inventories = FakeInventoryRepository()
        movements = FakeStockMovementRepository()
        val applier = StockMovementApplier(inventories, movements)
        service = InventoryService(inventories, applier, fixedClock)
    }

    @Nested
    @DisplayName("reserve")
    inner class Reserve {
        @Test
        fun `정상 차감 — 잔여 재고 감소 + RESERVE movement 1건 기록`() {
            inventories.seed(Inventory(productId = 1001L, availableQuantity = 100, updatedAt = now.minusSeconds(10)))

            val result = service.reserve(
                InventoryCommand.ReserveCommand(
                    orderId = 1L,
                    items = listOf(InventoryCommand.Item(productId = 1001L, quantity = 30)),
                ),
            )

            assertThat(inventories.find(1001L)!!.availableQuantity).isEqualTo(70)
            assertThat(inventories.find(1001L)!!.updatedAt).isEqualTo(now)
            assertThat(movements.all()).hasSize(1)
            assertThat(movements.all().first().type).isEqualTo(MovementType.RESERVE)
            assertThat(movements.all().first().quantity).isEqualTo(30)
            assertThat(result.items).containsExactly(
                ReserveResult.Item(productId = 1001L, quantity = 30),
            )
        }

        @Test
        fun `다중 productId — 모두 차감되고 movement 도 모두 기록`() {
            inventories.seed(Inventory(1001L, 100, now))
            inventories.seed(Inventory(1002L, 50, now))

            service.reserve(
                InventoryCommand.ReserveCommand(
                    orderId = 1L,
                    items = listOf(
                        InventoryCommand.Item(1001L, 10),
                        InventoryCommand.Item(1002L, 5),
                    ),
                ),
            )

            assertThat(inventories.find(1001L)!!.availableQuantity).isEqualTo(90)
            assertThat(inventories.find(1002L)!!.availableQuantity).isEqualTo(45)
            assertThat(movements.all()).hasSize(2)
        }

        @Test
        fun `재고 부족 — InsufficientStockException 전파`() {
            inventories.seed(Inventory(1001L, 5, now))

            assertThatThrownBy {
                service.reserve(
                    InventoryCommand.ReserveCommand(
                        orderId = 1L,
                        items = listOf(InventoryCommand.Item(1001L, 10)),
                    ),
                )
            }.isInstanceOf(InsufficientStockException::class.java)
        }

        @Test
        fun `productId 가 없으면 InventoryNotFoundException`() {
            assertThatThrownBy {
                service.reserve(
                    InventoryCommand.ReserveCommand(
                        orderId = 1L,
                        items = listOf(InventoryCommand.Item(99999L, 1)),
                    ),
                )
            }.isInstanceOf(InventoryNotFoundException::class.java)
        }

        @Test
        fun `같은 (orderId, productId, RESERVE) 두 번 호출 — 두 번째는 멱등 (실제 차감 X)`() {
            inventories.seed(Inventory(1001L, 100, now))
            val cmd = InventoryCommand.ReserveCommand(
                orderId = 1L,
                items = listOf(InventoryCommand.Item(1001L, 30)),
            )

            service.reserve(cmd)
            service.reserve(cmd)

            assertThat(inventories.find(1001L)!!.availableQuantity).isEqualTo(70)
            assertThat(movements.all()).hasSize(1)
        }
    }

    @Nested
    @DisplayName("release")
    inner class Release {
        @Test
        fun `정상 복원 — 재고 증가 + RELEASE movement 1건 기록`() {
            inventories.seed(Inventory(1001L, 70, now.minusSeconds(10)))

            service.release(
                InventoryCommand.ReleaseCommand(
                    orderId = 1L,
                    items = listOf(InventoryCommand.Item(1001L, 30)),
                ),
            )

            assertThat(inventories.find(1001L)!!.availableQuantity).isEqualTo(100)
            assertThat(inventories.find(1001L)!!.updatedAt).isEqualTo(now)
            assertThat(movements.all()).hasSize(1)
            assertThat(movements.all().first().type).isEqualTo(MovementType.RELEASE)
        }

        @Test
        fun `같은 (orderId, productId, RELEASE) 두 번 호출 — 두 번째는 멱등`() {
            inventories.seed(Inventory(1001L, 70, now))
            val cmd = InventoryCommand.ReleaseCommand(
                orderId = 1L,
                items = listOf(InventoryCommand.Item(1001L, 30)),
            )

            service.release(cmd)
            service.release(cmd)

            assertThat(inventories.find(1001L)!!.availableQuantity).isEqualTo(100)
            assertThat(movements.all()).hasSize(1)
        }

        @Test
        fun `release 의 RESERVE movement 가 있어도 별개 — RELEASE 도 정상 기록`() {
            // 같은 orderId 의 RESERVE 가 이미 있는 상태에서 RELEASE 호출
            inventories.seed(Inventory(1001L, 70, now))
            service.reserve(
                InventoryCommand.ReserveCommand(
                    orderId = 1L,
                    items = listOf(InventoryCommand.Item(1001L, 30)),
                ),
            )
            // 재고: 70 → 40, RESERVE movement 1건

            service.release(
                InventoryCommand.ReleaseCommand(
                    orderId = 1L,
                    items = listOf(InventoryCommand.Item(1001L, 30)),
                ),
            )

            assertThat(inventories.find(1001L)!!.availableQuantity).isEqualTo(70)
            assertThat(movements.all()).hasSize(2)
            assertThat(movements.all().map { it.type })
                .containsExactlyInAnyOrder(MovementType.RESERVE, MovementType.RELEASE)
        }
    }

    @Nested
    @DisplayName("findByProductId")
    inner class FindByProductId {
        @Test
        fun `존재하면 InventoryResult 반환`() {
            inventories.seed(Inventory(1001L, 50, now))

            val result = service.findByProductId(1001L)

            assertThat(result.productId).isEqualTo(1001L)
            assertThat(result.availableQuantity).isEqualTo(50)
            assertThat(result.updatedAt).isEqualTo(now)
        }

        @Test
        fun `존재하지 않으면 InventoryNotFoundException`() {
            assertThatThrownBy { service.findByProductId(99999L) }
                .isInstanceOf(InventoryNotFoundException::class.java)
        }
    }

    private class FakeInventoryRepository : InventoryRepository {
        private val store = mutableMapOf<Long, Inventory>()

        override fun save(inventory: Inventory): Inventory {
            store[inventory.productId] = inventory
            return inventory
        }

        override fun findByProductId(productId: Long): Inventory? = store[productId]
        override fun findByProductIdForUpdate(productId: Long): Inventory? = store[productId]

        fun seed(inventory: Inventory) {
            store[inventory.productId] = inventory
        }

        fun find(productId: Long): Inventory? = store[productId]
    }

    private class FakeStockMovementRepository : StockMovementRepository {
        private val movements = mutableListOf<StockMovement>()
        private var nextId = 1L

        override fun save(movement: StockMovement): StockMovement {
            val withId = if (movement.id == null) movement.copy(id = nextId++) else movement
            movements.add(withId)
            return withId
        }

        override fun findByOrderIdAndProductIdAndType(
            orderId: Long,
            productId: Long,
            type: MovementType,
        ): StockMovement? = movements.firstOrNull {
            it.orderId == orderId && it.productId == productId && it.type == type
        }

        fun all(): List<StockMovement> = movements.toList()
    }
}
