package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.UUID;

@Service
public class SeatHoldService {   // A dedicated service that orchestrates both Redis and Postgres for the hold operations
    private final RedisLockService redisLockService;
    private final SeatRepository seatRepository;

    public SeatHoldService(
            RedisLockService redisLockService,
            SeatRepository seatRepository
    ) {
        this.redisLockService = redisLockService;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public void holdSeat(UUID seatId, UUID userId) {
        // Step 1: acquire Redis lock atomically via Lua script
        boolean acquired = redisLockService.acquireSeatHold(
                seatId.toString(),
                userId.toString(),
                Duration.ofMinutes(5)
        );

        if (!acquired) {
            throw new SeatNotAvailableException("Seat " + seatId + " is already held");
        }

        // Step 2: update Postgres seat status to HELD (within the same transaction)
        try {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatNotAvailableException("Seat " + seatId + " is not available");
            }

            seat.setStatus(SeatStatus.HELD);
            seatRepository.save(seat);

        } catch (Exception e) {
            // Step 3: compensate — release the Redis lock if Postgres write fails
            redisLockService.releaseLock("seat:lock:" + seatId);
            throw e;
        }
    }
}