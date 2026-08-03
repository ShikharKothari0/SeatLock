package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CircuitBreakerIntegrationTest {
    private static final Logger log =
            LoggerFactory.getLogger(CircuitBreakerIntegrationTest.class);

    // dedicated non-singleton Redis container
    // NOT RedisTestContainer.getInstance(). This one can be stopped/started
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
                            new com.github.dockerjava.api.model.HostConfig()
                                    .withPortBindings(
                                            new com.github.dockerjava.api.model.PortBinding(
                                                    com.github.dockerjava.api.model.Ports.Binding.bindPort(16379),
                                                    new com.github.dockerjava.api.model.ExposedPort(6379)
                                            )
                                    )
                    )
                    );

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
        registry.add("spring.data.redis.port", () -> 16379);    // Fixed port
        // no Kafka needed for circuit breaker testing, so point at nowhere valid
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    // stable test seat UUIDs
    // A4 and A5: used in Step 2 (failure loop) — will be HELD via fallback
    private static final UUID SEAT_A4 =
            UUID.fromString("00000004-0000-0000-0000-000000000000");

    private static final UUID SEAT_A5 =
            UUID.fromString("00000005-0000-0000-0000-000000000000");

    // A6: used ONLY in Step 8 (recovery proof) and is never touched in Step 2
    private static final UUID SEAT_A6 =
            UUID.fromString("00000006-0000-0000-0000-000000000000");

    private static final UUID TEST_USER =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

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
        log.info("Circuit breaker state at test start: {}", cb.getState());
        log.info("Circuit breaker waitDuration (attempt 1): {}ms",
                cb.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState()
                        .apply(1));
        log.info("Circuit breaker slidingWindowSize: {}",
                cb.getCircuitBreakerConfig().getSlidingWindowSize());
        log.info("Circuit breaker minimumNumberOfCalls: {}",
                cb.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        log.info("Circuit breaker permittedCallsInHalfOpen: {}",
                cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());

        // verify circuit starts CLOSED
        assertThat(cb.getState())
                .as("Circuit must start CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // Step 1: stop Redis to simulate an outage
        log.info("Stopping Redis container to simulate outage...");
        redis.stop();

        // Step 2: fire enough requests to open the circuit
        // need minimum-number-of-calls=5 with failure-rate >= 50%
        // Alternate A4/A5 so each seat's first attempt genuinely tries Redis
        // (which fails with a connection error, counted by the circuit breaker)
        // before the DB status check can short-circuit things.
        // Pattern per seat:
        //   attempt 1 → Redis fails → acquireSeatHoldFallback(true) → DB holds seat
        //   attempt 2 → circuit OPEN → fallback immediately → DB rejects (seat HELD)
        List<UUID> failureSeats = List.of(SEAT_A4, SEAT_A5);
        int requestsFired = 0;
        for (int i = 0; i < 10; i++) {
            UUID seatToUse = failureSeats.get(i % 2);
            try {
                seatHoldService.holdSeat(seatToUse, TEST_USER);
            } catch (Exception ignored) {
                // SeatNotAvailableException is expected on repeat attempts
            }
                requestsFired++;
        }
        log.info("Fired {} requests with Redis down - circuit state now: {}", requestsFired, cb.getState());

        // Step 3: verify circuit is now OPEN
        // With the default sliding-window-size=10 and minimum-number-of-calls=5,
        // after 10 requests where Redis was down the circuit must have opened.
        // We give it 5 seconds for the state evaluation to complete.
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(cb.getState())
                                .as("Circuit must open after repeated Redis connection failures")
                                .isIn(
                                        CircuitBreaker.State.OPEN,
                                        CircuitBreaker.State.HALF_OPEN
                                )
                );

        log.info("Circuit breaker state after Redis outage: {}", cb.getState());

        // A4 must be HELD via DB fallback (first loop iteration held it)
        // acquireSeatHoldFallback returns true → SeatHoldService proceeds to DB
        // This proves the fallback path works and the users were not blocked
        var seatA4 = seatRepository.findById(SEAT_A4);
        assertThat(seatA4).isPresent();
        assertThat(seatA4.get().getStatus())
                .as("Seat A4 must be HELD via DB fallback even when Redis is down, this proves the fallback is working correctly")
                .isEqualTo(SeatStatus.HELD);

        // Step 4: restart Redis
        log.info("Restarting Redis container...");
        redis.stop();                   // ensure fully stopped first
        Thread.sleep(1000);       // brief pause for clean container shutdown

        redis.start();
        log.info("Redis restarted on fixed port 16379");

        // give Redis time to initialize and accept connections.
        // Lettuce auto-reconnects to localhost:16379 (fixed port — same address
        // as before the stop) — no connection factory reconfiguration needed.
        Thread.sleep(2000);
        await()
                .atMost(Duration.ofSeconds(15))
                        .pollInterval(Duration.ofSeconds(1))
                                .until(() -> {
                                    try {
                                        redisTemplate.opsForValue().get("health-check");
                                        return true;
                                    } catch (Exception e) {
                                        log.debug("Redis not ready yet: {}", e.getMessage());
                                        return false;
                                    }
                                });

        log.info("Redis confirmed ready for connections");

        // Step 5: Manually drive the circuit breaker to HALF-OPEN and then CLOSED
        // We can't rely on the automatic wait duration timer because:
        // 1. @DynamicPropertySource doesn't override Resilience4j registry config
        // 2. application.properties has wait-duration=30s which would make this test take 30+ seconds in the recovery phase alone
        // Instead we manually transition states and fire real probe calls, which is actually a more thorough test.
        // It proves the circuit correctly responds to real Redis success signals, not just a timer

        // Manually Transitioning from OPEN to HALF-OPEN
        log.info("Manually transitioning circuit breaker: {} → HALF_OPEN",
                cb.getState());
        cb.transitionToHalfOpenState();

        assertThat(cb.getState())
                .as("Circuit must be in HALF_OPEN after manual transition")
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // fire real holdSeat calls against AVAILABLE seats so the circuit breaker
        // records genuine successes which are  needed to transition from  HALF-OPEN to CLOSED
        int permittedCalls = cb.getCircuitBreakerConfig()
                .getPermittedNumberOfCallsInHalfOpenState();

        log.info("Need {} successful probe calls to close circuit breaker", permittedCalls);

        // find seats that are still AVAILABLE (not A4/A5 which were held in step 2)
        var availableSeats = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .limit((long) permittedCalls + 3) // a few extras in case some are rejected
                .toList();

        assertThat(availableSeats)
                .as("Must have enough AVAILABLE seats for recovery probes, ensure seed data has seats beyond A4 and A5")
                .hasSizeGreaterThanOrEqualTo(permittedCalls);

        int successfulProbes = 0;
        for (var seat : availableSeats) {
            if (successfulProbes >= permittedCalls) break;
            if (cb.getState() == CircuitBreaker.State.CLOSED) break;

            try {
                seatHoldService.holdSeat(seat.getId(), TEST_USER);
                successfulProbes++;
                log.debug("Successful probe {}/{} — seatId={} cb.state={}",
                        successfulProbes, permittedCalls, seat.getId(), cb.getState());
            } catch (Exception e) {
                log.debug("Probe rejected (cb may still be transitioning): {} and state={}",
                        e.getMessage(), cb.getState());
            }
        }

        log.info("Probe loop complete — successfulProbes={} cb.state={}",
                successfulProbes, cb.getState());

        // Step 6 assertion: circuit must be CLOSED
        assertThat(cb.getState())
                .as("Circuit must be CLOSED after " + successfulProbes +
                        " successful Redis-backed probe calls")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        log.info("Circuit breaker recovered to CLOSED");

        // Step 7 (proof): verify A6 hold produces a Redis key
        // A6 was never touched in step 2 so guaranteed AVAILABLE
        // holding it after recovery must produce a Redis lock key,
        // proving the Redis-backed code path is active (not the DB fallback)
        var seatA6 = seatRepository.findById(SEAT_A6)
                .orElseThrow(() -> new IllegalStateException(
                        "Seat A6 (000006-...) not found — check V2__seed_data.sql includes this UUID"
                ));

        assertThat(seatA6.getStatus())
                .as("Seat A6 must be AVAILABLE before recovery proof — " +
                        "it must not have been touched in Step 2")
                .isEqualTo(SeatStatus.AVAILABLE);

        seatHoldService.holdSeat(SEAT_A6, TEST_USER);

        // the definitive recovery proof: Redis lock key must exist
        // if fallback were still active, this key would NOT exist
        // (DB fallback skips the Redis write)
        String lockOwner = redisTemplate.opsForValue()
                .get("seat:lock:" + SEAT_A6);

        assertThat(lockOwner)
                .as("After recovery, Redis lock key must exist for seat A6; proves Redis-backed hold path is active again, not DB fallback." +
                        "DB fallback skips Redis write. real Redis hold writes this key")
                .isEqualTo(TEST_USER.toString());

        log.info("CircuitBreakerIntegrationTest PASSED");
        log.info("Lifecycle proven: CLOSED → OPEN (Redis down, Fallback active) → HALF_OPEN(manual) → CLOSED (Redis recovered), {} probe successes",
                successfulProbes);
        log.info("Recovery proof: seat:lock:{} exists in Redis with owner={}",
                SEAT_A6, TEST_USER);
    }
}
