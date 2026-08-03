package com.ShikharKothari0.SeatLock.dto.admin;

public record CircuitBreakerMetricsResponse(
    String name,
    String state,
    double failureRate,
    long failedCalls,
    long successfulCalls,
    long bufferedCalls
) {}