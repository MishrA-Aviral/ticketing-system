package com.aviral.ticketingsystem.scheduler;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.repository.BookingRepository;
import com.aviral.ticketingsystem.service.BookingExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically discovers PENDING_PAYMENT bookings whose payment deadline has
 * passed and delegates expiry of each one to {@link BookingExpiryService}.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li><b>No @Transactional here.</b> Each call to
 *       {@code bookingExpiryService.expireBooking()} carries its own
 *       transaction boundary. Wrapping the entire scan in one transaction
 *       would hold database connections open for the full duration of the run
 *       and would cause the pessimistic locks inside the service to span the
 *       whole batch — defeating their per-booking isolation purpose.</li>
 *   <li><b>Delegate, don't duplicate.</b> The scheduler extracts only the
 *       booking ID and passes it to the service. All business logic (status
 *       checks, deadline checks, inventory release) stays in
 *       {@link BookingExpiryService}.</li>
 *   <li><b>Per-booking failure isolation.</b> An exception for one booking
 *       is caught and logged; the remaining candidates are still processed
 *       during that run.</li>
 *   <li><b>Stale candidate list.</b> Bookings are discovered before each one
 *       is individually locked inside the service. A booking may have been
 *       confirmed or already expired between discovery and processing.
 *       {@code expireBooking} rechecks status and deadline under the lock,
 *       so stale entries are silently skipped without any side effects.</li>
 *   <li><b>Unbounded query (known limitation).</b> The candidate query
 *       {@code findByStatusAndExpiresAtBefore} returns all matching rows in a
 *       single result set. Under very high booking volume this could return a
 *       large list. A future optimisation can add pagination or a
 *       {@code SKIP LOCKED} query to cap the batch size and improve
 *       throughput on multi-instance deployments.</li>
 *   <li><b>Single-instance assumption.</b> This scheduler runs on every
 *       application instance. In a multi-instance deployment the same booking
 *       may be processed concurrently by more than one instance. This is safe
 *       because {@code expireBooking} holds a pessimistic row lock and
 *       rechecks eligibility; at most one instance will perform the actual
 *       state transition. However, it is not coordinated scheduling.
 *       A distributed lock (e.g., ShedLock) would be needed to guarantee
 *       exactly-once execution across instances.</li>
 * </ul>
 */
@Component
public class ExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);

    private final BookingRepository bookingRepository;
    private final BookingExpiryService bookingExpiryService;

    public ExpiryScheduler(BookingRepository bookingRepository,
                           BookingExpiryService bookingExpiryService) {
        this.bookingRepository = bookingRepository;
        this.bookingExpiryService = bookingExpiryService;
    }

    /**
     * Scans for expired pending bookings and processes them one by one.
     *
     * <p>Runs every 30 seconds with a 30-second initial delay.
     * The initial delay prevents the scheduler from firing during application
     * startup before the database connection pool is fully warmed up.
     *
     * <p>fixedDelay means the next run starts 30 seconds after the previous
     * run <em>completes</em>. This is safer than fixedRate for a database-
     * backed job: if a run takes longer than 30 seconds (e.g., large backlog
     * after a restart), runs do not pile up.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void processExpiredBookings() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("Expiry scheduler run started at {}", now);

        // Discover candidates. This query is intentionally outside a transaction:
        // each booking is processed in its own transaction inside expireBooking().
        List<Booking> candidates = bookingRepository
                .findByStatusAndExpiresAtBefore(BookingStatus.PENDING_PAYMENT, now);

        if (candidates.isEmpty()) {
            log.debug("No expired pending bookings found at {}", now);
            return;
        }

        log.info("Expiry scheduler found {} candidate(s) at {}", candidates.size(), now);

        int succeeded = 0;
        int failed = 0;

        for (Booking candidate : candidates) {
            Long bookingId = candidate.getId();
            try {
                // Pass only the ID. The service re-fetches and locks the row
                // inside its own @Transactional boundary.
                bookingExpiryService.expireBooking(bookingId);
                succeeded++;
            } catch (Exception e) {
                // Log and continue — one failed booking must not stop the others.
                log.error("Failed to expire booking id={}: {}", bookingId, e.getMessage(), e);
                failed++;
            }
        }

        log.info("Expiry scheduler run complete: {} succeeded, {} failed (total candidates: {}).",
                succeeded, failed, candidates.size());
    }
}
