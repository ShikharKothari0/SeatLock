package com.ShikharKothari0.SeatLock.kafka.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatConfirmedEvent(
        UUID bookingId,
        UUID userId,
        UUID eventId,
        List<UUID> seatIds,
        Instant confirmedAt
) {
}
