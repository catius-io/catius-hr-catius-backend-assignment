package com.catius.inventory.repository;

import com.catius.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    /**
     * 재고가 quantity 이상일 때만 한 번의 UPDATE 로 차감한다.
     * WHERE 절의 stock >= :quantity 가 DB 엔진 안에서 원자적으로 평가되므로
     * 별도 락 없이도 음수 재고가 발생하지 않는다 (영향 행 0 == 재고 부족).
     * clearAutomatically: 벌크 업데이트로 1차 캐시의 엔티티가 stale 해지는 것을 방지.
     * flushAutomatically: 같은 트랜잭션 내 선행 변경을 먼저 flush 한 뒤 UPDATE 가 실행되도록.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Inventory i set i.stock = i.stock - :quantity " +
            "where i.productId = :productId and i.stock >= :quantity")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Inventory i set i.stock = i.stock + :quantity " +
            "where i.productId = :productId")
    int increaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}