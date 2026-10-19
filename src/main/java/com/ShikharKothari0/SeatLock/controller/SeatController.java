package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.service.SeatService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
public class SeatController {
    private final SeatService seatService;
    private final com.ShikharKothari0.SeatLock.service.CatalogService catalog;

    public SeatController(SeatService seatService, com.ShikharKothari0.SeatLock.service.CatalogService catalog) {
        this.seatService = seatService; this.catalog=catalog;
    }

    @GetMapping
    public List<SeatResponse> getSeats(
            @PathVariable UUID eventId,
            @RequestParam(required = false) SeatStatus status
    ) {
        catalog.visible(eventId);
        return seatService.getSeats(eventId, status);
    }
}
