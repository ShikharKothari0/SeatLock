package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
