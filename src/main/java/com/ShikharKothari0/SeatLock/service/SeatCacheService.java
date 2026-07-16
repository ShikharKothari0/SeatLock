package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SeatCacheService {
    private static final Logger log = LoggerFactory.getLogger(SeatCacheService.class);

    private static final String CACHE_KEY_PREFIX = "seats:event:";
    static final Duration CACHE_TTL = Duration.ofSeconds(45);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // counters for cache observability
    private final Counter hitCounter;
    private final Counter missCounter;

    public SeatCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        this.hitCounter = Counter.builder("seatlock.cache.hits")
                .description("Number of Redis cache hits for seat listings")
                .tag("cache", "seats")
                .register(meterRegistry);

        this.missCounter = Counter.builder("seatlock.cache.misses")
                .description("Number of Redis cache misses for seat listings")
                .tag("cache", "seats")
                .register(meterRegistry);
    }

    // cache key builder
    public String cacheKey(UUID eventId) {
        return CACHE_KEY_PREFIX + eventId;
    }

    // read from cache
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getCachedSeatsFallback")
    public Optional<List<SeatResponse>> getCachedSeats(UUID eventId) {
        String key = cacheKey(eventId);
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            missCounter.increment();
            log.debug("Cache MISS — key={}", key);
            return Optional.empty();
        }

        try {
            List<SeatResponse> seats = objectMapper.readValue(
                    json,
                    new TypeReference<List<SeatResponse>>() {}
            );
            hitCounter.increment();
            log.debug("Cache HIT — key={} seats={}", key, seats.size());
            return Optional.of(seats);

        } catch (JsonProcessingException e) {
            log.error("Cache deserialization failed for key={}: {}", key, e.getMessage());
            // treat as miss — stale/corrupt cache entry
            bestEffortDelete(key);
            missCounter.increment();
            return Optional.empty();
        }
    }

    public Optional<List<SeatResponse>> getCachedSeatsFallback(
            UUID eventId, Throwable ex
    ) {
        log.warn(
                "redisCache circuit breaker OPEN — " +
                        "getCachedSeats falling back to Postgres for eventId={}. error={}",
                eventId, ex.getMessage()
        );
        missCounter.increment();
        return Optional.empty();    // empty = caller queries Postgres
    }

    // write to cache
    @CircuitBreaker(name = "redisCache", fallbackMethod = "cacheSeatsFallback")
    public void cacheSeats(UUID eventId, List<SeatResponse> seats) {
        String key = cacheKey(eventId);
        try {
            String json = objectMapper.writeValueAsString(seats);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
            log.debug("Cache WRITE — key={} seats={} ttl={}s",
                    key, seats.size(), CACHE_TTL.getSeconds());

        } catch (JsonProcessingException e) {
            log.error("Cache serialization failed for key={}: {}", key, e.getMessage());
            // don't cache — caller will get Postgres data without caching
        }
    }

    public void cacheSeatsFallback(
            UUID eventId, List<SeatResponse> seats, Throwable ex
    ) {
        log.warn(
                "redisCache circuit breaker OPEN — " +
                        "cacheSeats skipped for eventId={}. error={}",
                eventId, ex.getMessage()
        );
        // do nothing — system works without cache, just slower
    }

    // eviction (to be used later for cache invalidation logic)
    // internal calls (self-invocations) such as in case of "getCachedSeats" (where bestEffortDelete() is executed)
    // bypasses the Spring AOP proxy, so the @CircuitBreaker would be silently skipped, so instead we use
    // "bestEffortDelete()" so that we can still use "evictCache()" in the cache-invalidation phase
    @CircuitBreaker(name = "redisCache", fallbackMethod = "evictCacheFallback")
    public void evictCache(UUID eventId) {
        String key = cacheKey(eventId);
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Cache EVICT — key={} deleted={}", key, deleted);
    }

    public void evictCacheFallback(UUID eventId, Throwable ex) {
        log.warn(
                "redisCache circuit breaker OPEN — " +
                        "evictCache skipped for eventId={}. " +
                        "TTL will clean up stale entry in {}s. error={}",
                eventId, CACHE_TTL.getSeconds(), ex.getMessage()
        );
        // TTL handles eventual cleanup — no action needed
    }

    // internal best-effort delete used only from within this class,
    // where the AOP proxy is unavailable. Never throws.
    private void bestEffortDelete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("Cache EVICT (inline, corrupt entry) — key={} deleted={}", key, deleted);
        } catch (Exception ex) {
            log.warn("Inline cache evict failed for key={} — TTL ({}s) will clean up. error={}",
                    key, CACHE_TTL.getSeconds(), ex.getMessage());
        }
    }
}
