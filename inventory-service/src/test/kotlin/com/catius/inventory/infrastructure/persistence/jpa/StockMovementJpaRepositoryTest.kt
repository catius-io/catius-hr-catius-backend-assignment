package com.catius.inventory.infrastructure.persistence.jpa

import com.catius.inventory.domain.MovementType
import com.catius.inventory.infrastructure.persistence.entity.StockMovementEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

@DataJpaTest
class StockMovementJpaRepositoryTest @Autowired constructor(
    private val repository: StockMovementJpaRepository,
    private val em: TestEntityManager,
) {

    private val now = Instant.parse("2026-04-26T00:00:00Z")

    private fun movement(
        orderId: Long = 1L,
        productId: Long = 1001L,
        quantity: Int = 2,
        type: MovementType = MovementType.RESERVE,
        at: Instant = now,
    ) = StockMovementEntity(
        orderId = orderId,
        productId = productId,
        quantity = quantity,
        type = type,
        createdAt = at,
    )

    @Nested
    @DisplayName("저장")
    inner class Save {
        @Test
        fun `새 movement 를 저장하면 id 가 부여된다`() {
            val saved = repository.save(movement())
            em.flush()

            assertThat(saved.id).isNotNull()
        }

        @Test
        fun `같은 orderId 라도 type 이 다르면 둘 다 저장된다 (RESERVE 와 RELEASE)`() {
            repository.save(movement(orderId = 1L, type = MovementType.RESERVE))
            repository.save(movement(orderId = 1L, type = MovementType.RELEASE))
            em.flush()

            assertThat(repository.findAll()).hasSize(2)
        }

        @Test
        fun `같은 orderId 라도 productId 가 다르면 둘 다 저장된다`() {
            repository.save(movement(orderId = 1L, productId = 1001L))
            repository.save(movement(orderId = 1L, productId = 1002L))
            em.flush()

            assertThat(repository.findAll()).hasSize(2)
        }
    }

    @Nested
    @DisplayName("UNIQUE 제약 (orderId, productId, type) — 멱등성 보장")
    inner class Idempotency {
        @Test
        fun `같은 (orderId, productId, type) 조합을 두 번 저장하면 DataIntegrityViolationException`() {
            repository.save(movement(orderId = 1L, productId = 1001L, type = MovementType.RESERVE))
            em.flush()

            assertThatThrownBy {
                repository.save(
                    movement(orderId = 1L, productId = 1001L, type = MovementType.RESERVE),
                )
                em.flush()
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("findByOrderIdAndProductIdAndType (멱등성 사전 조회)")
    inner class FindByKey {
        @Test
        fun `매칭되는 movement 가 있으면 반환`() {
            repository.save(
                movement(orderId = 42L, productId = 1001L, quantity = 3, type = MovementType.RESERVE),
            )
            em.flush()
            em.clear()

            val found = repository.findByOrderIdAndProductIdAndType(
                orderId = 42L,
                productId = 1001L,
                type = MovementType.RESERVE,
            )

            assertThat(found).isNotNull
            assertThat(found!!.quantity).isEqualTo(3)
        }

        @Test
        fun `type 만 다르면 null 반환`() {
            repository.save(
                movement(orderId = 42L, productId = 1001L, type = MovementType.RESERVE),
            )
            em.flush()
            em.clear()

            val found = repository.findByOrderIdAndProductIdAndType(
                orderId = 42L,
                productId = 1001L,
                type = MovementType.RELEASE,
            )

            assertThat(found).isNull()
        }

        @Test
        fun `매칭 없으면 null`() {
            val found = repository.findByOrderIdAndProductIdAndType(
                orderId = 99L,
                productId = 99L,
                type = MovementType.RESERVE,
            )
            assertThat(found).isNull()
        }
    }
}
