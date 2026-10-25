package com.aviral.ticketingsystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Data Transfer Object (DTO) for ticket booking requests.
 *
 * Immutable Java record encapsulating the client parameters required
 * to create a reservation. Includes Bean Validation constraints.
 */
public record BookingRequest(
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be a positive number")
    Long userId,

    @NotNull(message = "Event ID is required")
    @Positive(message = "Event ID must be a positive number")
    Long eventId,

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1 ticket")
    @Max(value = 6, message = "Quantity cannot exceed 6 tickets per booking")
    Integer quantity
) {}
