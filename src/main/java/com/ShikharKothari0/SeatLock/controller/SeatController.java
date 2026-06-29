package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
public class SeatController {
    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping
    public List<SeatResponse> getSeats(
            @PathVariable UUID eventId,
            @RequestParam(required = false) SeatStatus status
    ) {
        return seatService.getSeats(eventId, status);
    }

    @PostMapping("/{seatId}/reserve")
    public ResponseEntity<Void> reserveSeat(@PathVariable UUID eventId, @PathVariable UUID seatId) {
        seatService.reserveSeatNaively(seatId);
        return ResponseEntity.ok().build();
    }
}
