package com.ShikharKothari0.SeatLock.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record SeatHeldEvent (
        UUID seatId,
        UUID userId,
        UUID eventId,
        Instant heldAt,
        Instant expiresAt
){
}
