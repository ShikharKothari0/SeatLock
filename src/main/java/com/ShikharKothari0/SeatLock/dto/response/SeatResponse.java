package com.ShikharKothari0.SeatLock.dto.response;

import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import java.util.UUID;

public record SeatResponse(
        UUID id,
        String seatNumber,
        String section,
        SeatStatus status
) {
}
