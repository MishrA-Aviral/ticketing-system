package com.aviral.ticketingsystem.exception;

/**
 * Thrown when a booking cannot be located by its primary key.
 * Mapped to HTTP 404 Not Found by {@link GlobalExceptionHandler}.
 */
public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) {
        super(message);
    }
}
