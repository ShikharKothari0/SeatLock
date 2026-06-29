package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByEventId(UUID eventId);
    List<Seat> findByEventIdAndStatus(UUID eventId, SeatStatus status);
}
