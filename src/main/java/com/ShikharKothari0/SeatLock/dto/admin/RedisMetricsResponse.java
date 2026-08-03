package com.ShikharKothari0.SeatLock.dto.admin;

public record RedisMetricsResponse(
    boolean connected,
    long activeLocks,
    long memoryUsedBytes,
    long totalKeys,
    double hitRatio,
    long connectedClients
) {}