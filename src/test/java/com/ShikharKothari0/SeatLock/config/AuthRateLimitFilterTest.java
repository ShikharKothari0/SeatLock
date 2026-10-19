package com.ShikharKothari0.SeatLock.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTest {
    @Test void limitsAuthAttemptsByAddressWithoutBlockingPublicReads() throws Exception {
        var filter = new AuthRateLimitFilter();
        var passed = new AtomicInteger();
        for (int i=0; i<31; i++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setServletPath("/api/auth/login"); request.setRemoteAddr("192.0.2.1");
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->passed.incrementAndGet());
            assertEquals(i<30 ? 200 : 429,response.getStatus());
        }
        assertEquals(30,passed.get());
        var request = new MockHttpServletRequest("GET","/api/events"); request.setRemoteAddr("192.0.2.1");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->passed.incrementAndGet());
        assertEquals(31,passed.get());
    }
}
