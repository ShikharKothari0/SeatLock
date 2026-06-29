package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.DtoMapper;
import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/events/{eventId}/seats")
public class SeatController {
    private final SeatRepository seatRepository;

    public SeatController(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @GetMapping
    public List<SeatResponse> getSeats(
            @PathVariable UUID eventId,
            @RequestParam(required = false) SeatStatus status
    ) {
        List<com.ShikharKothari0.SeatLock.entity.Seat> seats =
                (status != null)
                        ? seatRepository.findByEventIdAndStatus(eventId, status)
                        : seatRepository.findByEventId(eventId);

        return seats.stream().map(DtoMapper::toSeatResponse).toList();
    }
}
