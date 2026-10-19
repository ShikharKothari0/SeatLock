package com.ShikharKothari0.SeatLock.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"seatlock.payments.worker-enabled=false", "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false", "spring.kafka.bootstrap-servers=localhost:1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DemoPaymentTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.ShikharKothari0.SeatLock.service.CatalogService catalog;
    @Autowired com.ShikharKothari0.SeatLock.service.PricingService pricing;
    @Autowired com.ShikharKothari0.SeatLock.service.SeatHoldService holds;
    @Autowired com.ShikharKothari0.SeatLock.service.DemoPaymentService payments;
    static final UUID OWNER=UUID.fromString("33333333-3333-3333-3333-333333333333");
    UUID event() {
        var section=new com.ShikharKothari0.SeatLock.service.CatalogService.Section("Premium","P",3);
        UUID venue=catalog.createVenue(new com.ShikharKothari0.SeatLock.service.CatalogService.VenueInput("Test venue","Mumbai","Test address","Asia/Kolkata",List.of(section)),OWNER);
        return catalog.createEvent(new com.ShikharKothari0.SeatLock.service.CatalogService.EventInput(venue,"Test event",java.time.Instant.now().minusSeconds(60),java.time.Instant.now().plusSeconds(3600),java.time.Instant.now().plusSeconds(7200),2500,List.of(new com.ShikharKothari0.SeatLock.service.CatalogService.Price("Premium",12345L))),OWNER);
    }
    UUID seat(UUID event) { return jdbc.queryForList("SELECT id FROM seat WHERE event_id=? ORDER BY id",UUID.class,event).getFirst(); }

    com.ShikharKothari0.SeatLock.service.PricingService.Quote quote() {
        UUID event=event(), seat=seat(event); catalog.transition(event,"PUBLISHED",OWNER); holds.holdSeat(seat,OWNER);
        return pricing.quote(OWNER,List.of(seat));
    }
    com.ShikharKothari0.SeatLock.service.DemoPaymentService.Attempt start(com.ShikharKothari0.SeatLock.service.PricingService.Quote quote, String key, com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario scenario) {
        return payments.start(OWNER,quote.id(),quote.lines().stream().map(com.ShikharKothari0.SeatLock.service.PricingService.Line::seatId).toList(),key,com.ShikharKothari0.SeatLock.service.DemoPaymentService.Method.UPI,scenario);
    }
    @Test void historicalBookingRetryDoesNotInventAPayment() {
        var quote=quote();var ids=List.of(quote.lines().getFirst().seatId());String key=UUID.randomUUID().toString();
        var booking=pricing.confirm(OWNER,ids,key,quote.id());
        assertEquals(booking.id(),payments.confirmLegacy(OWNER,quote.id(),ids,key).id());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM payment_attempt WHERE quote_id=?",Integer.class,quote.id()));
    }
    @Test void successIsAtomicAndReplayRemainsStableAfterExpiry() {
        var quote=quote(); String key=UUID.randomUUID().toString();
        var attempt=start(quote,key,com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS);
        assertEquals("PENDING",attempt.status()); assertEquals(14845,attempt.amountMinor());
        var result=payments.process(attempt.id(),OWNER); assertEquals("SUCCEEDED",result.status()); assertNotNull(result.booking());
        jdbc.update("UPDATE payment_attempt SET expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE id=?",attempt.id());
        assertEquals(result.booking().id(),payments.process(attempt.id(),OWNER).booking().id());
        assertEquals(result.booking().id(),start(quote,key,com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS).booking().id());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,quote.eventId()));
    }
    @Test void declinedPaymentDoesNotBookAndRetryRequiresANewQuote() {
        var quote=quote(); String key=UUID.randomUUID().toString();
        var attempt=start(quote,key,com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.FAILURE);
        assertEquals("FAILED",payments.process(attempt.id(),OWNER).status());
        assertEquals("HELD",jdbc.queryForObject("SELECT status FROM seat WHERE id=?",String.class,quote.lines().getFirst().seatId()));
        assertThrows(RuntimeException.class,()->start(quote,key,com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS));
        assertThrows(RuntimeException.class,()->start(quote,UUID.randomUUID().toString(),com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS));
        var next=pricing.quote(OWNER,List.of(quote.lines().getFirst().seatId()));
        var retry=start(next,UUID.randomUUID().toString(),com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS);
        assertEquals("SUCCEEDED",payments.process(retry.id(),OWNER).status());
    }
    @Test void delayedPaymentStaysPendingAndWorkerCompletesIt() {
        var attempt=start(quote(),UUID.randomUUID().toString(),com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.DELAYED_SUCCESS);
        assertEquals("PENDING",payments.process(attempt.id(),OWNER).status());
        jdbc.update("UPDATE payment_attempt SET ready_at=CURRENT_TIMESTAMP-interval '1 second' WHERE id=?",attempt.id());
        payments.processDue();
        assertEquals("SUCCEEDED",payments.get(attempt.id(),OWNER).status());
    }
    @Test void latePaymentExpiresWithoutBooking() {
        var quote=quote(); var attempt=start(quote,UUID.randomUUID().toString(),com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.DELAYED_SUCCESS);
        jdbc.update("UPDATE payment_attempt SET expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE id=?",attempt.id());
        payments.processDue();
        assertEquals("EXPIRED",payments.get(attempt.id(),OWNER).status());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,quote.eventId()));
    }
    @Test void closureRejectsPaymentWithoutConfirmingSeats() {
        var quote=quote();var attempt=start(quote,UUID.randomUUID().toString(),com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS);
        catalog.transition(quote.eventId(),"CLOSED",OWNER);
        assertEquals("FAILED",payments.process(attempt.id(),OWNER).status());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,quote.eventId()));
    }
    @Test void concurrentOutcomeProcessingCreatesOneBooking() throws Exception {
        var quote=quote();var attempt=start(quote,UUID.randomUUID().toString(),com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results=pool.invokeAll(List.<java.util.concurrent.Callable<UUID>>of(()->payments.process(attempt.id(),OWNER).booking().id(),()->payments.process(attempt.id(),OWNER).booking().id()));
            assertEquals(results.get(0).get(),results.get(1).get());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,quote.eventId()));
    }
    @Test void paymentWriteFailureRollsBackBookingAndAllowsRetry() {
        var quote=quote();var attempt=start(quote,UUID.randomUUID().toString(),com.ShikharKothari0.SeatLock.service.DemoPaymentService.Scenario.SUCCESS);
        jdbc.execute("CREATE FUNCTION reject_payment_success() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='SUCCEEDED' THEN RAISE EXCEPTION 'injected payment write failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_payment_success BEFORE UPDATE ON payment_attempt FOR EACH ROW EXECUTE FUNCTION reject_payment_success()");
        try {
            assertThrows(RuntimeException.class,()->payments.process(attempt.id(),OWNER));
            assertEquals("PENDING",payments.get(attempt.id(),OWNER).status());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,quote.eventId()));
            assertEquals("HELD",jdbc.queryForObject("SELECT status FROM seat WHERE id=?",String.class,quote.lines().getFirst().seatId()));
        } finally { jdbc.execute("DROP TRIGGER reject_payment_success ON payment_attempt"); jdbc.execute("DROP FUNCTION reject_payment_success()"); }
        assertEquals("SUCCEEDED",payments.process(attempt.id(),OWNER).status());
    }
    @Test void httpOwnershipCsrfAndAuthoritativeAmount() throws Exception {
        var quote=quote(); String key=UUID.randomUUID().toString();
        var body=json.writeValueAsString(Map.of("quoteId",quote.id(),"seatIds",List.of(quote.lines().getFirst().seatId()),"idempotencyKey",key,"scenario","SUCCESS","method","UPI","amountMinor",1));
        var user=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("CUSTOMER");
        mvc.perform(post("/api/payments").with(user).contentType("application/json").content(body)).andExpect(status().isForbidden());
        var response=mvc.perform(post("/api/payments").with(csrf()).with(user).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.amountMinor").value(14845)).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        var stranger=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(UUID.randomUUID().toString()).roles("CUSTOMER");
        mvc.perform(get("/api/payments/"+id).with(stranger)).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/"+id+"/process").with(stranger).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/"+id+"/process").with(user).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }
}
