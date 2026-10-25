package com.aviral.ticketingsystem.repository;

import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Database access for Inventory records.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByEvent(Event event);

    /** Convenience method to avoid fetching the full Event object first. */
    Optional<Inventory> findByEventId(Long eventId);

    /**
     * Retrieves the inventory for an event using a pessimistic write lock (SELECT ... FOR UPDATE).
     * Locks the specific inventory row in PostgreSQL so concurrent booking requests must wait,
     * preventing race conditions and overselling.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.event.id = :eventId")
    Optional<Inventory> findByEventIdWithLock(@Param("eventId") Long eventId);
}

