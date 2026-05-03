package com.catius.inventory.service;

import com.catius.inventory.domain.Reservation;
import com.catius.inventory.domain.ReservationState;
import com.catius.inventory.repository.InventoryRepository;
import com.catius.inventory.repository.ReservationRepository;
import com.catius.inventory.service.exception.AlreadyCompensatedException;
import com.catius.inventory.service.exception.InsufficientStockException;
import com.catius.inventory.service.exception.ReservationConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * ADR-002 reserve 처리 순서를 단일 트랜잭션으로 orchestration.
 * claim → 충돌 검사 → atomic conditional UPDATE.
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public Reservation reserve(String orderId, long productId, int quantity) {
        // native SQL 경로는 도메인 팩토리를 우회하므로 service 경계에서 입력 검증 필수.
        // 미검증 시 음수 quantity가 inventory.quantity = quantity - :quantity 식에서 재고를 증가시킴.
        Objects.requireNonNull(orderId, "orderId");
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive: " + productId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }

        Instant now = Instant.now();
        int inserted = reservationRepository.insertIfAbsent(
                orderId, productId, quantity,
                ReservationState.RESERVED.name(), now, null);

        if (inserted == 0) {
            Reservation existing = reservationRepository.findByOrderIdAndProductId(orderId, productId)
                    .orElseThrow(() -> new IllegalStateException(
                            "unique conflict but row not found: orderId=" + orderId + ", productId=" + productId));
            if (existing.getState() == ReservationState.RELEASED) {
                throw new AlreadyCompensatedException(orderId, productId);
            }
            // 멱등 키(orderId, productId) 동일하지만 payload drift (quantity 다름) 차단.
            if (existing.getQuantity() != quantity) {
                throw new ReservationConflictException(
                        orderId, productId, existing.getQuantity(), quantity);
            }
            return existing;
        }

        int affected = inventoryRepository.decrementIfSufficient(productId, quantity);
        if (affected == 0) {
            throw new InsufficientStockException(productId, quantity);
        }

        return reservationRepository.findByOrderIdAndProductId(orderId, productId)
                .orElseThrow(() -> new IllegalStateException(
                        "reservation just inserted but not found: orderId=" + orderId + ", productId=" + productId));
    }

    @Transactional
    public ReleaseOutcome release(String orderId, long productId) {
        Objects.requireNonNull(orderId, "orderId");
        if (productId <= 0) {
            throw new IllegalArgumentException("productId must be positive: " + productId);
        }

        Optional<Reservation> existingOpt = reservationRepository.findByOrderIdAndProductId(orderId, productId);

        if (existingOpt.isEmpty()) {
            Instant now = Instant.now();
            int inserted = reservationRepository.insertIfAbsent(
                    orderId, productId, 0,
                    ReservationState.RELEASED.name(), now, now);
            if (inserted > 0) {
                return ReleaseOutcome.TOMBSTONED;
            }
            existingOpt = reservationRepository.findByOrderIdAndProductId(orderId, productId);
        }

        Reservation existing = existingOpt.orElseThrow(() -> new IllegalStateException(
                "race: insert conflict but row not found: orderId=" + orderId + ", productId=" + productId));

        // 동시 release race 방지: RESERVED → RELEASED 전이를 native 조건부 UPDATE로 원자화.
        // affected == 0 이면 이미 다른 트랜잭션이 release 했음 (idempotent no-op).
        int updated = reservationRepository.releaseIfReserved(orderId, productId, Instant.now());
        if (updated == 0) {
            return ReleaseOutcome.ALREADY_RELEASED;
        }

        inventoryRepository.increment(productId, existing.getQuantity());
        return ReleaseOutcome.RELEASED;
    }
}
