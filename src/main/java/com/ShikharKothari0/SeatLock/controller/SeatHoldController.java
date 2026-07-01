package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.request.SeatHoldRequest;
import com.ShikharKothari0.SeatLock.service.RedisLockService;
import com.ShikharKothari0.SeatLock.service.SeatHoldService;
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
    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<Map<String, Object>> holdSeat(
            @PathVariable UUID seatId,
            @Valid @RequestBody SeatHoldRequest request
    ) {
        seatHoldService.holdSeat(seatId, request.userId());
        return ResponseEntity.ok(Map.of(
                "status", "HELD",
                "seatId", seatId
        ));
    }
}
