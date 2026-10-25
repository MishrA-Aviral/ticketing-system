package com.aviral.ticketingsystem.service;

import com.aviral.ticketingsystem.dto.BookingConfirmationResponse;
import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.entity.Inventory;
import com.aviral.ticketingsystem.entity.User;
import com.aviral.ticketingsystem.exception.BookingConflictException;
import com.aviral.ticketingsystem.exception.BookingNotFoundException;
import com.aviral.ticketingsystem.repository.BookingRepository;
import com.aviral.ticketingsystem.repository.EventRepository;
import com.aviral.ticketingsystem.repository.InventoryRepository;
import com.aviral.ticketingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookingService#confirmBooking(Long)}.
 *
 * <p>Uses Mockito to isolate the service from the database. Tests verify:
 * <ul>
 *   <li>All success-path state transitions (inventory counters, status, confirmedAt, tickets)</li>
 *   <li>Idempotency on repeated confirmation</li>
 *   <li>Guard conditions (bad ID, missing booking, wrong status, expired deadline)</li>
 *   <li>Booking lock acquired before inventory lock</li>
 * </ul>
 *
 * <p>These tests do not verify @Transactional proxy behavior, PostgreSQL row locking,
 * or concurrent-execution safety. Those properties require integration tests against
 * a real database.
 */
