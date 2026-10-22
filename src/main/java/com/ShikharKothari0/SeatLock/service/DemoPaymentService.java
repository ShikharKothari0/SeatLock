package com.ShikharKothari0.SeatLock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.exception.ResourceNotFoundException;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class DemoPaymentService {
    public enum Scenario { SUCCESS, FAILURE, DELAYED_SUCCESS }
    public enum Method { UPI, CARD, NET_BANKING }
    public record Attempt(UUID id, UUID quoteId, String status, long amountMinor, String currency,
        String method, String scenario, Instant readyAt, Instant expiresAt, String resultCode,
        BookingResponse booking, boolean demo) {}
    private final JdbcTemplate jdbc;
    private final PricingService pricing;
    private final TransactionEffects effects;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final boolean enabled;
    public DemoPaymentService(JdbcTemplate jdbc, PricingService pricing, TransactionEffects effects,
            ObjectMapper json, PlatformTransactionManager manager,
            @Value("${seatlock.payments.demo-enabled:true}") boolean enabled,
            @Value("${seatlock.payments.razorpay-enabled:false}") boolean razorpayEnabled) {
        this.jdbc=jdbc; this.pricing=pricing; this.effects=effects; this.json=json;
        this.tx=new TransactionTemplate(manager); this.enabled=enabled && !razorpayEnabled;
    }
    private <T> T decode(String value,Class<T> type) {
        try { return json.readValue(value,type); } catch(Exception ex) { throw new IllegalStateException("Invalid payment snapshot",ex); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception ex) { throw new IllegalStateException("Cannot save payment snapshot",ex); }
    }
    private Instant instant(Map<String,Object> row,String field) { return ((java.sql.Timestamp)row.get(field)).toInstant(); }
    private Attempt response(Map<String,Object> row) {
        return new Attempt((UUID)row.get("id"),(UUID)row.get("quote_id"),(String)row.get("status"),
            ((Number)row.get("amount_minor")).longValue(),(String)row.get("currency"),(String)row.get("method"),
            (String)row.get("scenario"),instant(row,"ready_at"),instant(row,"expires_at"),(String)row.get("result_code"),
            row.get("booking_response")==null?null:decode((String)row.get("booking_response"),BookingResponse.class),true);
    }
    private Map<String,Object> row(UUID id,UUID owner,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM payment_attempt WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),id,owner);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Payment not found");
        return rows.getFirst();
    }
    public Attempt get(UUID id,UUID owner) { return response(row(id,owner,false)); }
    public BookingResponse confirmLegacy(UUID owner,UUID quoteId,List<UUID> ids,String key) {
        // Read-only replay of pre-V8 bookings; never fabricate a historical payment.
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM booking WHERE idempotency_key=?)",Boolean.class,key)))
            return pricing.confirm(owner,ids,key,quoteId);
        var attempt=start(owner,quoteId,ids,key,Method.UPI,Scenario.SUCCESS);
        var result=process(attempt.id(),owner);
        if(result.booking()==null) throw new SeatNotAvailableException("Reservation could not be confirmed. Review a new quote at checkout");
        return result.booking();
    }
    public Attempt start(UUID owner,UUID quoteId,List<UUID> seatIds,String key,Method method,Scenario scenario) {
        if(!enabled && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM payment_attempt WHERE idempotency_key=? AND user_id=?)",Boolean.class,key,owner)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"New demo payments are disabled");
        return tx.execute(status->{
            effects.serializeBooking(key);
            var existing=jdbc.queryForList("SELECT * FROM payment_attempt WHERE idempotency_key=?",key);
            if(!existing.isEmpty()) {
                var saved=existing.getFirst();
                if(!owner.equals(saved.get("user_id")) || !quoteId.equals(saved.get("quote_id")) || !method.name().equals(saved.get("method")) || !scenario.name().equals(saved.get("scenario")))
                    throw new SeatNotAvailableException("Idempotency key belongs to a different payment");
                validateSeats(quote(owner,quoteId,false),seatIds);
                return response(saved);
            }
            var quote=quote(owner,quoteId,true);
            validateSeats(quote,seatIds);
            if(!quote.expiresAt().isAfter(Instant.now())) throw new SeatNotAvailableException("Quote expired. Review a new total at checkout");
            if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM payment_attempt WHERE quote_id=? UNION ALL SELECT 1 FROM booking_price WHERE quote_id=? UNION ALL SELECT 1 FROM razorpay_attempt WHERE quote_id=?)",Boolean.class,quoteId,quoteId,quoteId))
                throw new SeatNotAvailableException("Quote already used. Review a new total at checkout");
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO payment_attempt(id,user_id,quote_id,idempotency_key,method,scenario,status,amount_minor,currency,ready_at,expires_at) VALUES (?,?,?,?,?,?,'PENDING',?,?,?,?)",
                id,owner,quoteId,key,method.name(),scenario.name(),quote.totalMinor(),quote.currency(),
                java.sql.Timestamp.from(Instant.now().plusSeconds(scenario==Scenario.DELAYED_SUCCESS?15:0)),java.sql.Timestamp.from(quote.expiresAt()));
            return get(id,owner);
        });
    }
    private PricingService.Quote quote(UUID owner,UUID id,boolean lock) {
        var rows=jdbc.queryForList("SELECT snapshot FROM checkout_quote WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),String.class,id,owner);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Quote not found");
        return decode(rows.getFirst(),PricingService.Quote.class);
    }
    private void validateSeats(PricingService.Quote quote,List<UUID> ids) {
        if(ids.size()!=quote.lines().size() || !new HashSet<>(ids).equals(quote.lines().stream().map(PricingService.Line::seatId).collect(java.util.stream.Collectors.toSet())))
            throw new SeatNotAvailableException("Quote does not match the selected seats");
    }
    public Attempt process(UUID id,UUID owner) {
        try {
            return tx.execute(status->{
                var saved=row(id,owner,true);
                if(!"PENDING".equals(saved.get("status"))) return response(saved);
                if(!instant(saved,"expires_at").isAfter(Instant.now())) {
                    finish(id,"EXPIRED","RESERVATION_EXPIRED"); return get(id,owner);
                }
                if(instant(saved,"ready_at").isAfter(Instant.now())) return response(saved);
                if("FAILURE".equals(saved.get("scenario"))) {
                    finish(id,"FAILED","SIMULATED_DECLINE"); return get(id,owner);
                }
                var quote=quote(owner,(UUID)saved.get("quote_id"),false);
                var booking=pricing.confirm(owner,quote.lines().stream().map(PricingService.Line::seatId).toList(),(String)saved.get("idempotency_key"),quote.id());
                jdbc.update("UPDATE payment_attempt SET status='SUCCEEDED',booking_id=?,booking_response=?,result_code='CONFIRMED',updated_at=CURRENT_TIMESTAMP WHERE id=?",booking.id(),encode(booking),id);
                return get(id,owner);
            });
        } catch(SeatNotAvailableException ex) {
            // Booking's transaction has fully rolled back before recording a rejected outcome.
            // Infrastructure errors are intentionally NOT terminal: leave PENDING for retry.
            return tx.execute(status->{
                var saved=row(id,owner,true);
                if("PENDING".equals(saved.get("status"))) {
                    boolean expired=!instant(saved,"expires_at").isAfter(Instant.now());
                    finish(id,expired?"EXPIRED":"FAILED",expired?"RESERVATION_EXPIRED":"RESERVATION_UNAVAILABLE");
                }
                return get(id,owner);
            });
        }
    }
    private void finish(UUID id,String status,String reason) {
        jdbc.update("UPDATE payment_attempt SET status=?,result_code=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,reason,id);
    }
    public void processDue() {
        if(!enabled) return;
        var due=jdbc.queryForList("SELECT id,user_id FROM payment_attempt WHERE status='PENDING' AND (ready_at<=CURRENT_TIMESTAMP OR expires_at<=CURRENT_TIMESTAMP) ORDER BY created_at LIMIT 50");
        for(var attempt:due) try { process((UUID)attempt.get("id"),(UUID)attempt.get("user_id")); }
        catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Demo payment {} remains pending for retry",attempt.get("id")); }
    }
}
