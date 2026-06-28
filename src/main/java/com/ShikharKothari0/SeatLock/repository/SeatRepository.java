package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
}
