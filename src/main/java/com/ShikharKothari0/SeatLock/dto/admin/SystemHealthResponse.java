package com.ShikharKothari0.SeatLock.dto.admin;

import java.util.List;

public record SystemHealthResponse(
    String status,
    double cpuUsage,
    long heapUsedBytes,
    long heapMaxBytes,
    int liveThreads,
    int hikariActiveConnections,
    int hikariPendingConnections,
    List<CircuitBreakerMetricsResponse> circuitBreakers
) {}