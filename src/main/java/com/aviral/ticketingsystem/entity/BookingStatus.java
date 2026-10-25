package com.aviral.ticketingsystem.entity;

/**
 * Lifecycle states for a Booking.
 *
 * State transitions:
 *
 *   [create reservation]
 *        │
 *        ▼
 *   PENDING_PAYMENT ──── [payment succeeds] ──► CONFIRMED
 *        │
 *        └─── [timer expires, no payment] ──► EXPIRED
 *        │
 *        └─── [payment rejected post-expiry] ──► CANCELLED
 *
 * Stored as a VARCHAR in the database (EnumType.STRING) so that the column
 * value is human-readable and adding new states never shifts ordinal positions
 * the way EnumType.ORDINAL would.
 */
public enum BookingStatus {

    /**
     * Seats are temporarily held. The user has a fixed window (e.g. 10 minutes)
     * to complete payment. During this window, reservedCount in Inventory is
     * incremented by the booking quantity.
     */
    PENDING_PAYMENT,

    /**
     * Payment was successful. Individual Ticket records have been created.
     * In Inventory: reservedCount decremented, soldCount incremented.
     */
    CONFIRMED,

    /**
     * The payment window closed before payment was received.
     * In Inventory: reservedCount decremented, returning seats to the pool.
     * Set by the expiry scheduler (Phase 4).
     */
    EXPIRED,

    /**
     * Booking was explicitly cancelled, or a late payment was attempted
     * on an already-expired reservation and rejected (Phase 5 edge case).
     */
    CANCELLED
}
