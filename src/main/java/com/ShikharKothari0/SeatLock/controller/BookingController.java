package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @Valid @RequestBody BookingConfirmRequest request
    ) {
        BookingResponse response = bookingService.confirmBooking(request);
        return ResponseEntity.ok(response);
    }
}
