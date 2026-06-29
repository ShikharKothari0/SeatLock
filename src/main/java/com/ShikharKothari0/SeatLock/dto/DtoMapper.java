package com.ShikharKothari0.SeatLock.dto;

import com.ShikharKothari0.SeatLock.dto.response.EventResponse;
import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.Event;
import com.ShikharKothari0.SeatLock.entity.Seat;

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
}
