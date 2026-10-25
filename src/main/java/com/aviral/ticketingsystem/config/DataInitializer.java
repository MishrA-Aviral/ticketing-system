package com.aviral.ticketingsystem.config;

import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.entity.Inventory;
import com.aviral.ticketingsystem.entity.User;
import com.aviral.ticketingsystem.repository.EventRepository;
import com.aviral.ticketingsystem.repository.InventoryRepository;
import com.aviral.ticketingsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds the database with one demo user and a small set of future concert events
 * on every application startup. All seed operations are idempotent:
 * each check runs before inserting, so re-running the application never
 * produces duplicate rows.
 *
 * CommandLineRunner.run() is invoked by Spring after the full ApplicationContext
 * is ready, which means the schema already exists (created by Hibernate ddl-auto).
 *
 * @Transactional on run() ensures that all seed work completes atomically.
 * If any insert fails, the entire startup seed is rolled back cleanly and
 * will be retried on the next startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    // Constructor injection is preferred over @Autowired field injection:
    // makes dependencies explicit and the class easier to test in isolation.
    public DataInitializer(UserRepository userRepository,
                           EventRepository eventRepository) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedEvents();
    }

    // -------------------------------------------------------------------------
    // User seed
    // -------------------------------------------------------------------------

    private void seedUsers() {
        final String demoEmail = "demo@ticketing.com";
        if (userRepository.existsByEmail(demoEmail)) {
            log.info("Demo user already exists — skipping user seed.");
            return;
        }
        User demo = new User("Demo User", demoEmail);
        userRepository.save(demo);
        log.info("Seeded demo user: {}", demoEmail);
    }

    // -------------------------------------------------------------------------
    // Event + Inventory seed
    // -------------------------------------------------------------------------

    private void seedEvents() {
        List<EventSeedData> catalogue = List.of(
            new EventSeedData(
                "Coldplay: Music of the Spheres World Tour",
                "Narendra Modi Stadium, Ahmedabad",
                "Experience Coldplay's spectacular world tour with stunning visuals and timeless hits.",
                LocalDateTime.of(2027, 3, 15, 19, 0),
                new BigDecimal("150.00"),
                500
            ),
            new EventSeedData(
                "Ed Sheeran: Mathematics Tour",
                "D.Y. Patil Stadium, Mumbai",
                "An intimate evening with Ed Sheeran performing his greatest hits and new material.",
                LocalDateTime.of(2027, 4, 22, 20, 0),
                new BigDecimal("120.00"),
                800
            ),
            new EventSeedData(
                "Taylor Swift: The Eras Tour",
                "Jawaharlal Nehru Stadium, Delhi",
                "Relive every era of Taylor Swift's iconic music career in one unforgettable night.",
                LocalDateTime.of(2027, 5, 10, 18, 30),
                new BigDecimal("200.00"),
                1000
            ),
            new EventSeedData(
                "A.R. Rahman Live in Concert",
                "YMCA Grounds, Chennai",
                "An enchanting live performance by India's greatest music composer.",
                LocalDateTime.of(2027, 6, 1, 19, 30),
                new BigDecimal("80.00"),
                300
            )
        );

        int seeded = 0;
        for (EventSeedData data : catalogue) {
            if (eventRepository.existsByName(data.name())) {
                log.info("Event '{}' already exists — skipping.", data.name());
                continue;
            }

            // Build Event first (no id yet).
            Event event = new Event(
                data.name(), data.venue(), data.description(),
                data.eventDate(), data.ticketPrice()
            );

            // Build Inventory and link both sides of the relationship.
            // Setting both sides is required so that Hibernate's in-memory
            // object graph is consistent before the INSERT statements are issued.
            Inventory inventory = new Inventory(event, data.totalCapacity());
            event.setInventory(inventory);

            // CascadeType.ALL on Event.inventory means saving the Event
            // automatically persists the linked Inventory in the same transaction.
            eventRepository.save(event);

            seeded++;
            log.info("Seeded event '{}' — {} seats at ${}",
                data.name(), data.totalCapacity(), data.ticketPrice());
        }

        if (seeded == 0) {
            log.info("All events already seeded — no changes made.");
        } else {
            log.info("Phase 1 seed complete: {} new event(s) created.", seeded);
        }
    }

    // -------------------------------------------------------------------------
    // Internal record for seed definitions
    // -------------------------------------------------------------------------

    /**
     * A Java record (Java 16+) acts as an immutable data carrier.
     * Equivalent to a class with final fields, constructor, and getters —
     * without the boilerplate.
     */
    private record EventSeedData(
        String name,
        String venue,
        String description,
        LocalDateTime eventDate,
        BigDecimal ticketPrice,
        int totalCapacity
    ) {}
}