@ExtendWith(MockitoExtension.class)
class BookingConfirmationServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;

    private BookingService bookingService;

    private static final Long BOOKING_ID = 55L;
    private static final Long EVENT_ID   = 42L;
    private static final Long USER_ID    = 7L;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                inventoryRepository,
                userRepository,
                eventRepository,
                10
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a Booking that will reach the inventory lock step.
     * Event.getId() is stubbed via spy so Mockito strict mode does not complain.
     */
    private Booking buildBooking(BookingStatus status, LocalDateTime expiresAt, int quantity) {
        Event event = spy(new Event(
                "Test Concert", "Test Venue", "desc",
                LocalDateTime.of(2027, 6, 1, 19, 0),
                new BigDecimal("100.00")
        ));
        doReturn(EVENT_ID).when(event).getId();

        User user = new User("Test User", "test@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", BOOKING_ID);
        ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.now().minusMinutes(5));
        booking.setEvent(event);
        booking.setUser(user);
        booking.setStatus(status);
        booking.setExpiresAt(expiresAt);
        booking.setQuantity(quantity);
        booking.setTotalAmount(new BigDecimal("100.00").multiply(BigDecimal.valueOf(quantity)));
        return booking;
    }

    /**
     * For tests that return early before event.getId() is called.
     * Uses a plain Event (no spy) to satisfy Mockito strict stub verification.
     */
    private Booking buildBookingNoEventStub(BookingStatus status, LocalDateTime expiresAt) {
        Event event = new Event(
                "Test Concert", "Test Venue", "desc",
                LocalDateTime.of(2027, 6, 1, 19, 0),
                new BigDecimal("100.00")
        );
        User user = new User("Test User", "test@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", BOOKING_ID);
        ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.now().minusMinutes(5));
        booking.setEvent(event);
        booking.setUser(user);
        booking.setStatus(status);
        booking.setExpiresAt(expiresAt);
        booking.setQuantity(2);
        booking.setTotalAmount(new BigDecimal("200.00"));
        return booking;
    }

    private Inventory buildInventory(int reserved, int sold) {
        Event event = new Event(
                "Test Concert", "Test Venue", "desc",
                LocalDateTime.of(2027, 6, 1, 19, 0),
                new BigDecimal("100.00")
        );
        Inventory inv = new Inventory(event, 100);
        inv.setReservedCount(reserved);
        inv.setSoldCount(sold);
        return inv;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. Happy path: PENDING_PAYMENT booking within deadline is confirmed")
    void confirmBooking_WhenPendingAndWithinDeadline_Confirms() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), 2);
        Inventory inventory = buildInventory(10, 3);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        BookingConfirmationResponse response = bookingService.confirmBooking(BOOKING_ID);

        assertThat(response).isNotNull();
        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("2. reservedCount is decremented and soldCount is incremented by quantity")
    void confirmBooking_CorrectInventoryCounters() {
        int quantity = 3;
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), quantity);
        Inventory inventory = buildInventory(10, 5);  // reserved=10, sold=5

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        bookingService.confirmBooking(BOOKING_ID);

        assertThat(inventory.getReservedCount()).isEqualTo(10 - quantity); // 7
        assertThat(inventory.getSoldCount()).isEqualTo(5 + quantity);       // 8
    }

    @Test
    @DisplayName("3. Exactly quantity tickets are created with unique, valid ticket numbers")
    void confirmBooking_ExactlyQuantityTicketsCreated() {
        int quantity = 3;
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), quantity);
        Inventory inventory = buildInventory(10, 0);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        bookingService.confirmBooking(BOOKING_ID);

        assertThat(booking.getTickets()).hasSize(quantity);
        // Ticket numbers are unique and within the 20-char column limit.
        assertThat(booking.getTickets()).extracting(t -> t.getTicketNumber())
                .doesNotHaveDuplicates()
                .allMatch(tn -> tn.length() <= 20);
        // Deterministic: TKT-{bookingId}-{seq}
        assertThat(booking.getTickets()).extracting(t -> t.getTicketNumber())
                .containsExactlyInAnyOrder(
                        "TKT-" + BOOKING_ID + "-1",
                        "TKT-" + BOOKING_ID + "-2",
                        "TKT-" + BOOKING_ID + "-3"
                );
    }

    @Test
    @DisplayName("4. All issued tickets have non-null issuedAt equal to confirmedAt")
    void confirmBooking_TicketsHaveIssuedAt() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), 2);
        Inventory inventory = buildInventory(5, 0);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        BookingConfirmationResponse response = bookingService.confirmBooking(BOOKING_ID);

        LocalDateTime confirmedAt = response.confirmedAt();
        assertThat(booking.getTickets())
                .allMatch(t -> t.getIssuedAt() != null)
                .allMatch(t -> !t.getIssuedAt().isAfter(confirmedAt));
    }

    @Test
    @DisplayName("5. Repeated confirmation returns existing details — inventory unchanged, no extra tickets")
    void confirmBooking_Idempotent_WhenAlreadyConfirmed() {
        // Build a booking that is already CONFIRMED with 2 existing tickets.
        Booking booking = buildBookingNoEventStub(
                BookingStatus.CONFIRMED, LocalDateTime.now().plusMinutes(5));
        // Pre-populate tickets to simulate already-confirmed state.
        com.aviral.ticketingsystem.entity.Ticket existing1 =
                new com.aviral.ticketingsystem.entity.Ticket(booking, "TKT-" + BOOKING_ID + "-1", LocalDateTime.now());
        com.aviral.ticketingsystem.entity.Ticket existing2 =
                new com.aviral.ticketingsystem.entity.Ticket(booking, "TKT-" + BOOKING_ID + "-2", LocalDateTime.now());
        booking.getTickets().add(existing1);
        booking.getTickets().add(existing2);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        BookingConfirmationResponse response = bookingService.confirmBooking(BOOKING_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        // Inventory must never be touched.
        verifyNoInteractions(inventoryRepository);
        // No additional tickets were created.
        assertThat(booking.getTickets()).hasSize(2);
    }

    @Test
    @DisplayName("6. Booking not found throws BookingNotFoundException (HTTP 404)")
    void confirmBooking_WhenBookingNotFound_ThrowsBookingNotFoundException() {
        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("Booking not found with id=" + BOOKING_ID);

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("7. Invalid bookingId (zero) throws IllegalArgumentException")
    void confirmBooking_WhenBookingIdIsZero_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> bookingService.confirmBooking(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Booking ID must be a positive number");

        verifyNoInteractions(bookingRepository, inventoryRepository);
    }

    @Test
    @DisplayName("8. EXPIRED booking is rejected with BookingConflictException (HTTP 409)")
    void confirmBooking_WhenExpired_ThrowsBookingConflictException() {
        Booking booking = buildBookingNoEventStub(
                BookingStatus.EXPIRED, LocalDateTime.now().minusMinutes(10));

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("EXPIRED");

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("9. CANCELLED booking is rejected with BookingConflictException (HTTP 409)")
    void confirmBooking_WhenCancelled_ThrowsBookingConflictException() {
        Booking booking = buildBookingNoEventStub(
                BookingStatus.CANCELLED, LocalDateTime.now().plusMinutes(5));

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("CANCELLED");

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("10. Deadline exactly reached (now == expiresAt) is rejected")
    void confirmBooking_WhenDeadlineExactlyReached_Rejected() {
        // expiresAt in the past (even 1 second) must fail.
        Booking booking = buildBookingNoEventStub(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().minusSeconds(1));

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("payment deadline has passed");

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("11. Deadline still in the future by 1 second is accepted")
    void confirmBooking_WhenDeadlineOneSecondInFuture_Accepted() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusSeconds(1), 1);
        Inventory inventory = buildInventory(5, 0);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        // Must not throw.
        BookingConfirmationResponse response = bookingService.confirmBooking(BOOKING_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("12. Insufficient reservedCount throws IllegalStateException (data integrity guard)")
    void confirmBooking_WhenReservedCountTooLow_ThrowsIllegalStateException() {
        // quantity=3, but reservedCount=1 — inconsistent state.
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), 3);
        Inventory inventory = buildInventory(1, 0);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Data integrity violation");

        // Neither inventory nor booking must have been mutated.
        assertThat(inventory.getReservedCount()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("13. Booking lock is acquired before inventory lock (correct lock order)")
    void confirmBooking_BookingLockedBeforeInventory() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), 2);
        Inventory inventory = buildInventory(10, 0);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        bookingService.confirmBooking(BOOKING_ID);

        // Verify strict ordering: booking lock must be called before inventory lock.
        InOrder inOrder = inOrder(bookingRepository, inventoryRepository);
        inOrder.verify(bookingRepository).findByIdWithLock(BOOKING_ID);
        inOrder.verify(inventoryRepository).findByEventIdWithLock(EVENT_ID);
    }

    @Test
    @DisplayName("14. Response contains booking summary with correct fields")
    void confirmBooking_ResponseContainsCorrectFields() {
        int quantity = 2;
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), quantity);
        Inventory inventory = buildInventory(10, 0);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        BookingConfirmationResponse response = bookingService.confirmBooking(BOOKING_ID);

        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.quantity()).isEqualTo(quantity);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.confirmedAt()).isNotNull();
        assertThat(response.tickets()).hasSize(quantity);
    }

    @Test
    @DisplayName("15. Missing inventory for valid booking throws IllegalStateException")
    void confirmBooking_WhenInventoryMissing_ThrowsIllegalStateException() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(5), 2);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inventory not found for event id=" + EVENT_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"EXPIRED", "CANCELLED"})
    @DisplayName("16. EXPIRED and CANCELLED statuses are both rejected with BookingConflictException")
    void confirmBooking_TerminalStatuses_AllRejected(BookingStatus status) {
        Booking booking = buildBookingNoEventStub(status, LocalDateTime.now().plusMinutes(5));

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                .isInstanceOf(BookingConflictException.class);

        verifyNoInteractions(inventoryRepository);
    }
}
