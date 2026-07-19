package com.ShikharKothari0.SeatLock.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class RateLimiterService {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:hold:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    @Value("${seatlock.ratelimit.hold.max-requests}")
    private int maxRequests;

    @Value("${seatlock.ratelimit.hold.window-seconds}")
    private int windowSeconds;

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            RedisScript<Long> rateLimitScript
    ) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
    }

    @CircuitBreaker(name = "redisLock", fallbackMethod = "isAllowedFallback")
    public boolean isAllowed(String userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;

        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds)
        );

        boolean allowed = result != null && result == 1L;

        if (!allowed) {
            log.warn("Rate limit exceeded — userId={} maxRequests={} windowSeconds={}",
                    userId, maxRequests, windowSeconds);
        }

        return allowed;
    }

    public boolean isAllowedFallback(String userId, Throwable ex) {
        log.warn(
                "Circuit breaker OPEN — rate limiting unavailable for userId={}. " +
                        "Failing open (allowing request) since Redis unavailability " +
                        "shouldn't block legitimate users. error={}",
                userId, ex.getMessage()
        );
        return true;   // fail open
    }
}
