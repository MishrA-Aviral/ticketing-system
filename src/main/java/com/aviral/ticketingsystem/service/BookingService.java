package com.aviral.ticketingsystem.service;

import com.aviral.ticketingsystem.dto.BookingConfirmationResponse;
import com.aviral.ticketingsystem.dto.BookingRequest;
import com.aviral.ticketingsystem.dto.BookingResponse;
import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.entity.Inventory;
import com.aviral.ticketingsystem.entity.Ticket;
import com.aviral.ticketingsystem.entity.User;
import com.aviral.ticketingsystem.exception.BookingConflictException;
import com.aviral.ticketingsystem.exception.BookingNotFoundException;
import com.aviral.ticketingsystem.exception.EventNotFoundException;
import com.aviral.ticketingsystem.exception.InsufficientInventoryException;
import com.aviral.ticketingsystem.exception.UserNotFoundException;
import com.aviral.ticketingsystem.repository.BookingRepository;
import com.aviral.ticketingsystem.repository.EventRepository;
import com.aviral.ticketingsystem.repository.InventoryRepository;
import com.aviral.ticketingsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service orchestrating transactional ticket booking creation with safe inventory reservation.
 *
 * <p>Concurrency & Integrity guarantees:
 * <ul>
 *   <li>Pessimistic write lock on the target event's {@link Inventory} row via
 *       {@code inventoryRepository.findByEventIdWithLock(eventId)} prevents concurrent
 *       transactions from overselling the same inventory capacity.</li>
 *   <li>The entire reservation operation runs inside a Spring-managed {@code @Transactional} boundary;
 *       any failure rolls back both the inventory reservation and the booking creation.</li>
 *   <li>Calculates total amounts server-side using the authoritative {@link Event#getTicketPrice()}
 *       and immutable {@link BigDecimal} arithmetic.</li>
 * </ul>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** Default payment window duration in minutes. */
    public static final int DEFAULT_PAYMENT_WINDOW_MINUTES = 10;

    private final BookingRepository bookingRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final int paymentWindowMinutes;

    public BookingService(BookingRepository bookingRepository,
                          InventoryRepository inventoryRepository,
                          UserRepository userRepository,
                          EventRepository eventRepository,
                          @Value("${booking.payment-window-minutes:10}") int paymentWindowMinutes) {
        this.bookingRepository = bookingRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.paymentWindowMinutes = paymentWindowMinutes > 0 ? paymentWindowMinutes : DEFAULT_PAYMENT_WINDOW_MINUTES;
    }

    /**
     * Atomically reserves event inventory and creates a {@link BookingStatus#PENDING_PAYMENT} booking.
     *
     * @param request the validated booking request parameters
     * @return populated {@link BookingResponse} DTO
     * @throws IllegalArgumentException if request values are invalid
     * @throws UserNotFoundException if the specified user does not exist
     * @throws EventNotFoundException if the specified event does not exist
     * @throws InsufficientInventoryException if requested quantity exceeds available capacity
     * @throws IllegalStateException if event has invalid pricing or missing inventory configuration
     */
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        // ── Step 1: Validate request input ───────────────────────────────────
        if (request == null) {
            throw new IllegalArgumentException("Booking request must not be null");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
        if (request.eventId() == null || request.eventId() <= 0) {
            throw new IllegalArgumentException("Event ID must be a positive number");
        }
        if (request.quantity() == null || request.quantity() < 1 || request.quantity() > 6) {
            throw new IllegalArgumentException("Quantity must be between 1 and 6 tickets per booking");
        }

        // ── Step 2: Validate User existence ──────────────────────────────────
        // Validate user before locking inventory to avoid holding locks if user is missing.
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id=" + request.userId()));

        // ── Step 3: Find and lock inventory ──────────────────────────────────
        // Acquire pessimistic write lock (SELECT ... FOR UPDATE) on the inventory row.
        // Serializes concurrent reservations for this specific event.
        Inventory inventory = inventoryRepository.findByEventIdWithLock(request.eventId())
                .orElseThrow(() -> {
                    if (!eventRepository.existsById(request.eventId())) {
                        return new EventNotFoundException("Event not found with id=" + request.eventId());
                    }
                    return new IllegalStateException("Inventory not found for event id=" + request.eventId());
                });

        // ── Step 4: Calculate available inventory ────────────────────────────
        int availableCount = inventory.getAvailableCount();
        if (request.quantity() > availableCount) {
            log.warn("Insufficient inventory for event id={}: requested={}, available={}",
                    request.eventId(), request.quantity(), availableCount);
            throw new InsufficientInventoryException(
                    "Insufficient inventory for event id=" + request.eventId() +
                    ". Requested: " + request.quantity() + ", available: " + availableCount);
        }

        // ── Step 5: Authoritative price & total amount calculation ───────────
        Event event = inventory.getEvent();
        BigDecimal ticketPrice = event.getTicketPrice();
        if (ticketPrice == null || ticketPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Event id=" + event.getId() + " has an invalid ticket price: " + ticketPrice);
        }
        BigDecimal totalAmount = ticketPrice.multiply(BigDecimal.valueOf(request.quantity()));

        // ── Step 6: Reserve inventory ────────────────────────────────────────
        // Increment reservedCount; soldCount is untouched.
        inventory.setReservedCount(inventory.getReservedCount() + request.quantity());
        inventoryRepository.save(inventory);

        // ── Step 7: Create the pending booking ───────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(paymentWindowMinutes);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setQuantity(request.quantity());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(totalAmount);
        booking.setExpiresAt(expiresAt);

        Booking savedBooking = bookingRepository.save(booking);

        log.info("Booking created: id={}, eventId={}, userId={}, quantity={}, totalAmount={}, expiresAt={}",
                savedBooking.getId(), event.getId(), user.getId(),
                request.quantity(), totalAmount, expiresAt);

        // ── Step 8: Return response DTO ──────────────────────────────────────
        return BookingResponse.fromEntity(savedBooking);
    }

    /**
     * Confirms a PENDING_PAYMENT booking, simulating a successful payment.
     *
     * <p>Lock acquisition order (must match {@link BookingExpiryService} to prevent deadlocks):
     * <ol>
     *   <li>Booking row  — via {@code bookingRepository.findByIdWithLock()}</li>
     *   <li>Inventory row — via {@code inventoryRepository.findByEventIdWithLock()}</li>
     * </ol>
     *
     * <p>Within a single transaction:
     * <ul>
     *   <li>Decrements {@code reservedCount} by {@code booking.quantity}</li>
     *   <li>Increments {@code soldCount} by {@code booking.quantity}</li>
     *   <li>Sets {@code status = CONFIRMED} and {@code confirmedAt}</li>
     *   <li>Creates exactly {@code quantity} {@link Ticket} records via cascade</li>
     * </ul>
     *
     * <p>Idempotent for already-CONFIRMED bookings: returns the existing confirmation
     * without touching inventory or creating duplicate tickets.
     *
     * @param bookingId the primary key of the booking to confirm
     * @return {@link BookingConfirmationResponse} with the confirmed booking and issued tickets
     * @throws IllegalArgumentException    if {@code bookingId} is not positive
     * @throws BookingNotFoundException    if no booking exists with this ID (HTTP 404)
     * @throws BookingConflictException    if the booking status cannot be confirmed, or
     *                                     its payment deadline has passed (HTTP 409)
     * @throws IllegalStateException       if inventory is in an inconsistent state
     */
    @Transactional
    public BookingConfirmationResponse confirmBooking(Long bookingId) {

        // ── Step 1: Validate bookingId ────────────────────────────────────────
        if (bookingId == null || bookingId <= 0) {
            throw new IllegalArgumentException("Booking ID must be a positive number");
        }

        // ── Step 2: Lock the booking row ──────────────────────────────────────
        // findByIdWithLock issues SELECT ... FOR UPDATE on the bookings row.
        // This is the FIRST lock acquired — consistent with BookingExpiryService.
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(
                        "Booking not found with id=" + bookingId));

        // ── Step 3: Idempotency guard — already CONFIRMED ─────────────────────
        // Return the current state without touching inventory or creating more tickets.
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            log.debug("Booking id={} is already CONFIRMED — returning existing details.", bookingId);
            return BookingConfirmationResponse.fromEntity(booking);
        }

        // ── Step 4: Status guard — must be PENDING_PAYMENT ───────────────────
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BookingConflictException(
                    "Cannot confirm booking id=" + bookingId +
                    " with status " + booking.getStatus() +
                    ". Only PENDING_PAYMENT bookings may be confirmed.");
        }

        // ── Step 5: Deadline guard ────────────────────────────────────────────
        // The check runs while holding the booking lock to eliminate the TOCTOU
        // window between the expiry scheduler and this flow.
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(booking.getExpiresAt())) {
            throw new BookingConflictException(
                    "Booking id=" + bookingId +
                    " cannot be confirmed because its payment deadline has passed (expiresAt=" +
                    booking.getExpiresAt() + ").");
        }

        // ── Step 6: Lock the inventory row ────────────────────────────────────
        // Lock order: booking first (done above), inventory second.
        // Must remain consistent with BookingExpiryService to avoid deadlocks.
        Long eventId = booking.getEvent().getId();
        Inventory inventory = inventoryRepository.findByEventIdWithLock(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory not found for event id=" + eventId +
                        " (booking id=" + bookingId + "). This indicates a data integrity problem."));

        // ── Step 7: Invariant check ───────────────────────────────────────────
        int quantity = booking.getQuantity();
        int currentReserved = inventory.getReservedCount();
        if (currentReserved < quantity) {
            throw new IllegalStateException(
                    "Cannot confirm booking id=" + bookingId + ": reservedCount (" +
                    currentReserved + ") is less than booking quantity (" + quantity +
                    ") for event id=" + eventId + ". Data integrity violation.");
        }

        // ── Step 8: Transition inventory counters ─────────────────────────────
        // Reserved seat → sold seat: sum (reserved + sold) is unchanged, so the
        // DB check constraint (reserved_count + sold_count <= total_capacity) is safe.
        inventory.setReservedCount(currentReserved - quantity);
        inventory.setSoldCount(inventory.getSoldCount() + quantity);

        // ── Step 9: Confirm the booking ───────────────────────────────────────
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);

        // ── Step 10: Generate tickets ─────────────────────────────────────────
        // Ticket number scheme: "TKT-{bookingId}-{seq}" where seq is 1-indexed.
        // Maximum format: "TKT-" (4) + 12-digit Long (12) + "-" (1) + 1 digit (1) = 18 chars max.
        // The column allows 20 chars; bookingId up to 9,999,999,999,999 and quantity up to 6
        // guarantees this stays within 20 chars. All combinations are unique by construction.
        for (int seq = 1; seq <= quantity; seq++) {
            String ticketNumber = "TKT-" + bookingId + "-" + seq;
            Ticket ticket = new Ticket(booking, ticketNumber, now);
            booking.getTickets().add(ticket);
        }

        // Booking and Ticket are managed entities within this transaction.
        // Cascade ALL on the tickets collection persists tickets automatically on flush.
        // No explicit save() calls required for either.

        log.info("Booking id={} confirmed: {} ticket(s) issued for event id={}.",
                bookingId, quantity, eventId);

        return BookingConfirmationResponse.fromEntity(booking);
    }
}
