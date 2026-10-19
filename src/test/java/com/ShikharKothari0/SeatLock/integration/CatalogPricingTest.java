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

@SpringBootTest(properties={"spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false", "spring.kafka.bootstrap-servers=localhost:1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CatalogPricingTest {
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
    static final UUID OWNER=UUID.fromString("33333333-3333-3333-3333-333333333333");
    UUID event() {
        var section=new com.ShikharKothari0.SeatLock.service.CatalogService.Section("Premium","P",3);
        UUID venue=catalog.createVenue(new com.ShikharKothari0.SeatLock.service.CatalogService.VenueInput("Test venue","Mumbai","Test address","Asia/Kolkata",List.of(section)),OWNER);
        return catalog.createEvent(new com.ShikharKothari0.SeatLock.service.CatalogService.EventInput(venue,"Test event",java.time.Instant.now().minusSeconds(60),java.time.Instant.now().plusSeconds(3600),java.time.Instant.now().plusSeconds(7200),2500,List.of(new com.ShikharKothari0.SeatLock.service.CatalogService.Price("Premium",12345L))),OWNER);
    }
    UUID seat(UUID event) { return jdbc.queryForList("SELECT id FROM seat WHERE event_id=? ORDER BY id",UUID.class,event).getFirst(); }
    @Test void draftPrivacyPublicationAndRoles() throws Exception {
        UUID event=event(), seat=seat(event);
        mvc.perform(get("/api/events/"+event)).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/"+event+"/seats")).andExpect(status().isNotFound());
        assertThrows(RuntimeException.class,()->holds.holdSeat(seat,OWNER));
        mvc.perform(post("/api/admin/catalog/events/"+event+"/publish").with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/catalog/events/"+event+"/publish").with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/events/"+event)).andExpect(status().isOk());
        holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat));
        catalog.transition(event,"CLOSED",OWNER);
        assertThrows(RuntimeException.class,()->pricing.quote(OWNER,List.of(seat)));
        assertThrows(RuntimeException.class,()->pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id()));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM catalog_audit WHERE actor_id=? AND (resource_id=? OR resource_id=(SELECT venue_id FROM event WHERE id=?)) AND action<>'CLOSED'",Integer.class,OWNER,event,event));
    }
    @Test void exactTotalsImmutableReceiptsAndIdempotentRetry() {
        UUID event=event(), seat=seat(event); catalog.transition(event,"PUBLISHED",OWNER); holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat)); assertEquals(14845,quote.totalMinor());
        jdbc.update("UPDATE event_price SET amount_minor=99999 WHERE event_id=?",event);
        String key=UUID.randomUUID().toString(); var booking=pricing.confirm(OWNER,List.of(seat),key,quote.id());
        assertEquals(14845,pricing.receipt(booking.id(),OWNER).totalMinor());
        assertEquals(booking.id(),pricing.confirm(OWNER,List.of(seat),key,quote.id()).id());
        assertThrows(RuntimeException.class,()->pricing.confirm(OWNER,List.of(seat),key,UUID.randomUUID()));
        assertThrows(RuntimeException.class,()->pricing.receipt(booking.id(),UUID.randomUUID()));
    }
    @Test void quoteCannotSurviveReholdOrBeUsedByAnotherAccount() {
        UUID event=event(), seat=seat(event); catalog.transition(event,"PUBLISHED",OWNER); holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat));
        assertThrows(RuntimeException.class,()->pricing.confirm(UUID.randomUUID(),List.of(seat),"foreign-"+UUID.randomUUID(),quote.id()));
        holds.release(seat,OWNER); holds.holdSeat(seat,OWNER);
        assertThrows(RuntimeException.class,()->pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id()));
        assertEquals("HELD",jdbc.queryForObject("SELECT status FROM seat WHERE id=?",String.class,seat));
    }
    @Test void expiredAndMismatchedQuotesFailWithoutConsumingHold() {
        UUID event=event(), seat=seat(event); catalog.transition(event,"PUBLISHED",OWNER); holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat));
        assertThrows(RuntimeException.class,()->pricing.confirm(OWNER,List.of(UUID.randomUUID()),UUID.randomUUID().toString(),quote.id()));
        jdbc.update("UPDATE checkout_quote SET snapshot=jsonb_set(snapshot::jsonb,'{expiresAt}',to_jsonb(?::text))::text WHERE id=?",java.time.Instant.now().minusSeconds(1).toString(),quote.id());
        assertThrows(RuntimeException.class,()->pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,event));
    }
    @Test void concurrentQuoteConsumptionCreatesOnlyOneBooking() throws Exception {
        UUID event=event(), seat=seat(event); catalog.transition(event,"PUBLISHED",OWNER); holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat)); var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=List.<java.util.concurrent.Callable<Boolean>>of(()->{ try { pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id()); return true; } catch(RuntimeException ex) { return false; } },
                ()->{ try { pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id()); return true; } catch(RuntimeException ex) { return false; } });
            int successes=0; for(var result:pool.invokeAll(tasks)) if(result.get()) successes++;
            assertEquals(1,successes); assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,event));
        } finally { pool.shutdownNow(); }
    }
    @Test void catalogRequiresExplicitIntegerPrices() throws Exception {
        UUID event=event();
        UUID venue=jdbc.queryForObject("SELECT venue_id FROM event WHERE id=?",UUID.class,event);
        for (String price : List.of("{\"section\":\"Premium\"}", "{\"section\":\"Premium\",\"amountMinor\":12.5}")) {
            String body="""
                {"venueId":"%s","name":"Invalid price","saleStartTime":"%s",
                 "saleEndTime":"%s","eventTime":"%s","prices":[%s]}
                """.formatted(venue,java.time.Instant.now(),java.time.Instant.now().plusSeconds(60),java.time.Instant.now().plusSeconds(120),price);
            mvc.perform(post("/api/admin/catalog/events").with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("ADMIN"))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM event WHERE venue_id=?",Integer.class,venue));
    }
    @Test void invalidCatalogInputIsRejectedAndLayoutsAreIndependent() {
        UUID first=event(); UUID venue=jdbc.queryForObject("SELECT venue_id FROM event WHERE id=?",UUID.class,first);
        assertThrows(IllegalArgumentException.class,()->catalog.createEvent(new com.ShikharKothari0.SeatLock.service.CatalogService.EventInput(venue,"Missing prices",java.time.Instant.now(),java.time.Instant.now().plusSeconds(60),java.time.Instant.now().plusSeconds(120),0,List.of()),OWNER));
        UUID second=catalog.createEvent(new com.ShikharKothari0.SeatLock.service.CatalogService.EventInput(venue,"Second",java.time.Instant.now(),java.time.Instant.now().plusSeconds(60),java.time.Instant.now().plusSeconds(120),0,List.of(new com.ShikharKothari0.SeatLock.service.CatalogService.Price("Premium",100L))),OWNER);
        assertNotEquals(seat(first),seat(second));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM seat WHERE event_id=?",Integer.class,second));
    }
}
