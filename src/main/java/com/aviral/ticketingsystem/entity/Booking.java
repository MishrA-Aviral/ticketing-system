package com.aviral.ticketingsystem.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a user's attempt to purchase tickets for an event.
 *
 * Key design decisions:
 *
 * totalAmount is stored at booking time (ticketPrice × quantity).
 * This locks in the price the customer agreed to pay, so that a later change
 * to the event's ticketPrice cannot retroactively alter an in-progress booking.
 *
 * expiresAt is an explicit timestamp rather than a duration, so the expiry
 * scheduler in Phase 4 can use a simple indexed query:
 *   WHERE status = 'PENDING_PAYMENT' AND expires_at < NOW()
 * An index on (status, expires_at) makes this query fast even at scale.
 *
 * The tickets collection is empty until the booking is CONFIRMED.
 * Ticket creation is deferred to Phase 6.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Business rule: a single booking may cover 1 to 6 seats.
    @Min(1)
    @Max(6)
    @Column(nullable = false)
    private int quantity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // Price locked at reservation time. Never recalculated from the Event.
    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // The deadline for payment. After this timestamp the booking will be
    // expired by the scheduler and the seats returned to inventory.
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Set only when status transitions to CONFIRMED.
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // Individual ticket records. Empty until booking is CONFIRMED (Phase 6).
    // orphanRemoval = true: if a ticket is removed from this list, it is
    // deleted from the database automatically — prevents orphaned rows.
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Ticket> tickets = new ArrayList<>();

    public Booking() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId()                              { return id; }
    public User getUser()                            { return user; }
    public void setUser(User user)                   { this.user = user; }
    public Event getEvent()                          { return event; }
    public void setEvent(Event event)                { this.event = event; }
    public int getQuantity()                         { return quantity; }
    public void setQuantity(int quantity)             { this.quantity = quantity; }
    public BookingStatus getStatus()                 { return status; }
    public void setStatus(BookingStatus status)      { this.status = status; }
    public BigDecimal getTotalAmount()               { return totalAmount; }
    public void setTotalAmount(BigDecimal amount)    { this.totalAmount = amount; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public LocalDateTime getExpiresAt()              { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt){ this.expiresAt = expiresAt; }
    public LocalDateTime getConfirmedAt()            { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime t)      { this.confirmedAt = t; }
    public List<Ticket> getTickets()                 { return tickets; }
}
