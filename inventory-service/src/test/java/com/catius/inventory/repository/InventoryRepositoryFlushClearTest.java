package com.catius.inventory.repository;

import com.catius.inventory.domain.Inventory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Modifying(clearAutomatically=true, flushAutomatically=true) 가
 * 실제로 효과가 있음을 회귀 테스트로 못 박는다.
 *
 * 두 플래그를 떼면 이 테스트들은 빨간불이 되어야 한다 — 그게 정상.
 */
@SpringBootTest
@DisplayName("InventoryRepository — @Modifying 플래그 효과 검증 (regression guard)")
class InventoryRepositoryFlushClearTest {

    @Autowired
    private InventoryRepository repository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("[regression guard] clearAutomatically 를 끄면 같은 TX 의 재조회가 stale 1차 캐시를 본다")
    void clearAutomatically_효과_검증() {
        Long productId = 9100L;
        repository.saveAndFlush(Inventory.create(productId, 10));

        Integer reReadStock = tx.execute(status -> {
            // 1) 1차 캐시에 stock=10 적재
            Inventory loaded = repository.findByProductId(productId).orElseThrow();
            assertThat(loaded.getStock()).isEqualTo(10);

            // 2) bulk UPDATE: SQL 레벨에서 10 → 7
            int updated = repository.decreaseStock(productId, 3);
            assertThat(updated).isEqualTo(1);

            // 3) 같은 TX 에서 재조회 — clearAutomatically 가 캐시를 비웠는지 확인
            return repository.findByProductId(productId).orElseThrow().getStock();
        });

        // clearAutomatically=true  → 7 (캐시 비워져 DB 재조회)
        // clearAutomatically=false → 10 (1차 캐시 stale, 테스트 실패)
        assertThat(reReadStock)
                .as("clearAutomatically 가 꺼져 있으면 stale 캐시 hit 으로 10 이 보일 것")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("[regression guard] FlushMode=COMMIT 일 때 flushAutomatically 가 없으면 인메모리 더티가 bulk UPDATE 직전에 flush 되지 않는다")
    void flushAutomatically_효과_검증() {
        Long productId = 9101L;
        repository.saveAndFlush(Inventory.create(productId, 10));

        tx.executeWithoutResult(status -> {
            // Hibernate 기본 FlushMode.AUTO 는 쿼리 실행 전 자동 flush 를 해버려
            // flushAutomatically 의 효과를 가린다. 자동 flush 를 끄고 명시적 효과만 측정.
            em.setFlushMode(FlushModeType.COMMIT);

            Inventory loaded = repository.findByProductId(productId).orElseThrow();

            // 인메모리에서만 stock 10 → 8 (DB 는 아직 10)
            loaded.reserve(2);

            // bulk UPDATE 시 (FlushMode=COMMIT, 두 플래그 모두 꺼진 경우):
            //   1) flushAutomatically=false → 인메모리 더티(stock=8) 를 flush 하지 않음
            //   2) SQL UPDATE 가 DB 의 stock=10 을 보고 10 → 7 로 변경 (DB=7)
            //   3) clearAutomatically=false → 1차 캐시의 loaded 엔티티가 살아 있음
            //   4) TX 커밋 시 dirty check: snapshot=10, 현재=8 → "update set stock=8" 로 flush
            //   5) 결과: SQL bulk UPDATE 의 -3 차감이 통째로 사라지고 DB 에 8 이 남음 (데이터 손실)
            // 두 플래그가 모두 켜진 경우:
            //   1) flushAutomatically=true  → 인메모리 8 을 먼저 flush → DB=8
            //   2) SQL UPDATE: 8 → 5 (DB=5)
            //   3) clearAutomatically=true  → 1차 캐시 비워져 커밋 시 flush 할 dirty 가 없음
            //   4) 결과: 5 (정상)
            int updated = repository.decreaseStock(productId, 3);
            assertThat(updated).isEqualTo(1);
        });

        int finalStock = repository.findByProductId(productId).orElseThrow().getStock();

        // 플래그 ON  → 5
        // 플래그 OFF → 8 (bulk UPDATE 의 -3 이 dirty flush 에 의해 덮어써짐 — 가장 위험한 데이터 손실 모드)
        assertThat(finalStock)
                .as("플래그가 꺼져 있으면 dirty flush 가 bulk UPDATE 결과를 덮어써 8 이 남을 것")
                .isEqualTo(5);
    }
}