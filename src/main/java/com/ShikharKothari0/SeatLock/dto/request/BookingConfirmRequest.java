package com.ShikharKothari0.SeatLock.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BookingConfirmRequest(
        @NotNull(message = "userId is required")
        UUID userId,

        @NotEmpty(message = "seatIds must contain at least one seat")
        @jakarta.validation.constraints.Size(max = 50, message = "At most 50 seats may be confirmed together")
        List<@NotNull UUID> seatIds,

        @jakarta.validation.constraints.NotBlank(message = "idempotencyKey is required")
        @jakarta.validation.constraints.Size(max = 255)
        String idempotencyKey
) {
}
