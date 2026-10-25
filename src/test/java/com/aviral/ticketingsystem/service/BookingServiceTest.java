package com.aviral.ticketingsystem.service;

import com.aviral.ticketingsystem.dto.BookingRequest;
import com.aviral.ticketingsystem.dto.BookingResponse;
import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.entity.Inventory;
import com.aviral.ticketingsystem.entity.User;
import com.aviral.ticketingsystem.exception.EventNotFoundException;
import com.aviral.ticketingsystem.exception.InsufficientInventoryException;
import com.aviral.ticketingsystem.exception.UserNotFoundException;
import com.aviral.ticketingsystem.repository.BookingRepository;
import com.aviral.ticketingsystem.repository.EventRepository;
import com.aviral.ticketingsystem.repository.InventoryRepository;
import com.aviral.ticketingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookingService}.
 *
 * <p>Uses Mockito to isolate the service logic from the database.
 * Tests verify business invariants, validation boundaries, locking invocations,
 * price calculations, and exception propagation for transaction rollback.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    private BookingService bookingService;

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 42L;
    private static final Long BOOKING_ID = 999L;
    private static final BigDecimal TICKET_PRICE = new BigDecimal("150.00");

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

    private User buildUser() {
        User user = new User("Jane Doe", "jane@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private Event buildEvent() {
        Event event = new Event(
                "Coldplay Concert",
                "Ahmedabad Stadium",
                "World Tour",
                LocalDateTime.of(2027, 3, 15, 19, 0),
                TICKET_PRICE
        );
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }

    private Inventory buildInventory(Event event, int totalCapacity, int reservedCount, int soldCount) {
        Inventory inventory = new Inventory(event, totalCapacity);
        inventory.setReservedCount(reservedCount);
        inventory.setSoldCount(soldCount);
        ReflectionTestUtils.setField(inventory, "id", 10L);
        return inventory;
    }

    @Test
    @DisplayName("1. Valid booking reserves requested seats and creates a PENDING_PAYMENT booking")
    void createBooking_ValidRequest_ReservesSeatsAndCreatesPendingBooking() {
        User user = buildUser();
        Event event = buildEvent();
        Inventory inventory = buildInventory(event, 100, 10, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", BOOKING_ID);
            ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now());
            return b;
        });

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 2);
        BookingResponse response = bookingService.createBooking(request);

        // Verify inventory reservation
        assertThat(inventory.getReservedCount()).isEqualTo(12);
        verify(inventoryRepository).findByEventIdWithLock(EVENT_ID);
        verify(inventoryRepository).save(inventory);

        // Verify booking creation
        assertThat(response).isNotNull();
        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.eventName()).isEqualTo("Coldplay Concert");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("2. Booking requesting more seats than available is rejected")
    void createBooking_RequestedMoreThanAvailable_ThrowsInsufficientInventoryException() {
        User user = buildUser();
        Event event = buildEvent();
        // totalCapacity=10, reserved=8, sold=0 => available = 2. Requested = 3.
        Inventory inventory = buildInventory(event, 10, 8, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 3);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("Insufficient inventory");

        // Inventory reservedCount must remain untouched
        assertThat(inventory.getReservedCount()).isEqualTo(8);
        verify(inventoryRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. A quantity below 1 is rejected")
    void createBooking_QuantityBelowOne_ThrowsIllegalArgumentException() {
        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 0);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be between 1 and 6");

        verifyNoInteractions(inventoryRepository, bookingRepository);
    }

    @Test
    @DisplayName("4. A quantity above 6 is rejected")
    void createBooking_QuantityAboveSix_ThrowsIllegalArgumentException() {
        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 7);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be between 1 and 6");

        verifyNoInteractions(inventoryRepository, bookingRepository);
    }

    @ParameterizedTest(name = "Quantity {0} within [1, 6] is accepted")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    @DisplayName("Quantity boundary checks between 1 and 6 are accepted")
    void createBooking_ValidQuantities_Accepted(int quantity) {
        User user = buildUser();
        Event event = buildEvent();
        Inventory inventory = buildInventory(event, 100, 0, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", BOOKING_ID);
            ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now());
            return b;
        });

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, quantity);
        BookingResponse response = bookingService.createBooking(request);

        assertThat(response.quantity()).isEqualTo(quantity);
        assertThat(inventory.getReservedCount()).isEqualTo(quantity);
    }

    @Test
    @DisplayName("5. Missing inventory for existing event is handled without modifying inventory")
    void createBooking_MissingInventoryForExistingEvent_ThrowsIllegalStateException() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 2);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inventory not found for event id=" + EVENT_ID);

        verify(inventoryRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("6a. Missing user is rejected without locking inventory")
    void createBooking_MissingUser_ThrowsUserNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 2);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with id=" + USER_ID);

        verifyNoInteractions(inventoryRepository, bookingRepository);
    }

    @Test
    @DisplayName("6b. Missing event is rejected with EventNotFoundException")
    void createBooking_MissingEvent_ThrowsEventNotFoundException() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(false);

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 2);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("Event not found with id=" + EVENT_ID);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Total amount is calculated correctly using BigDecimal arithmetic")
    void createBooking_CalculatesTotalAmountCorrectly() {
        User user = buildUser();
        Event event = buildEvent();
        event.setTicketPrice(new BigDecimal("123.45"));
        Inventory inventory = buildInventory(event, 50, 0, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", BOOKING_ID);
            ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now());
            return b;
        });

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 3);
        BookingResponse response = bookingService.createBooking(request);

        // 123.45 * 3 = 370.35
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("370.35"));
    }

    @Test
    @DisplayName("8. Expiry deadline is non-null and later than the booking creation time")
    void createBooking_ExpiryDeadlineIsNonNullAndInFuture() {
        User user = buildUser();
        Event event = buildEvent();
        Inventory inventory = buildInventory(event, 50, 0, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(bookingCaptor.capture())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", BOOKING_ID);
            ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now());
            return b;
        });

        LocalDateTime beforeCall = LocalDateTime.now();
        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 2);
        BookingResponse response = bookingService.createBooking(request);

        Booking capturedBooking = bookingCaptor.getValue();
        assertThat(capturedBooking.getExpiresAt()).isNotNull();
        assertThat(capturedBooking.getExpiresAt()).isAfter(beforeCall);
        // Default payment window is 10 minutes
        assertThat(capturedBooking.getExpiresAt()).isAfterOrEqualTo(beforeCall.plusMinutes(9).plusSeconds(50));
        assertThat(response.expiresAt()).isEqualTo(capturedBooking.getExpiresAt());
    }

    @Test
    @DisplayName("9. Booking starts as PENDING_PAYMENT, not CONFIRMED")
    void createBooking_StartsAsPendingPayment() {
        User user = buildUser();
        Event event = buildEvent();
        Inventory inventory = buildInventory(event, 50, 0, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(bookingCaptor.capture())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", BOOKING_ID);
            ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now());
            return b;
        });

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 1);
        bookingService.createBooking(request);

        Booking captured = bookingCaptor.getValue();
        assertThat(captured.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(captured.getConfirmedAt()).isNull();
        assertThat(captured.getTickets()).isEmpty();
    }

    @Test
    @DisplayName("10. Persistence failure while creating booking propagates exception for transaction rollback")
    void createBooking_PersistenceFailure_PropagatesException() {
        User user = buildUser();
        Event event = buildEvent();
        Inventory inventory = buildInventory(event, 50, 0, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("Database constraint violation"));

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 2);

        // Exception must propagate so that Spring's @Transactional triggers rollback
        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Database constraint violation");

        // The inventory was mutated in-memory before save, but since the transaction will roll back,
        // it must not be committed to the database.
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("Event with negative or null ticket price is rejected")
    void createBooking_InvalidTicketPrice_ThrowsIllegalStateException() {
        User user = buildUser();
        Event event = buildEvent();
        event.setTicketPrice(new BigDecimal("-10.00"));
        Inventory inventory = buildInventory(event, 50, 0, 0);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inventoryRepository.findByEventIdWithLock(EVENT_ID)).thenReturn(Optional.of(inventory));

        BookingRequest request = new BookingRequest(USER_ID, EVENT_ID, 2);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid ticket price");

        verify(bookingRepository, never()).save(any());
    }
}
