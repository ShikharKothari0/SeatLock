package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByEventId(UUID eventId);
    List<Seat> findByEventIdAndStatus(UUID eventId, SeatStatus status);

    @Query("SELECT s FROM Seat s WHERE s.status = :status AND s.holdExpiresAt < :now")
    List<Seat> findExpiredHolds(@Param("status") SeatStatus status, @Param("now") Instant now);
}
