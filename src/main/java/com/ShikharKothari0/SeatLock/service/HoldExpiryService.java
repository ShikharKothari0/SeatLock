package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.kafka.event.SeatReleasedEvent;
import com.ShikharKothari0.SeatLock.kafka.producer.SeatEventProducer;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class HoldExpiryService {
    private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

    private final SeatRepository seatRepository;
    private final RedisLockService redisLockService;
    private final SeatEventProducer seatEventProducer;
    private final SeatCacheService seatCacheService;
    private final Counter holdsExpiredCounter;

    public HoldExpiryService(
            SeatRepository seatRepository,
            RedisLockService redisLockService,
            SeatEventProducer seatEventProducer,
            SeatCacheService seatCacheService,
            MeterRegistry meterRegistry
    ) {
        this.seatRepository = seatRepository;
        this.redisLockService = redisLockService;
        this.seatEventProducer = seatEventProducer;
        this.seatCacheService = seatCacheService;

        this.holdsExpiredCounter = Counter.builder("seatlock.holds.expired")
                .description("Number of seat holds released by the expiry job")
                .tag("application", "SeatLock")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 30000)  // runs the job again 30 seconds after the jobs previous execution
    @Transactional
    public void releaseExpiredHolds() {
        Instant now = Instant.now();
        List<Seat> expiredSeats = seatRepository.findExpiredHolds(SeatStatus.HELD, now);

        if (expiredSeats.isEmpty()) {
            return;
        }

        log.info("Hold expiry job: found {} expired holds to release", expiredSeats.size());

        // ── collect distinct event IDs affected in this batch ─────────────────
        // multiple expired seats can belong to different events in one run,
        // so we track distinct event IDs to evict each exactly once
        Set<UUID> affectedEventIds = new HashSet<>();

        for (Seat seat : expiredSeats) {
            String redisKey = "seat:lock:" + seat.getId();

            // clean up Redis key if it somehow still exists (edge case)
            String lockOwner = redisLockService.getLockOwner(redisKey);
            if (lockOwner != null) {
                log.warn(
                        "Redis key {} still exists during expiry cleanup — releasing manually",
                        redisKey
                );
                redisLockService.releaseLock(redisKey);
            }

            seat.setStatus(SeatStatus.AVAILABLE);
            holdsExpiredCounter.increment();
            seat.setHoldExpiresAt(null);

            affectedEventIds.add(seat.getEvent().getId());

            seatEventProducer.publishSeatReleased(new SeatReleasedEvent(
                    seat.getId(),
                    seat.getEvent().getId(),
                    Instant.now(),
                    "HOLD_EXPIRED"
            ));
            log.info("Released expired hold on seat: {}", seat.getId());
        }

        seatRepository.saveAll(expiredSeats);

        // invalidate cache once per distinct event, after all writes commit
        for (UUID eventId : affectedEventIds) {
            seatCacheService.evictCache(eventId);
        }
        log.debug("Cache invalidated for {} distinct events after expiry batch", affectedEventIds.size());

        log.info("Hold expiry job: successfully released {} seats", expiredSeats.size());
    }
}
