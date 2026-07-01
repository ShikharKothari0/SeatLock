package com.ShikharKothari0.SeatLock.dto.response;

import com.ShikharKothari0.SeatLock.entity.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse (
        UUID id,
        UUID userId,
        UUID eventId,
        BookingStatus status,
        Instant createdAt,
        List<UUID> seatIds
){
}
