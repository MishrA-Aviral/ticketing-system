package com.aviral.ticketingsystem.scheduler;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.repository.BookingRepository;
import com.aviral.ticketingsystem.service.BookingExpiryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExpiryScheduler.processExpiredBookings().
 *
 * The processing method is called directly rather than waiting for the
 * @Scheduled interval — this avoids real-time delays and keeps tests
 * deterministic.
 *
 * These tests use Mockito and no Spring context or database. They verify:
 *   - The scheduler calls the service for each discovered candidate.
 *   - No service call is made when no candidates exist.
 *   - A failure on one booking does not prevent the rest from being processed.
 *   - The scheduler passes only the booking ID to the service.
 *
 * Limitations honestly stated:
 *   - These tests do not verify @Transactional proxy behaviour.
 *   - These tests do not verify the scheduling interval.
 *   - These tests do not verify concurrent or multi-instance behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ExpirySchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingExpiryService bookingExpiryService;

    @InjectMocks
    private ExpiryScheduler expiryScheduler;

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Builds a Booking candidate with a known ID.
     *
     * The scheduler only calls booking.getId() — it never accesses event.getId().
     * Therefore we spy only on Booking and stub getId(); the Event is a plain
     * object with no spy, satisfying Mockito's strict stub usage rule.
     */
    private Booking buildCandidate(Long id, LocalDateTime expiresAt) {
        // Plain Event — no spy, no stub needed by the scheduler.
        Event event = new Event(
                "Test Concert", "Test Venue", "desc",
                LocalDateTime.of(2027, 6, 1, 19, 0),
                new BigDecimal("100.00")
        );

        // Spy on Booking so we can stub getId() (set by JPA, not constructable).
        Booking booking = spy(new Booking());
        doReturn(id).when(booking).getId();
        booking.setEvent(event);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setExpiresAt(expiresAt);
        booking.setQuantity(2);
        booking.setTotalAmount(new BigDecimal("200.00"));
        return booking;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("No candidates — expireBooking is never called")
    void processExpiredBookings_WhenNoCandidates_DoesNotCallService() {
        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        expiryScheduler.processExpiredBookings();

        verifyNoInteractions(bookingExpiryService);
    }

    @Test
    @DisplayName("Two candidates — expireBooking is called once per candidate with its ID")
    void processExpiredBookings_WithTwoCandidates_CallsServiceForEach() {
        Booking b1 = buildCandidate(10L, LocalDateTime.now().minusMinutes(5));
        Booking b2 = buildCandidate(20L, LocalDateTime.now().minusMinutes(2));

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(b1, b2));

        expiryScheduler.processExpiredBookings();

        // Service must be called exactly once per candidate, with only the ID.
        verify(bookingExpiryService, times(1)).expireBooking(10L);
        verify(bookingExpiryService, times(1)).expireBooking(20L);
        verifyNoMoreInteractions(bookingExpiryService);
    }

    @Test
    @DisplayName("First booking fails — second booking is still processed")
    void processExpiredBookings_WhenFirstFails_ContinuesToProcessRemainingCandidates() {
        Booking b1 = buildCandidate(10L, LocalDateTime.now().minusMinutes(5));
        Booking b2 = buildCandidate(20L, LocalDateTime.now().minusMinutes(2));

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(b1, b2));

        // Simulate a transient failure on the first booking.
        doThrow(new RuntimeException("DB timeout"))
                .when(bookingExpiryService).expireBooking(10L);

        // Must not throw — the scheduler catches and logs individual failures.
        expiryScheduler.processExpiredBookings();

        // Both bookings must have been attempted.
        verify(bookingExpiryService, times(1)).expireBooking(10L);
        verify(bookingExpiryService, times(1)).expireBooking(20L);
    }

    @Test
    @DisplayName("All candidates fail — no exception propagates out of the scheduler")
    void processExpiredBookings_WhenAllFail_DoesNotThrow() {
        Booking b1 = buildCandidate(10L, LocalDateTime.now().minusMinutes(5));

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(b1));

        doThrow(new IllegalStateException("Inventory missing"))
                .when(bookingExpiryService).expireBooking(10L);

        // The scheduler must absorb the exception; Spring's task scheduler
        // must not be interrupted.
        expiryScheduler.processExpiredBookings();

        verify(bookingExpiryService, times(1)).expireBooking(10L);
    }

    @Test
    @DisplayName("Scheduler passes only the booking ID to the service — not the entity")
    void processExpiredBookings_PassesOnlyIdToService() {
        Long expectedId = 42L;
        Booking b = buildCandidate(expectedId, LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(b));

        expiryScheduler.processExpiredBookings();

        // Verify the exact ID was passed — not any other value.
        verify(bookingExpiryService).expireBooking(expectedId);
        verifyNoMoreInteractions(bookingExpiryService);
    }
}
