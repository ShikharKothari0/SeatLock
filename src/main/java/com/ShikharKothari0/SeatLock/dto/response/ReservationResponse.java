package com.ShikharKothari0.SeatLock.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReservationResponse {
    private ReservationResponse() {}
    public record Hold(String status, UUID seatId, EventResponse event, SeatResponse seat,
                       Instant expiresAt, Instant serverTime) {}
    public record ActiveHolds(List<Hold> holds, Instant serverTime) {}
    public record Receipt(BookingResponse booking, EventResponse event, List<SeatResponse> seats) {}
    public record History(List<Receipt> items, int page, int size, long totalElements, int totalPages) {}
}
