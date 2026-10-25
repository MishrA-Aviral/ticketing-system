package com.aviral.ticketingsystem.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single concert event.
 *
 * Intentionally contains only static, rarely-changing data (name, venue, date, price).
 * Seat counts live in a separate Inventory entity so that concurrent booking
 * updates only ever lock the Inventory row, never the Event row.
 * This is the critical design decision that enables high-concurrency booking
 * without blocking users who are simply browsing event details.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String venue;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Not annotated @Future at the entity level so that tests can use past-dated
    // events (e.g., to simulate expiry scenarios). @Future will be applied on
    // the request DTO in Phase 2 when the API layer is built.
    @NotNull
    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    // BigDecimal is mandatory for monetary values.
    // double/float introduce rounding errors that corrupt financial calculations.
    @NotNull
    @DecimalMin("0.00")
    @Column(name = "ticket_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    // updatable = false: once written on INSERT, never changed by JPA.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Inverse side of the OneToOne. Inventory owns the FK column (event_id).
    // CascadeType.ALL: saving or deleting an Event cascades to its Inventory.
    // @JsonIgnore prevents Jackson infinite recursion and lazy-loading proxy errors during serialization.
    @JsonIgnore
    @OneToOne(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    private Inventory inventory;

    public Event() {}

    public Event(String name, String venue, String description,
                 LocalDateTime eventDate, BigDecimal ticketPrice) {
        this.name = name;
        this.venue = venue;
        this.description = description;
        this.eventDate = eventDate;
        this.ticketPrice = ticketPrice;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId()                         { return id; }
    public String getName()                     { return name; }
    public void setName(String name)            { this.name = name; }
    public String getVenue()                    { return venue; }
    public void setVenue(String venue)          { this.venue = venue; }
    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }
    public LocalDateTime getEventDate()         { return eventDate; }
    public void setEventDate(LocalDateTime d)   { this.eventDate = d; }
    public BigDecimal getTicketPrice()          { return ticketPrice; }
    public void setTicketPrice(BigDecimal p)    { this.ticketPrice = p; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public Inventory getInventory()             { return inventory; }
    public void setInventory(Inventory inv)     { this.inventory = inv; }
}
