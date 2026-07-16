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

    public SeatService(SeatRepository seatRepository,  SeatCacheService seatCacheService) {
        this.seatRepository = seatRepository;
        this.seatCacheService = seatCacheService;
    }

    public List<SeatResponse> getSeats(UUID eventId, SeatStatus status) {
        // Step 1: try cache for the full event seat list
        Optional<List<SeatResponse>> cached = seatCacheService.getCachedSeats(eventId);
        if (cached.isPresent()) {
            List<SeatResponse> seats = cached.get();
            log.debug("Serving seats from cache - eventId={} totalCached={} filter={}", eventId, seats.size(), status);

            // filter in-memory on cache hit
            // avoids needing a separate cache key per status
            if(status != null) {
                return seats.stream()
                        .filter(s -> s.status() == status)
                        .toList();
            }
            return seats;
        }
        // Step 2: for cache miss, we query Postgres
        log.debug("Cache miss — querying Postgres for eventId={} filter={}", eventId, status);

        List<Seat> seats = seatRepository.findByEventId(eventId);
        List<SeatResponse> response = seats.stream()
                .map(DtoMapper::toSeatResponse)
                .toList();

        // Step 3: populate cache with the full unfiltered list
        // always cache the complete list — filtering happens in-memory on cache-hits
        // this keeps cache invalidation simple: one key per event
        seatCacheService.cacheSeats(eventId, response);

        // Step 4: apply status filter if required
        if (status != null) {
            return response.stream()
                    .filter(s -> s.status() == status)
                    .toList();
        }
        return response;
    }
}
