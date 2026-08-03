package com.ShikharKothari0.SeatLock.dto.admin;

import java.time.Instant;
import java.util.List;

public record MetricStreamSnapshot(
    MetricsOverviewResponse overview,
    CacheMetricsResponse cache,
    RedisMetricsResponse redis,
    List<CircuitBreakerMetricsResponse> circuitBreakers,
    Instant timestamp
) {}