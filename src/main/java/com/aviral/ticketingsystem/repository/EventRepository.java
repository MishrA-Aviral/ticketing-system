package com.aviral.ticketingsystem.repository;

import com.aviral.ticketingsystem.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Database access for Event records.
 * JpaRepository provides save, findById, findAll, delete, count, etc. for free.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /** Used by DataInitializer to skip seeding if an event already exists. */
    boolean existsByName(String name);

    Optional<Event> findByName(String name);
}
