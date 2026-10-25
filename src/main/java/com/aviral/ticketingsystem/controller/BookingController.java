package com.aviral.ticketingsystem.controller;

import com.aviral.ticketingsystem.dto.BookingConfirmationResponse;
import com.aviral.ticketingsystem.dto.BookingRequest;
import com.aviral.ticketingsystem.dto.BookingResponse;
import com.aviral.ticketingsystem.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing endpoints for booking creation and management.
 */
@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * POST /bookings
     * Creates a new pending ticket reservation with safe inventory allocation.
     *
     * @param request the validated booking request parameters
     * @return HTTP 201 Created with the reservation details in the response body
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /bookings/{bookingId}/confirm
     * Simulates a successful payment and confirms a PENDING_PAYMENT booking.
     *
     * <p>No request body is required; calling this endpoint acts as the payment
     * confirmation signal. The service enforces all business rules (status check,
     * deadline check, inventory update, ticket generation) within a single transaction.
     *
     * @param bookingId the primary key of the booking to confirm
     * @return HTTP 200 OK with the confirmation details and issued tickets
     */
    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<BookingConfirmationResponse> confirmBooking(
            @PathVariable Long bookingId) {
        BookingConfirmationResponse response = bookingService.confirmBooking(bookingId);
        return ResponseEntity.ok(response);
    }
}
