package com.aviral.ticketingsystem.controller;

import com.aviral.ticketingsystem.dto.BookingConfirmationResponse;
import com.aviral.ticketingsystem.dto.TicketResponse;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.exception.BookingConflictException;
import com.aviral.ticketingsystem.exception.BookingNotFoundException;
import com.aviral.ticketingsystem.exception.GlobalExceptionHandler;
import com.aviral.ticketingsystem.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link BookingController#confirmBooking(Long)}.
 *
 * Uses MockMvc in standalone mode with {@link GlobalExceptionHandler} wired in,
 * exactly mirroring the existing {@code BookingControllerTest} setup.
 */
@ExtendWith(MockitoExtension.class)
class BookingConfirmationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookingConfirmationResponse buildResponse(Long bookingId, int quantity) {
        LocalDateTime now = LocalDateTime.now();
        List<TicketResponse> tickets = new java.util.ArrayList<>();
        for (int i = 1; i <= quantity; i++) {
            tickets.add(new TicketResponse(UUID.randomUUID(), "TKT-" + bookingId + "-" + i, now));
        }
        return new BookingConfirmationResponse(
                bookingId, 1L, 42L, "Test Concert", quantity,
                new BigDecimal("200.00"), BookingStatus.CONFIRMED,
                now.minusMinutes(5), now.plusMinutes(5), now,
                tickets
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /bookings/{id}/confirm with valid pending booking returns HTTP 200 and confirmation body")
    void confirmBooking_WhenValid_ReturnsOk() throws Exception {
        long bookingId = 55L;
        BookingConfirmationResponse response = buildResponse(bookingId, 2);

        when(bookingService.confirmBooking(bookingId)).thenReturn(response);

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.tickets").isArray())
                .andExpect(jsonPath("$.tickets.length()").value(2));

        verify(bookingService).confirmBooking(bookingId);
    }

    @Test
    @DisplayName("POST /bookings/{id}/confirm for already-CONFIRMED booking returns HTTP 200 (idempotent)")
    void confirmBooking_WhenAlreadyConfirmed_ReturnsOk() throws Exception {
        long bookingId = 55L;
        BookingConfirmationResponse response = buildResponse(bookingId, 1);

        when(bookingService.confirmBooking(bookingId)).thenReturn(response);

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("POST /bookings/{id}/confirm for non-existent booking returns HTTP 404 Not Found")
    void confirmBooking_WhenBookingNotFound_Returns404() throws Exception {
        long bookingId = 999L;
        when(bookingService.confirmBooking(bookingId))
                .thenThrow(new BookingNotFoundException("Booking not found with id=" + bookingId));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Booking not found with id=" + bookingId));
    }

    @Test
    @DisplayName("POST /bookings/{id}/confirm for expired booking returns HTTP 409 Conflict")
    void confirmBooking_WhenDeadlinePassed_Returns409() throws Exception {
        long bookingId = 55L;
        when(bookingService.confirmBooking(bookingId))
                .thenThrow(new BookingConflictException(
                        "Booking id=" + bookingId + " cannot be confirmed because its payment deadline has passed."));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("payment deadline has passed")));
    }

    @Test
    @DisplayName("POST /bookings/{id}/confirm for EXPIRED booking returns HTTP 409 Conflict")
    void confirmBooking_WhenStatusExpired_Returns409() throws Exception {
        long bookingId = 55L;
        when(bookingService.confirmBooking(bookingId))
                .thenThrow(new BookingConflictException(
                        "Cannot confirm booking id=" + bookingId + " with status EXPIRED."));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EXPIRED")));
    }

    @Test
    @DisplayName("POST /bookings/{id}/confirm for CANCELLED booking returns HTTP 409 Conflict")
    void confirmBooking_WhenStatusCancelled_Returns409() throws Exception {
        long bookingId = 55L;
        when(bookingService.confirmBooking(bookingId))
                .thenThrow(new BookingConflictException(
                        "Cannot confirm booking id=" + bookingId + " with status CANCELLED."));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CANCELLED")));
    }

    @Test
    @DisplayName("POST /bookings/{id}/confirm with inventory integrity failure returns HTTP 400")
    void confirmBooking_WhenInventoryIntegrityFails_Returns400() throws Exception {
        long bookingId = 55L;
        when(bookingService.confirmBooking(bookingId))
                .thenThrow(new IllegalStateException(
                        "Cannot confirm booking id=55: reservedCount (1) is less than booking quantity (3). Data integrity violation."));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Data integrity violation")));
    }

    @Test
    @DisplayName("POST /bookings/{id}/confirm response includes ticket details with correct fields")
    void confirmBooking_ResponseTicketDetails() throws Exception {
        long bookingId = 55L;
        BookingConfirmationResponse response = buildResponse(bookingId, 3);

        when(bookingService.confirmBooking(bookingId)).thenReturn(response);

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(3))
                .andExpect(jsonPath("$.tickets[0].ticketNumber").value("TKT-" + bookingId + "-1"))
                .andExpect(jsonPath("$.tickets[0].issuedAt").isNotEmpty());
    }
}
