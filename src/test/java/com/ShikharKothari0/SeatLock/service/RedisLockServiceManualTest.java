package com.ShikharKothari0.SeatLock.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisLockServiceManualTest {
    @Autowired
    private RedisLockService redisLockService;

    @Test
    void acquireAndInspectLock() {
        boolean acquired = redisLockService.acquireLock("seat:lock:test-seat-1", "user-abc", Duration.ofMinutes(5));
        assertThat(acquired).isTrue();

        String owner = redisLockService.getLockOwner("seat:lock:test-seat-1");
        assertThat(owner).isEqualTo("user-abc");
    }
}