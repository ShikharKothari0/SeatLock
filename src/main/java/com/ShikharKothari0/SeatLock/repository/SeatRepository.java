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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id IN :ids ORDER BY s.id")
    List<Seat> lockSeats(@Param("ids") List<UUID> ids);

    List<Seat> findByHeldByAndStatusAndHoldExpiresAtAfter(UUID userId, SeatStatus status, Instant now);
    List<Seat> findByEventId(UUID eventId);
    List<Seat> findByEventIdAndStatus(UUID eventId, SeatStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.status = :status AND s.holdExpiresAt < :now ORDER BY s.id")
    List<Seat> findExpiredHolds(@Param("status") SeatStatus status, @Param("now") Instant now);

    List<Seat> findByBookingId(UUID bookingId);
}
