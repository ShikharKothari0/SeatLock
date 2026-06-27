package com.ShikharKothari0.SeatLock.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "venue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Venue {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;
}
