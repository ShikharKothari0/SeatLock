package com.ShikharKothari0.SeatLock.dto;

import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.dto.response.EventResponse;
import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.Booking;
import com.ShikharKothari0.SeatLock.entity.Event;
import com.ShikharKothari0.SeatLock.entity.Seat;

import java.util.List;
import java.util.UUID;

public class DtoMapper {
    public static EventResponse toEventResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getVenue().getName(),
                event.getVenue().getCity(),
                event.getSaleStartTime(),
                event.getEventTime()
        );
    }

    public static SeatResponse toSeatResponse(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSeatNumber(),
                seat.getSection(),
                seat.getStatus()
        );
    }

    public static BookingResponse toBookingResponse(Booking booking) {
        List<UUID> seatIds = booking.getSeats().stream()
                .map(Seat::getId)
                .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getEvent().getId(),
                booking.getStatus(),
                booking.getCreatedAt(),
                seatIds
        );
    }
}
