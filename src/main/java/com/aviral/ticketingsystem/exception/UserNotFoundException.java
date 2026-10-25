package com.aviral.ticketingsystem.exception;

/**
 * Thrown when a referenced User cannot be found in the database.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
