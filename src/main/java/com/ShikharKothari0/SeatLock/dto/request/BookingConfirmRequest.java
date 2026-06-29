package com.ShikharKothari0.SeatLock.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BookingConfirmRequest(
        @NotNull(message = "userId is required")
        UUID userId,

        @NotEmpty(message = "seatIds must contain at least one seat")
        List<UUID> seatIds,

        @NotNull(message = "idempotencyKey is required")
        String idempotencyKey
) {
}
