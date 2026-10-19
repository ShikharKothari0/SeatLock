package com.ShikharKothari0.SeatLock.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// Bounded per-instance abuse protection. A shared gateway limit is needed for multiple replicas.
final class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long expires, int count) {}
    private final Map<String,Window> windows = new HashMap<>();
    private synchronized boolean allow(String address) {
        long now = System.nanoTime();
        windows.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
        Window window = windows.get(address);
        if (window == null) {
            if (windows.size() >= 10000) return false;
            windows.put(address, new Window(now + 60_000_000_000L, 1)); return true;
        }
        if (window.count() >= 30) return false;
        windows.put(address, new Window(window.expires(), window.count()+1)); return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && ("/api/auth/login".equals(request.getServletPath()) || "/api/auth/register".equals(request.getServletPath())) && !allow(request.getRemoteAddr())) {
            response.setStatus(429); response.setHeader("Retry-After","60"); response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many sign-in attempts. Please wait one minute.\"}"); return;
        }
        chain.doFilter(request,response);
    }
}
