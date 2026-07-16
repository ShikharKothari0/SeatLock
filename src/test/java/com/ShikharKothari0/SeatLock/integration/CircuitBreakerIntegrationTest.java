package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.service.SeatHoldService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.time.Duration;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CircuitBreakerIntegrationTest {
    private static final Logger log =
            LoggerFactory.getLogger(CircuitBreakerIntegrationTest.class);

    // dedicated Redis container for this test only
    // NOT the singleton as we need to stop/start it mid-test
    private static final int REDIS_PORT = 16379;

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd->cmd.withHostConfig(
                        new com.github.dockerjava.api.model.HostConfig().withPortBindings(
                            new com.github.dockerjava.api.model.PortBinding(
                                com.github.dockerjava.api.model.Ports.Binding.bindPort(REDIS_PORT),
                                new com.github.dockerjava.api.model.ExposedPort(6379)))));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("seatlock_test")
                    .withUsername("admin")
                    .withPassword("password");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_PORT);    // Fixed port
        // no Kafka needed for circuit breaker testing
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private com.ShikharKothari0.SeatLock.repository.SeatRepository seatRepository;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private static final UUID SEAT_A1 =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TEST_USER =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void resetState() {
        // reset circuit breaker to CLOSED before each test
        circuitBreakerRegistry.circuitBreaker("redisLock").reset();

        // reset seats
        var allSeats = seatRepository.findAll();
        allSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);
        });
        seatRepository.saveAll(allSeats);

        // flush Redis
        Set<String> lockKeys = redisTemplate.keys("seat:lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    @Test
    void circuitBreakerOpensFallsBackToDbAndRecoversWhenRedisReturns()
            throws InterruptedException
    {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("redisLock");

        // log the actual config being used
        log.info("Circuit breaker config — waitDurationInOpenState={}",
                cb.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState());

        // verify circuit starts CLOSED
        assertThat(cb.getState())
                .as("Circuit must start CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // Step 1: stop Redis to simulate an outage
        log.info("Stopping Redis container to simulate outage...");
        redis.stop();

        // Step 2: fire enough requests to open the circuit
        // need minimum-number-of-calls=5 with failure-rate >= 50%
        int requestsFired = 0;
        for (int i = 0; i < 10; i++) {
            try {
                seatHoldService.holdSeat(SEAT_A1, TEST_USER);
                requestsFired++;
            } catch (Exception e) {
                requestsFired++;
                // SeatNotAvailableException expected on repeat attempts
                // to same seat — use different seats for each attempt
            }
        }
        log.info("Fired {} requests with Redis down", requestsFired);

        // Step 3: verify circuit is now OPEN
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(cb.getState())
                                .as("Circuit must open after repeated Redis failures")
                                .isIn(
                                        CircuitBreaker.State.OPEN,
                                        CircuitBreaker.State.HALF_OPEN
                                )
                );

        log.info("Circuit breaker state after Redis outage: {}", cb.getState());

        // Step 4: verify fallback works while circuit is OPEN
        // holds should succeed via DB-only optimistic locking fallback
        // use a fresh seat that wasn't used in the loop above
        UUID SEAT_A2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        // reset seat A2 specifically
        seatRepository.findById(SEAT_A2).ifPresent(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);
            seatRepository.save(seat);
        });

        // this should succeed via fallback
        seatHoldService.holdSeat(SEAT_A2, TEST_USER);

        // verify seat A2 is HELD in Postgres (fallback wrote to DB)
        var seatA2 = seatRepository.findById(SEAT_A2);
        assertThat(seatA2).isPresent();
        assertThat(seatA2.get().getStatus())
                .as("Seat must be HELD via DB fallback even when Redis is down")
                .isEqualTo(SeatStatus.HELD);

        // Step 5: restart Redis
        log.info("Restarting Redis container...");
        redis.start();

        // Step 6: wait for circuit to transition to HALF_OPEN then CLOSED
        await()
                .atMost(Duration.ofSeconds(30)) // 30s open state + buffer
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    try {
                        // pick a fresh AVAILABLE seat each poll so probes reach Redis
                        // instead of dying early on SeatNotAvailableException
                        var seat = seatRepository.findAll().stream()
                                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                                .findFirst()
                                .orElseThrow();
                        seatHoldService.holdSeat(seat.getId(), TEST_USER);
                    } catch (Exception ignored) {
                        // early probes may be rejected while OPEN / saturated HALF_OPEN
                    }
                    assertThat(cb.getState())
                            .as("Circuit must recover to CLOSED after Redis returns")
                            .isEqualTo(CircuitBreaker.State.CLOSED);
                });

        log.info("Circuit breaker recovered: state={}", cb.getState());

        // Step 7: verify normal Redis-backed holds work after recovery
        UUID SEAT_A3 = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        seatRepository.findById(SEAT_A3).ifPresent(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);
            seatRepository.save(seat);
        });

        seatHoldService.holdSeat(SEAT_A3, TEST_USER);

        // verify Redis key exists — proves Redis-backed hold was used and not the fallback
        String lockOwner = redisTemplate.opsForValue()
                .get("seat:lock:" + SEAT_A3);

        assertThat(lockOwner)
                .as("After recovery, Redis lock key must exist — " +
                        "proves Redis-backed path is active again, not fallback")
                .isEqualTo(TEST_USER.toString());

        log.info("Circuit breaker full lifecycle test passed: " +
                "CLOSED → OPEN (Redis down) → HALF_OPEN → CLOSED (Redis recovered)");
    }
}
