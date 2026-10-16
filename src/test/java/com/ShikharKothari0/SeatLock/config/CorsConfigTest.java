package com.ShikharKothari0.SeatLock.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    private static class Registry extends CorsRegistry {
        CorsConfiguration api() { return getCorsConfigurations().get("/api/**"); }
    }

    @Test
    void localFrontendOriginsAreAllowedButUntrustedOriginsAreRejected() {
        Registry registry = new Registry();
        new CorsConfig().addCorsMappings(registry);
        CorsConfiguration cors = registry.api();
        for (String origin : new String[] { "http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:3000", "http://127.0.0.1:3000" }) {
            assertEquals(origin, cors.checkOrigin(origin));
        }
        assertNull(cors.checkOrigin("https://untrusted.example"));
        assertNull(cors.checkOrigin("http://127.0.0.1:9999"));
    }
}
