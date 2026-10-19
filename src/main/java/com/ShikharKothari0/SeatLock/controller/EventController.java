package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.DtoMapper;
import com.ShikharKothari0.SeatLock.dto.response.EventResponse;
import com.ShikharKothari0.SeatLock.entity.Event;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.repository.EventRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventRepository eventRepository;
    private final com.ShikharKothari0.SeatLock.service.CatalogService catalog;

    public EventController(EventRepository eventRepository, com.ShikharKothari0.SeatLock.service.CatalogService catalog) {
        this.eventRepository = eventRepository; this.catalog=catalog;
    }

    @GetMapping
    public List<EventResponse> getAllEvents() {
        return eventRepository.findPublished().stream()
                .map(DtoMapper::toEventResponse)
                .toList();
    }

    @GetMapping("/{id}/prices") public Object prices(@PathVariable UUID id) { return catalog.prices(id); }
    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable UUID id) {
        catalog.visible(id);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
        return DtoMapper.toEventResponse(event);
    }
}
