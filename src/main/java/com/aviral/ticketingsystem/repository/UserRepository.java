package com.aviral.ticketingsystem.repository;

import com.aviral.ticketingsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Database access for User records.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by DataInitializer to skip seeding if the demo user already exists. */
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
