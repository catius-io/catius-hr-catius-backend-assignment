package com.catius.inventory.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationTest {

    @Test
    void reserved_createsReservedState() {
        Reservation r = Reservation.reserved("order-1", 1001L, 3);

        assertEquals("order-1", r.getOrderId());
        assertEquals(1001L, r.getProductId());
        assertEquals(3, r.getQuantity());
        assertEquals(ReservationState.RESERVED, r.getState());
        assertNotNull(r.getCreatedAt());
        assertNull(r.getReleasedAt());
    }

    @Test
    void reserved_rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                Reservation.reserved("order-1", 1001L, 0));
    }

    @Test
    void reserved_rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                Reservation.reserved("order-1", 1001L, -1));
    }

    @Test
    void reserved_rejectsNullOrderId() {
        assertThrows(NullPointerException.class, () ->
                Reservation.reserved(null, 1001L, 1));
    }

    @Test
    void reserved_rejectsZeroProductId() {
        assertThrows(IllegalArgumentException.class, () ->
                Reservation.reserved("order-1", 0L, 1));
    }

    @Test
    void reserved_rejectsNegativeProductId() {
        assertThrows(IllegalArgumentException.class, () ->
                Reservation.reserved("order-1", -1L, 1));
    }

    @Test
    void tombstone_rejectsNullOrderId() {
        assertThrows(NullPointerException.class, () ->
                Reservation.tombstone(null, 1001L));
    }

    @Test
    void tombstone_rejectsZeroProductId() {
        assertThrows(IllegalArgumentException.class, () ->
                Reservation.tombstone("order-1", 0L));
    }

    @Test
    void tombstone_rejectsNegativeProductId() {
        assertThrows(IllegalArgumentException.class, () ->
                Reservation.tombstone("order-1", -1L));
    }

    @Test
    void tombstone_createsReleasedStateWithZeroQuantity() {
        Reservation r = Reservation.tombstone("order-1", 1001L);

        assertEquals(ReservationState.RELEASED, r.getState());
        assertEquals(0, r.getQuantity());
        assertNotNull(r.getReleasedAt());
    }

    @Test
    void release_transitionsFromReservedToReleased() {
        Reservation r = Reservation.reserved("order-1", 1001L, 3);

        boolean changed = r.release();

        assertTrue(changed);
        assertEquals(ReservationState.RELEASED, r.getState());
        assertNotNull(r.getReleasedAt());
    }

    @Test
    void release_isIdempotentWhenAlreadyReleased() {
        Reservation r = Reservation.reserved("order-1", 1001L, 3);
        r.release();
        var firstReleasedAt = r.getReleasedAt();

        boolean changed = r.release();

        assertFalse(changed);
        assertEquals(ReservationState.RELEASED, r.getState());
        assertEquals(firstReleasedAt, r.getReleasedAt());
    }

    @Test
    void release_isIdempotentOnTombstone() {
        Reservation tombstone = Reservation.tombstone("order-1", 1001L);

        boolean changed = tombstone.release();

        assertFalse(changed);
        assertEquals(ReservationState.RELEASED, tombstone.getState());
    }
}
