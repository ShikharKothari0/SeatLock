package com.ShikharKothari0.SeatLock.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class RedisLockService {
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
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
}
