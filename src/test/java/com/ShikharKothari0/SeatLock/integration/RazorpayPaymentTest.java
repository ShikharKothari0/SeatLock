package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.kafka.listener.auto-startup=false","spring.kafka.admin.auto-create=false","spring.kafka.bootstrap-servers=localhost:1","seatlock.payments.razorpay-enabled=true","seatlock.payments.razorpay-poll-ms=3600000"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class RazorpayPaymentTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));}
    @Autowired RazorpayPaymentService payments;@Autowired CatalogService catalog;@Autowired PricingService pricing;@Autowired SeatHoldService holds;
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    @MockitoBean RazorpayGateway gateway;
    final UUID owner=UUID.fromString("33333333-3333-3333-3333-333333333333");
    String order,payment;PricingService.Quote quote;UUID seat;String key;
    @BeforeEach void setup() throws Exception {
        order="order_"+UUID.randomUUID().toString().replace("-","");payment="pay_"+UUID.randomUUID().toString().replace("-","");key=UUID.randomUUID().toString();
        when(gateway.enabled()).thenReturn(true);when(gateway.key()).thenReturn("rzp_test_fixture");
        when(gateway.verifyCheckout(eq(order),eq(payment),eq("a".repeat(64)))).thenReturn(true);
        UUID venue=catalog.createVenue(new CatalogService.VenueInput("Gateway hall","Pune","Test","Asia/Kolkata",List.of(new CatalogService.Section("Premium","P",2))),owner);
        UUID event=catalog.createEvent(new CatalogService.EventInput(venue,"Gateway event",Instant.now().minusSeconds(60),Instant.now().plusSeconds(3600),Instant.now().plusSeconds(7200),0,List.of(new CatalogService.Price("Premium",50000L))),owner);
        catalog.transition(event,"PUBLISHED",owner);seat=jdbc.queryForList("SELECT id FROM seat WHERE event_id=?",UUID.class,event).getFirst();holds.holdSeat(seat,owner);quote=pricing.quote(owner,List.of(seat));
        when(gateway.call(eq("POST"),eq("/orders"),any(),isNull())).thenAnswer(inv->{var body=(Map<?,?>)inv.getArgument(2);return json.valueToTree(Map.of("id",order,"amount",50000,"currency","INR","receipt",body.get("receipt")));});
        when(gateway.call(eq("GET"),eq("/payments/"+payment),isNull(),isNull())).thenAnswer(inv->captured(50000));
        when(gateway.call(eq("GET"),eq("/orders/"+order+"/payments"),isNull(),isNull())).thenAnswer(inv->json.valueToTree(Map.of("items",List.of(captured(50000)))));
    }
    com.fasterxml.jackson.databind.node.ObjectNode captured(long amount){return json.valueToTree(Map.of("id",payment,"order_id",order,"amount",amount,"currency","INR","status","captured","amount_refunded",0));}
    RazorpayPaymentService.Attempt start(){return payments.start(owner,quote.id(),List.of(seat),key);}
    @Test void verifiedPaymentCreatesOneBookingAcrossCallbackAndWebhook() throws Exception {
        var a=start();assertEquals(a.id(),start().id());
        var done=payments.verify(a.id(),owner,payment,"a".repeat(64));assertEquals("SUCCEEDED",done.status());
        when(gateway.verifyWebhook(any(),eq("valid"))).thenReturn(true);
        byte[] webhook=json.writeValueAsBytes(Map.of("payload",Map.of("payment",Map.of("entity",Map.of("order_id",order)))));
        mvc.perform(post("/api/payments/razorpay/webhook").header("X-Razorpay-Signature","valid").contentType("application/json").content(webhook)).andExpect(status().isOk());
        assertEquals(done.booking().id(),payments.verify(a.id(),owner,payment,"a".repeat(64)).booking().id());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM booking WHERE idempotency_key=?",Integer.class,key));
        verify(gateway,times(1)).call(eq("POST"),eq("/orders"),any(),isNull());
    }
    @Test void rejectsTamperingAndOtherOwners() throws Exception {
        var a=start();
        assertThrows(RuntimeException.class,()->payments.verify(a.id(),owner,payment,"b".repeat(64)));
        assertThrows(RuntimeException.class,()->payments.get(a.id(),UUID.randomUUID()));
        when(gateway.call("GET","/payments/"+payment,null,null)).thenReturn(captured(1));
        assertThrows(IllegalStateException.class,()->payments.verify(a.id(),owner,payment,"a".repeat(64)));
        assertEquals("PENDING",payments.get(a.id(),owner).status());
        mvc.perform(post("/api/payments/razorpay/"+a.id()+"/reconcile").with(user(owner.toString()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/razorpay/webhook").contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
    }
    @Test void latePaymentRefundsWithStableKeyAndRecoversLostRefundResponse() {
        var a=start();catalog.transition(quote.eventId(),"CLOSED",owner);
        when(gateway.call(eq("POST"),eq("/payments/"+payment+"/refund"),any(),eq(a.id().toString())))
            .thenThrow(new IllegalStateException("timeout"))
            .thenReturn(json.valueToTree(Map.of("id","rfnd_test","payment_id",payment,"amount",50000,"status","pending")));
        assertThrows(IllegalStateException.class,()->payments.verify(a.id(),owner,payment,"a".repeat(64)));
        assertEquals("REFUND_PENDING",payments.get(a.id(),owner).status());
        payments.reconcile(a.id(),owner);
        when(gateway.call("GET","/refunds/rfnd_test",null,null)).thenReturn(json.valueToTree(Map.of("id","rfnd_test","payment_id",payment,"amount",50000,"status","processed")));
        assertEquals("REFUNDED",payments.reconcile(a.id(),owner).status());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE idempotency_key=?",Integer.class,key));
        verify(gateway,times(2)).call(eq("POST"),eq("/payments/"+payment+"/refund"),any(),eq(a.id().toString()));
    }
    @Test void uncertainOrderCreationIsRecoveredWithoutAnotherCreate() {
        when(gateway.call(eq("POST"),eq("/orders"),any(),isNull())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(IllegalStateException.class,this::start);
        var a=start();assertEquals("CREATING",a.status());
        when(gateway.call("GET","/orders?receipt="+a.id(),null,null)).thenReturn(json.valueToTree(Map.of("items",List.of(Map.of("id",order,"receipt",a.id().toString(),"amount",50000,"currency","INR")))));
        assertEquals("SUCCEEDED",payments.reconcile(a.id(),owner).status());
        verify(gateway,times(1)).call(eq("POST"),eq("/orders"),any(),isNull());
    }
    @Test void expiredReservationCannotBeRevivedByCapturedPayment() {
        var a=start();
        jdbc.update("UPDATE seat SET hold_expires_at=now()-interval '1 minute' WHERE id=?",seat);
        when(gateway.call(eq("POST"),eq("/payments/"+payment+"/refund"),any(),eq(a.id().toString())))
            .thenReturn(json.valueToTree(Map.of("id","rfnd_expired","payment_id",payment,"amount",50000,"status","processed")));
        assertEquals("REFUNDED",payments.verify(a.id(),owner,payment,"a".repeat(64)).status());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE idempotency_key=?",Integer.class,key));
        assertNotEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM seat WHERE id=?",String.class,seat));
    }
    @Test void authorizedPaymentDoesNotConfirmAndDemoCannotBypassGateway() throws Exception {
        var a=start();when(gateway.call("GET","/payments/"+payment,null,null)).thenReturn(captured(50000).deepCopy().put("status","authorized"));
        assertEquals("PENDING",payments.verify(a.id(),owner,payment,"a".repeat(64)).status());
        mvc.perform(post("/api/payments").with(user(owner.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("quoteId",quote.id(),"seatIds",List.of(seat),"idempotencyKey",key,"method","UPI","scenario","SUCCESS")))).andExpect(status().isServiceUnavailable());
    }
}
