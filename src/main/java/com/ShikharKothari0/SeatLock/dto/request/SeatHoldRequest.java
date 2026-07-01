package com.ShikharKothari0.SeatLock.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SeatHoldRequest (
    @NotNull(message = "userId is required")
    UUID userId
) {
}
