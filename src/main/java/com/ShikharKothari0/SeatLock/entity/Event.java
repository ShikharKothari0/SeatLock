package com.ShikharKothari0.SeatLock.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Event {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "sale_start_time", nullable = false)
    private Instant saleStartTime;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;
    private String description = "";
    private String category = "Other";
    @Column(name="poster_url")
    private String posterUrl = "";
    @Column(name="duration_minutes")
    private Integer durationMinutes;
    private String publication = "PUBLISHED";
    @Column(name="sale_end_time")
    private Instant saleEndTime;

    public Event(UUID id, String name, Venue venue, Instant saleStartTime, Instant eventTime) {
        this.id=id; this.name=name; this.venue=venue; this.saleStartTime=saleStartTime; this.eventTime=eventTime;
    }
}
