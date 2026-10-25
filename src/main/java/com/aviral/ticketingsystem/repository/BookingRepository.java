package com.aviral.ticketingsystem.repository;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Database access for Booking records.
 *
 * The findByStatusAndExpiresAtBefore method is the query the expiry scheduler
 * (Phase 4) will call periodically to find stale PENDING_PAYMENT bookings.
 * Spring Data translates this method name into:
 *   SELECT * FROM bookings WHERE status = ? AND expires_at < ?
 * Adding a composite index on (status, expires_at) in Phase 4 will keep
 * this query fast as the bookings table grows.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByStatus(BookingStatus status);

    /**
     * Finds all bookings in a given status whose expiry deadline has passed.
     * Primary use: expiry scheduler finds PENDING_PAYMENT bookings past their window.
     */
    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime cutoff);

    List<Booking> findByUserId(Long userId);

    List<Booking> findByEventId(Long eventId);

    /**
     * Retrieves a booking by ID using a pessimistic write lock (SELECT ... FOR UPDATE).
     * Locks the booking row so that concurrent payment-confirmation and expiry-scheduler
     * threads cannot both mutate the same booking status simultaneously.
     * Must be called inside an active @Transactional method — the lock is released
     * when the transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
    Optional<Booking> findByIdWithLock(@Param("bookingId") Long bookingId);
}
