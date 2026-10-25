package com.aviral.ticketingsystem.dto;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for booking responses.
 *
 * Immutable Java record returning reservation details, locked-in total amount,
 * and the expiration timestamp.
 */
public record BookingResponse(
    Long bookingId,
    Long userId,
    Long eventId,
    String eventName,
    int quantity,
    BigDecimal totalAmount,
    BookingStatus status,
    LocalDateTime createdAt,
    LocalDateTime expiresAt
) {
    /**
     * Convenience factory method to construct a BookingResponse from a Booking entity.
     *
     * @param booking the persisted booking entity
     * @return populated BookingResponse DTO
     */
    public static BookingResponse fromEntity(Booking booking) {
        return new BookingResponse(
            booking.getId(),
            booking.getUser().getId(),
            booking.getEvent().getId(),
            booking.getEvent().getName(),
            booking.getQuantity(),
            booking.getTotalAmount(),
            booking.getStatus(),
            booking.getCreatedAt(),
            booking.getExpiresAt()
        );
    }
}
