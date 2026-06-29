package com.ShikharKothari0.SeatLock.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String venueName,
        String venueCity,
        Instant saleStartTime,
        Instant eventTime
) {
}
