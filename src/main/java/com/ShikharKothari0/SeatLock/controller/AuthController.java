package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts) { this.accounts = accounts; }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Registration(@NotBlank @Email @Size(max=255) String email,
        @NotBlank @Size(max=100) String displayName, @NotBlank @Size(min=12,max=72) String password) {}

    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken token) { return Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }

    @GetMapping("/me")
    public AccountService.Account me(Principal principal) { return accounts.account(principal.getName()); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody Registration registration) {
        if (registration.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        try { accounts.register(registration.email(), registration.displayName(), registration.password()); }
        catch (DataIntegrityViolationException conflict) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register that email"); }
    }
}
