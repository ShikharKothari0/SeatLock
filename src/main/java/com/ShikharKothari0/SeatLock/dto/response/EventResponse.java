package com.ShikharKothari0.SeatLock.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String venueName,
        String venueCity,
        Instant saleStartTime,
        Instant eventTime,
        String description, String category, String posterUrl, Integer durationMinutes,
        String publication, Instant saleEndTime
) {
}
