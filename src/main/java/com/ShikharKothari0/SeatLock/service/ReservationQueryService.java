package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.DtoMapper;
import com.ShikharKothari0.SeatLock.dto.response.ReservationResponse.*;
import com.ShikharKothari0.SeatLock.entity.*;
import com.ShikharKothari0.SeatLock.exception.*;
import com.ShikharKothari0.SeatLock.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ReservationQueryService {
    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final RedisLockService locks;
    public ReservationQueryService(SeatRepository seats, BookingRepository bookings, RedisLockService locks) {
        this.seats = seats; this.bookings = bookings; this.locks = locks;
    }
    private Hold snapshot(Seat seat, UUID userId) {
        Instant now = Instant.now();
        if (seat.getStatus() != SeatStatus.HELD || !userId.equals(seat.getHeldBy())
                || seat.getHoldExpiresAt() == null || !seat.getHoldExpiresAt().isAfter(now)) return null;
        long ttl = locks.ownedTtlMillis("seat:lock:" + seat.getId(), seat.lockValue());
        if (ttl <= 0) return null;
        Instant expiry = now.plusMillis(ttl);
        if (seat.getHoldExpiresAt().isBefore(expiry)) expiry = seat.getHoldExpiresAt();
        return new Hold("HELD", seat.getId(), DtoMapper.toEventResponse(seat.getEvent()),
                DtoMapper.toSeatResponse(seat), expiry, now);
    }
    public Hold hold(UUID seatId, UUID userId) {
        Seat seat = seats.findById(seatId).orElseThrow(() -> new ResourceNotFoundException("Seat not found"));
        Hold hold = snapshot(seat, userId);
        if (hold == null) throw new SeatNotAvailableException("Hold is no longer active");
        return hold;
    }
    public ActiveHolds active(UUID userId) {
        var holds = seats.findByHeldByAndStatusAndHoldExpiresAtAfter(userId, SeatStatus.HELD, Instant.now())
                .stream().map(seat -> snapshot(seat, userId)).filter(Objects::nonNull).toList();
        return new ActiveHolds(holds, Instant.now());
    }
    private Receipt receipt(Booking booking) {
        var bookedSeats = seats.findByBookingId(booking.getId());
        booking.setSeats(bookedSeats);
        return new Receipt(DtoMapper.toBookingResponse(booking), DtoMapper.toEventResponse(booking.getEvent()),
                bookedSeats.stream().map(DtoMapper::toSeatResponse).toList());
    }
    public Receipt booking(UUID id, UUID userId) {
        Booking booking = bookings.findById(id).filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return receipt(booking);
    }
    public History history(UUID userId, int page, int size) {
        var result = bookings.findByUserId(userId, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new History(result.getContent().stream().map(this::receipt).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
