package com.aviral.ticketingsystem.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single physical seat in a confirmed booking.
 *
 * A booking for quantity=3 produces exactly 3 Ticket rows.
 * Tickets are created only when a booking reaches CONFIRMED status (Phase 6).
 * This entity is defined now so the schema is complete, but the table will
 * remain empty until payment confirmation is implemented.
 *
 * The primary key is a UUID rather than a sequential Long because:
 * - Ticket IDs are exposed to end users (printed on tickets, used for entry).
 * - Sequential integers are easy to enumerate and guess.
 * - UUIDs are opaque and unforgeable without a database breach.
 *
 * ticketNumber is a separate human-readable reference (e.g. "TKT-00000001")
 * that is easier to read aloud or type at a venue gate than a full UUID.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    // Hibernate 6+ supports GenerationType.UUID natively.
    // PostgreSQL stores this as a native uuid column (not varchar).
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // Short human-readable reference. Unique across all tickets.
    @NotBlank
    @Column(name = "ticket_number", nullable = false, unique = true, length = 20)
    private String ticketNumber;

    // Null until the booking is confirmed and this ticket is issued.
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    public Ticket() {}

    public Ticket(Booking booking, String ticketNumber, LocalDateTime issuedAt) {
        this.booking = booking;
        this.ticketNumber = ticketNumber;
        this.issuedAt = issuedAt;
    }

    public UUID getId()                          { return id; }
    public Booking getBooking()                  { return booking; }
    public void setBooking(Booking booking)      { this.booking = booking; }
    public String getTicketNumber()              { return ticketNumber; }
    public void setTicketNumber(String number)   { this.ticketNumber = number; }
    public LocalDateTime getIssuedAt()           { return issuedAt; }
    public void setIssuedAt(LocalDateTime t)     { this.issuedAt = t; }
}
