package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.service.RazorpayPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
public class RazorpayController {
    private final RazorpayPaymentService payments;
    public RazorpayController(RazorpayPaymentService payments){this.payments=payments;}
    public record Start(@NotNull UUID quoteId,@NotEmpty @Size(max=50) List<@NotNull UUID> seatIds,@NotBlank @Size(max=255) String idempotencyKey) {}
    public record Verify(@NotBlank @Pattern(regexp="pay_[A-Za-z0-9]+") @Size(max=80) String paymentId,@NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String signature) {}
    @GetMapping("/config") public Object config(){return Map.of("provider",payments.enabled()?"RAZORPAY":"DEMO","testMode",true);}
    @PostMapping("/razorpay") public Object start(@Valid @RequestBody Start input,Principal owner){return payments.start(UUID.fromString(owner.getName()),input.quoteId(),input.seatIds(),input.idempotencyKey());}
    @GetMapping("/razorpay/{id}") public Object get(@PathVariable UUID id,Principal owner){return payments.get(id,UUID.fromString(owner.getName()));}
    @PostMapping("/razorpay/{id}/reconcile") public Object reconcile(@PathVariable UUID id,Principal owner){return payments.reconcile(id,UUID.fromString(owner.getName()));}
    @PostMapping("/razorpay/{id}/verify") public Object verify(@PathVariable UUID id,@Valid @RequestBody Verify input,Principal owner){return payments.verify(id,UUID.fromString(owner.getName()),input.paymentId(),input.signature());}
    @PostMapping("/razorpay/webhook") public void webhook(@RequestBody byte[] body,@RequestHeader(value="X-Razorpay-Signature",required=false) String signature){
        if(body.length>65536)throw new IllegalArgumentException("Webhook exceeds size limit");payments.webhook(body,signature);
    }
}
