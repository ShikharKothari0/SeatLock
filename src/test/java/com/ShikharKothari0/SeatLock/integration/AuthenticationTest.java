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
class AuthenticationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    static final String PASSWORD = "Account-test-password-27";
    record Login(String email, String id, MockHttpSession session) {}
    Login register() throws Exception {
        String email = UUID.randomUUID()+"@example.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD,"displayName","Test account","role","ADMIN"))))
            .andExpect(status().isCreated());
        var session = login(email);
        var me = mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("role").value("CUSTOMER")).andReturn();
        return new Login(email,json.readTree(me.getResponse().getContentAsString()).get("id").asText(),session);
    }
    MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password",PASSWORD))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    @Test void anonymousAndCsrfBoundaries() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isOk());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/health")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(jsonPath("components").doesNotExist());
    }
    @Test void realCsrfTokenAndSessionRotation() throws Exception {
        Login account = register();
        var bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        String oldId = session.getId();
        var token = json.readTree(bootstrap.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asText(),token.get("token").asText()).param("email",account.email()).param("password",PASSWORD)).andExpect(status().isNoContent());
        assertNotEquals(oldId, session.getId());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("id").value(account.id()));
    }
    @Test void passwordHashValidationAndDuplicateEmail() throws Exception {
        Login account = register();
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,UUID.fromString(account.id()));
        assertTrue(hash.startsWith("$2a$12$")); assertNotEquals(PASSWORD,hash);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",account.email().toUpperCase(Locale.ROOT),"password",PASSWORD,"displayName","Other")))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"email\":\"short@example.test\",\"password\":\"short\",\"displayName\":\"Test\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email",account.email()).param("password","wrong")).andExpect(status().isUnauthorized());
    }
    @Test void rolesAndLogout() throws Exception {
        Login account = register();
        mvc.perform(get("/api/admin/health").session(account.session())).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").session(account.session())).andExpect(status().isForbidden());
        jdbc.update("UPDATE app_user SET role='ADMIN' WHERE id=?",UUID.fromString(account.id()));
        MockHttpSession admin = login(account.email());
        mvc.perform(get("/actuator/metrics").session(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(admin).with(csrf())).andExpect(status().isNoContent());
        assertTrue(admin.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void callerCannotChooseReservationOwner() throws Exception {
        Login owner = register(), other = register();
        UUID seat = UUID.randomUUID(); UUID event = UUID.fromString("22222222-2222-2222-2222-222222222222");
        jdbc.update("INSERT INTO seat(id,event_id,seat_number,section,status) VALUES (?,?,?,'AUTH','AVAILABLE')", seat,event,seat.toString().substring(0,12));
        mvc.perform(post("/api/seats/"+seat+"/hold").session(owner.session()).with(csrf()).contentType("application/json").content("{\"userId\":\""+other.id()+"\"}")).andExpect(status().isOk());
        assertEquals(owner.id(),jdbc.queryForObject("SELECT held_by::text FROM seat WHERE id=?",String.class,seat));
        mvc.perform(get("/api/seats/holds").session(other.session()).param("userId",owner.id())).andExpect(jsonPath("holds").isEmpty());
        mvc.perform(delete("/api/seats/"+seat+"/hold").session(other.session()).with(csrf()).param("userId",owner.id())).andExpect(status().isConflict());
        jdbc.update("INSERT INTO event_price VALUES (?,'AUTH',50000) ON CONFLICT DO NOTHING",event);
        var quoteResult=mvc.perform(post("/api/bookings/quote").session(owner.session()).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("seatIds",List.of(seat))))).andExpect(status().isOk()).andReturn();
        String quoteId=json.readTree(quoteResult.getResponse().getContentAsString()).get("id").asText();
        String payload = json.writeValueAsString(Map.of("userId",owner.id(),"seatIds",List.of(seat),"idempotencyKey",UUID.randomUUID(),"quoteId",quoteId));
        mvc.perform(post("/api/bookings/confirm").session(other.session()).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isNotFound());
        var result = mvc.perform(post("/api/bookings/confirm").session(owner.session()).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isOk()).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/bookings/"+id).session(other.session()).param("userId",owner.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings").session(other.session()).param("userId",owner.id())).andExpect(jsonPath("items").isEmpty());
        mvc.perform(get("/api/bookings/"+id).session(owner.session())).andExpect(status().isOk());
    }
}
