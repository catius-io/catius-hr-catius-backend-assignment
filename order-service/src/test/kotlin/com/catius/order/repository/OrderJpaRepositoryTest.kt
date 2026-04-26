package com.catius.order.repository

import com.catius.order.domain.OrderStatus
import com.catius.order.repository.entity.OrderEntity
import com.catius.order.repository.entity.OrderItemEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.Instant

@DataJpaTest
class OrderJpaRepositoryTest @Autowired constructor(
    private val repository: OrderJpaRepository,
    private val em: TestEntityManager,
) {

    private val now = Instant.parse("2026-04-26T00:00:00Z")
    private val later = now.plusSeconds(60)

    private fun newOrder(
        customerId: Long = 100L,
        status: OrderStatus = OrderStatus.PENDING,
        items: List<Pair<Long, Int>> = listOf(1001L to 2),
        createdAt: Instant = now,
        updatedAt: Instant = now,
    ) = OrderEntity(
        customerId = customerId,
        status = status,
        items = items.map { (pid, qty) ->
            OrderItemEntity(productId = pid, quantity = qty)
        }.toMutableList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Nested
    @DisplayName("save (insert)")
    inner class SaveInsert {
        @Test
        fun `새 주문을 저장하면 id 가 부여되고 items 도 함께 저장된다`() {
            val entity = newOrder(items = listOf(1001L to 2, 1002L to 3))

            val saved = repository.save(entity)
            em.flush()

            assertThat(saved.id).isNotNull()
            assertThat(saved.items).hasSize(2)
            assertThat(saved.items.map { it.id }).allMatch { it != null }
        }
    }

    @Nested
    @DisplayName("findById")
    inner class FindById {
        @Test
        fun `저장 후 동일 id 로 조회하면 모든 필드가 복원된다`() {
            val saved = repository.save(newOrder(items = listOf(1001L to 2)))
            em.flush()
            em.clear()

            val found = repository.findById(saved.id!!).orElseThrow()

            assertThat(found.id).isEqualTo(saved.id)
            assertThat(found.customerId).isEqualTo(100L)
            assertThat(found.status).isEqualTo(OrderStatus.PENDING)
            assertThat(found.items).hasSize(1)
            assertThat(found.items.first().productId).isEqualTo(1001L)
            assertThat(found.items.first().quantity).isEqualTo(2)
            assertThat(found.createdAt).isEqualTo(now)
            assertThat(found.updatedAt).isEqualTo(now)
        }

        @Test
        fun `존재하지 않는 id 면 빈 Optional`() {
            val result = repository.findById(99999L)
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {
        @Test
        fun `status 와 updatedAt 변경 후 저장하면 갱신되어 다음 조회에서 보인다`() {
            val saved = repository.save(newOrder())
            em.flush()
            em.clear()

            val fetched = repository.findById(saved.id!!).orElseThrow()
            fetched.status = OrderStatus.CONFIRMED
            fetched.updatedAt = later
            repository.save(fetched)
            em.flush()
            em.clear()

            val refetched = repository.findById(saved.id!!).orElseThrow()
            assertThat(refetched.status).isEqualTo(OrderStatus.CONFIRMED)
            assertThat(refetched.updatedAt).isEqualTo(later)
            // 변경하지 않은 필드는 유지
            assertThat(refetched.customerId).isEqualTo(100L)
            assertThat(refetched.createdAt).isEqualTo(now)
            assertThat(refetched.items).hasSize(1)
        }
    }

    @Nested
    @DisplayName("OneToMany 연관관계")
    inner class Association {
        @Test
        fun `여러 items 가 부모 order 의 id 를 FK 로 갖고 함께 저장된다`() {
            val saved = repository.save(
                newOrder(items = listOf(1001L to 2, 1002L to 3, 1003L to 1)),
            )
            em.flush()
            em.clear()

            val found = repository.findById(saved.id!!).orElseThrow()
            assertThat(found.items).hasSize(3)
            assertThat(found.items.map { it.productId to it.quantity })
                .containsExactlyInAnyOrder(
                    1001L to 2,
                    1002L to 3,
                    1003L to 1,
                )
        }

        @Test
        fun `orphanRemoval=true 로 items 컬렉션에서 제거하면 DB 에서도 삭제된다`() {
            val saved = repository.save(
                newOrder(items = listOf(1001L to 2, 1002L to 1)),
            )
            em.flush()
            em.clear()

            val fetched = repository.findById(saved.id!!).orElseThrow()
            fetched.items.removeIf { it.productId == 1001L }
            repository.save(fetched)
            em.flush()
            em.clear()

            val refetched = repository.findById(saved.id!!).orElseThrow()
            assertThat(refetched.items).hasSize(1)
            assertThat(refetched.items.first().productId).isEqualTo(1002L)
        }
    }

    @Nested
    @DisplayName("delete")
    inner class Delete {
        @Test
        fun `삭제 후 조회하면 비어있고 cascade 로 items 도 함께 사라진다`() {
            val saved = repository.save(newOrder(items = listOf(1001L to 2)))
            em.flush()
            em.clear()

            repository.deleteById(saved.id!!)
            em.flush()

            assertThat(repository.findById(saved.id!!)).isEmpty()
        }
    }
}
