package com.catius.inventory.repository;

import com.catius.inventory.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByOrderIdAndProductId(String orderId, long productId);

    @Modifying
    @Query(value = """
            INSERT OR IGNORE INTO reservations (order_id, product_id, quantity, state, created_at, released_at)
            VALUES (:orderId, :productId, :quantity, :state, :createdAt, :releasedAt)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("orderId") String orderId,
                       @Param("productId") long productId,
                       @Param("quantity") int quantity,
                       @Param("state") String state,
                       @Param("createdAt") Instant createdAt,
                       @Param("releasedAt") Instant releasedAt);

    @Modifying
    @Query(value = """
            UPDATE reservations
               SET state = 'RELEASED',
                   released_at = :releasedAt
             WHERE order_id = :orderId
               AND product_id = :productId
               AND state = 'RESERVED'
            """, nativeQuery = true)
    int releaseIfReserved(@Param("orderId") String orderId,
                          @Param("productId") long productId,
                          @Param("releasedAt") Instant releasedAt);
}
