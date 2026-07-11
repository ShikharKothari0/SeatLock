package com.ShikharKothari0.SeatLock.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Collections;

@Service
public class RedisLockService {
    private static final Logger log =  LoggerFactory.getLogger(RedisLockService.class);
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> holdSeatScript;


    public RedisLockService(StringRedisTemplate redisTemplate, RedisScript<Long> holdSeatScript) {
        this.redisTemplate = redisTemplate;
        this.holdSeatScript = holdSeatScript;
    }

    @CircuitBreaker(name = "redisLock", fallbackMethod = "acquireLockFallback")
    public boolean acquireLock(String key, String value, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean acquireLockFallback(String key, String value, Duration ttl, Throwable ex) {
        log.warn(
                "Circuit breaker OPEN — could not acquire Redis lock for key={}. " +
                        "Returning false to caller. error={}",
                key, ex.getMessage()
        );
        return false;
    }

    @CircuitBreaker(name = "redisLock", fallbackMethod = "releaseLockFallback")
    public void releaseLock(String key) {
        redisTemplate.delete(key);
    }

    public void releaseLockFallback(String key, Throwable ex) {
        log.warn(
                "Circuit breaker OPEN — could not release Redis lock for key={}. " +
                        "Lock will expire via TTL. error={}",
                key, ex.getMessage()
        );
        // TTL based expiry handles eventual cleanup; so, therefore no action needed
    }

    @CircuitBreaker(name = "redisLock", fallbackMethod = "getLockOwnerFallback")
    public String getLockOwner(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public String getLockOwnerFallback(String key, Throwable ex) {
        log.warn(
                "Circuit breaker OPEN — could not read Redis lock owner for key={}. " +
                        "Returning null to caller. error={}",
                key, ex.getMessage()
        );
        return null;
    }

    @CircuitBreaker(name = "redisLock", fallbackMethod = "acquireSeatHoldFallback")
    public boolean acquireSeatHold(String seatId, String userId, Duration ttl) {
        String key = "seat:lock:" + seatId;
        Long result = redisTemplate.execute(
                holdSeatScript,
                Collections.singletonList(key),
                userId,
                String.valueOf(ttl.getSeconds())
        );
        return result != null && result == 1L;
    }

    public boolean acquireSeatHoldFallback(
            String seatId, String userId, Duration ttl, Throwable ex
    ) {
        log.warn(
                "Circuit breaker OPEN — Redis unavailable for seat hold. " +
                        "Falling back to DB-only optimistic locking for seatId={}. error={}",
                seatId, ex.getMessage()
        );
        return true;
    }
}
