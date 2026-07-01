package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.request.SeatHoldRequest;
import com.ShikharKothari0.SeatLock.service.RedisLockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
public class SeatHoldController {
    private final RedisLockService redisLockService;

    public SeatHoldController(RedisLockService redisLockService) {
        this.redisLockService = redisLockService;
    }

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<Map<String, Object>> holdSeat(
            @PathVariable UUID seatId,
            @Valid @RequestBody SeatHoldRequest request
    ) {
        boolean acquired = redisLockService.acquireSeatHold(
                seatId.toString(),
                request.userId().toString(),
                Duration.ofMinutes(5)
        );

        if (acquired) {
            return ResponseEntity.ok(Map.of("status", "HELD", "seatId", seatId));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "ALREADY_HELD", "seatId", seatId));
        }
    }
}
