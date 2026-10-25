package com.aviral.ticketingsystem.controller;

import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for EventController exposing GET /events.
 */
@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController eventController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(eventController).build();
    }

    @Test
    @DisplayName("GET /events returns HTTP 200 with JSON array of events")
    void getAllEvents_ReturnsEventsArray() throws Exception {
        Event event1 = new Event(
            "Coldplay: Music of the Spheres World Tour",
            "Narendra Modi Stadium, Ahmedabad",
            "Experience Coldplay's spectacular world tour.",
            LocalDateTime.of(2027, 3, 15, 19, 0),
            new BigDecimal("150.00")
        );
        Event event2 = new Event(
            "Ed Sheeran: Mathematics Tour",
            "D.Y. Patil Stadium, Mumbai",
            "An intimate evening with Ed Sheeran.",
            LocalDateTime.of(2027, 4, 22, 20, 0),
            new BigDecimal("120.00")
        );

        when(eventService.getAllEvents()).thenReturn(List.of(event1, event2));

        mockMvc.perform(get("/events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Coldplay: Music of the Spheres World Tour"))
                .andExpect(jsonPath("$[0].venue").value("Narendra Modi Stadium, Ahmedabad"))
                .andExpect(jsonPath("$[0].ticketPrice").value(150.00))
                .andExpect(jsonPath("$[1].name").value("Ed Sheeran: Mathematics Tour"))
                .andExpect(jsonPath("$[1].venue").value("D.Y. Patil Stadium, Mumbai"))
                .andExpect(jsonPath("$[1].ticketPrice").value(120.00));

        verify(eventService).getAllEvents();
    }

    @Test
    @DisplayName("GET /events returns HTTP 200 with empty array when no events exist")
    void getAllEvents_WhenEmpty_ReturnsEmptyArray() throws Exception {
        when(eventService.getAllEvents()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));

        verify(eventService).getAllEvents();
    }
}
