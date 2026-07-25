package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.config.RedisTestContainer;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.repository.BookingRepository;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class IntegrationTestBase {
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

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(2));   // explicit startup timeout

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");

        // force 5s open-state wait for this test class only
        registry.add(
                "resilience4j.circuitbreaker.instances.redisLock.wait-duration-in-open-state",
                () -> "5s"
        );
        registry.add(
                "resilience4j.circuitbreaker.instances.redisLock.sliding-window-size",
                () -> "5"
        );
        registry.add(
                "resilience4j.circuitbreaker.instances.redisLock.minimum-number-of-calls",
                () -> "3"
        );
    }

    // shared autowired fields

    @Autowired
    protected SeatRepository seatRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    // stable test UUIDs matching V2__seed_data.sql

    protected static final java.util.UUID SEAT_A1 =
            java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    protected static final java.util.UUID SEAT_A2 =
            java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    protected static final java.util.UUID SEAT_A3 =
            java.util.UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    protected static final java.util.UUID TEST_USER =
            java.util.UUID.fromString("33333333-3333-3333-3333-333333333333");

    // reset state before every test

    @BeforeEach
    void baseResetState() {
        // reset all seats to AVAILABLE
        var allSeats = seatRepository.findAll();
        allSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);
        });
        seatRepository.saveAll(allSeats);

        // delete all bookings
        bookingRepository.deleteAll();
        for (String pattern : List.of(
                "seat:lock:*",          // distributed seat hold locks
                "seats:event:*",        // cache-aside seat listings
                "seat-cache-lock:*",    // stampede protector locks
                "ratelimit:hold:*"      // per-user rate limit buckets
        )) {

            // flush all Redis lock keys
            Set<String> lockKeys = redisTemplate.keys(pattern);
            if (lockKeys != null && !lockKeys.isEmpty()) {
                redisTemplate.delete(lockKeys);
            }
        }
    }
}
