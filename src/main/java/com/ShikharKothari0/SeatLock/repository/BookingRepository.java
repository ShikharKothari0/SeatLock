package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    org.springframework.data.domain.Page<Booking> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
