package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.DtoMapper;
import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.entity.*;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import com.ShikharKothari0.SeatLock.kafka.event.SeatConfirmedEvent;
import com.ShikharKothari0.SeatLock.kafka.producer.SeatEventProducer;
import com.ShikharKothari0.SeatLock.repository.AppUserRepository;
import com.ShikharKothari0.SeatLock.repository.BookingRepository;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final AppUserRepository appUserRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final RedisLockService redisLockService;
    private final SeatEventProducer seatEventProducer;
    private final SeatCacheService seatCacheService;

    public BookingService(
            AppUserRepository appUserRepository,
            SeatRepository seatRepository,
            BookingRepository bookingRepository,
            RedisLockService redisLockService,
            SeatEventProducer seatEventProducer,
            SeatCacheService seatCacheService
    ) {
        this.appUserRepository = appUserRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.redisLockService = redisLockService;
        this.seatEventProducer = seatEventProducer;
        this.seatCacheService = seatCacheService;
    }

    @Transactional
    public BookingResponse confirmBooking(BookingConfirmRequest request) {

        // Step 1: idempotency check
        Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(request.idempotencyKey());

        if (existingBooking.isPresent()) {
            log.info(
                    "Idempotent request detected — returning existing booking {} " + "for idempotencyKey={}",
                    existingBooking.get().getId(),
                    request.idempotencyKey()
            );
            Booking booking = existingBooking.get();
            booking.setSeats(seatRepository.findByBookingId(booking.getId()));
            return DtoMapper.toBookingResponse(booking);
        }

        // Step 2: validate Redis holds exist and belong to this user
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

        // Step 3: find the user
        AppUser user = appUserRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + request.userId()
                ));

        // Step 4: find all seats
        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new ResourceNotFoundException("One or more seats not found");
        }

        // Step 5: get the event from the first seat
        Event event = seats.get(0).getEvent();

        // Step 6: create and save the booking
        Booking booking = new Booking(
                null,
                user,
                event,
                BookingStatus.CONFIRMED,
                Instant.now(),
                null,
                request.idempotencyKey()
        );
        booking = bookingRepository.save(booking);

        // Step 7: flip each seat to CONFIRMED and link to this booking
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setBooking(booking);
            seat.setHoldExpiresAt(null);
        }
        seatRepository.saveAll(seats);

        // invalidate cache after seat status writes commit
        seatCacheService.evictCache(event.getId());
        log.debug("Cache invalidated after booking confirmed — eventId={} bookingId={}",
                event.getId(), booking.getId());

        // Step 8: set seats on booking so DtoMapper can access them
        booking.setSeats(seats);

        // Step 9: release Redis holds after successful DB writes
        for (UUID seatId : request.seatIds()) {
            redisLockService.releaseLock("seat:lock:" + seatId);
        }

        // Step 10: publish SeatConfirmedEvent to Kafka
        seatEventProducer.publishSeatConfirmed(new SeatConfirmedEvent(
                booking.getId(),
                request.userId(),
                event.getId(),
                request.seatIds(),
                Instant.now()
        ));

        log.info("Booking confirmed: bookingId:{} userId:{} seatCount:{}", booking.getId(), request.userId(), seats.size());

        return DtoMapper.toBookingResponse(booking);
    }
}
