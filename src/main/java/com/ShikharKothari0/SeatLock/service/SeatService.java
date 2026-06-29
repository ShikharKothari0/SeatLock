package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.dto.DtoMapper;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class SeatService {
    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<SeatResponse> getSeats(UUID eventId, SeatStatus status) {
        List<Seat> seats = (status != null)
                ? seatRepository.findByEventIdAndStatus(eventId, status)
                : seatRepository.findByEventId(eventId);
        return seats.stream().map(DtoMapper::toSeatResponse).toList();
    }

    public void reserveSeatNaively(UUID seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException("Seat is not available: " + seatId);
        }

        seat.setStatus(SeatStatus.HELD);
        seatRepository.save(seat);
    }
}
