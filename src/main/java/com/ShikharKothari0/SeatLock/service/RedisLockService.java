package com.ShikharKothari0.SeatLock.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Collections;

@Service
public class RedisLockService {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> holdSeatScript;


    public RedisLockService(StringRedisTemplate redisTemplate, RedisScript<Long> holdSeatScript) {
        this.redisTemplate = redisTemplate;
        this.holdSeatScript = holdSeatScript;
    }

    public boolean acquireLock(String key, String value, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseLock(String key) {
        redisTemplate.delete(key);
    }

    public String getLockOwner(String key) {
        return redisTemplate.opsForValue().get(key);
    }

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
}
