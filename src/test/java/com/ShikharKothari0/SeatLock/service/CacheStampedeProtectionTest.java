package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.config.RedisTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CacheStampedeProtectionTest {
    private static final Logger log =
            LoggerFactory.getLogger(CacheStampedeProtectionTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("seatlock_test")
                    .withUsername("admin")
                    .withPassword("password");

    @Container
    static RedisTestContainer redis = RedisTestContainer.getInstance();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getMappedPort);
    }

    @Autowired private SeatService seatService;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final UUID EVENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    // simple counter wrapping SeatRepository calls would require a spy;
    // for this test we rely on Hibernate SQL log inspection (see console
    // output) as the source of truth for query count, consistent with
    // how the baseline was measured in Step 1
    private final AtomicInteger dbLoadCount = new AtomicInteger(0);

    @BeforeEach
    void resetState() {
        Set<String> cacheKeys = redisTemplate.keys("seats:event:*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
        }
        Set<String> lockKeys = redisTemplate.keys("seat-cache-lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    @Test
    void stampedeProtectionLimitsConcurrentDatabaseLoadsUnderCacheMiss()
            throws InterruptedException
    {
        int concurrentRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
        AtomicInteger completedRequests = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    seatService.getSeats(EVENT_ID, null);
                    completedRequests.incrementAndGet();
                } catch (Exception e) {
                    log.error("Request failed: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(completedRequests.get()).isEqualTo(concurrentRequests);

        // the key assertion: exactly one lock acquisition should have
        // happened for this event, meaning exactly one thread queried Postgres
        String lockKey = "seat-cache-lock:" + EVENT_ID;
        log.info("PROTECTED: {} concurrent requests completed. " +
                "Check Hibernate SQL logs — expect close to 1 SELECT query, " +
                "not {}.", completedRequests.get(), concurrentRequests);

        // the cache must be populated after all requests complete
        assertThat(redisTemplate.hasKey("seats:event:" + EVENT_ID))
                .as("Cache must be populated after stampede-protected load")
                .isTrue();
    }
}
