package com.aviral.ticketingsystem.exception;

/**
 * Thrown when an inventory reservation fails due to insufficient available capacity.
 */
public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String message) {
        super(message);
    }
}
