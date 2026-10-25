package com.aviral.ticketingsystem.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Check;

/**
 * Tracks seat availability for a single Event.
 *
 * Kept deliberately separate from Event so that concurrent booking
 * transactions can lock only this row (via SELECT FOR UPDATE in Phase 3)
 * without blocking users reading event details.
 *
 * Availability rule:
 *   availableCount = totalCapacity - reservedCount - soldCount
 *
 * availableCount is NOT stored in the database — it is a derived value
 * computed on demand via getAvailableCount(). This eliminates the risk of
 * the three counters and the stored available field getting out of sync.
 *
 * Lifecycle of a seat:
 *   AVAILABLE  → reservedCount++  (reservation created, PENDING_PAYMENT)
 *   RESERVED   → soldCount++, reservedCount-- (booking confirmed, payment success)
 *   RESERVED   → reservedCount-- (reservation expired, seat returned to pool)
 */
@Entity
@Table(name = "inventory")
@Check(constraints = "reserved_count + sold_count <= total_capacity")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side of the OneToOne: this table holds the event_id FK column.
    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Min(1)
    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    // Seats currently held by PENDING_PAYMENT bookings.
    @Min(0)
    @Column(name = "reserved_count", nullable = false)
    private int reservedCount;

    // Seats belonging to CONFIRMED bookings.
    @Min(0)
    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    public Inventory() {}

    public Inventory(Event event, int totalCapacity) {
        this.event = event;
        this.totalCapacity = totalCapacity;
        this.reservedCount = 0;
        this.soldCount = 0;
    }

    /**
     * Returns the number of seats currently open for reservation.
     * @Transient tells JPA not to create a database column for this method.
     */
    @Transient
    public int getAvailableCount() {
        return totalCapacity - reservedCount - soldCount;
    }

    public Long getId()                              { return id; }
    public Event getEvent()                          { return event; }
    public void setEvent(Event event)                { this.event = event; }
    public int getTotalCapacity()                    { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity)  { this.totalCapacity = totalCapacity; }
    public int getReservedCount()                    { return reservedCount; }
    public void setReservedCount(int reservedCount)  { this.reservedCount = reservedCount; }
    public int getSoldCount()                        { return soldCount; }
    public void setSoldCount(int soldCount)          { this.soldCount = soldCount; }
}
