package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.entity.*;
import com.ShikharKothari0.SeatLock.exception.*;
import com.ShikharKothari0.SeatLock.repository.*;
import com.ShikharKothari0.SeatLock.kafka.producer.SeatEventProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationContractTest {
    final SeatRepository seats = mock(SeatRepository.class);
    final BookingRepository bookings = mock(BookingRepository.class);
    final RedisLockService locks = mock(RedisLockService.class);
    final AppUserRepository users = mock(AppUserRepository.class);
    final SeatEventProducer events = mock(SeatEventProducer.class);
    final SeatCacheService cache = mock(SeatCacheService.class);
    final TransactionEffects effects = mock(TransactionEffects.class);
    final UUID owner = UUID.randomUUID();
    BookingService bookingService() { return new BookingService(users, seats, bookings, locks, events, cache, effects, new SimpleMeterRegistry()); }
    Seat held() {
        Seat seat = new Seat(); seat.setId(UUID.randomUUID()); seat.setHeldBy(owner);
        seat.setStatus(SeatStatus.HELD); seat.setHoldExpiresAt(Instant.now().plusSeconds(120));
        Event event = new Event(); event.setId(UUID.randomUUID()); seat.setEvent(event);
        return seat;
    }
    @Test void rejectsSeatsFromDifferentEventsBeforeChangingState() {
        Seat a = held(), b = held(); var ids = List.of(a.getId(), b.getId());
        when(bookings.findByIdempotencyKey("key")).thenReturn(Optional.empty());
        when(seats.lockSeats(ids)).thenReturn(List.of(a, b));
        assertThrows(SeatNotAvailableException.class, () -> bookingService().confirmBooking(new BookingConfirmRequest(owner, ids, "key")));
        verify(bookings, never()).save(any()); verifyNoInteractions(locks, events);
    }
    @Test void rejectsReusingKeyForAnotherOwnerOrPayload() {
        Seat seat = held(); AppUser user = new AppUser(); user.setId(owner);
        Booking booking = new Booking(); booking.setId(UUID.randomUUID()); booking.setUser(user);
        when(bookings.findByIdempotencyKey("key")).thenReturn(Optional.of(booking));
        when(seats.findByBookingId(booking.getId())).thenReturn(List.of(seat));
        assertThrows(SeatNotAvailableException.class, () -> bookingService().confirmBooking(new BookingConfirmRequest(UUID.randomUUID(), List.of(seat.getId()), "key")));
        assertThrows(SeatNotAvailableException.class, () -> bookingService().confirmBooking(new BookingConfirmRequest(owner, List.of(UUID.randomUUID()), "key")));
        verifyNoInteractions(locks, events);
    }
    @Test void missingRedisHoldIsNotRecoveredFromDatabaseAlone() {
        Seat seat = held(); when(seats.findByHeldByAndStatusAndHoldExpiresAtAfter(eq(owner), eq(SeatStatus.HELD), any())).thenReturn(List.of(seat));
        when(locks.ownedTtlMillis(anyString(), eq(owner.toString()))).thenReturn(-2L);
        assertTrue(new ReservationQueryService(seats, bookings, locks).active(owner).holds().isEmpty());
    }
    @Test void wrongOwnerCannotReleaseOrReadBooking() {
        Seat seat = held(); when(seats.lockSeats(List.of(seat.getId()))).thenReturn(List.of(seat));
        var service = new SeatHoldService(locks, seats, events, cache, effects, new SimpleMeterRegistry());
        assertThrows(SeatNotAvailableException.class, () -> service.release(seat.getId(), UUID.randomUUID()));
        verifyNoInteractions(locks, events);
        AppUser user = new AppUser(); user.setId(owner);
        Booking booking = new Booking(); booking.setUser(user); booking.setId(UUID.randomUUID());
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        assertThrows(ResourceNotFoundException.class, () -> new ReservationQueryService(seats, bookings, locks).booking(booking.getId(), UUID.randomUUID()));
    }
}
