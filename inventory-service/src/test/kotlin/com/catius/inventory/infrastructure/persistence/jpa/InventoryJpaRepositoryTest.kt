package com.catius.inventory.infrastructure.persistence.jpa

import com.catius.inventory.infrastructure.persistence.entity.InventoryEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.Instant

@DataJpaTest
class InventoryJpaRepositoryTest @Autowired constructor(
    private val repository: InventoryJpaRepository,
    private val em: TestEntityManager,
) {

    private val now = Instant.parse("2026-04-26T00:00:00Z")
    private val later = now.plusSeconds(60)

    private fun inventory(productId: Long = 1001L, available: Int = 100, at: Instant = now) =
        InventoryEntity(
            productId = productId,
            availableQuantity = available,
            updatedAt = at,
        )

    @Nested
    @DisplayName("save 와 findById")
    inner class SaveAndFind {
        @Test
        fun `productId 를 PK 로 저장하고 같은 id 로 조회된다`() {
            repository.save(inventory(productId = 1001L, available = 50))
            em.flush()
            em.clear()

            val found = repository.findById(1001L).orElseThrow()
            assertThat(found.productId).isEqualTo(1001L)
            assertThat(found.availableQuantity).isEqualTo(50)
            assertThat(found.updatedAt).isEqualTo(now)
        }

        @Test
        fun `존재하지 않는 productId 면 빈 Optional`() {
            assertThat(repository.findById(99999L)).isEmpty()
        }

        @Test
        fun `availableQuantity 와 updatedAt 변경 후 저장하면 갱신된다`() {
            repository.save(inventory(productId = 1001L, available = 50))
            em.flush()
            em.clear()

            val fetched = repository.findById(1001L).orElseThrow()
            fetched.availableQuantity = 30
            fetched.updatedAt = later
            repository.save(fetched)
            em.flush()
            em.clear()

            val refetched = repository.findById(1001L).orElseThrow()
            assertThat(refetched.availableQuantity).isEqualTo(30)
            assertThat(refetched.updatedAt).isEqualTo(later)
        }
    }

    @Nested
    @DisplayName("findByProductIdForUpdate (PESSIMISTIC_WRITE)")
    inner class FindForUpdate {
        @Test
        fun `존재하면 entity 를 반환한다`() {
            repository.save(inventory(productId = 1001L, available = 50))
            em.flush()
            em.clear()

            val locked = repository.findByProductIdForUpdate(1001L)
            assertThat(locked).isNotNull
            assertThat(locked!!.productId).isEqualTo(1001L)
            assertThat(locked.availableQuantity).isEqualTo(50)
        }

        @Test
        fun `존재하지 않으면 null`() {
            assertThat(repository.findByProductIdForUpdate(99999L)).isNull()
        }
    }
}
