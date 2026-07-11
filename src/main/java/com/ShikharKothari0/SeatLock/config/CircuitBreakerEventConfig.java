package com.ShikharKothari0.SeatLock.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerEventConfig {
    private static final Logger log =
            LoggerFactory.getLogger(CircuitBreakerEventConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerEventConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void registerEventListeners() {
        CircuitBreaker redisLockCb =
                circuitBreakerRegistry.circuitBreaker("redisLock");

        redisLockCb.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "[CIRCUIT BREAKER] redisLock state transition: {} → {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()
                ))
                .onError(event -> log.debug(
                        "[CIRCUIT BREAKER] redisLock recorded failure: {}",
                        event.getThrowable().getMessage()
                ))
                .onSuccess(event -> log.debug(
                        "[CIRCUIT BREAKER] redisLock recorded success"
                ));
    }
}
