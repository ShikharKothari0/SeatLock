package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import com.ShikharKothari0.SeatLock.kafka.event.SeatHeldEvent;
import com.ShikharKothari0.SeatLock.kafka.producer.SeatEventProducer;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class SeatHoldService {       // A dedicated service that orchestrates both Redis and Postgres for the hold operations
    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);     // Set a TTL for the lock to avoid indefinite holds
    private final RedisLockService redisLockService;
    private final SeatRepository seatRepository;
    private final SeatEventProducer seatEventProducer;

    public SeatHoldService(
            RedisLockService redisLockService,
            SeatRepository seatRepository,
            SeatEventProducer seatEventProducer
    ) {
        this.redisLockService = redisLockService;
        this.seatRepository = seatRepository;
        this.seatEventProducer = seatEventProducer;
    }

    @Transactional
    public void holdSeat(UUID seatId, UUID userId) {
        // Step 1: acquire Redis lock atomically via Lua script
        boolean acquired = redisLockService.acquireSeatHold(
                seatId.toString(),
                userId.toString(),
                HOLD_DURATION
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
            seat.setHoldExpiresAt(Instant.now().plus(HOLD_DURATION));
            seatRepository.save(seat);

            seatEventProducer.publishSeatHeld(new SeatHeldEvent(
                    seatId,
                    userId,
                    seat.getEvent().getId(),
                    Instant.now(),
                    seat.getHoldExpiresAt()
            ));

        } catch (Exception e) {
            // Step 3: compensate — release the Redis lock if Postgres write fails
            redisLockService.releaseLock("seat:lock:" + seatId);
            throw e;
        }
    }
}
