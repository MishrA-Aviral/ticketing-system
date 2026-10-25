package com.aviral.ticketingsystem.service;

import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for concert event operations.
 * Coordinates database retrieval via EventRepository.
 */
@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Retrieves all concert events from the database.
     * Transaction is read-only for database optimization.
     *
     * @return list of all events
     */
    @Transactional(readOnly = true)
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }
}
