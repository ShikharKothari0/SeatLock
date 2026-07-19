package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.request.SeatHoldRequest;
import com.ShikharKothari0.SeatLock.exception.RateLimitExceededException;
import com.ShikharKothari0.SeatLock.service.RateLimiterService;
import com.ShikharKothari0.SeatLock.service.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
public class SeatHoldController {
    private final SeatHoldService seatHoldService;
    private final RateLimiterService rateLimiterService;

    public SeatHoldController(SeatHoldService seatHoldService,  RateLimiterService rateLimiterService) {
        this.seatHoldService = seatHoldService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<Map<String, Object>> holdSeat(
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
        return ResponseEntity.ok(Map.of(
                "status", "HELD",
                "seatId", seatId
        ));
    }
}
