package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.dto.DtoMapper;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Service
public class SeatService {
    private final SeatRepository seatRepository;
    private static final Logger log = LoggerFactory.getLogger(SeatService.class);
    private final SeatCacheService seatCacheService;
    private final CacheStampedeProtector stampedeProtector;

    public SeatService(SeatRepository seatRepository,  SeatCacheService seatCacheService, CacheStampedeProtector stampedeProtector) {
        this.seatRepository = seatRepository;
        this.seatCacheService = seatCacheService;
        this.stampedeProtector = stampedeProtector;
    }

    public List<SeatResponse> getSeats(UUID eventId, SeatStatus status) {
        // Step 1: try cache directly (fast path, no lock overhead)
        Optional<List<SeatResponse>> cached = seatCacheService.getCachedSeats(eventId);

        if (cached.isPresent()) {
            log.debug("Serving seats from cache — eventId={} totalCached={} filter={}",
                    eventId, cached.get().size(), status);
            return applyStatusFilter(cached.get(), status);
        }

        // Step 2: cache miss — load with stampede protection
        // only one thread queries Postgres; others wait briefly and
        // retry the cache rather than independently hitting the DB
        log.debug("Cache MISS — loading with stampede protection for eventId={}", eventId);

        List<SeatResponse> response = stampedeProtector.loadWithStampedeProtection(
                eventId,
                () -> seatCacheService.getCachedSeats(eventId).orElse(null),       // cacheReader
                () -> loadFromDatabase(eventId),                                         // databaseLoader
                seats -> seatCacheService.cacheSeats(eventId, seats)   // cacheWriter
        );

        // Step 3: apply status filter
        return applyStatusFilter(response, status);
    }

    private List<SeatResponse> loadFromDatabase(UUID eventId) {
        List<Seat> seats = seatRepository.findByEventId(eventId);
        return seats.stream().map(DtoMapper::toSeatResponse).toList();
    }

    private List<SeatResponse> applyStatusFilter(
            List<SeatResponse> seats, SeatStatus status
    ) {
        if (status == null) {
            return seats;
        }
        return seats.stream()
                .filter(s -> s.status() == status)
                .toList();
    }
}
