package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import com.ShikharKothari0.SeatLock.kafka.event.SeatHeldEvent;
import com.ShikharKothari0.SeatLock.kafka.producer.SeatEventProducer;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class SeatHoldService {       // A dedicated service that orchestrates both Redis and Postgres for the hold operations
    private static final Logger log = LoggerFactory.getLogger(SeatHoldService.class);
    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);     // Set a TTL for the lock to avoid indefinite holds
    private final RedisLockService redisLockService;
    private final SeatRepository seatRepository;
    private final SeatEventProducer seatEventProducer;
    private final TransactionEffects effects;

    private final Counter holdsCreatedCounter;
    private final Counter holdsRejectedCounter;
    private final Timer holdLatencyTimer;

    public SeatHoldService(
            RedisLockService redisLockService,
            SeatRepository seatRepository,
            SeatEventProducer seatEventProducer,
            SeatCacheService seatCacheService,
            TransactionEffects effects,
            MeterRegistry meterRegistry
    ) {
        this.redisLockService = redisLockService;
        this.seatRepository = seatRepository;
        this.seatEventProducer = seatEventProducer;
        this.effects = effects;

        this.holdsCreatedCounter = Counter.builder("seatlock.holds.success")
                .description("Number of seat holds successfully created")
                .tag("application", "SeatLock")
                .register(meterRegistry);

        this.holdsRejectedCounter = Counter.builder("seatlock.holds.rejected")
                .description("Number of seat hold attempts rejected (seat unavailable or already held)")
                .tag("application", "SeatLock")
                .register(meterRegistry);

        this.holdLatencyTimer = Timer.builder("seatlock.holds.latency")
                .description("End-to-end latency of the holdSeat operation")
                .tag("application", "SeatLock")
                .publishPercentiles(0.5, 0.95, 0.99)    // p50, p95, p99
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Transactional
    public void holdSeat(UUID seatId, UUID userId) {
        holdLatencyTimer.record(() -> {
        Seat seat = seatRepository.lockSeats(java.util.List.of(seatId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            holdsRejectedCounter.increment();
            throw new SeatNotAvailableException("Seat is not available");
        }
        UUID token = UUID.randomUUID();
        String lockValue = userId + ":" + token;
        // Register before the network call: a timeout can hide a successful Redis acquisition.
        effects.releaseOnRollback(seatId, lockValue);
        Instant expiresAt = Instant.now().plus(HOLD_DURATION);
        if (!redisLockService.acquireSeatHold(seatId.toString(), lockValue, HOLD_DURATION)) {
            holdsRejectedCounter.increment();
            throw new SeatNotAvailableException("Seat is already held");
        }
        seat.setStatus(SeatStatus.HELD);
        seat.setHeldBy(userId);
        seat.setHoldToken(token);
        seat.setHoldExpiresAt(expiresAt);
        seatRepository.save(seat);
        effects.changed(seat.getEvent().getId());
        effects.afterCommit(holdsCreatedCounter::increment);
        seatEventProducer.publishSeatHeld(new SeatHeldEvent(seatId, userId, seat.getEvent().getId(), Instant.now(), expiresAt));
    });
    }

    @Transactional
    public void release(UUID seatId, UUID userId) {
        Seat seat = seatRepository.lockSeats(java.util.List.of(seatId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found"));
        // Repeating a completed release is harmless, but never release another owner's hold.
        if (seat.getStatus() == SeatStatus.AVAILABLE) return;
        if (seat.getStatus() != SeatStatus.HELD || !userId.equals(seat.getHeldBy()))
            throw new SeatNotAvailableException("This seat is not held by this user");
        effects.releaseAfterCommit(seatId, seat.lockValue());
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setHeldBy(null);
        seat.setHoldToken(null);
        seat.setHoldExpiresAt(null);
        UUID eventId = seat.getEvent().getId();
        effects.changed(eventId);
        seatEventProducer.publishSeatReleased(new com.ShikharKothari0.SeatLock.kafka.event.SeatReleasedEvent(
                seatId, eventId, Instant.now(), "USER_RELEASED"));
    }
}
