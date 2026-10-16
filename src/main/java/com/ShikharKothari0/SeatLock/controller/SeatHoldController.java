package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.request.SeatHoldRequest;
import com.ShikharKothari0.SeatLock.exception.RateLimitExceededException;
import com.ShikharKothari0.SeatLock.service.RateLimiterService;
import com.ShikharKothari0.SeatLock.service.SeatHoldService;
import jakarta.validation.Valid;
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
            @Valid @RequestBody SeatHoldRequest request
    ) {
        // rate limit check happens before any business logic
        if(!rateLimiterService.isAllowed(request.userId().toString())) {
            throw new RateLimitExceededException(
                    "Too many hold requests - please wait before trying again"
            );
        }

        seatHoldService.holdSeat(seatId, request.userId());
        return ResponseEntity.ok(reservations.hold(seatId, request.userId()));
    }

    @GetMapping("/holds")
    public ReservationResponse.ActiveHolds activeHolds(@RequestParam UUID userId) { return reservations.active(userId); }

    @DeleteMapping("/{seatId}/hold")
    public ResponseEntity<Void> release(@PathVariable UUID seatId, @RequestParam UUID userId) {
        seatHoldService.release(seatId, userId);
        return ResponseEntity.noContent().build();
    }
}
