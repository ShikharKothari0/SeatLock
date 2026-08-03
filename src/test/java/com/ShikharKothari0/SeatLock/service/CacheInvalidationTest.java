package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.config.RedisTestContainer;
import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class CacheInvalidationTest {
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

    @Autowired private SeatHoldService seatHoldService;
    @Autowired private SeatService seatService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final UUID SEAT_A1 =
            UUID.fromString("00000001-0000-0000-0000-000000000000");
    private static final UUID EVENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TEST_USER =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void resetState() {
        var allSeats = seatRepository.findAll();
        allSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);
        });
        seatRepository.saveAll(allSeats);

        Set<String> lockKeys = redisTemplate.keys("seat:lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }

        Set<String> cacheKeys = redisTemplate.keys("seats:event:*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
        }
    }

    @Test
    void cacheReflectsSeatStatusChangeImmediatelyAfterHold() {
        // Step 1: populate the cache with a read showing AVAILABLE
        List<SeatResponse> beforeHold = seatService.getSeats(EVENT_ID, null);

        SeatResponse seatA1Before = beforeHold.stream()
                .filter(s -> s.id().equals(SEAT_A1))
                .findFirst()
                .orElseThrow();

        assertThat(seatA1Before.status())
                .as("Seat must show AVAILABLE before any hold")
                .isEqualTo(SeatStatus.AVAILABLE);

        // confirm the cache key actually exists now
        String cacheKey = "seats:event:" + EVENT_ID;
        assertThat(redisTemplate.hasKey(cacheKey))
                .as("Cache must be populated after the first read")
                .isTrue();

        // Step 2: hold the seat, thus invalidating the cache
        seatHoldService.holdSeat(SEAT_A1, TEST_USER);

        // Step 3: confirm the cache key was evicted, not just stale
        assertThat(redisTemplate.hasKey(cacheKey))
                .as("Cache key must be evicted immediately after seat status changes")
                .isFalse();

        // Step 4: read again, so must reflect the new HELD status
        // this read will be a cache miss, repopulating from Postgres
        List<SeatResponse> afterHold = seatService.getSeats(EVENT_ID, null);

        SeatResponse seatA1After = afterHold.stream()
                .filter(s -> s.id().equals(SEAT_A1))
                .findFirst()
                .orElseThrow();

        assertThat(seatA1After.status())
                .as("Seat must show HELD immediately after hold — " +
                        "no 45-second staleness window")
                .isEqualTo(SeatStatus.HELD);

        // Step 5: confirm cache was repopulated by the read in Step 4
        assertThat(redisTemplate.hasKey(cacheKey))
                .as("Cache must be repopulated after the post-invalidation read")
                .isTrue();
    }

    @Test
    void differentSeatsAreUnaffectedByOtherSeatsInvalidation() {
        UUID seatB = UUID.fromString("00000002-0000-0000-0000-000000000000");

        // populate cache
        seatService.getSeats(EVENT_ID, null);

        // hold seat A1 — invalidates the WHOLE event's cache key
        seatHoldService.holdSeat(SEAT_A1, TEST_USER);

        // read again — seat B should still show AVAILABLE despite
        // sharing the same event-level cache key as seat A1
        List<SeatResponse> seats = seatService.getSeats(EVENT_ID, null);

        SeatResponse seatBResponse = seats.stream()
                .filter(s -> s.id().equals(seatB))
                .findFirst()
                .orElseThrow();

        assertThat(seatBResponse.status())
                .as("Seat B must correctly show AVAILABLE — " +
                        "invalidating the event cache doesn't corrupt unrelated seat data")
                .isEqualTo(SeatStatus.AVAILABLE);
    }
}
