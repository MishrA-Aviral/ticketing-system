package com.aviral.ticketingsystem.dto;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO returned after a booking is successfully confirmed.
 *
 * Contains the full booking summary, the confirmed timestamp, and one
 * {@link TicketResponse} per booked seat.
 */
public record BookingConfirmationResponse(
        Long bookingId,
        Long userId,
        Long eventId,
        String eventName,
        int quantity,
        BigDecimal totalAmount,
        BookingStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt,
        List<TicketResponse> tickets
) {
    /**
     * Convenience factory. Assumes the booking is already CONFIRMED and its
     * {@code tickets} collection is initialised and loaded.
     *
     * @param booking the managed, fully-confirmed booking entity
     * @return populated BookingConfirmationResponse
     */
    public static BookingConfirmationResponse fromEntity(Booking booking) {
        List<TicketResponse> ticketResponses = booking.getTickets().stream()
                .map(TicketResponse::fromEntity)
                .toList();

        return new BookingConfirmationResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getEvent().getId(),
                booking.getEvent().getName(),
                booking.getQuantity(),
                booking.getTotalAmount(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getExpiresAt(),
                booking.getConfirmedAt(),
                ticketResponses
        );
    }
}
