package com.aviral.ticketingsystem.service;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.entity.Inventory;
import com.aviral.ticketingsystem.repository.BookingRepository;
import com.aviral.ticketingsystem.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookingExpiryService.
 *
 * Uses Mockito to isolate the service from the database. No Spring context, no
 * real database connection. All repository calls are mocked.
 *
 * Convention note on buildBooking vs buildBookingNoEventStub:
 *   Mockito strict mode (used by @ExtendWith(MockitoExtension.class)) rejects
 *   stubs that are set up but never called. The service only calls
 *   booking.getEvent().getId() when it reaches the inventory lookup step.
 *   Tests that return early (wrong status, not-yet-expired) must therefore use
 *   buildBookingNoEventStub, which creates a plain Event with no spy/stub.
 *   Tests that reach the inventory lookup use buildBooking, which spies on Event
 *   and stubs getId() to return a known EVENT_ID.
 */
@ExtendWith(MockitoExtension.class)
class BookingExpiryServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private BookingExpiryService bookingExpiryService;

    private static final Long BOOKING_ID = 1L;
    private static final Long EVENT_ID   = 42L;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * For tests where the service WILL reach booking.getEvent().getId().
     * Spies on Event and stubs getId() to return EVENT_ID.
     */
    private Booking buildBooking(BookingStatus status, LocalDateTime expiresAt) {
        Event event = spy(new Event(
                "Test Concert", "Test Venue", "desc",
                LocalDateTime.of(2027, 6, 1, 19, 0),
                new BigDecimal("100.00")
        ));
        doReturn(EVENT_ID).when(event).getId();

        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setStatus(status);
        booking.setExpiresAt(expiresAt);
        booking.setQuantity(2);
        booking.setTotalAmount(new BigDecimal("200.00"));
        return booking;
    }

    /**
     * For tests where the service returns EARLY before calling event.getId().
     * Uses a plain Event with no spy/stub to satisfy Mockito strict mode.
     */
    private Booking buildBookingNoEventStub(BookingStatus status, LocalDateTime expiresAt) {
        Event event = new Event(
                "Test Concert", "Test Venue", "desc",
                LocalDateTime.of(2027, 6, 1, 19, 0),
                new BigDecimal("100.00")
        );
        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setStatus(status);
        booking.setExpiresAt(expiresAt);
        booking.setQuantity(2);
        booking.setTotalAmount(new BigDecimal("200.00"));
        return booking;
    }

    private Inventory buildInventory(int reservedCount) {
        Event event = new Event(
                "Test Concert", "Test Venue", "desc",
                LocalDateTime.of(2027, 6, 1, 19, 0),
                new BigDecimal("100.00")
        );
        Inventory inventory = new Inventory(event, 100);
        inventory.setReservedCount(reservedCount);
        return inventory;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("An expired PENDING_PAYMENT booking releases reserved seats and becomes EXPIRED")
    void expireBooking_WhenPendingAndExpired_ReleasesInventoryAndSetsExpiredStatus() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusMinutes(5)
        );
        Inventory inventory = buildInventory(10);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        bookingExpiryService.expireBooking(BOOKING_ID);

        // Reserved count must decrease by booking.getQuantity() == 2.
        assertThat(inventory.getReservedCount()).isEqualTo(8);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(inventoryRepository, times(1)).findByEventIdWithLock(EVENT_ID);
    }

    @Test
    @DisplayName("A booking whose deadline has not passed is left completely unchanged")
    void expireBooking_WhenPendingButNotYetExpired_DoesNothing() {
        Booking booking = buildBookingNoEventStub(
                BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusMinutes(10)
        );

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingExpiryService.expireBooking(BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("A CONFIRMED booking is left unchanged — inventory is not touched")
    void expireBooking_WhenAlreadyConfirmed_DoesNothing() {
        Booking booking = buildBookingNoEventStub(
                BookingStatus.CONFIRMED,
                LocalDateTime.now().minusMinutes(5)
        );

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingExpiryService.expireBooking(BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("An already EXPIRED booking is left unchanged — inventory is not decremented again")
    void expireBooking_WhenAlreadyExpired_DoesNotDecrementInventoryAgain() {
        Booking booking = buildBookingNoEventStub(
                BookingStatus.EXPIRED,
                LocalDateTime.now().minusMinutes(15)
        );

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingExpiryService.expireBooking(BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        // Core idempotency guarantee: inventory must never be decremented twice.
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("A CANCELLED booking is left unchanged")
    void expireBooking_WhenCancelled_DoesNothing() {
        Booking booking = buildBookingNoEventStub(
                BookingStatus.CANCELLED,
                LocalDateTime.now().minusMinutes(5)
        );

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingExpiryService.expireBooking(BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("A non-existent booking ID is handled safely without touching inventory")
    void expireBooking_WhenBookingNotFound_DoesNothing() {
        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.empty());

        bookingExpiryService.expireBooking(BOOKING_ID);

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("A PENDING_PAYMENT booking with null expiresAt is ineligible — booking and inventory unchanged")
    void expireBooking_WhenExpiresAtIsNull_DoesNothing() {
        // expiresAt == null: the service must not throw NullPointerException.
        // It must log a warning and return without touching booking status or inventory.
        // The service returns before calling event.getId(), so no spy/stub is needed.
        Booking booking = buildBookingNoEventStub(
                BookingStatus.PENDING_PAYMENT,
                null  // explicitly null — the defensive guard must catch this
        );

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Must not throw.
        bookingExpiryService.expireBooking(BOOKING_ID);

        // Booking status must remain PENDING_PAYMENT.
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        // Inventory must never be accessed.
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("Missing inventory for a valid expired booking throws IllegalStateException")
    void expireBooking_WhenInventoryMissing_ThrowsIllegalStateException() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusMinutes(5)
        );

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingExpiryService.expireBooking(BOOKING_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inventory not found for event id=" + EVENT_ID);

        // Booking must not have been mutated before the exception.
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Inconsistent reservedCount (less than booking quantity) throws IllegalStateException")
    void expireBooking_WhenReservedCountLessThanQuantity_ThrowsIllegalStateException() {
        // Booking quantity = 2, but inventory only has 1 reserved — data integrity violation.
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusMinutes(5)
        );
        Inventory inventory = buildInventory(1);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> bookingExpiryService.expireBooking(BOOKING_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Data integrity violation");

        // Neither inventory nor booking must have been modified.
        assertThat(inventory.getReservedCount()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("A booking expiring exactly at or just before now is expired (boundary check)")
    void expireBooking_WhenExpiresAtJustBeforeNow_IsExpired() {
        Booking booking = buildBooking(
                BookingStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusSeconds(1)
        );
        Inventory inventory = buildInventory(5);

        when(bookingRepository.findByIdWithLock(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        bookingExpiryService.expireBooking(BOOKING_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        // 5 reserved - 2 quantity = 3
        assertThat(inventory.getReservedCount()).isEqualTo(3);
    }
}
