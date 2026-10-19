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
    private final com.ShikharKothari0.SeatLock.service.PricingService pricing;
    private final ReservationQueryService reservations;
    private final com.ShikharKothari0.SeatLock.service.DemoPaymentService payments;

    public BookingController(com.ShikharKothari0.SeatLock.service.PricingService pricing, ReservationQueryService reservations, com.ShikharKothari0.SeatLock.service.DemoPaymentService payments) {
        this.pricing = pricing;
        this.reservations = reservations;
        this.payments = payments;
    }

    @GetMapping("/{bookingId}")
    public ReservationResponse.Receipt booking(@PathVariable UUID bookingId, java.security.Principal principal) {
        return reservations.booking(bookingId, UUID.fromString(principal.getName()));
    }

    @GetMapping
    public ReservationResponse.History history(java.security.Principal principal, @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size) {
        return reservations.history(UUID.fromString(principal.getName()), page, size);
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Confirm(@jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max=50) java.util.List<@jakarta.validation.constraints.NotNull UUID> seatIds, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=255) String idempotencyKey, @jakarta.validation.constraints.NotNull UUID quoteId) {}

    public record QuoteRequest(@jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max=50) java.util.List<@jakarta.validation.constraints.NotNull UUID> seatIds) {}
    @PostMapping("/quote") public Object quote(@Valid @RequestBody QuoteRequest input,java.security.Principal principal) { return pricing.quote(UUID.fromString(principal.getName()),input.seatIds()); }
    @GetMapping("/{bookingId}/price") public Object price(@PathVariable UUID bookingId,java.security.Principal principal) { return pricing.receipt(bookingId,UUID.fromString(principal.getName())); }
    @PostMapping("/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @Valid @RequestBody Confirm request, java.security.Principal principal
    ) {
        // Compatibility endpoint still performs a recorded successful demo payment.
        var owner=UUID.fromString(principal.getName());
        return ResponseEntity.ok(payments.confirmLegacy(owner,request.quoteId(),request.seatIds(),request.idempotencyKey()));
    }
}
