package com.ShikharKothari0.SeatLock.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record SeatReleasedEvent (
        UUID seatId,
        UUID eventId,
        Instant releasedAt,
        String reason
){
}
