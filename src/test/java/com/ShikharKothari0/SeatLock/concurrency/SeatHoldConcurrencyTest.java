package com.ShikharKothari0.SeatLock.concurrency;

import com.ShikharKothari0.SeatLock.config.RedisTestContainer;
import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import com.ShikharKothari0.SeatLock.service.SeatHoldService;
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
import java.util.List;
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
public class SeatHoldConcurrencyTest {
    private static final Logger log = LoggerFactory.getLogger(SeatHoldConcurrencyTest.class);

    // containers

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

    // autowired beans

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // test setup

    @BeforeEach
    void resetState() {
        // reset all seats back to AVAILABLE and clear holdExpiresAt
        List<Seat> allSeats = seatRepository.findAll();
        allSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);
        });
        seatRepository.saveAll(allSeats);

        // flush all Redis keys so no stale locks interfere with this test run
        Set<String> lockKeys = redisTemplate.keys("seat:lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }

        log.info("Test state reset: {} seats set to AVAILABLE, Redis flushed", allSeats.size());
    }

    // concurrency test

    @Test
    void exactly100ThreadsShouldSucceedWhenHolding100SeatsWithin500Concurrent()
            throws InterruptedException {

        // 1. load seats from DB
        List<Seat> seats = seatRepository.findAll();
        int totalSeats = seats.size();

        assertThat(totalSeats)
                .as("Seed data must provide at least 100 seats for this test to be meaningful")
                .isGreaterThanOrEqualTo(100);

        List<UUID> seatIds = seats.stream()
                .map(Seat::getId)
                .limit(100)
                .toList();

        // 2. setting up 500 threads: 5 threads competing per seat
        int totalThreads = 500;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        // 3. submit all tasks before releasing the start gate
        for (int i = 0; i < totalThreads; i++) {
            // thread i and thread i+100 both compete for seatIds[i % 100]
            // so every seat has exactly 5 threads contending for it
            UUID seatId = seatIds.get(i % 100);
            UUID userId = UUID.randomUUID();

            executor.submit(() -> {
                try {
                    startGate.await();           // all threads wait here
                    seatHoldService.holdSeat(seatId, userId);
                    successCount.incrementAndGet();
                } catch (SeatNotAvailableException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                    log.error("Unexpected error in concurrency test thread: {}", e.getMessage(), e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 4. release all threads at once
        startGate.countDown();

        // 5. wait for all threads to finish (30s timeout)
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

        // 6. shut down thread pool
        executor.shutdown();

        // 7. assertions
        log.info("Concurrency test results — success: {}, failure: {}, unexpected: {}",
                successCount.get(), failureCount.get(), unexpectedErrorCount.get());

        assertThat(completed)
                .as("All 500 threads should complete within 30 seconds — timeout suggests deadlock")
                .isTrue();

        assertThat(unexpectedErrorCount.get())
                .as("No thread should throw anything other than SeatNotAvailableException")
                .isZero();

        assertThat(successCount.get())
                .as("Exactly 100 holds should succeed — one per seat")
                .isEqualTo(100);

        assertThat(failureCount.get())
                .as("Exactly 400 holds should fail — the losing thread for each seat")
                .isEqualTo(400);

        // 8. verify database state directly
        long heldSeatsInDb = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.HELD)
                .count();

        assertThat(heldSeatsInDb)
                .as("Database must show exactly 100 HELD seats — zero overselling")
                .isEqualTo(100);

        long availableSeatsInDb = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .count();

        assertThat(availableSeatsInDb)
                .as("Remaining seats must all be AVAILABLE")
                .isEqualTo(totalSeats - 100);
    }
}
