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
class EventEditorialTest {
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

    String edit(UUID event,long revision,String poster) throws Exception {
        return json.writeValueAsString(Map.ofEntries(Map.entry("revision",revision),Map.entry("name","Jazz night"),Map.entry("description","An evening of live music."),Map.entry("category","Music"),Map.entry("posterUrl",poster),Map.entry("durationMinutes",90),Map.entry("saleStartTime",java.time.Instant.now().minusSeconds(60)),Map.entry("saleEndTime",java.time.Instant.now().plusSeconds(3600)),Map.entry("eventTime",java.time.Instant.now().plusSeconds(7200)),Map.entry("feeMinor",1000),Map.entry("prices",List.of(Map.of("section","Premium","amountMinor",15000)))));
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor admin() { return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("ADMIN"); }
    @Test void savedDraftPreviewRemainsPrivateAndPublicationExposesContent() throws Exception {
        UUID event=event();UUID seat=seat(event);
        mvc.perform(put("/api/admin/catalog/events/"+event).with(admin()).with(csrf()).contentType("application/json").content(edit(event,0,"https://example.com/poster.png"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/catalog/events/"+event).with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.description").value("An evening of live music."));
        mvc.perform(get("/api/events/"+event)).andExpect(status().isNotFound());
        assertEquals(seat,seat(event));
        catalog.transition(event,"PUBLISHED",OWNER);
        mvc.perform(get("/api/events/"+event)).andExpect(status().isOk()).andExpect(jsonPath("$.category").value("Music")).andExpect(jsonPath("$.durationMinutes").value(90)).andExpect(jsonPath("$.posterUrl").value("https://example.com/poster.png"));
        holds.holdSeat(seat,OWNER);assertEquals(16000,pricing.quote(OWNER,List.of(seat)).totalMinor());
    }
    @Test void publishedEventRejectsEditsWithoutChangingBookings() throws Exception {
        UUID event=event(),seat=seat(event);catalog.transition(event,"PUBLISHED",OWNER);holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat));var booking=pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id());
        mvc.perform(put("/api/admin/catalog/events/"+event).with(admin()).with(csrf()).contentType("application/json").content(edit(event,1,""))).andExpect(status().isConflict());
        assertEquals(14845,pricing.receipt(booking.id(),OWNER).totalMinor());
        assertEquals("Test event",jdbc.queryForObject("SELECT name FROM event WHERE id=?",String.class,event));
    }
    @Test void staleEditsCannotOverwriteAnotherSave() throws Exception {
        UUID event=event();String body=edit(event,0,"");
        mvc.perform(put("/api/admin/catalog/events/"+event).with(admin()).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/catalog/events/"+event).with(admin()).with(csrf()).contentType("application/json").content(body.replace("Jazz night","Stale change"))).andExpect(status().isConflict());
        assertEquals("Jazz night",jdbc.queryForObject("SELECT name FROM event WHERE id=?",String.class,event));
    }
    @Test void previewEditingRequireAdminAndCsrf() throws Exception {
        UUID event=event();var customer=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("CUSTOMER");
        mvc.perform(get("/api/admin/catalog/events/"+event).with(customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/catalog/events/"+event).with(customer).with(csrf()).contentType("application/json").content(edit(event,0,""))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/catalog/events/"+event).with(admin()).contentType("application/json").content(edit(event,0,""))).andExpect(status().isForbidden());
    }
    @Test void invalidPostersAndIncompletePricingDoNotPartiallySave() throws Exception {
        UUID event=event();
        for(String poster:List.of("javascript:alert(1)","http://example.com/image.png","https://user:pass@example.com/image.png"))
            mvc.perform(put("/api/admin/catalog/events/"+event).with(admin()).with(csrf()).contentType("application/json").content(edit(event,0,poster))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/catalog/events/"+event).with(admin()).with(csrf()).contentType("application/json").content(edit(event,0,"").replace("Premium","Missing"))).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT revision FROM event WHERE id=?",Integer.class,event));
        assertEquals(12345,jdbc.queryForObject("SELECT amount_minor FROM event_price WHERE event_id=?",Integer.class,event));
    }

    @Test void reopeningPreservesInventoryBookingsAndPrices() throws Exception {
        UUID event=event(),seat=seat(event);catalog.transition(event,"PUBLISHED",OWNER);holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat));var booking=pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id());
        catalog.transition(event,"CLOSED",OWNER);
        var inventory=jdbc.queryForList("SELECT * FROM seat WHERE event_id=? ORDER BY id",event);
        var prices=jdbc.queryForList("SELECT * FROM event_price WHERE event_id=?",event);
        mvc.perform(post("/api/admin/catalog/events/"+event+"/reopen").with(admin()).with(csrf())).andExpect(status().isOk());
        assertEquals(inventory,jdbc.queryForList("SELECT * FROM seat WHERE event_id=? ORDER BY id",event));
        assertEquals(prices,jdbc.queryForList("SELECT * FROM event_price WHERE event_id=?",event));
        assertEquals(14845,pricing.receipt(booking.id(),OWNER).totalMinor());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM catalog_audit WHERE resource_id=? AND action='REOPEN'",Integer.class,event));
        assertEquals(3,jdbc.queryForObject("SELECT revision FROM event WHERE id=?",Integer.class,event));
        mvc.perform(get("/api/events/"+event)).andExpect(status().isOk());
        UUID free=jdbc.queryForList("SELECT id FROM seat WHERE event_id=? AND status='AVAILABLE'",UUID.class,event).getFirst();
        holds.holdSeat(free,OWNER);
    }
    @Test void reopeningRejectsInvalidStatesAndRequiresAdminAndCsrf() throws Exception {
        UUID event=event();String url="/api/admin/catalog/events/"+event+"/reopen";
        mvc.perform(post(url).with(admin()).with(csrf())).andExpect(status().isConflict());
        catalog.transition(event,"PUBLISHED",OWNER);
        mvc.perform(post(url).with(admin()).with(csrf())).andExpect(status().isConflict());
        catalog.transition(event,"CLOSED",OWNER);
        mvc.perform(post(url).with(admin())).andExpect(status().isForbidden());
        mvc.perform(post(url).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
        catalog.archive(event,OWNER);
        mvc.perform(post(url).with(admin()).with(csrf())).andExpect(status().isConflict());
        UUID ended=event();catalog.transition(ended,"PUBLISHED",OWNER);catalog.transition(ended,"CLOSED",OWNER);
        jdbc.update("UPDATE event SET sale_start_time=now()-interval '2 hours',sale_end_time=now()-interval '1 hour' WHERE id=?",ended);
        mvc.perform(post("/api/admin/catalog/events/"+ended+"/reopen").with(admin()).with(csrf())).andExpect(status().isConflict());
        jdbc.update("UPDATE event SET event_time=now()-interval '30 minutes' WHERE id=?",ended);
        mvc.perform(post("/api/admin/catalog/events/"+ended+"/reopen").with(admin()).with(csrf())).andExpect(status().isConflict());
        assertEquals("CLOSED",jdbc.queryForObject("SELECT publication FROM event WHERE id=?",String.class,ended));
    }
    String contentBody(long revision) throws Exception {
        return json.writeValueAsString(Map.of("revision",revision,"description","Updated description","category","Theatre","posterUrl","https://example.com/new.png"));
    }
    @Test void publishedContentChangesPreserveBookingsAndRejectStaleWrites() throws Exception {
        UUID event=event(),seat=seat(event);catalog.transition(event,"PUBLISHED",OWNER);holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat));var booking=pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id());
        var before=jdbc.queryForMap("SELECT name,event_time,sale_end_time,fee_minor FROM event WHERE id=?",event);
        mvc.perform(put("/api/admin/catalog/events/"+event+"/content").with(admin()).with(csrf()).contentType("application/json").content(contentBody(1).replace("{","{\"feeMinor\":1,"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/catalog/events/"+event+"/content").with(admin()).with(csrf()).contentType("application/json").content(contentBody(1))).andExpect(status().isOk());
        mvc.perform(get("/api/events/"+event)).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated description"));
        assertEquals(before,jdbc.queryForMap("SELECT name,event_time,sale_end_time,fee_minor FROM event WHERE id=?",event));
        assertEquals(14845,pricing.receipt(booking.id(),OWNER).totalMinor());
        mvc.perform(put("/api/admin/catalog/events/"+event+"/content").with(admin()).with(csrf()).contentType("application/json").content(contentBody(1))).andExpect(status().isConflict());
    }
    @Test void archiveHidesInventoryStopsSalesAndPreservesReceipts() throws Exception {
        UUID event=event(),seat=seat(event);catalog.transition(event,"PUBLISHED",OWNER);holds.holdSeat(seat,OWNER);
        var quote=pricing.quote(OWNER,List.of(seat));var booking=pricing.confirm(OWNER,List.of(seat),UUID.randomUUID().toString(),quote.id());
        mvc.perform(post("/api/admin/catalog/events/"+event+"/archive").with(admin()).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/events/"+event)).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/"+event+"/seats")).andExpect(status().isNotFound());
        mvc.perform(get("/api/events")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(event.toString()))));
        assertEquals(14845,pricing.receipt(booking.id(),OWNER).totalMinor());
        UUID free=jdbc.queryForList("SELECT id FROM seat WHERE event_id=? AND status='AVAILABLE'",UUID.class,event).getFirst();
        assertThrows(com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException.class,()->holds.holdSeat(free,OWNER));
        mvc.perform(put("/api/admin/catalog/events/"+event+"/content").with(admin()).with(csrf()).contentType("application/json").content(contentBody(2))).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/catalog/events/"+event).with(admin()).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/catalog/events/"+event+"/publish").with(admin()).with(csrf())).andExpect(status().isConflict());
    }
    @Test void deleteDraftRemovesOnlyItsInventoryAndRetainsVenueAndAudit() throws Exception {
        UUID event=event();UUID venue=jdbc.queryForObject("SELECT venue_id FROM event WHERE id=?",UUID.class,event);
        mvc.perform(delete("/api/admin/catalog/events/"+event).with(admin()).with(csrf())).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM seat WHERE event_id=?",Integer.class,event));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM event WHERE id=?",Integer.class,event));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM venue WHERE id=?",Integer.class,venue));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM catalog_audit WHERE resource_id=? AND action='DELETE_DRAFT'",Integer.class,event));
    }
    @Test void contentArchiveAndDeleteRequireAdminAndCsrf() throws Exception {
        UUID event=event();var customer=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(OWNER.toString()).roles("CUSTOMER");
        mvc.perform(put("/api/admin/catalog/events/"+event+"/content").with(customer).with(csrf()).contentType("application/json").content(contentBody(0))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/catalog/events/"+event+"/content").with(admin()).contentType("application/json").content(contentBody(0))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/catalog/events/"+event+"/archive").with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/catalog/events/"+event+"/archive").with(admin())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/catalog/events/"+event).with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/catalog/events/"+event).with(admin())).andExpect(status().isForbidden());
    }
}
