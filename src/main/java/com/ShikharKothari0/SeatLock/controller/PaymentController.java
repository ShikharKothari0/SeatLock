package com.ShikharKothari0.SeatLock.controller;
import com.ShikharKothari0.SeatLock.service.DemoPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final DemoPaymentService payments;
    public PaymentController(DemoPaymentService payments) { this.payments=payments; }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Start(@NotNull UUID quoteId,@NotEmpty @Size(max=50) List<@NotNull UUID> seatIds,
        @NotBlank @Size(max=255) String idempotencyKey,@NotNull DemoPaymentService.Method method,@NotNull DemoPaymentService.Scenario scenario) {}
    @PostMapping public DemoPaymentService.Attempt start(@Valid @RequestBody Start input,Principal principal) {
        return payments.start(UUID.fromString(principal.getName()),input.quoteId(),input.seatIds(),input.idempotencyKey(),input.method(),input.scenario());
    }
    @GetMapping("/{id}") public DemoPaymentService.Attempt get(@PathVariable UUID id,Principal principal) { return payments.get(id,UUID.fromString(principal.getName())); }
    @PostMapping("/{id}/process") public DemoPaymentService.Attempt process(@PathVariable UUID id,Principal principal) { return payments.process(id,UUID.fromString(principal.getName())); }
}
