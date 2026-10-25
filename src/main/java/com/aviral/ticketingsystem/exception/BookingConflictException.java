package com.aviral.ticketingsystem.exception;

/**
 * Thrown when a confirmation attempt is rejected because the booking is in a
 * state that cannot be confirmed (e.g. EXPIRED, CANCELLED) or its payment
 * deadline has passed.
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
