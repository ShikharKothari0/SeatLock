package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.config.RedisTestContainer;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
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
class CacheStampedeBaselineTest {
    private static final Logger log =
            LoggerFactory.getLogger(CacheStampedeBaselineTest.class);

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
    @Autowired private SeatRepository seatRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final UUID EVENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void resetState() {
        // ensure cache is empty before the stampede test
        Set<String> cacheKeys = redisTemplate.keys("seats:event:*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
        }
    }

    @Test
    void measureQueryCountUnderConcurrentCacheMiss() throws InterruptedException {
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

        log.info("BASELINE (no stampede protection): " +
                "{} concurrent requests completed", completedRequests.get());

        // Note: without protection, we expect this scenario to generate
        // close to 100 independent Postgres queries which can be verified via Hibernate SQL logging
    }
}
