package com.aviral.ticketingsystem.service;

import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for EventService.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    @DisplayName("getAllEvents delegates to eventRepository.findAll()")
    void getAllEvents_DelegatesToRepository() {
        Event event = new Event(
            "Taylor Swift: The Eras Tour",
            "Jawaharlal Nehru Stadium, Delhi",
            "Relive every era.",
            LocalDateTime.of(2027, 5, 10, 18, 30),
            new BigDecimal("200.00")
        );
        when(eventRepository.findAll()).thenReturn(List.of(event));

        List<Event> results = eventService.getAllEvents();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Taylor Swift: The Eras Tour");
        assertThat(results.get(0).getVenue()).isEqualTo("Jawaharlal Nehru Stadium, Delhi");
        verify(eventRepository).findAll();
    }
}
