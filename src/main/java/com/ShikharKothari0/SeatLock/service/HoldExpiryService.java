package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class HoldExpiryService {
    private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

    private final SeatRepository seatRepository;
    private final RedisLockService redisLockService;

    public HoldExpiryService(
            SeatRepository seatRepository,
            RedisLockService redisLockService
    ) {
        this.seatRepository = seatRepository;
        this.redisLockService = redisLockService;
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
            seat.setHoldExpiresAt(null);
            log.info("Released expired hold on seat: {}", seat.getId());
        }

        seatRepository.saveAll(expiredSeats);
        log.info("Hold expiry job: successfully released {} seats", expiredSeats.size());
    }
}
