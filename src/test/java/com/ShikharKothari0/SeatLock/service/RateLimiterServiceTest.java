package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.config.RedisTestContainer;
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
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RateLimiterServiceTest {
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

    @Autowired private RateLimiterService rateLimiterService;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final String TEST_USER = "rate-limit-test-user";

    @BeforeEach
    void resetState() {
        Set<String> keys = redisTemplate.keys("ratelimit:hold:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void allowsRequestsUpToLimitThenRejects() {
        // application.properties: max-requests=5, window-seconds=10

        for (int i = 1; i <= 5; i++) {
            boolean allowed = rateLimiterService.isAllowed(TEST_USER);
            assertThat(allowed)
                    .as("Request %d of 5 must be allowed", i)
                    .isTrue();
        }

        // the 6th request within the same window must be rejected
        boolean sixthRequest = rateLimiterService.isAllowed(TEST_USER);
        assertThat(sixthRequest)
                .as("6th request within the window must be rejected")
                .isFalse();
    }

    @Test
    void differentUsersHaveIndependentLimits() {
        String userA = "user-a";
        String userB = "user-b";

        // exhaust user A's limit
        for (int i = 0; i < 5; i++) {
            rateLimiterService.isAllowed(userA);
        }
        assertThat(rateLimiterService.isAllowed(userA))
                .as("User A must be rate-limited after 5 requests")
                .isFalse();

        // user B must be completely unaffected
        assertThat(rateLimiterService.isAllowed(userB))
                .as("User B must have an independent limit, unaffected by User A")
                .isTrue();
    }
}
