package com.ShikharKothari0.SeatLock.controller;
import com.ShikharKothari0.SeatLock.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog=catalog; }
    @GetMapping("/venues") public Object venues() { return catalog.venues(); }
    @GetMapping("/venues/{id}/sections") public Object sections(@PathVariable UUID id) { return catalog.sections(id); }
    @GetMapping("/events") public Object events() { return catalog.events(); }
    @GetMapping("/events/{id}") public Object preview(@PathVariable UUID id) { return catalog.preview(id); }
    @PutMapping("/events/{id}") public void edit(@PathVariable UUID id,@Valid @RequestBody CatalogService.DraftInput input,Principal actor) { catalog.updateDraft(id,input,UUID.fromString(actor.getName())); }
    @PostMapping("/venues") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Object venue(@Valid @RequestBody CatalogService.VenueInput input,Principal actor) { return Map.of("id",catalog.createVenue(input,UUID.fromString(actor.getName()))); }
    @PostMapping("/events") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Object event(@Valid @RequestBody CatalogService.EventInput input,Principal actor) { return Map.of("id",catalog.createEvent(input,UUID.fromString(actor.getName()))); }
    @PostMapping("/events/{id}/publish") public void publish(@PathVariable UUID id,Principal actor) { catalog.transition(id,"PUBLISHED",UUID.fromString(actor.getName())); }
    @PostMapping("/events/{id}/close") public void close(@PathVariable UUID id,Principal actor) { catalog.transition(id,"CLOSED",UUID.fromString(actor.getName())); }
}
