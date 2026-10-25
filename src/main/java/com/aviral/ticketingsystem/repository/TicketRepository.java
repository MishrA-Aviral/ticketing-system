package com.aviral.ticketingsystem.repository;

import com.aviral.ticketingsystem.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Database access for Ticket records.
 * Primary key is UUID (not Long) because ticket IDs are user-facing.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByBookingId(Long bookingId);

    /** Venue staff can look up a ticket by its human-readable number. */
    Optional<Ticket> findByTicketNumber(String ticketNumber);

    /** Used during ticket creation to guarantee ticketNumber uniqueness before saving. */
    boolean existsByTicketNumber(String ticketNumber);
}
