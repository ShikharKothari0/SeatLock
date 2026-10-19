package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.exception.RateLimitExceededException;
import com.ShikharKothari0.SeatLock.service.RateLimiterService;
import com.ShikharKothari0.SeatLock.service.SeatHoldService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ShikharKothari0.SeatLock.dto.response.ReservationResponse;
import com.ShikharKothari0.SeatLock.service.ReservationQueryService;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
public class SeatHoldController {
    private final SeatHoldService seatHoldService;
    private final RateLimiterService rateLimiterService;
    private final ReservationQueryService reservations;

    public SeatHoldController(SeatHoldService seatHoldService, RateLimiterService rateLimiterService,
            ReservationQueryService reservations) {
        this.seatHoldService = seatHoldService;
        this.rateLimiterService = rateLimiterService;
        this.reservations = reservations;
    }

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<ReservationResponse.Hold> holdSeat(
            @PathVariable UUID seatId,
            java.security.Principal principal
    ) {
        // rate limit check happens before any business logic
        if(!rateLimiterService.isAllowed(UUID.fromString(principal.getName()).toString())) {
            throw new RateLimitExceededException(
                    "Too many hold requests - please wait before trying again"
            );
        }

        seatHoldService.holdSeat(seatId, UUID.fromString(principal.getName()));
        return ResponseEntity.ok(reservations.hold(seatId, UUID.fromString(principal.getName())));
    }

    @GetMapping("/holds")
    public ReservationResponse.ActiveHolds activeHolds(java.security.Principal principal) { return reservations.active(UUID.fromString(principal.getName())); }

    @DeleteMapping("/{seatId}/hold")
    public ResponseEntity<Void> release(@PathVariable UUID seatId, java.security.Principal principal) {
        seatHoldService.release(seatId, UUID.fromString(principal.getName()));
        return ResponseEntity.noContent().build();
    }
}
