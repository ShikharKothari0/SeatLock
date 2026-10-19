package com.ShikharKothari0.SeatLock.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.repository.*;
import com.ShikharKothari0.SeatLock.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class PricingService {
    public record Line(UUID seatId,String seatNumber,String section,long amountMinor,String holdToken) {}
    public record Quote(UUID id,UUID userId,UUID eventId,String currency,List<Line> lines,long subtotalMinor,long feeMinor,long totalMinor,Instant expiresAt,Instant serverTime) {}
    private final JdbcTemplate jdbc; private final SeatRepository seats; private final BookingRepository bookings;
    private final BookingService bookingService; private final TransactionEffects effects; private final RedisLockService locks; private final ObjectMapper json;
    public PricingService(JdbcTemplate jdbc,SeatRepository seats,BookingRepository bookings,BookingService bookingService,TransactionEffects effects,RedisLockService locks,ObjectMapper json) {
        this.jdbc=jdbc; this.seats=seats; this.bookings=bookings; this.bookingService=bookingService; this.effects=effects; this.locks=locks; this.json=json;
    }
    private String encode(Quote quote) { try { return json.writeValueAsString(quote); } catch(Exception ex) { throw new IllegalStateException("Unable to serialize quote",ex); } }
    private Quote decode(String value) { try { return json.readValue(value,Quote.class); } catch(Exception ex) { throw new IllegalStateException("Unable to read quote",ex); } }
    @Transactional public Quote quote(UUID owner,List<UUID> ids) {
        var held=seats.lockSeats(ids);
        if(held.isEmpty()||held.size()!=ids.size()) throw new SeatNotAvailableException("Invalid or duplicate seats");
        UUID event=held.getFirst().getEvent().getId(); effects.requireSalesOpen(event);
        Instant expiry=Instant.now().plusSeconds(120); long subtotal=0; var lines=new ArrayList<Line>();
        for(var seat:held) {
            if(!event.equals(seat.getEvent().getId()) || seat.getStatus()!=SeatStatus.HELD || !owner.equals(seat.getHeldBy()) || seat.getHoldExpiresAt()==null)
                throw new SeatNotAvailableException("Quote requires your active holds for one event");
            long ttl=locks.ownedTtlMillis("seat:lock:"+seat.getId(),seat.lockValue());
            if(ttl<=0 || !seat.getHoldExpiresAt().isAfter(Instant.now())) throw new SeatNotAvailableException("Seat hold expired");
            expiry=Collections.min(List.of(expiry,seat.getHoldExpiresAt(),Instant.now().plusMillis(ttl)));
            var amounts=jdbc.queryForList("SELECT amount_minor FROM event_price WHERE event_id=? AND section=?",Long.class,event,seat.getSection());
            if(amounts.isEmpty()) throw new SeatNotAvailableException("This section has no configured price");
            long amount=amounts.getFirst(); subtotal=Math.addExact(subtotal,amount);
            lines.add(new Line(seat.getId(),seat.getSeatNumber(),seat.getSection(),amount,seat.lockValue()));
        }
        long fee=jdbc.queryForObject("SELECT fee_minor FROM event WHERE id=?",Long.class,event);
        var quote=new Quote(UUID.randomUUID(),owner,event,"INR",lines,subtotal,fee,Math.addExact(subtotal,fee),expiry,Instant.now());
        jdbc.update("INSERT INTO checkout_quote(id,user_id,event_id,expires_at,snapshot) VALUES (?,?,?,?,?)",quote.id(),owner,event,java.sql.Timestamp.from(expiry),encode(quote));
        return quote;
    }
    @Transactional public BookingResponse confirm(UUID owner,List<UUID> ids,String key,UUID quoteId) {
        effects.serializeBooking(key);
        var existing=bookings.findByIdempotencyKey(key);
        if(existing.isPresent()) {
            var stored=jdbc.queryForList("SELECT quote_id FROM booking_price WHERE booking_id=?",UUID.class,existing.get().getId());
            if(stored.isEmpty()||!stored.getFirst().equals(quoteId)) throw new SeatNotAvailableException("Idempotency key belongs to a different quote");
            return bookingService.confirmBooking(new BookingConfirmRequest(owner,ids,key));
        }
        var rows=jdbc.queryForList("SELECT snapshot FROM checkout_quote WHERE id=? AND user_id=? FOR UPDATE",String.class,quoteId,owner);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Quote not found");
        Quote quote=decode(rows.getFirst());
        if(!quote.expiresAt().isAfter(Instant.now())) throw new SeatNotAvailableException("Quote expired. Review a new total at checkout");
        if(ids.size()!=quote.lines().size() || !new HashSet<>(ids).equals(quote.lines().stream().map(Line::seatId).collect(java.util.stream.Collectors.toSet())))
            throw new SeatNotAvailableException("Quote does not match the selected seats");
        if(jdbc.queryForObject("SELECT count(*) FROM booking_price WHERE quote_id=?",Integer.class,quoteId)>0) throw new SeatNotAvailableException("Quote already used");
        var held=seats.lockSeats(ids);
        for(var seat:held) if(quote.lines().stream().noneMatch(line->line.seatId().equals(seat.getId()) && Objects.equals(line.holdToken(),seat.lockValue())))
            throw new SeatNotAvailableException("Hold changed. Review a new quote");
        effects.requireSalesOpen(quote.eventId());
        // Locks may have waited behind another reservation or a sales closure.
        if(!quote.expiresAt().isAfter(Instant.now())) throw new SeatNotAvailableException("Quote expired. Review a new total at checkout");
        var result=bookingService.confirmBooking(new BookingConfirmRequest(owner,ids,key));
        bookings.flush();
        jdbc.update("INSERT INTO booking_price(booking_id,quote_id,snapshot) VALUES (?,?,?)",result.id(),quoteId,encode(quote));
        return result;
    }
    public Quote receipt(UUID booking,UUID owner) {
        var rows=jdbc.queryForList("SELECT p.snapshot FROM booking_price p JOIN booking b ON b.id=p.booking_id WHERE b.id=? AND b.user_id=?",String.class,booking,owner);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Price snapshot unavailable for this booking");
        return decode(rows.getFirst());
    }
}
