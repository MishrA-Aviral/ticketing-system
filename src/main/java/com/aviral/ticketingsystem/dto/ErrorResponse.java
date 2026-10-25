package com.aviral.ticketingsystem.dto;

import java.time.LocalDateTime;

/**
 * Standardized error payload returned to clients on API failures.
 */
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message
) {
    public ErrorResponse(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message);
    }
}
