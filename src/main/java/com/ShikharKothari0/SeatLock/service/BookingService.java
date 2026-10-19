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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final TransactionEffects effects;
    private final Counter bookingsConfirmedCounter;

    public BookingService(
            AppUserRepository appUserRepository,
            SeatRepository seatRepository,
            BookingRepository bookingRepository,
            RedisLockService redisLockService,
            SeatEventProducer seatEventProducer,
            SeatCacheService seatCacheService,
            TransactionEffects effects,
            MeterRegistry meterRegistry
    ) {
        this.appUserRepository = appUserRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.redisLockService = redisLockService;
        this.seatEventProducer = seatEventProducer;
        this.effects = effects;

        this.bookingsConfirmedCounter = Counter.builder("seatlock.bookings.confirmed")
                .description("Number of bookings successfully confirmed")
                .tag("application", "SeatLock")
                .register(meterRegistry);
    }

    @Transactional
    public BookingResponse confirmBooking(BookingConfirmRequest request) {

        effects.serializeBooking(request.idempotencyKey());
        // All processes serialize a key before checking for its committed result.
        Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(request.idempotencyKey());

        if (existingBooking.isPresent()) {
            log.info(
                    "Idempotent request detected — returning existing booking {} " + "for idempotencyKey={}",
                    existingBooking.get().getId(),
                    request.idempotencyKey()
            );
            Booking booking = existingBooking.get();
            booking.setSeats(seatRepository.findByBookingId(booking.getId()));
            if (!booking.getUser().getId().equals(request.userId()) || request.seatIds().size() != booking.getSeats().size()
                    || !new java.util.HashSet<>(request.seatIds()).equals(booking.getSeats().stream().map(Seat::getId).collect(java.util.stream.Collectors.toSet())))
                throw new SeatNotAvailableException("Idempotency key belongs to a different booking request");
            return DtoMapper.toBookingResponse(booking);
        }

        List<Seat> seats = seatRepository.lockSeats(request.seatIds());
        if (seats.size() != request.seatIds().size()) throw new SeatNotAvailableException("Invalid or duplicate seats");
        Event event = seats.get(0).getEvent();
        effects.requireSalesOpen(event.getId());
        for (Seat seat : seats) {
            if (!seat.getEvent().getId().equals(event.getId())) throw new SeatNotAvailableException("All seats must belong to the same event");
            if (seat.getStatus() != SeatStatus.HELD || !request.userId().equals(seat.getHeldBy())
                    || seat.getHoldExpiresAt() == null || !seat.getHoldExpiresAt().isAfter(Instant.now()))
                throw new SeatNotAvailableException("Seat hold is no longer valid");
        }
        for (Seat seat : seats) {
            if (redisLockService.ownedTtlMillis("seat:lock:" + seat.getId(), seat.lockValue()) <= 0)
                throw new SeatNotAvailableException("Hold has expired or no longer matches this reservation");
        }

        // Step 3: find the user
        AppUser user = appUserRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + request.userId()
                ));

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
            effects.releaseAfterCommit(seat.getId(), seat.lockValue());
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setBooking(booking);
            seat.setHoldExpiresAt(null);
            seat.setHeldBy(null);
            seat.setHoldToken(null);
        }
        seatRepository.saveAll(seats);

        effects.afterCommit(bookingsConfirmedCounter::increment);
        UUID committedBookingId = booking.getId();
        effects.afterCommit(() -> log.info("Booking committed: bookingId={} userId={} seatCount={}", committedBookingId, request.userId(), seats.size()));

        // Advance the cache revision atomically with the booking.
        effects.changed(event.getId());
        log.debug("Cache revision staged with booking — eventId={} bookingId={}",
                event.getId(), booking.getId());

        // Step 8: set seats on booking so DtoMapper can access them
        booking.setSeats(seats);

        // Step 10: publish SeatConfirmedEvent to Kafka
        seatEventProducer.publishSeatConfirmed(new SeatConfirmedEvent(
                booking.getId(),
                request.userId(),
                event.getId(),
                request.seatIds(),
                Instant.now()
        ));



        return DtoMapper.toBookingResponse(booking);
    }
}
