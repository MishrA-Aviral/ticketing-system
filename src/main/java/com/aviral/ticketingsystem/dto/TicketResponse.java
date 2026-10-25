package com.aviral.ticketingsystem.dto;

import com.aviral.ticketingsystem.entity.Ticket;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable summary of a single issued ticket returned inside a
 * {@link BookingConfirmationResponse}.
 *
 * Uses UUID as the primary key because ticket IDs are user-facing;
 * sequential integers are trivially enumerable.
 */
public record TicketResponse(
        UUID ticketId,
        String ticketNumber,
        LocalDateTime issuedAt
) {
    /**
     * Convenience factory method that constructs a TicketResponse from a
     * managed Ticket entity.
     *
     * @param ticket the persisted ticket entity
     * @return populated TicketResponse
     */
    public static TicketResponse fromEntity(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getIssuedAt()
        );
    }
}
