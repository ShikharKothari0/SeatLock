package com.ShikharKothari0.SeatLock.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "seat",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Seat {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private String section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Version
    private Long version;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "held_by")
    private UUID heldBy;

    @Column(name = "hold_token")
    private UUID holdToken;

    public String lockValue() {
        return heldBy == null ? null : holdToken == null ? heldBy.toString() : heldBy + ":" + holdToken;
    }

    public Seat(UUID id, Event event, String seatNumber, String section, SeatStatus status,
                Booking booking, Long version, Instant holdExpiresAt) {
        this(id, event, seatNumber, section, status, booking, version, holdExpiresAt, null, null);
    }
}
