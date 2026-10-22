package com.ShikharKothari0.SeatLock.service;

import com.fasterxml.jackson.databind.*;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.*;

@Service
public class RazorpayPaymentService {
    public record Attempt(UUID id,String status,String orderId,String keyId,long amountMinor,String currency,Instant expiresAt,BookingResponse booking,boolean testMode) {}
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final RazorpayGateway gateway;
    private final PricingService pricing;private final TransactionEffects effects;private final TransactionTemplate tx;
    public RazorpayPaymentService(JdbcTemplate jdbc,ObjectMapper json,RazorpayGateway gateway,PricingService pricing,TransactionEffects effects,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.json=json;this.gateway=gateway;this.pricing=pricing;this.effects=effects;this.tx=new TransactionTemplate(manager);
    }
    public boolean enabled(){return gateway.enabled();}
    private void requireEnabled(){if(!enabled()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Razorpay test checkout is disabled");}
    private <T>T decode(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception ex){throw new IllegalStateException("Invalid payment snapshot",ex);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private Map<String,Object> row(UUID id,UUID owner,boolean lock){
        var rows=jdbc.queryForList("SELECT * FROM razorpay_attempt WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),id,owner);
        if(rows.isEmpty())throw new ResourceNotFoundException("Payment not found");return rows.getFirst();
    }
    public Attempt get(UUID id,UUID owner){var r=row(id,owner,false);return new Attempt(id,(String)r.get("status"),(String)r.get("order_id"),gateway.key(),((Number)r.get("amount_minor")).longValue(),(String)r.get("currency"),((java.sql.Timestamp)r.get("expires_at")).toInstant(),r.get("booking_response")==null?null:decode((String)r.get("booking_response"),BookingResponse.class),true);}
    public Attempt start(UUID owner,UUID quoteId,List<UUID> seats,String key){
        requireEnabled();boolean[] created={false};
        UUID id=tx.execute(status->{
            effects.serializeBooking(key);
            var quotes=jdbc.queryForList("SELECT snapshot FROM checkout_quote WHERE id=? AND user_id=? FOR UPDATE",String.class,quoteId,owner);
            if(quotes.isEmpty())throw new ResourceNotFoundException("Quote not found");
            var quote=decode(quotes.getFirst(),PricingService.Quote.class);
            if(seats.size()!=quote.lines().size() || !new HashSet<>(seats).equals(new HashSet<>(quote.lines().stream().map(PricingService.Line::seatId).toList())))throw new SeatNotAvailableException("Quote does not match selected seats");
            var saved=jdbc.queryForList("SELECT * FROM razorpay_attempt WHERE idempotency_key=?",key);
            if(!saved.isEmpty()){
                var r=saved.getFirst();if(!owner.equals(r.get("user_id"))||!quoteId.equals(r.get("quote_id")))throw new SeatNotAvailableException("Payment key already used");return (UUID)r.get("id");
            }
            if(!quote.expiresAt().isAfter(Instant.now()))throw new SeatNotAvailableException("Quote expired. Return to checkout");
            if(quote.totalMinor()<100)throw new SeatNotAvailableException("Razorpay test checkout requires a total of at least INR 1");
            if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM payment_attempt WHERE quote_id=? UNION ALL SELECT 1 FROM booking_price WHERE quote_id=? UNION ALL SELECT 1 FROM razorpay_attempt WHERE quote_id=?)",Boolean.class,quoteId,quoteId,quoteId))throw new SeatNotAvailableException("Quote already used");
            UUID fresh=UUID.randomUUID();jdbc.update("INSERT INTO razorpay_attempt(id,user_id,quote_id,idempotency_key,amount_minor,currency,status,expires_at) VALUES (?,?,?,?,?,?,'CREATING',?)",fresh,owner,quoteId,key,quote.totalMinor(),quote.currency(),java.sql.Timestamp.from(quote.expiresAt()));created[0]=true;return fresh;
        });
        if(created[0]) {
            var attempt=get(id,owner);
            // Commit the attempt before the remote call. Never blindly retry order creation.
            var order=gateway.call("POST","/orders",Map.of("amount",attempt.amountMinor(),"currency","INR","receipt",id.toString()),null);
            attachOrder(id,owner,order);
        }
        return get(id,owner);
    }
    private void attachOrder(UUID id,UUID owner,JsonNode order){
        var attempt=get(id,owner);
        if(!id.toString().equals(order.path("receipt").asText())||order.path("amount").asLong()!=attempt.amountMinor()||!"INR".equals(order.path("currency").asText())||!order.path("id").asText().matches("order_[A-Za-z0-9]+"))throw new IllegalStateException("Unexpected provider order");
        jdbc.update("UPDATE razorpay_attempt SET order_id=?,status='PENDING' WHERE id=? AND order_id IS NULL",order.path("id").asText(),id);
    }
    public Attempt verify(UUID id,UUID owner,String payment,String signature){
        requireEnabled();var attempt=get(id,owner);
        if(attempt.orderId()==null||!payment.matches("pay_[A-Za-z0-9]+")||!gateway.verifyCheckout(attempt.orderId(),payment,signature))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid payment signature");
        settle(id,owner,gateway.call("GET","/payments/"+payment,null,null));return get(id,owner);
    }
    public Attempt reconcile(UUID id,UUID owner){
        requireEnabled();var attempt=get(id,owner);
        if(List.of("SUCCEEDED","REFUNDED").contains(attempt.status()))return attempt;
        jdbc.update("UPDATE razorpay_attempt SET checked_at=now() WHERE id=?",id);
        if(attempt.orderId()==null){
            var orders=gateway.call("GET","/orders?receipt="+id,null,null).path("items");
            if(orders.size()>1)throw new IllegalStateException("Multiple orders require review");
            if(orders.size()==1)attachOrder(id,owner,orders.get(0));
            attempt=get(id,owner);if(attempt.orderId()==null)return attempt;
        }
        if("REFUND_PENDING".equals(attempt.status())){refund(id,owner);return get(id,owner);}
        for(var payment:gateway.call("GET","/orders/"+attempt.orderId()+"/payments",null,null).path("items"))settle(id,owner,payment);
        jdbc.update("UPDATE razorpay_attempt SET status='EXPIRED' WHERE id=? AND status='PENDING' AND expires_at<=now()",id);
        return get(id,owner);
    }
    private void settle(UUID id,UUID owner,JsonNode payment){
        var attempt=get(id,owner);
        String paymentId=payment.path("id").asText();
        if(!Objects.equals(attempt.orderId(),payment.path("order_id").asText())||payment.path("amount").asLong()!=attempt.amountMinor()||!attempt.currency().equals(payment.path("currency").asText())||!paymentId.matches("pay_[A-Za-z0-9]+"))throw new IllegalStateException("Payment does not match stored order");
        if(!"captured".equals(payment.path("status").asText()))return;
        if(payment.path("amount_refunded").asLong()!=0)throw new IllegalStateException("Externally refunded payment requires review");
        try {
            tx.execute(status->{
                var saved=row(id,owner,true);String state=(String)saved.get("status");
                if(List.of("SUCCEEDED","REFUND_PENDING","REFUNDED").contains(state))return null;
                var quote=decode(jdbc.queryForObject("SELECT snapshot FROM checkout_quote WHERE id=?",String.class,saved.get("quote_id")),PricingService.Quote.class);
                var booking=pricing.confirm(owner,quote.lines().stream().map(PricingService.Line::seatId).toList(),(String)saved.get("idempotency_key"),quote.id());
                jdbc.update("UPDATE razorpay_attempt SET status='SUCCEEDED',payment_id=?,booking_response=? WHERE id=?",paymentId,encode(booking),id);return null;
            });
        }catch(SeatNotAvailableException ex){
            tx.execute(status->{var saved=row(id,owner,true);if(!List.of("SUCCEEDED","REFUNDED").contains(saved.get("status")))jdbc.update("UPDATE razorpay_attempt SET status='REFUND_PENDING',payment_id=? WHERE id=?",paymentId,id);return null;});
        }
        if("REFUND_PENDING".equals(get(id,owner).status()))refund(id,owner);
    }
    private void refund(UUID id,UUID owner){
        var saved=row(id,owner,false);String payment=(String)saved.get("payment_id");
        var response=saved.get("refund_id")==null
            ?gateway.call("POST","/payments/"+payment+"/refund",Map.of("amount",((Number)saved.get("amount_minor")).longValue()),id.toString())
            :gateway.call("GET","/refunds/"+saved.get("refund_id"),null,null);
        if(!payment.equals(response.path("payment_id").asText())||response.path("amount").asLong()!=((Number)saved.get("amount_minor")).longValue())throw new IllegalStateException("Unexpected refund response");
        jdbc.update("UPDATE razorpay_attempt SET refund_id=?,status=? WHERE id=? AND status='REFUND_PENDING'",response.path("id").asText(),"processed".equals(response.path("status").asText())?"REFUNDED":"REFUND_PENDING",id);
    }
    public void webhook(byte[] body,String signature){
        if(!gateway.verifyWebhook(body,signature))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid webhook signature");
        JsonNode event;try{event=json.readTree(body);}catch(Exception ex){throw new IllegalArgumentException("Invalid webhook");}
        String order=event.path("payload").path("payment").path("entity").path("order_id").asText();
        if(order.isBlank())return;
        for(var saved:jdbc.queryForList("SELECT id,user_id FROM razorpay_attempt WHERE order_id=?",order))reconcile((UUID)saved.get("id"),(UUID)saved.get("user_id"));
    }
    @Scheduled(fixedDelayString="${seatlock.payments.razorpay-poll-ms:30000}")
    public void recover(){
        if(!enabled())return;
        var attempts=jdbc.queryForList("SELECT id,user_id FROM razorpay_attempt WHERE status NOT IN ('SUCCEEDED','REFUNDED') AND (created_at>now()-interval '1 day' OR status='REFUND_PENDING') AND checked_at<now()-interval '30 seconds' ORDER BY checked_at LIMIT 10");
        for(var r:attempts)try{reconcile((UUID)r.get("id"),(UUID)r.get("user_id"));}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Razorpay test attempt {} requires another reconciliation",r.get("id"));}
    }
}
