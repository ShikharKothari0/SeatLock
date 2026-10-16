package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.service.BookingService;
import jakarta.validation.Valid;
import java.util.UUID;
import com.ShikharKothari0.SeatLock.dto.response.ReservationResponse;
import com.ShikharKothari0.SeatLock.service.ReservationQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;
    private final ReservationQueryService reservations;

    public BookingController(BookingService bookingService, ReservationQueryService reservations) {
        this.bookingService = bookingService;
        this.reservations = reservations;
    }

    @GetMapping("/{bookingId}")
    public ReservationResponse.Receipt booking(@PathVariable UUID bookingId, @RequestParam UUID userId) {
        return reservations.booking(bookingId, userId);
    }

    @GetMapping
    public ReservationResponse.History history(@RequestParam UUID userId, @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size) {
        return reservations.history(userId, page, size);
    }

    @PostMapping("/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @Valid @RequestBody BookingConfirmRequest request
    ) {
        BookingResponse response = bookingService.confirmBooking(request);
        return ResponseEntity.ok(response);
    }
}
