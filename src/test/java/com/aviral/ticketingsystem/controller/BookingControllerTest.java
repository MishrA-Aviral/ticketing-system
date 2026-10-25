package com.aviral.ticketingsystem.controller;

import com.aviral.ticketingsystem.dto.BookingRequest;
import com.aviral.ticketingsystem.dto.BookingResponse;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.exception.EventNotFoundException;
import com.aviral.ticketingsystem.exception.GlobalExceptionHandler;
import com.aviral.ticketingsystem.exception.InsufficientInventoryException;
import com.aviral.ticketingsystem.exception.UserNotFoundException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link BookingController} exposing POST /bookings.
 * Integrates {@link GlobalExceptionHandler} to verify error mapping.
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

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

    @Test
    @DisplayName("POST /bookings with valid payload returns HTTP 201 Created and BookingResponse")
    void createBooking_ValidPayload_ReturnsCreated() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(10);

        BookingResponse response = new BookingResponse(
                100L, 1L, 42L, "Coldplay Concert", 2,
                new BigDecimal("300.00"), BookingStatus.PENDING_PAYMENT,
                now, expiresAt
        );

        when(bookingService.createBooking(any(BookingRequest.class))).thenReturn(response);

        String jsonPayload = """
                {
                    "userId": 1,
                    "eventId": 42,
                    "quantity": 2
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bookingId").value(100))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.eventId").value(42))
                .andExpect(jsonPath("$.eventName").value("Coldplay Concert"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(300.00))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        verify(bookingService).createBooking(any(BookingRequest.class));
    }

    @Test
    @DisplayName("POST /bookings with quantity > 6 returns HTTP 400 Bad Request")
    void createBooking_QuantityGreaterThanSix_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "userId": 1,
                    "eventId": 42,
                    "quantity": 7
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Quantity cannot exceed 6 tickets")));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("POST /bookings with quantity < 1 returns HTTP 400 Bad Request")
    void createBooking_QuantityLessThanOne_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "userId": 1,
                    "eventId": 42,
                    "quantity": 0
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Quantity must be at least 1 ticket")));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("POST /bookings when user does not exist returns HTTP 404 Not Found")
    void createBooking_UserNotFound_ReturnsNotFound() throws Exception {
        when(bookingService.createBooking(any(BookingRequest.class)))
                .thenThrow(new UserNotFoundException("User not found with id=999"));

        String jsonPayload = """
                {
                    "userId": 999,
                    "eventId": 42,
                    "quantity": 2
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found with id=999"));

        verify(bookingService).createBooking(any(BookingRequest.class));
    }

    @Test
    @DisplayName("POST /bookings when event does not exist returns HTTP 404 Not Found")
    void createBooking_EventNotFound_ReturnsNotFound() throws Exception {
        when(bookingService.createBooking(any(BookingRequest.class)))
                .thenThrow(new EventNotFoundException("Event not found with id=888"));

        String jsonPayload = """
                {
                    "userId": 1,
                    "eventId": 888,
                    "quantity": 2
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Event not found with id=888"));

        verify(bookingService).createBooking(any(BookingRequest.class));
    }

    @Test
    @DisplayName("POST /bookings when inventory is insufficient returns HTTP 400 Bad Request")
    void createBooking_InsufficientInventory_ReturnsBadRequest() throws Exception {
        when(bookingService.createBooking(any(BookingRequest.class)))
                .thenThrow(new InsufficientInventoryException("Insufficient inventory for event id=42. Requested: 5, available: 2"));

        String jsonPayload = """
                {
                    "userId": 1,
                    "eventId": 42,
                    "quantity": 5
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Insufficient inventory for event id=42. Requested: 5, available: 2"));

        verify(bookingService).createBooking(any(BookingRequest.class));
    }
}
