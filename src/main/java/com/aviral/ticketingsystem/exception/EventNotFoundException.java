package com.aviral.ticketingsystem.exception;

/**
 * Thrown when a referenced Event cannot be found in the database.
 */
public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String message) {
        super(message);
    }
}
