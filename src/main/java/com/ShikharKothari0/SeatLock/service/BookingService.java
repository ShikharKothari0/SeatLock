package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.DtoMapper;
import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.entity.*;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import com.ShikharKothari0.SeatLock.repository.AppUserRepository;
import com.ShikharKothari0.SeatLock.repository.BookingRepository;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private final AppUserRepository appUserRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final RedisLockService redisLockService;

    public BookingService(
            AppUserRepository appUserRepository,
            SeatRepository seatRepository,
            BookingRepository bookingRepository,
            RedisLockService redisLockService
    ) {
        this.appUserRepository = appUserRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.redisLockService = redisLockService;
    }

    @Transactional
    public BookingResponse confirmBooking(BookingConfirmRequest request) {

        // Step 1: validate Redis holds exist and belong to this user
        for (UUID seatId : request.seatIds()) {
            String holdOwner = redisLockService.getLockOwner("seat:lock:" + seatId);

            if (holdOwner == null) {
                throw new SeatNotAvailableException(
                        "Hold has expired or does not exist for seat: " + seatId
                );
            }
            if (!holdOwner.equals(request.userId().toString())) {
                throw new SeatNotAvailableException(
                        "Hold for seat " + seatId + " belongs to a different user"
                );
            }
        }

        // Step 2: find the user
        AppUser user = appUserRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + request.userId()
                ));

        // Step 3: find all seats
        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new ResourceNotFoundException("One or more seats not found");
        }

        // Step 4: get the event from the first seat
        Event event = seats.get(0).getEvent();

        // Step 5: create and save the booking
        Booking booking = new Booking(
                null,
                user,
                event,
                BookingStatus.CONFIRMED,
                Instant.now(),
                null,
                request.idempotencyKey()
        );
        bookingRepository.save(booking);

        // Step 6: flip each seat to CONFIRMED and link to this booking
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setBooking(booking);
        }
        seatRepository.saveAll(seats);

        // Step 7: set seats on booking so DtoMapper can access them
        booking.setSeats(seats);

        // Step 8: release Redis holds after successful DB writes
        for (UUID seatId : request.seatIds()) {
            redisLockService.releaseLock("seat:lock:" + seatId);
        }

        return DtoMapper.toBookingResponse(booking);
    }
}
