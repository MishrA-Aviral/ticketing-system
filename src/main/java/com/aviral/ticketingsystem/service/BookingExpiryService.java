package com.aviral.ticketingsystem.service;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.entity.Inventory;
import com.aviral.ticketingsystem.repository.BookingRepository;
import com.aviral.ticketingsystem.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles the expiry of individual PENDING_PAYMENT bookings.
 *
 * Lock acquisition order (must never be reversed to avoid deadlocks):
 *   1. Booking row  — via bookingRepository.findByIdWithLock()
 *   2. Inventory row — via inventoryRepository.findByEventIdWithLock()
 *
 * This order must be respected by every future flow (payment confirmation,
 * cancellation) that touches both rows in the same transaction.
 */
@Service
public class BookingExpiryService {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryService.class);

    private final BookingRepository bookingRepository;
    private final InventoryRepository inventoryRepository;

    public BookingExpiryService(BookingRepository bookingRepository,
                                InventoryRepository inventoryRepository) {
        this.bookingRepository = bookingRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Expires a single booking atomically if it is eligible.
     *
     * <p>Eligibility rules (all must hold):
     * <ol>
     *   <li>The booking exists.</li>
     *   <li>Its status is {@code PENDING_PAYMENT}.</li>
     *   <li>Its {@code expiresAt} field is not null.</li>
     *   <li>Its {@code expiresAt} timestamp is in the past (i.e., &le; now).</li>
     * </ol>
     *
     * <p>If any rule is not met the method returns without modifying the booking or
     * inventory. This is intentional — the same booking ID may be submitted multiple
     * times by the scheduler and it must be safe to do so.
     *
     * <p>The entire method runs inside a single database transaction. If any
     * database operation fails, both the inventory decrement and the status
     * update are rolled back together.
     *
     * @param bookingId the primary key of the booking to expire
     */
    @Transactional
    public void expireBooking(Long bookingId) {

        // ── Step 1: Lock the booking row ─────────────────────────────────────
        // findByIdWithLock issues SELECT ... FOR UPDATE on the bookings row.
        // No other transaction can modify this booking until our transaction commits.
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElse(null);

        if (booking == null) {
            log.warn("expireBooking called for non-existent booking id={}", bookingId);
            return;
        }

        // ── Step 2: Guard — status must be PENDING_PAYMENT ───────────────────
        // If another thread already expired or confirmed this booking, the lock
        // above ensures we see its committed status, so we exit safely.
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            log.debug("Booking id={} is already {} — skipping expiry.",
                    bookingId, booking.getStatus());
            return;
        }

        // ── Step 3: Guard — expiry deadline must be present and have passed ──
        // Check while holding the booking lock to avoid a TOCTOU race with the
        // payment-confirmation flow, which can confirm a booking right up to its
        // deadline.
        //
        // expiresAt is @Column(nullable = false) in the schema, but the Java field
        // carries no compile-time @NotNull guarantee. Defensively guard against null
        // rather than allow an accidental NullPointerException at runtime.
        if (booking.getExpiresAt() == null) {
            log.warn("Booking id={} has no expiresAt value — treating as ineligible for expiry.",
                    bookingId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (booking.getExpiresAt().isAfter(now)) {
            log.debug("Booking id={} has not expired yet (expiresAt={}, now={}) — skipping.",
                    bookingId, booking.getExpiresAt(), now);
            return;
        }

        // ── Step 4: Lock the inventory row ───────────────────────────────────
        // Lock order: booking first (done above), inventory second.
        // This order must be consistent with every other flow that touches both rows.
        Long eventId = booking.getEvent().getId();
        Inventory inventory = inventoryRepository.findByEventIdWithLock(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory not found for event id=" + eventId +
                        " (booking id=" + bookingId + "). " +
                        "This indicates a data integrity problem."));

        // ── Step 5: Release the reserved seats ───────────────────────────────
        // A PENDING_PAYMENT booking holds exactly booking.getQuantity() reserved seats.
        // Expiring returns them to the available pool by decrementing reservedCount.
        int currentReserved = inventory.getReservedCount();
        int quantity = booking.getQuantity();

        if (currentReserved < quantity) {
            // reservedCount is inconsistent with the booking quantity.
            // Fail loudly rather than producing a negative counter.
            throw new IllegalStateException(
                    "Cannot release " + quantity + " reserved seat(s) for event id=" + eventId +
                    " because reservedCount is only " + currentReserved +
                    " (booking id=" + bookingId + "). Data integrity violation.");
        }

        inventory.setReservedCount(currentReserved - quantity);

        // ── Step 6: Mark the booking as EXPIRED ──────────────────────────────
        booking.setStatus(BookingStatus.EXPIRED);

        // Both entities are managed by JPA (fetched within this transaction),
        // so Hibernate will flush the changes to the database automatically
        // when the transaction commits. No explicit save() call is required.

        log.info("Booking id={} expired: {} seat(s) released for event id={}.",
                bookingId, quantity, eventId);
    }
}
