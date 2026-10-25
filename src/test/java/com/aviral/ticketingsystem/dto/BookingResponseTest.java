package com.aviral.ticketingsystem.dto;

import com.aviral.ticketingsystem.entity.Booking;
import com.aviral.ticketingsystem.entity.BookingStatus;
import com.aviral.ticketingsystem.entity.Event;
import com.aviral.ticketingsystem.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit tests for BookingResponse mapping.
 */
class BookingResponseTest {

    @Test
    @DisplayName("fromEntity maps all Booking, Event, and User fields correctly")
    void fromEntity_MapsAllFieldsCorrectly() {
        User user = new User("John Doe", "john@example.com");
        ReflectionTestUtils.setField(user, "id", 10L);

        Event event = new Event(
            "Coldplay: Music of the Spheres World Tour",
            "Narendra Modi Stadium, Ahmedabad",
            "Concert tour",
            LocalDateTime.of(2027, 3, 15, 19, 0),
            new BigDecimal("150.00")
        );
        ReflectionTestUtils.setField(event, "id", 20L);

        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", 100L);
        booking.setUser(user);
        booking.setEvent(event);
        booking.setQuantity(2);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("300.00"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(10);
        ReflectionTestUtils.setField(booking, "createdAt", now);
        booking.setExpiresAt(expiresAt);

        BookingResponse response = BookingResponse.fromEntity(booking);

        assertThat(response.bookingId()).isEqualTo(100L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.eventId()).isEqualTo(20L);
        assertThat(response.eventName()).isEqualTo("Coldplay: Music of the Spheres World Tour");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }
}
