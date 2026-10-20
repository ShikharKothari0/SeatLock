package com.ShikharKothari0.SeatLock.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import java.time.*;
import java.util.*;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;
    public CatalogService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Section(@NotBlank @Size(max=50) String name, @Pattern(regexp="[A-Za-z0-9]{1,8}") @NotNull String prefix,
                          @Min(1) @Max(1000) int seats) {}
    public record VenueInput(@NotBlank @Size(max=255) String name, @NotBlank @Size(max=255) String city,
        @NotBlank @Size(max=500) String address, @NotBlank String timezone, @NotEmpty @Size(max=20) List<@Valid Section> sections) {}
    public record Price(@NotBlank @Size(max=50) String section, @NotNull @Min(0) @Max(100000000) Long amountMinor) {}
    public record EventInput(@NotNull UUID venueId, @NotBlank @Size(max=255) String name,
        @NotNull Instant saleStartTime, @NotNull Instant saleEndTime, @NotNull Instant eventTime,
        @Min(0) @Max(100000000) long feeMinor, @NotEmpty @Size(max=20) List<@Valid Price> prices) {}
    public record DraftInput(@NotNull @Min(0) Long revision, @NotBlank @Size(max=255) String name,
        @NotNull @Size(max=5000) String description,
        @NotNull @Pattern(regexp="Music|Comedy|Theatre|Sports|Workshop|Other") String category,
        @NotNull @Size(max=2048) String posterUrl, @Min(1) @Max(1440) Integer durationMinutes,
        @NotNull Instant saleStartTime,@NotNull Instant saleEndTime,@NotNull Instant eventTime,
        @NotNull @Min(0) @Max(100000000) Long feeMinor,@NotEmpty @Size(max=20) List<@Valid Price> prices) {}
    public record ContentInput(@NotNull @Min(0) Long revision,
        @NotNull @Size(max=5000) String description,
        @NotNull @Pattern(regexp="Music|Comedy|Theatre|Sports|Workshop|Other") String category,
        @NotNull @Size(max=2048) String posterUrl) {}
    private String validatedPoster(String value) {
        String poster=value.strip();
        if(!poster.isEmpty()) {
            try {
                var uri=java.net.URI.create(poster);
                if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
                    throw new IllegalArgumentException();
            } catch(IllegalArgumentException ex) { throw new IllegalArgumentException("Poster must be a valid HTTPS image URL without credentials"); }
        }
        return poster;
    }
    @Transactional public void updateContent(UUID id,ContentInput input,UUID actor) {
        var rows=jdbc.queryForList("SELECT revision FROM event WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Event not found");
        if(((Number)rows.getFirst().get("revision")).longValue()!=input.revision())
            throw new SeatNotAvailableException("This event changed. Reload it before saving again");
        String poster=validatedPoster(input.posterUrl());
        jdbc.update("UPDATE event SET description=?,category=?,poster_url=?,revision=revision+1 WHERE id=?",
            input.description().strip(),input.category(),poster,id);
        audit(actor,"EDIT_CONTENT",id);
    }
    @Transactional public void archive(UUID id,UUID actor) {
        var rows=jdbc.queryForList("SELECT publication,archived FROM event WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Event not found");
        if("DRAFT".equals(rows.getFirst().get("publication"))) throw new SeatNotAvailableException("Delete an unused draft instead of archiving it");
        if(Boolean.TRUE.equals(rows.getFirst().get("archived"))) return;
        jdbc.update("UPDATE event SET archived=true,publication='CLOSED',revision=revision+1 WHERE id=?",id);
        audit(actor,"ARCHIVE",id);
    }
    @Transactional public void deleteDraft(UUID id,UUID actor) {
        var rows=jdbc.queryForList("SELECT publication FROM event WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Event not found");
        if(!"DRAFT".equals(rows.getFirst().get("publication"))) throw new SeatNotAvailableException("Only unpublished drafts can be deleted");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM booking WHERE event_id=?) OR EXISTS(SELECT 1 FROM checkout_quote WHERE event_id=?) OR EXISTS(SELECT 1 FROM seat WHERE event_id=? AND (status<>'AVAILABLE' OR booking_id IS NOT NULL))",Boolean.class,id,id,id)))
            throw new SeatNotAvailableException("This event has reservation records and cannot be deleted");
        jdbc.update("DELETE FROM event_price WHERE event_id=?",id);
        jdbc.update("DELETE FROM seat WHERE event_id=?",id);
        jdbc.update("DELETE FROM event WHERE id=?",id);
        audit(actor,"DELETE_DRAFT",id);
    }
    public Map<String,Object> preview(UUID id) {
        var rows=jdbc.queryForList("SELECT e.*,v.name AS venue_name,v.city AS venue_city FROM event e JOIN venue v ON v.id=e.venue_id WHERE e.id=?",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Event not found");
        var event=rows.getFirst();
        event.put("prices",jdbc.queryForList("SELECT section,amount_minor AS \"amountMinor\" FROM event_price WHERE event_id=? ORDER BY section",id));
        return event;
    }
    @Transactional public void updateDraft(UUID id,DraftInput input,UUID actor) {
        var rows=jdbc.queryForList("SELECT publication,revision FROM event WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Event not found");
        var event=rows.getFirst();
        if(!"DRAFT".equals(event.get("publication"))) throw new SeatNotAvailableException("Only drafts can be edited. Published events and existing bookings are protected");
        if(((Number)event.get("revision")).longValue()!=input.revision()) throw new SeatNotAvailableException("This draft changed. Reload it before saving again");
        if(!input.saleStartTime().isBefore(input.saleEndTime()) || input.saleEndTime().isAfter(input.eventTime()) || !input.eventTime().isAfter(Instant.now()))
            throw new IllegalArgumentException("Sales must start before closing and close no later than the future event time");
        String poster=validatedPoster(input.posterUrl());
        var expected=new HashSet<>(jdbc.queryForList("SELECT DISTINCT section FROM seat WHERE event_id=?",String.class,id));
        var prices=new HashMap<String,Long>();
        for(var price:input.prices()) if(prices.put(price.section(),price.amountMinor())!=null) throw new IllegalArgumentException("Duplicate section price");
        if(!prices.keySet().equals(expected)) throw new IllegalArgumentException("Provide exactly one price for every event section");
        jdbc.update("UPDATE event SET name=?,description=?,category=?,poster_url=?,duration_minutes=?,sale_start_time=?,sale_end_time=?,event_time=?,fee_minor=?,revision=revision+1 WHERE id=?",
            input.name().strip(),input.description().strip(),input.category(),poster,input.durationMinutes(),java.sql.Timestamp.from(input.saleStartTime()),java.sql.Timestamp.from(input.saleEndTime()),java.sql.Timestamp.from(input.eventTime()),input.feeMinor(),id);
        prices.forEach((section,amount)->jdbc.update("UPDATE event_price SET amount_minor=? WHERE event_id=? AND section=?",amount,id,section));
        audit(actor,"EDIT_DRAFT",id);
    }
    public List<Map<String,Object>> venues() { return jdbc.queryForList("SELECT v.*, (SELECT count(*) FROM venue_seat s WHERE s.venue_id=v.id) AS layout_seats FROM venue v ORDER BY name"); }
    public List<Map<String,Object>> events() {
        var events=jdbc.queryForList("SELECT e.*,v.name AS venue_name FROM event e JOIN venue v ON v.id=e.venue_id ORDER BY e.event_time DESC");
        events.forEach(event->event.put("prices",jdbc.queryForList("SELECT section,amount_minor AS \"amountMinor\" FROM event_price WHERE event_id=? ORDER BY section",event.get("id"))));
        return events;
    }
    public List<Map<String,Object>> sections(UUID venue) { return jdbc.queryForList("SELECT section,count(*) AS seats FROM venue_seat WHERE venue_id=? GROUP BY section ORDER BY section",venue); }
    public Map<String,Object> prices(UUID event) {
        visible(event);
        return Map.of("currency","INR","feeMinor",jdbc.queryForObject("SELECT fee_minor FROM event WHERE id=?",Long.class,event),
            "sections",jdbc.queryForList("SELECT section,amount_minor AS \"amountMinor\" FROM event_price WHERE event_id=? ORDER BY section",event));
    }
    public void visible(UUID event) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM event WHERE id=? AND publication<>'DRAFT' AND NOT archived)",Boolean.class,event)))
            throw new ResourceNotFoundException("Event not found");
    }
    private void audit(UUID actor,String action,UUID id) { jdbc.update("INSERT INTO catalog_audit(id,actor_id,action,resource_id) VALUES (?,?,?,?)",UUID.randomUUID(),actor,action,id); }
    @Transactional public UUID createVenue(VenueInput input,UUID actor) {
        try { ZoneId.of(input.timezone()); } catch (DateTimeException ex) { throw new IllegalArgumentException("Invalid venue timezone"); }
        var names=new HashSet<String>(); var prefixes=new HashSet<String>(); int total=0;
        for(var section:input.sections()) {
            if(!names.add(section.name().strip()) || !prefixes.add(section.prefix())) throw new IllegalArgumentException("Section names and seat prefixes must be unique");
            total+=section.seats();
        }
        if(total>5000) throw new IllegalArgumentException("At most 5000 seats per venue");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO venue(id,name,city,total_capacity,address,timezone) VALUES (?,?,?,?,?,?)",id,input.name().strip(),input.city().strip(),total,input.address().strip(),input.timezone());
        for(var section:input.sections()) for(int number=1;number<=section.seats();number++)
            jdbc.update("INSERT INTO venue_seat(id,venue_id,seat_number,section) VALUES (?,?,?,?)",UUID.randomUUID(),id,section.prefix()+number,section.name().strip());
        audit(actor,"CREATE_VENUE",id); return id;
    }
    @Transactional public UUID createEvent(EventInput input,UUID actor) {
        if (!input.saleStartTime().isBefore(input.saleEndTime()) || input.saleEndTime().isAfter(input.eventTime()) || !input.eventTime().isAfter(Instant.now()))
            throw new IllegalArgumentException("Sales must start before closing and close no later than the future event time");
        var seats=jdbc.queryForList("SELECT seat_number,section FROM venue_seat WHERE venue_id=? ORDER BY seat_number",input.venueId());
        if(seats.isEmpty()) throw new IllegalArgumentException("Choose a venue with a seating layout");
        var expected=new HashSet<String>(); seats.forEach(s->expected.add((String)s.get("section")));
        var prices=new HashMap<String,Long>();
        for(var price:input.prices()) if(prices.put(price.section(),price.amountMinor())!=null) throw new IllegalArgumentException("Duplicate section price");
        if(!prices.keySet().equals(expected)) throw new IllegalArgumentException("Provide exactly one price for every venue section");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO event(id,name,venue_id,sale_start_time,sale_end_time,event_time,publication,fee_minor) VALUES (?,?,?,?,?,?,'DRAFT',?)",id,input.name().strip(),input.venueId(),java.sql.Timestamp.from(input.saleStartTime()),java.sql.Timestamp.from(input.saleEndTime()),java.sql.Timestamp.from(input.eventTime()),input.feeMinor());
        for(var seat:seats) jdbc.update("INSERT INTO seat(id,event_id,seat_number,section,status) VALUES (?,?,?,?,'AVAILABLE')",UUID.randomUUID(),id,seat.get("seat_number"),seat.get("section"));
        prices.forEach((section,amount)->jdbc.update("INSERT INTO event_price VALUES (?,?,?)",id,section,amount));
        audit(actor,"CREATE_EVENT",id); return id;
    }
    @Transactional public void transition(UUID event,String next,UUID actor) {
        var rows=jdbc.queryForList("SELECT publication,event_time,sale_end_time,archived FROM event WHERE id=? FOR UPDATE",event);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Event not found");
        String current=(String)rows.getFirst().get("publication");
        boolean reopening=next.equals("REOPEN");
        if (Boolean.TRUE.equals(rows.getFirst().get("archived"))) throw new SeatNotAvailableException("Archived events cannot reopen sales");
        if (!(current.equals("DRAFT")&&next.equals("PUBLISHED") || current.equals("PUBLISHED")&&next.equals("CLOSED") || current.equals("CLOSED")&&reopening))
            throw new SeatNotAvailableException("This event cannot make the requested sales transition");
        if(reopening) {
            if(!((java.sql.Timestamp)rows.getFirst().get("sale_end_time")).toInstant().isAfter(Instant.now())) throw new SeatNotAvailableException("The scheduled sales window has ended");
            next="PUBLISHED";
        }
        if(next.equals("PUBLISHED")) {
            if(!((java.sql.Timestamp)rows.getFirst().get("event_time")).toInstant().isAfter(Instant.now())) throw new SeatNotAvailableException("Event time has passed");
            if(jdbc.queryForObject("SELECT count(*) FROM seat s LEFT JOIN event_price p ON p.event_id=s.event_id AND p.section=s.section WHERE s.event_id=? AND p.amount_minor IS NULL",Integer.class,event)>0)
                throw new SeatNotAvailableException("Every section requires a price");
        }
        jdbc.update("UPDATE event SET publication=?,revision=revision+1 WHERE id=?",next,event); audit(actor,reopening ? "REOPEN" : next,event);
    }
}
